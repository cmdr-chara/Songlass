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

/**
 * A folder of music files, shown as a playlist: many people organise music in folders
 * ("Chill", "Gym") rather than by album.
 */
data class Playlist(
    /** Full folder path, unique. */
    val id: String,
    /** The folder's own name. */
    val name: String,
    /** Parent folders below the common music root, outermost first (empty for top-level ones). */
    val location: List<String>,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

/**
 * A folder in the music tree. Unlike [Playlist], it also exists when it only holds other
 * folders (e.g. `Soundtracks/JoJo Parts 1-5`), so the library can be browsed like folders.
 */
data class Folder(
    /** Full folder path, unique; the same id as the [Playlist] of its own files. */
    val id: String,
    val name: String,
    /** Files directly inside, in playlist order. */
    val tracks: List<Track>,
    /** Sub-folders that hold music somewhere below, sorted by name. */
    val children: List<Folder>,
) {
    /** Everything below: its own files first, then each sub-folder in order. */
    val allTracks: List<Track> = tracks + children.flatMap { it.allTracks }

    val durationMs: Long get() = allTracks.sumOf { it.durationMs }

    /**
     * Up to [limit] tracks from different albums, to draw a mosaic cover; fewer when the
     * folder has fewer distinct albums.
     */
    fun coverTracks(limit: Int = 4): List<Track> =
        allTracks.distinctBy { (it.album ?: it.id) to it.groupingArtist }.take(limit)
}

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
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

    /**
     * One playlist per folder that directly contains music. Names drop the root shared by all
     * folders (e.g. `Music/`), so `Music/Chill` and `Music/Gym` become "Chill" and "Gym".
     */
    val playlists: List<Playlist> = run {
        val byFolder = tracks.filter { !it.folder.isNullOrBlank() }.groupBy { it.folder!!.trim('/') }
        val root = commonParent(byFolder.keys.map { it.split('/') })
        byFolder.map { (path, folderTracks) ->
            val segments = path.split('/').drop(root.size)
            Playlist(
                id = path,
                name = segments.lastOrNull() ?: path.substringAfterLast('/'),
                location = segments.dropLast(1),
                tracks = folderTracks.sortedWith(playlistTrackOrder(folderTracks)),
            )
        }.sortedWith(compareBy({ (it.location + it.name).joinToString("/").sortKey() }))
    }

    /**
     * The folder tree below the common music root (see [playlists]): top-level folders, each
     * with its sub-folders. Folders without music anywhere below are left out.
     */
    val folders: List<Folder> = run {
        val withMusic = playlists.associateBy { it.id }
        val rootSize = playlists.firstOrNull()?.let { it.id.split('/').size - it.location.size - 1 } ?: 0
        // Every folder on the way to a playlist, intermediate ones included.
        val paths = playlists.flatMap { playlist ->
            val segments = playlist.id.split('/')
            (rootSize + 1..segments.size).map { segments.take(it).joinToString("/") }
        }.toSet()
        fun build(path: String): Folder {
            val prefix = "$path/"
            val children = paths.filter { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
                .map(::build)
                .sortedBy { it.name.sortKey() }
            return Folder(
                id = path,
                name = withMusic[path]?.name ?: path.substringAfterLast('/'),
                tracks = withMusic[path]?.tracks.orEmpty(),
                children = children,
            )
        }
        paths.filter { it.split('/').size == rootSize + 1 }.map(::build).sortedBy { it.name.sortKey() }
    }

    private val foldersById: Map<String, Folder> = buildMap {
        fun index(folder: Folder) {
            put(folder.id, folder)
            folder.children.forEach(::index)
        }
        folders.forEach(::index)
    }

    /** The folder with this path (the id of a [Playlist] or of an intermediate folder). */
    fun folder(id: String): Folder? = foldersById[id]

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
            playlists = playlists.filter { it.name.matches() },
        )
    }

    companion object {
        const val MIN_ALBUM_TRACKS = 2

        // TODO: also ignore diacritics and leading articles ("The") once a multiplatform
        //  normalization approach is chosen; common Kotlin has no Unicode normalizer.
        private fun String.sortKey(): String = lowercase()

        /**
         * The folders' common parent, kept out of playlist names. A single folder keeps its own
         * name: the root is at most its parent.
         */
        private fun commonParent(paths: List<List<String>>): List<String> {
            if (paths.isEmpty()) return emptyList()
            val shortestParent = paths.minOf { it.size - 1 }
            var length = 0
            while (length < shortestParent && paths.all { it[length] == paths[0][length] }) length++
            return paths[0].take(length)
        }

        /** A folder that is one album keeps album order; a mixed folder is sorted by title. */
        private fun playlistTrackOrder(folderTracks: List<Track>): Comparator<Track> =
            if (folderTracks.map { it.album to it.groupingArtist }.distinct().size == 1) albumTrackOrder
            else compareBy { it.title.sortKey() }

        private val albumTrackOrder: Comparator<Track> =
            compareBy<Track> { it.discNumber ?: 0 }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.sortKey() }
    }
}
