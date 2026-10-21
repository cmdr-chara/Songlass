package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.model.Track

internal fun sourceTrackOf(track: Track) = SourceTrack(
    id = track.id,
    uri = track.uri,
    path = track.path,
    title = track.title,
    artist = track.artist,
    album = track.album,
    durationMs = track.durationMs.coerceAtLeast(0),
)

/** What a backed-up track id means on this device. */
internal sealed interface Resolution {
    /** The same id names the same file: references stay as they are. */
    data object Same : Resolution

    /** The file now has another id ([to]); references move to it. */
    data class Moved(val to: Track) : Resolution

    /** Not on this device, and the old id names nothing here: references are kept untouched. */
    data object Missing : Resolution

    /** The old id now names a different file, or the match is ambiguous: references are dropped. */
    data object Conflict : Resolution
}

/**
 * Decides, per referenced id, whether it can still be trusted. MediaStore ids are not stable
 * across devices/reindexing. A known positive duration and unchanged recording metadata are
 * required even at the same path; the v1 format cannot prove that conflicting tags were edited
 * on the original file rather than supplied by its replacement. A move additionally needs one
 * unclaimed target at the exact path. These are conservative hints, never an audio fingerprint.
 */
internal object TrackMatching {
    /** Two files with the same length within this are treated as having the same duration. */
    private const val DURATION_TOLERANCE_MS = 1_000L

    fun resolve(referenced: Collection<String>, sources: Map<String, SourceTrack>, current: List<Track>): Map<String, Resolution> {
        val idCandidates = current.groupBy { it.id }
        val byId = idCandidates.mapNotNull { (id, candidates) -> candidates.singleOrNull()?.let { id to it } }.toMap()
        val occupiedIds = idCandidates.keys
        val byPath = current.filter { it.path != null }.groupBy { it.path!! }
        val result = LinkedHashMap<String, Resolution>()
        val pending = mutableListOf<SourceTrack>()

        for (id in referenced.distinct()) {
            val source = sources[id]
            val here = byId[id]
            when {
                // No identity to compare with: an id that names a file today could be anything.
                source == null -> result[id] = unresolved(id, occupiedIds)
                // Duplicate current IDs are malformed/ambiguous; never let associateBy pick one.
                id in occupiedIds && here == null -> result[id] = Resolution.Conflict
                here != null && sameRecording(source, here) -> result[id] = Resolution.Same
                else -> pending += source
            }
        }

        val claimed = result.filterValues { it == Resolution.Same }.keys
        val wanted = LinkedHashMap<String, MutableList<SourceTrack>>()
        for (source in pending) {
            // Only an unambiguous path, on this side and on the file's, and not a file another id already is.
            val target = source.path?.takeIf { it.isNotBlank() }?.let { byPath[it] }?.singleOrNull()
                ?.takeIf { it.id in byId && it.id !in claimed && sameRecording(source, it) }
            if (target == null) result[source.id] = unresolved(source.id, occupiedIds)
            else wanted.getOrPut(target.id) { mutableListOf() } += source
        }
        for ((targetId, claimants) in wanted) {
            if (claimants.size == 1) result[claimants[0].id] = Resolution.Moved(byId.getValue(targetId))
            else claimants.forEach { result[it.id] = unresolved(it.id, occupiedIds) }
        }
        return result
    }

    private fun unresolved(id: String, occupiedIds: Set<String>) =
        if (id in occupiedIds) Resolution.Conflict else Resolution.Missing

    /**
     * Missing optional tags agree only when both are absent; they supply no positive evidence.
     * A missing title or duration cannot establish identity. Same-ID moves remain possible when
     * all these facts agree, but even a matching ID/URI/path never overrides contradictory facts.
     */
    private fun sameRecording(source: SourceTrack, here: Track): Boolean {
        if (source.title.isBlank() || source.title != here.title || source.artist != here.artist || source.album != here.album) return false
        if (source.durationMs <= 0 || here.durationMs <= 0) return false
        val difference = if (source.durationMs >= here.durationMs) source.durationMs - here.durationMs else here.durationMs - source.durationMs
        return difference <= DURATION_TOLERANCE_MS
    }
}
