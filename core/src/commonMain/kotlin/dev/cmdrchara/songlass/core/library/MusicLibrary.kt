package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track

data class Album(
    val title: String?,
    val artist: String?,
    /** Ordered by disc, track number, then title. */
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class Artist(
    val name: String?,
    val albums: List<Album>,
    val tracks: List<Track>,
)

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/**
 * In-memory index of the user's music, grouped into albums and artists.
 *
 * Albums are keyed by (album title, album artist or artist), so two albums with the same
 * title by different artists stay separate. Artists are grouped the same way, which keeps
 * compilation guests out of the artist list, as in Apple Music.
 *
 * An album only counts as one when its tracks have an album tag and the library holds at
 * least [MIN_ALBUM_TRACKS] of them: a lone song (a single, or one track kept from a bigger
 * album) or an untagged file is listed as a song, not as an album.
 */
class MusicLibrary(tracks: List<Track>) {

    val tracks: List<Track> = tracks.sortedWith(compareBy { it.title.sortKey() })

    val albums: List<Album> = tracks
        .filter { it.album != null }
        .groupBy { it.album to it.groupingArtist }
        .filterValues { it.size >= MIN_ALBUM_TRACKS }
        .map { (key, albumTracks) ->
            Album(title = key.first, artist = key.second, tracks = albumTracks.sortedWith(albumTrackOrder))
        }
        .sortedWith(compareBy<Album, String?>(nullsLast()) { it.title?.sortKey() }
            .thenBy(nullsLast()) { it.artist?.sortKey() })

    val artists: List<Artist> = tracks
        .groupBy { it.groupingArtist }
        .map { (name, artistTracks) ->
            Artist(
                name = name,
                albums = albums.filter { it.artist == name },
                tracks = artistTracks.sortedWith(compareBy { it.title.sortKey() }),
            )
        }
        .sortedWith(compareBy(nullsLast()) { it.name?.sortKey() })

    /** The album [track] belongs to, or `null` if it is a lone song (see [albums]). */
    fun albumOf(track: Track): Album? =
        albums.firstOrNull { it.title == track.album && it.artist == track.groupingArtist }

    /** The artist [track] is listed under (album artist when tagged, as in [artists]). */
    fun artistOf(track: Track): Artist? = artists.firstOrNull { it.name == track.groupingArtist }

    /** Case-insensitive substring search over titles, artists and albums. A blank query matches nothing. */
    fun search(query: String): SearchResults {
        val needle = query.trim().sortKey()
        if (needle.isEmpty()) return SearchResults()
        fun String?.matches() = this != null && sortKey().contains(needle)
        return SearchResults(
            tracks = tracks.filter { it.title.matches() || it.artist.matches() || it.album.matches() },
            albums = albums.filter { it.title.matches() || it.artist.matches() },
            artists = artists.filter { it.name.matches() },
        )
    }

    companion object {
        const val MIN_ALBUM_TRACKS = 2

        // TODO: also ignore diacritics and leading articles ("The") once a multiplatform
        //  normalization approach is chosen; common Kotlin has no Unicode normalizer.
        private fun String.sortKey(): String = lowercase()

        private val albumTrackOrder: Comparator<Track> =
            compareBy<Track> { it.discNumber ?: 0 }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.sortKey() }
    }
}
