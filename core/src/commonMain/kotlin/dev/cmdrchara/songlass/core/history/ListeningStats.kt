package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.library.*
import dev.cmdrchara.songlass.core.model.Track

enum class StatsPeriod(val days: Int?) { Week(7), Month(30), Year(365), AllTime(null) }

/** [rank] is a competition rank: equal play counts share a position (1, 2, 2, 4). */
data class RankedTrack(val track: Track, val plays: Int, val rank: Int = 1)
data class RankedArtist(val artist: Artist, val plays: Int, val rank: Int = 1)
data class RankedAlbum(val album: Album, val plays: Int, val rank: Int = 1)

/** One figure compared with the same figure in the period just before. */
data class StatChange(val previous: Int, val current: Int) {
    val difference: Int get() = current - previous
}

/** Totals of the previous period of the same length, against the current ones. */
data class PeriodComparison(val plays: StatChange, val tracks: StatChange, val artists: StatChange)

/** Counts qualified plays, never claims elapsed listening time from track durations. */
data class ListeningStats(
    val totalPlays: Int,
    val differentTracks: Int,
    val differentArtists: Int,
    val tracks: List<RankedTrack>,
    val artists: List<RankedArtist>,
    val albums: List<RankedAlbum>,
    /** False when dated tracking began after the period's start: earlier days are unknown, not silent. */
    val coversWholePeriod: Boolean = true,
) {
    companion object {
        fun build(library: MusicLibrary, history: HistorySnapshot, period: StatsPeriod, nowMs: Long, fromMs: Long? = null): ListeningStats {
            val from = period.days?.let { days -> fromMs ?: (nowMs - days * 86_400_000L) }
            val counts = from?.let { start ->
                history.datedPlays.filter { it.atMs in start..nowMs }.groupingBy { it.trackId }.eachCount()
            } ?: history.playCounts
            val rankedTracks = ranked(library.tracks.mapNotNull { track -> counts[track.id]?.takeIf { it > 0 }?.let { track to it } },
                { it.title.lowercase() }) { track, plays, rank -> RankedTrack(track, plays, rank) }
            val rankedArtists = ranked(library.artists.mapNotNull { artist ->
                artist.tracks.sumOf { counts[it.id] ?: 0 }.takeIf { it > 0 }?.let { artist to it }
            }, { it.name.orEmpty().lowercase() }) { artist, plays, rank -> RankedArtist(artist, plays, rank) }
            val rankedAlbums = ranked(library.albums.mapNotNull { album ->
                album.tracks.sumOf { counts[it.id] ?: 0 }.takeIf { it > 0 }?.let { album to it }
            }, { it.title.orEmpty().lowercase() }) { album, plays, rank -> RankedAlbum(album, plays, rank) }
            val covered = from == null || history.trackingStartedMs?.let { it <= from } ?: true
            return ListeningStats(rankedTracks.sumOf { it.plays }, rankedTracks.size, rankedArtists.size,
                rankedTracks, rankedArtists, rankedAlbums, covered)
        }

        /**
         * The period [previousFromMs] until [fromMs] (exclusive) against [current]. Null for All time,
         * or when dated history does not cover that whole earlier period (tracking began later, or the
         * oldest events were pruned): a partial count would read as a drop that never happened.
         */
        fun compare(library: MusicLibrary, history: HistorySnapshot, period: StatsPeriod, current: ListeningStats,
                    previousFromMs: Long, fromMs: Long): PeriodComparison? {
            if (period.days == null || previousFromMs >= fromMs) return null
            if (history.trackingStartedMs?.let { it > previousFromMs } == true) return null
            val oldest = history.datedPlays.minOfOrNull { it.atMs }
            if (history.datedPlays.size >= ListeningHistory.MAX_DATED_PLAYS && (oldest == null || oldest > previousFromMs)) return null
            if (oldest == null && history.trackingStartedMs == null) return null
            val previous = build(library, history, period, fromMs - 1, previousFromMs)
            return PeriodComparison(
                StatChange(previous.totalPlays, current.totalPlays),
                StatChange(previous.differentTracks, current.differentTracks),
                StatChange(previous.differentArtists, current.differentArtists),
            )
        }

        private fun <K, R> ranked(entries: List<Pair<K, Int>>, name: (K) -> String, make: (K, Int, Int) -> R): List<R> {
            val sorted = entries.sortedWith(compareByDescending<Pair<K, Int>> { it.second }.thenBy { name(it.first) })
            var rank = 0
            return sorted.mapIndexed { index, (item, plays) ->
                if (index == 0 || sorted[index - 1].second != plays) rank = index + 1
                make(item, plays, rank)
            }
        }
    }
}
