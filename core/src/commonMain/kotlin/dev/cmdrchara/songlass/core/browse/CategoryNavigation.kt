package dev.cmdrchara.songlass.core.browse

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.Folder
import dev.cmdrchara.songlass.core.library.FolderSort
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.UserPlaylist

/** A collection the player can switch to: the context its queue starts with, its own title, its songs. */
data class CategoryTarget(val context: PlayContext, val title: String?, val tracks: List<Track>) {
    /**
     * Where to start inside this collection: the track last heard there while it is still part
     * of the collection, otherwise the first one (the same "last known position, else first
     * track" rule documented for the category commands).
     */
    fun startIndex(rememberedTrackId: String?): Int =
        tracks.indexOfFirst { it.id == rememberedTrackId }.takeIf { it >= 0 } ?: 0
}

/**
 * The collection next to [context] in the library's navigation order, or `null` when there is
 * no neighbour. The order is the one the Library screens already present:
 *
 * - albums and artists: [MusicLibrary.albums] / [MusicLibrary.artists], alphabetical by the
 *   same case-insensitive sort key the library uses;
 * - folders: the user's [FolderSort] on the top-level folders, then each folder's children in
 *   their library order (flat pre-order, so a nested folder has its parent as neighbour);
 * - user playlists: the stored (newest first) order of the playlist shelf. Empty playlists are
 *   not playable collections and are skipped instead of becoming a dead neighbour.
 *
 * Favorites, facets, smart playlists and a missing context are not part of that order, so they
 * have no neighbour either. Boundaries do not wrap: the first collection has no previous
 * neighbour and the last has no next one. Adjacent collections always have at least one song.
 */
fun neighborCategory(
    context: PlayContext?,
    library: MusicLibrary,
    userPlaylists: List<UserPlaylist> = emptyList(),
    folderSort: FolderSort = FolderSort.Name,
    playCounts: Map<String, Int> = emptyMap(),
    forward: Boolean,
): CategoryTarget? {
    val source = context ?: return null
    return when (source) {
        is PlayContext.Album -> {
            val index = library.albums.indexOfFirst { it.title.orEmpty() == source.title && it.artist == source.artist }
            library.albums.neighborOf(index, forward)?.let { album ->
                CategoryTarget(PlayContext.Album(album.title.orEmpty(), album.artist), album.title, album.tracks)
            }
        }
        is PlayContext.Artist -> {
            val index = library.artists.indexOfFirst { it.name == source.name }
            library.artists.neighborOf(index, forward)?.let { artist ->
                CategoryTarget(PlayContext.Artist(artist.name), artist.name, artist.tracks)
            }
        }
        is PlayContext.Playlist -> {
            val folders = orderedFolders(library, folderSort, playCounts)
            val index = folders.indexOfFirst { it.id == source.id }
            folders.neighborOf(index, forward)?.let { folder ->
                CategoryTarget(PlayContext.Playlist(folder.id), folder.name, folder.allTracks)
            }
        }
        is PlayContext.UserPlaylist -> {
            val targets = userPlaylists.mapNotNull { playlist ->
                val tracks = playlist.tracksIn(library)
                if (tracks.isEmpty()) null else CategoryTarget(PlayContext.UserPlaylist(playlist.id), playlist.name, tracks)
            }
            val index = targets.indexOfFirst { it.context == source }
            targets.neighborOf(index, forward)
        }
        is PlayContext.Facet, PlayContext.Favorites, is PlayContext.SmartPlaylist -> null
    }
}

/**
 * The folders as the Library browses them: the chosen sort on the top-level entries, then each
 * level's children in the library's name order, flattened so every folder has an index.
 */
private fun orderedFolders(library: MusicLibrary, sort: FolderSort, playCounts: Map<String, Int>): List<Folder> {
    fun flatten(folder: Folder): List<Folder> = listOf(folder) + folder.children.flatMap(::flatten)
    return sort.sort(library.folders, playCounts).flatMap(::flatten)
}

private fun <T> List<T>.neighborOf(index: Int, forward: Boolean): T? =
    if (index < 0) null else getOrNull(if (forward) index + 1 else index - 1)
