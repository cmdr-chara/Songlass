package dev.cmdrchara.songlass.core.model

import kotlin.math.abs

/**
 * One full, unfiltered scan. With [stableIds], IDs must already have been validated by the track
 * identity registry: only a unique canonical ID may resolve, including to a reindexed URI.
 * Legacy sources instead use conservative location/metadata matching, not an audio fingerprint.
 */
class TrackAvailability(tracks: List<Track>, private val stableIds: Boolean = false) {
    private val byId = if (stableIds) tracks.groupBy { it.id } else emptyMap()
    private val byIdentity = if (stableIds) emptyMap() else tracks.groupBy { it.id to it.uri }
    private val byPath = if (stableIds) emptyMap() else tracks.filter { it.path != null }.groupBy { it.path }

    fun resolve(track: Track): Track? {
        if (stableIds) return byId[track.id]?.singleOrNull()
        val sameIdentity = byIdentity[track.id to track.uri]
        if (sameIdentity != null) {
            // A conflicting reused identity cannot fall back to the path and bypass the guard.
            return sameIdentity.singleOrNull()?.takeIf { track.isSameFileAs(it) }
        }
        val path = track.path ?: return null
        return byPath[path]?.singleOrNull()?.takeIf {
            it.title == track.title && it.artist == track.artist && it.album == track.album &&
                abs(it.durationMs.coerceAtLeast(0) - track.durationMs.coerceAtLeast(0)) <= 1_000
        }
    }
}
