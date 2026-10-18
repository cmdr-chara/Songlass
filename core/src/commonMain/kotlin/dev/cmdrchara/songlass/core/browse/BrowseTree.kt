package dev.cmdrchara.songlass.core.browse

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.Folder
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.UserPlaylist

/** How a car screen or another media browser should lay a node's children out. */
enum class BrowseLayout { List, Grid }

/**
 * One entry of the tree that Android Auto and other media browsers see. [artworkTrack] is the
 * song whose cover stands for the node.
 */
data class BrowseNode(
    val id: String,
    val title: String,
    val subtitle: String?,
    val browsable: Boolean,
    val playable: Boolean,
    val artworkTrack: Track?,
    /** Layout for this node's children, when it has any. */
    val childLayout: BrowseLayout = BrowseLayout.List,
)

/** What to play for a chosen node. */
data class PlayRequest(val tracks: List<Track>, val startIndex: Int, val context: PlayContext?, val shuffle: Boolean = false)

/** Localised titles the tree needs (resources live with the platform). */
data class BrowseLabels(
    val home: String,
    val playlists: String,
    val albums: String,
    val artists: String,
    val favorites: String,
    val shuffleAll: String,
    val unknownArtist: String,
    val unknownAlbum: String,
    val songs: (Int) -> String,
)

/**
 * The browse tree for cars: four tabs (Home, Playlists, Albums, Artists), collections by their
 * [PlayContext] key and songs by their collection plus id, so choosing a song plays its whole
 * collection from there, as in the app.
 */
