package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AutomaticArtworkResult { Blocked, Existing, Local, CoolingDown, NoMatch, Ambiguous, Failed, Saved }

/** A background match may not make the looser guesses available to the manual picker. */
object AutomaticArtworkChoice {
    fun forTrack(track: Track, candidates: List<ArtworkCandidate>): ArtworkCandidate? {
        val requestedTitle = normalizeArtworkText(track.title)
        val requestedArtist = track.artist?.trim()?.takeUnless { it.equals("<unknown>", true) }
            ?.let(::normalizeArtworkText).orEmpty()
        val exact = candidates.filter { candidate ->
            val title = candidate.trackTitle?.let(::normalizeArtworkText).orEmpty()
            val artist = normalizeArtworkText(candidate.artist)
            title.isNotEmpty() && artist.isNotEmpty() && if (requestedArtist.isNotEmpty()) {
                title == requestedTitle && artist == requestedArtist
            } else {
                // A filename-derived title can carry the complete artist credit. With neither
                // a known artist nor that full credit, a plain song name is too ambiguous.
                requestedTitle == normalizeArtworkText("${candidate.trackTitle} ${candidate.artist}") ||
                    requestedTitle == normalizeArtworkText("${candidate.artist} ${candidate.trackTitle}")
            }
        }
        val album = track.album?.let(::normalizeArtworkText).orEmpty()
        val albumMatches = exact.filter { album.isNotEmpty() && normalizeArtworkText(it.album) == album }
        val choices = albumMatches.ifEmpty { exact }
        val releases = choices.groupBy { normalizeArtworkText(it.artist) to normalizeArtworkText(it.album) }
        return releases.values.singleOrNull()?.minByOrNull { it.providerId }
    }
}

/**
 * Missing-only automatic artwork, serialized to one network operation per controller.
 * Callers own lifecycle/availability/network policy and supply fresh guards after every suspend.
 * No-match/ambiguity is cooled down for a day; transport/decoding/storage errors for a minute.
 */
class AutomaticArtworkFetcher(
    private val search: suspend (Track) -> ArtworkSearchResult,
    private val downloader: ArtworkDownloader,
    private val downloaded: DownloadedArtworks,
    private val nowMs: () -> Long,
    private val validImage: suspend (ByteArray) -> Boolean,
) {
    private val mutex = Mutex()
    private val cooldowns = LinkedHashMap<String, Long>()

    suspend fun fetch(
        track: Track,
        canFetch: () -> Boolean,
        hasLocalCover: suspend () -> Boolean,
    ): AutomaticArtworkResult = mutex.withLock {
        val subject = ArtworkSubject.forTrack(track) ?: return AutomaticArtworkResult.Blocked
        val job = currentCoroutineContext()
        fun allowed() = job.isActive && canFetch()
        fun existing() = ArtworkCovers.downloadedForTrack(track, downloaded.state.value) != null
        fun coolDown(delayMs: Long) {
            val now = nowMs()
            cooldowns[subject.key] = if (now > Long.MAX_VALUE - delayMs) Long.MAX_VALUE else now + delayMs
            while (cooldowns.size > 256) cooldowns.remove(cooldowns.keys.first())
        }
        if (!allowed()) return AutomaticArtworkResult.Blocked
        try {
            if (!downloaded.loaded.value) downloaded.load()
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            if (existing()) return AutomaticArtworkResult.Existing
            if (hasLocalCover()) return AutomaticArtworkResult.Local
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            if (!downloaded.hasAutomaticCapacity) return AutomaticArtworkResult.Blocked
            if (cooldowns[subject.key]?.let { nowMs() < it } == true) return AutomaticArtworkResult.CoolingDown
            val response = search(track)
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            val found = when (val result = response) {
                is ArtworkSearchResult.Found -> result.candidates
                ArtworkSearchResult.NoMatch -> {
                    coolDown(NO_MATCH_COOLDOWN_MS)
                    return AutomaticArtworkResult.NoMatch
                }
                is ArtworkSearchResult.Failed -> {
                    coolDown(ERROR_COOLDOWN_MS)
                    return AutomaticArtworkResult.Failed
                }
            }
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            if (existing()) return AutomaticArtworkResult.Existing
            val choice = AutomaticArtworkChoice.forTrack(track, found) ?: run {
                coolDown(NO_MATCH_COOLDOWN_MS)
                return AutomaticArtworkResult.Ambiguous
            }
            val result = downloader.downloadImage(choice.imageUrl)
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            val bytes = (result as? ArtworkImageResult.Success)?.bytes
            val imageValid = bytes != null && validImage(bytes)
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            if (!imageValid) {
                coolDown(ERROR_COOLDOWN_MS)
                return AutomaticArtworkResult.Failed
            }
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            if (hasLocalCover()) return AutomaticArtworkResult.Local
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            if (existing()) return AutomaticArtworkResult.Existing
            val saved = downloaded.saveAutomaticIfAbsent(subject, checkNotNull(bytes)) { allowed() && !existing() }
            job.ensureActive()
            if (saved == null) {
                if (existing()) return AutomaticArtworkResult.Existing
                if (!allowed()) return AutomaticArtworkResult.Blocked
                coolDown(ERROR_COOLDOWN_MS)
                return AutomaticArtworkResult.Failed
            }
            coolDown(NO_MATCH_COOLDOWN_MS)
            AutomaticArtworkResult.Saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            job.ensureActive()
            if (!allowed()) return AutomaticArtworkResult.Blocked
            coolDown(ERROR_COOLDOWN_MS)
            AutomaticArtworkResult.Failed
        }
    }

    companion object {
        const val NO_MATCH_COOLDOWN_MS = 24L * 60 * 60 * 1000
        const val ERROR_COOLDOWN_MS = 60_000L
    }
}
