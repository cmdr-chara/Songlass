package dev.cmdrchara.songlass.core.model

import kotlin.math.abs

/**
 * One full, unfiltered scan. A stale content URI is never reopened just because its media id
 * still exists. A unique unchanged recording at the same path may acquire a new platform id.
 * This is conservative metadata matching, not a fingerprint of the audio bytes.
 */
class TrackAvailability(tracks: List<Track>) {
    private val byIdentity = tracks.groupBy { it.id to it.uri }
    private val byPath = tracks.filter { it.path != null }.groupBy { it.path }

    fun resolve(track: Track): Track? {
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