class BrowseTree(
    private val library: MusicLibrary,
    private val favorites: List<Track>,
    private val userPlaylists: List<UserPlaylist>,
    private val recent: List<PlayContext>,
    private val labels: BrowseLabels,
) {
    fun children(parentId: String): List<BrowseNode>? = when (parentId) {
        ROOT -> listOf(
            BrowseNode(HOME, labels.home, null, browsable = true, playable = false, artworkTrack = null),
            BrowseNode(PLAYLISTS, labels.playlists, null, browsable = true, playable = false, artworkTrack = null, childLayout = BrowseLayout.Grid),
            BrowseNode(ALBUMS, labels.albums, null, browsable = true, playable = false, artworkTrack = null, childLayout = BrowseLayout.Grid),
            BrowseNode(ARTISTS, labels.artists, null, browsable = true, playable = false, artworkTrack = null, childLayout = BrowseLayout.Grid),
        )
        HOME -> buildList {
            if (library.tracks.isNotEmpty()) {
                add(BrowseNode(SHUFFLE_ALL, labels.shuffleAll, labels.songs(library.tracks.size), browsable = false, playable = true, artworkTrack = library.tracks.first()))
            }
            if (favorites.isNotEmpty()) add(contextNode(PlayContext.Favorites)!!)
            recent.filter { it != PlayContext.Favorites }.distinct().take(MAX_RECENT).forEach { context -> contextNode(context)?.let(::add) }
        }
        PLAYLISTS -> userPlaylists.mapNotNull { contextNode(PlayContext.UserPlaylist(it.id)) } +
            library.folders.mapNotNull { contextNode(PlayContext.Playlist(it.id)) }
        ALBUMS -> library.albums.sortedBy { it.title?.lowercase() }.mapNotNull { contextNode(PlayContext.Album(it.title.orEmpty(), it.artist)) }
        ARTISTS -> library.artists.mapNotNull { contextNode(PlayContext.Artist(it.name)) }
        else -> parentId.removePrefixOrNull(CONTEXT)?.let(PlayContext::fromKey)?.let { context ->
            val subfolders = (context as? PlayContext.Playlist)?.let { library.folder(it.id) }?.children.orEmpty()
            // A folder shows its sub-folders, then its own songs; other collections all their songs.
            val songs = (context as? PlayContext.Playlist)?.let { library.folder(it.id)?.tracks } ?: tracksOf(context)
            subfolders.mapNotNull { contextNode(PlayContext.Playlist(it.id)) } + songs.map { trackNode(context, it) }
        }
    }

    /** What playing [id] means: a song within its collection, a whole collection, or shuffle all. */
    fun resolvePlay(id: String): PlayRequest? {
        if (id == SHUFFLE_ALL) return library.tracks.takeIf { it.isNotEmpty() }?.let { PlayRequest(it, 0, null, shuffle = true) }
        id.removePrefixOrNull(TRACK)?.let { rest ->
            val contextKey = rest.substringBeforeLast(SEPARATOR, "")
            val trackId = rest.substringAfterLast(SEPARATOR)
            val context = PlayContext.fromKey(contextKey) ?: return null
            val tracks = tracksOf(context)
            val index = tracks.indexOfFirst { it.id == trackId }.takeIf { it >= 0 } ?: return null
            return PlayRequest(tracks, index, context)
        }
        val context = id.removePrefixOrNull(CONTEXT)?.let(PlayContext::fromKey) ?: return null
        return tracksOf(context).takeIf { it.isNotEmpty() }?.let { PlayRequest(it, 0, context) }
    }

    /** Voice or typed search from the car: artists, albums, then songs (each within its album). */
    fun search(query: String): List<BrowseNode> {
        val results = library.search(query)
        return results.artists.take(MAX_SEARCH).mapNotNull { contextNode(PlayContext.Artist(it.name)) } +
            results.albums.take(MAX_SEARCH).mapNotNull { contextNode(PlayContext.Album(it.title.orEmpty(), it.artist)) } +
            results.tracks.take(MAX_SEARCH_SONGS).map { track ->
                val album = library.albumOf(track)
                val context = album?.let { PlayContext.Album(it.title.orEmpty(), it.artist) } ?: PlayContext.Artist(track.groupingArtist)
                trackNode(context, track)
            }
    }

    /** Songs a collection plays, in order. Folders play everything inside them. */
    fun tracksOf(context: PlayContext): List<Track> = when (context) {
        is PlayContext.Album -> library.albums.firstOrNull { it.title == context.title && it.artist == context.artist }?.tracks
        is PlayContext.Artist -> library.artists.firstOrNull { it.name == context.name }?.tracks
        is PlayContext.Playlist -> library.folder(context.id)?.allTracks
        is PlayContext.UserPlaylist -> userPlaylists.firstOrNull { it.id == context.id }?.tracksIn(library)
        PlayContext.Favorites -> favorites
    }.orEmpty()

    private fun contextNode(context: PlayContext): BrowseNode? {
        val id = CONTEXT + context.key
        return when (context) {
            is PlayContext.Album -> {
                val album = library.albums.firstOrNull { it.title == context.title && it.artist == context.artist } ?: return null
                BrowseNode(id, album.title ?: labels.unknownAlbum, album.artist ?: labels.unknownArtist, browsable = true, playable = true, artworkTrack = album.tracks.firstOrNull())
            }
            is PlayContext.Artist -> {
                val artist = library.artists.firstOrNull { it.name == context.name } ?: return null
                BrowseNode(id, artist.name ?: labels.unknownArtist, labels.songs(artist.tracks.size), browsable = true, playable = true, artworkTrack = artist.albums.firstOrNull()?.tracks?.firstOrNull() ?: artist.tracks.firstOrNull())
            }
            is PlayContext.Playlist -> {
                val folder: Folder = library.folder(context.id) ?: return null
                BrowseNode(id, folder.name, labels.songs(folder.allTracks.size), browsable = true, playable = true, artworkTrack = folder.allTracks.firstOrNull())
            }
            is PlayContext.UserPlaylist -> {
                val playlist = userPlaylists.firstOrNull { it.id == context.id } ?: return null
                val tracks = playlist.tracksIn(library)
                if (tracks.isEmpty()) return null
                BrowseNode(id, playlist.name, labels.songs(tracks.size), browsable = true, playable = true, artworkTrack = tracks.first())
            }
            PlayContext.Favorites -> favorites.takeIf { it.isNotEmpty() }?.let {
                BrowseNode(id, labels.favorites, labels.songs(it.size), browsable = true, playable = true, artworkTrack = it.first())
            }
        }
    }

    private fun trackNode(context: PlayContext, track: Track) = BrowseNode(
        id = TRACK + context.key + SEPARATOR + track.id,
        title = track.title,
        subtitle = track.artist ?: labels.unknownArtist,
        browsable = false,
        playable = true,
        artworkTrack = track,
    )

    companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val PLAYLISTS = "playlists"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val SHUFFLE_ALL = "shuffle_all"
        private const val CONTEXT = "c:"
        private const val TRACK = "t:"
        private const val SEPARATOR = '\u001F'
        private const val MAX_RECENT = 12
        private const val MAX_SEARCH = 5
        private const val MAX_SEARCH_SONGS = 25

        private fun String.removePrefixOrNull(prefix: String): String? = if (startsWith(prefix)) removePrefix(prefix) else null
    }
}
