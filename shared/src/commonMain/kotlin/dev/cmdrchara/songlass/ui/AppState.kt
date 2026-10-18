package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Artist
import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.library.Folder
import dev.cmdrchara.songlass.core.library.Playlist
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.AppGraph

enum class Tab { Home, Library, Search }

enum class PlayerPage { Artwork, Queue, Lyrics }

/** Library sections, in display order. Folders first: that is how many people organise music. */
/** Library categories listed above the folders, each opening its own page. */
enum class LibraryCategory { Artists, Albums, Songs, Favorites }

sealed interface Route {
    data object Settings : Route
    data class AlbumDetail(val album: Album) : Route
    data class ArtistDetail(val artist: Artist) : Route
    data class FolderDetail(val folder: Folder) : Route
    data class LibraryPage(val category: LibraryCategory) : Route
}

/** A stable snapshot so outgoing screens retain their route during navigation. */
data class ScreenDestination(val tab: Tab, val route: Route?, val depth: Int)

/**
 * UI state that survives recomposition: which tab and page are shown, and whether Now Playing
 * is open. Playback and library logic stay in core; this class only holds navigation state.
 */
@Stable
class AppState(graph: AppGraph) {
    val controller: PlaybackController = graph.controller
    val favorites: Favorites = graph.favorites
    val history: ListeningHistory = graph.history
    val lyrics = graph.lyrics

    /** Latest library snapshot; updated by the UI from [AppGraph.library]. */
    var library by mutableStateOf(graph.library.value)

    var tab by mutableStateOf(Tab.Home)
        private set
    var searchQuery by mutableStateOf("")
    var isNowPlayingOpen by mutableStateOf(false)
        private set
    var playerPage by mutableStateOf(PlayerPage.Artwork)
        private set
    var isArtworkExpanded by mutableStateOf(false)
        private set
    var isSongInfoVisible by mutableStateOf(false)
        private set
    fun updateArtworkExpansion(expanded: Boolean) { isArtworkExpanded = expanded }
    fun showSongInfo() { isSongInfoVisible = true }
    fun closeSongInfo() { isSongInfoVisible = false }
    val isQueueVisible: Boolean get() = playerPage == PlayerPage.Queue

    /** Track whose "…" menu is open, if any. */
    var menuTrack by mutableStateOf<Track?>(null)
        private set

    private val backStacks = Tab.entries.associateWith { mutableStateListOf<Route>() }

    val currentRoute: Route? get() = backStacks.getValue(tab).lastOrNull()
    val destination: ScreenDestination get() = ScreenDestination(tab, currentRoute, backStacks.getValue(tab).size)

    /** Opens the folder a playlist stands for (playlists are folders with music in them). */
    fun openPlaylist(playlist: Playlist) {
        library.folder(playlist.id)?.let { open(Route.FolderDetail(it)) }
    }

    val searchResults by derivedStateOf { library.search(searchQuery) }

    val canGoBack: Boolean get() = menuTrack != null || isSongInfoVisible || isArtworkExpanded || isNowPlayingOpen || currentRoute != null

    fun selectTab(newTab: Tab) {
        // Tapping the current tab again returns to its root, as in iOS.
        if (newTab == tab) backStacks.getValue(tab).clear()
        tab = newTab
    }

    fun open(route: Route) {
        backStacks.getValue(tab).add(route)
    }

    fun goBack() {
        when {
            menuTrack != null -> closeMenu()
            isSongInfoVisible -> closeSongInfo()
            isArtworkExpanded -> updateArtworkExpansion(false)
            isNowPlayingOpen -> closeNowPlaying()
            else -> backStacks.getValue(tab).removeLastOrNull()
        }
    }

    fun openNowPlaying() {
        if (!isNowPlayingOpen) playerPage = PlayerPage.Artwork
        isNowPlayingOpen = true
    }

    fun closeNowPlaying() {
        // Keep the outgoing queue intact until the sheet has left the screen.
        isArtworkExpanded = false
        isSongInfoVisible = false
        isNowPlayingOpen = false
    }

    fun openMenu(track: Track) {
        menuTrack = track
    }

    fun closeMenu() {
        menuTrack = null
    }

    /** Leaves Now Playing if needed and opens the album of [track] in the current tab. */
    fun showAlbumOf(track: Track) {
        val album = library.albumOf(track) ?: return
        closeNowPlaying()
        open(Route.AlbumDetail(album))
    }

    fun showArtistOf(track: Track) {
        val artist = library.artistOf(track) ?: return
        closeNowPlaying()
        open(Route.ArtistDetail(artist))
    }

    fun toggleLyrics() {
        playerPage = if (playerPage == PlayerPage.Lyrics) PlayerPage.Artwork else PlayerPage.Lyrics
    }

    fun toggleQueue() {
        playerPage = if (isQueueVisible) PlayerPage.Artwork else PlayerPage.Queue
    }
}
