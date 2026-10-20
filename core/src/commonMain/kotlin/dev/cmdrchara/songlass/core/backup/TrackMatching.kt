package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.model.Track
import kotlin.math.abs

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
 * across reindexing, so an id is only kept when the file behind it is recognisably the same,
 * and only moved to a new id when exactly one file here has the same storage path.
 * An old id is never attached to a file it was not recognised on.
 */
internal object TrackMatching {
    /** Two files with the same length within this are treated as having the same duration. */
    private const val DURATION_TOLERANCE_MS = 1_000L

    fun resolve(referenced: Collection<String>, sources: Map<String, SourceTrack>, current: List<Track>): Map<String, Resolution> {
        val byId = current.associateBy { it.id }
        val byPath = current.filter { it.path != null }.groupBy { it.path!! }
        val result = LinkedHashMap<String, Resolution>()
        val pending = mutableListOf<SourceTrack>()

        for (id in referenced) {
            val source = sources[id]
            val here = byId[id]
            when {
                // No identity to compare with: an id that names a file today could be anything.
                source == null -> result[id] = if (here == null) Resolution.Missing else Resolution.Conflict
                here != null && sameFile(source, here) -> result[id] = Resolution.Same
                else -> pending += source
            }
        }

        val claimed = result.filterValues { it == Resolution.Same }.keys
        val wanted = LinkedHashMap<String, MutableList<SourceTrack>>()
        for (source in pending) {
            // Only an unambiguous path, on this side and on the file's, and not a file another id already is.
            val target = source.path?.let { byPath[it] }?.singleOrNull()?.takeIf { it.id !in claimed }
            if (target == null) result[source.id] = unresolved(source.id, byId)
            else wanted.getOrPut(target.id) { mutableListOf() } += source
        }
        for ((targetId, claimants) in wanted) {
            if (claimants.size == 1) result[claimants[0].id] = Resolution.Moved(byId.getValue(targetId))
            else claimants.forEach { result[it.id] = unresolved(it.id, byId) }
        }
        return result
    }

    private fun unresolved(id: String, byId: Map<String, Track>) =
        if (byId.containsKey(id)) Resolution.Conflict else Resolution.Missing

    /**
     * Same storage path means the same file (tags may have been edited since). Otherwise,
     * which also covers a file renamed under an unchanged id, everything the app can compare
     * has to agree.
     */
    private fun sameFile(source: SourceTrack, here: Track): Boolean {
        if (source.path != null && here.path != null && source.path == here.path) return true
        return source.title == here.title && source.artist == here.artist && source.album == here.album &&
            abs(source.durationMs - here.durationMs.coerceAtLeast(0)) <= DURATION_TOLERANCE_MS
    }
}
