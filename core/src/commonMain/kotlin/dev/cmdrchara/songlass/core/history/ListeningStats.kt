package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.library.*
import dev.cmdrchara.songlass.core.model.Track

enum class StatsPeriod(val days: Int?) { Week(7), Month(30), Year(365), AllTime(null) }
data class RankedTrack(val track: Track, val plays: Int)
data class RankedArtist(val artist: Artist, val plays: Int)
data class RankedAlbum(val album: Album, val plays: Int)

/** Counts qualified plays, never claims elapsed listening time from track durations. */
data class ListeningStats(
    val totalPlays: Int,
    val differentTracks: Int,
    val differentArtists: Int,
    val tracks: List<RankedTrack>,
    val artists: List<RankedArtist>,
    val albums: List<RankedAlbum>,
) {
    companion object {
        fun build(library: MusicLibrary, history: HistorySnapshot, period: StatsPeriod, nowMs: Long, fromMs: Long? = null): ListeningStats {
            val counts = period.days?.let { days ->
                val from = fromMs ?: (nowMs - days * 86_400_000L)
                history.datedPlays.filter { it.atMs in from..nowMs }.groupingBy { it.trackId }.eachCount()
            } ?: history.playCounts
            val rankedTracks = library.tracks.mapNotNull { track ->
                counts[track.id]?.takeIf { it > 0 }?.let { RankedTrack(track, it) }
            }.sortedWith(compareByDescending<RankedTrack> { it.plays }.thenBy { it.track.title.lowercase() })
            val rankedArtists = library.artists.mapNotNull { artist ->
                artist.tracks.sumOf { counts[it.id] ?: 0 }.takeIf { it > 0 }?.let { RankedArtist(artist, it) }
            }.sortedWith(compareByDescending<RankedArtist> { it.plays }.thenBy { it.artist.name.orEmpty().lowercase() })
            val rankedAlbums = library.albums.mapNotNull { album ->
                album.tracks.sumOf { counts[it.id] ?: 0 }.takeIf { it > 0 }?.let { RankedAlbum(album, it) }
            }.sortedWith(compareByDescending<RankedAlbum> { it.plays }.thenBy { it.album.title.orEmpty().lowercase() })
            return ListeningStats(rankedTracks.sumOf { it.plays }, rankedTracks.size, rankedArtists.size,
                rankedTracks, rankedArtists, rankedAlbums)
        }
    }
}
