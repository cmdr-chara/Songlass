package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.ReleaseDate
import dev.cmdrchara.songlass.core.model.Track

data class OnThisDay(val releases: List<Track>, val memories: List<Track>) {
    companion object {
        /** Release anniversaries require a full tag date; memories use actual dated listens.
         * [dateOf] supplies the device's local calendar, including DST, from the platform.
         */
        fun build(library: MusicLibrary, history: HistorySnapshot, today: ReleaseDate, dateOf: (Long) -> ReleaseDate): OnThisDay {
            fun ReleaseDate.matchesToday() = year < today.year && month != null && day != null && month == today.month && day == today.day
            val releases = library.tracks.filter { it.releaseDate?.matchesToday() == true }
                .sortedWith(compareBy<Track> { it.releaseDate?.year }.thenBy { it.title.lowercase() })
            val byId = library.tracks.associateBy { it.id }
            val memories = history.datedPlays.asSequence().filter { dateOf(it.atMs).matchesToday() }
                .mapNotNull { byId[it.trackId] }.distinctBy { it.id }.take(12).toList()
            return OnThisDay(releases.take(12), memories)
        }
    }
}
