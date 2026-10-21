package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.playlists.UserPlaylist
import dev.cmdrchara.songlass.core.playlists.SmartPlaylist
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.library.LibraryFacetKind
import dev.cmdrchara.songlass.core.model.Track

enum class ShortcutKind { Favorites, Album, Artist, Folder, UserPlaylist, SmartPlaylist, Metadata }

/** Something that can be chosen as a Home shortcut, described for a picker. */
data class ShortcutOption(
    val context: PlayContext,
    val kind: ShortcutKind,
    /** Null for an unknown artist or album: the UI supplies the localized placeholder. */
    val title: String?,
    val subtitle: String? = null,
)

/**
 * Shortcut identities are [PlayContext]s (their stable `key`), the same ones "Recently played"
 * and pins use. Albums, artists, folders and favorites can be chosen; user and smart playlists
 * resolve from live repository snapshots, including empty playlists.
 */
object HomeShortcuts {
    /** Matches the size of the shortcut grid. */
    const val MAX = HomeFeed.SHORTCUTS

    /** Resolve saved playlist membership against the visible library, never against stale queues. */
    fun savedPlaylists(
        library: MusicLibrary,
        userPlaylists: List<UserPlaylist>,
        smartPlaylists: List<SmartPlaylist>,
        history: HistorySnapshot,
        favorites: Set<String>,
        nowMs: Long,
    ): List<HomeItem> = userPlaylists.map { HomeItem.UserPlaylistItem(it, it.tracksIn(library)) } +
        smartPlaylists.map { HomeItem.SmartPlaylistItem(it, it.rules.resolve(library, history, favorites, nowMs)) }

    /** The identity of [item], or null for a single song (not a valid shortcut). */
    fun contextOf(item: HomeItem): PlayContext? = when (item) {
        is HomeItem.AlbumItem -> PlayContext.Album(item.album.title.orEmpty(), item.album.artist)
        is HomeItem.PlaylistItem -> PlayContext.Playlist(item.playlist.id)
        is HomeItem.ArtistItem -> PlayContext.Artist(item.artist.name)
        is HomeItem.FacetItem -> item.facet.context
        is HomeItem.FavoritesItem -> PlayContext.Favorites
        is HomeItem.UserPlaylistItem -> PlayContext.UserPlaylist(item.playlist.id)
        is HomeItem.SmartPlaylistItem -> PlayContext.SmartPlaylist(item.playlist.id)
        is HomeItem.TrackItem -> null
    }

    /** Resolves [context] against the visible [library]; null when its music is not there. */
    fun resolve(library: MusicLibrary, context: PlayContext, favorites: List<Track>, savedPlaylists: List<HomeItem> = emptyList()): HomeItem? = when (context) {
        is PlayContext.Album -> library.albums.firstOrNull { it.title == context.title && it.artist == context.artist }
            ?.let(HomeItem::AlbumItem)
        is PlayContext.Playlist -> library.playlists.firstOrNull { it.id == context.id }?.let(HomeItem::PlaylistItem)
        is PlayContext.Artist -> library.artists.firstOrNull { it.name == context.name }?.let(HomeItem::ArtistItem)
        is PlayContext.Facet -> library.facet(context)?.let(HomeItem::FacetItem)
        PlayContext.Favorites -> favorites.takeIf { it.isNotEmpty() }?.let(HomeItem::FavoritesItem)
        is PlayContext.UserPlaylist, is PlayContext.SmartPlaylist -> savedPlaylists.firstOrNull { contextOf(it) == context }
    }

    /** Resolves [contexts] in order, dropping unavailable and repeated ones. */
    fun resolveAll(library: MusicLibrary, contexts: List<PlayContext>, favorites: List<Track>, savedPlaylists: List<HomeItem> = emptyList()): List<HomeItem> =
        contexts.distinct().mapNotNull { resolve(library, it, favorites, savedPlaylists) }

    /** Albums, artists and folders of the visible [library], each group in library order. */
    fun options(library: MusicLibrary, savedPlaylists: List<HomeItem> = emptyList()): List<ShortcutOption> = buildList {
        savedPlaylists.forEach { item ->
            when (item) {
                is HomeItem.UserPlaylistItem -> add(ShortcutOption(PlayContext.UserPlaylist(item.playlist.id), ShortcutKind.UserPlaylist, item.playlist.name))
                is HomeItem.SmartPlaylistItem -> add(ShortcutOption(PlayContext.SmartPlaylist(item.playlist.id), ShortcutKind.SmartPlaylist, item.playlist.name))
                else -> Unit
            }
        }
        library.albums.forEach { album ->
            add(ShortcutOption(PlayContext.Album(album.title.orEmpty(), album.artist), ShortcutKind.Album, album.title, album.artist))
        }
        library.artists.forEach { artist ->
            add(ShortcutOption(PlayContext.Artist(artist.name), ShortcutKind.Artist, artist.name))
        }
        library.playlists.forEach { playlist ->
            add(ShortcutOption(PlayContext.Playlist(playlist.id), ShortcutKind.Folder, playlist.name, playlist.location.joinToString(" / ").ifEmpty { null }))
        }
        LibraryFacetKind.entries.forEach { kind -> library.facets(kind).forEach { facet ->
            add(ShortcutOption(facet.context, ShortcutKind.Metadata, facet.value))
        } }
    }

    /** Describes [context] if the library has it, for the list of chosen shortcuts. */
    fun describe(library: MusicLibrary, context: PlayContext, savedPlaylists: List<HomeItem> = emptyList()): ShortcutOption? = when (context) {
        PlayContext.Favorites -> ShortcutOption(context, ShortcutKind.Favorites, null)
        else -> options(library, savedPlaylists).firstOrNull { it.context == context }
    }

    /** Case-insensitive match on title and subtitle; a blank [query] keeps everything. */
    fun filter(options: List<ShortcutOption>, query: String, kind: ShortcutKind? = null): List<ShortcutOption> {
        val needle = query.trim().lowercase()
        return options.filter { option ->
            (kind == null || option.kind == kind) &&
                (needle.isEmpty() || option.title?.lowercase()?.contains(needle) == true ||
                    option.subtitle?.lowercase()?.contains(needle) == true)
        }
    }
}
