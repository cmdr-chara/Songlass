package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** On-demand lookup: imported file, embedded/local lyrics, then the optional online source. */
class LyricsRepository(
    private val local: LyricsSource = LyricsSource.Empty,
    private val remote: LyricsSource = LyricsSource.Empty,
    private val store: KeyValueStore = InMemoryKeyValueStore(),
    /** Partitions cached results when the configured online providers change. */
    private val cacheVersion: () -> Int = { 0 },
) {
    private data class Entry(val track: Track, val result: LyricsResult, val sourceVersion: Int)
    private data class Cache(val entries: List<Entry> = emptyList(), val importRevision: Long = 0)

    private val cache = MutableStateFlow(Cache())

    suspend fun load(track: Track, refresh: Boolean = false): LyricsResult {
        while (true) {
            currentCoroutineContext().ensureActive()
            val sourceVersion = cacheVersion()
            var failed = false
            fun imported(): Lyrics? = try {
                store.getString(importKey(track))?.let { LyricsParser.parse(it, LyricsOrigin.File) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                null
            }
            imported()?.let { return LyricsResult.Found(it) }
            if (!refresh) cached(track, sourceVersion)?.let { return it }

            suspend fun read(source: LyricsSource): Lyrics? = try {
                source.load(track).also { currentCoroutineContext().ensureActive() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                failed = true
                null
            }
            val localLyrics = read(local)
            // An import made while local I/O was suspended also avoids an unnecessary online request.
            val lyrics = imported() ?: localLyrics ?: read(remote)
            val result = lyrics?.let(LyricsResult::Found)
                ?: if (failed) LyricsResult.Failed else LyricsResult.NotFound

            if (sourceVersion != cacheVersion()) continue
            while (true) {
                currentCoroutineContext().ensureActive()
                val revision = cache.value.importRevision
                // A slow source must never replace a file imported while that source was loading.
                val latest = imported()?.let(LyricsResult::Found) ?: result
                val final = if (latest == LyricsResult.NotFound && failed) LyricsResult.Failed else latest
                if (remember(track, final, revision, sourceVersion)) {
                    if (sourceVersion == cacheVersion()) return final
                    break
                }
            }
        }
    }

    /**
     * Forgets what was found so far, after the stored imports were replaced (a backup restore).
     * Loads already running notice and look the imports up again instead of remembering stale results.
     */
    fun reload() {
        cache.update { Cache(importRevision = it.importRevision + 1) }
    }

    /** Persists valid LRC, TTML or plain text for this URI, preserving an existing import on invalid input. */
    fun import(track: Track, text: String): Boolean {
        val lyrics = LyricsParser.parse(text, LyricsOrigin.File) ?: return false
        try {
            store.putString(importKey(track), text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        }
        cache.update { current ->
            Cache(
                entries = (current.entries.filterNot { it.track.uri == track.uri } +
                    Entry(track, LyricsResult.Found(lyrics), cacheVersion())).takeLast(CACHE_CAPACITY),
                importRevision = current.importRevision + 1,
            )
        }
        return true
    }

    private fun cached(track: Track, sourceVersion: Int): LyricsResult? {
        while (true) {
            val current = cache.value
            val entry = current.entries.firstOrNull { it.track == track && it.sourceVersion == sourceVersion } ?: return null
            val updated = current.copy(entries = current.entries.filterNot { it.track == track } + entry)
            if (cache.compareAndSet(current, updated)) return entry.result
        }
    }

    private fun remember(track: Track, result: LyricsResult, revision: Long, sourceVersion: Int): Boolean {
        while (true) {
            val current = cache.value
            if (current.importRevision != revision) return false
            if (result == LyricsResult.Failed) return true
            val updated = current.copy(
                entries = (current.entries.filterNot { it.track == track } + Entry(track, result, sourceVersion))
                    .takeLast(CACHE_CAPACITY),
            )
            if (cache.compareAndSet(current, updated)) return true
        }
    }

    private fun importKey(track: Track) = "lyrics.import.${track.uri}"

    private companion object {
        const val CACHE_CAPACITY = 32
    }
}
