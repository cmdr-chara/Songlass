package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Artist
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.AppGraph

enum class Tab { Library, Search }

enum class LibrarySection { Songs, Albums, Artists, Favorites }

sealed interface Route {
    data class AlbumDetail(val album: Album) : Route
    data class ArtistDetail(val artist: Artist) : Route
}

/**
 * UI state that survives recomposition: which tab and page are shown, and whether Now Playing
 * is open. Playback and library logic stay in core; this class only holds navigation state.
 */
@Stable
class AppState(graph: AppGraph) {
    val controller: PlaybackController = graph.controller
    val favorites: Favorites = graph.favorites

    /** Latest library snapshot; updated by the UI from [AppGraph.library]. */
    var library by mutableStateOf(graph.library.value)

    var tab by mutableStateOf(Tab.Library)
        private set
    var librarySection by mutableStateOf(LibrarySection.Albums)
    var searchQuery by mutableStateOf("")
    var isNowPlayingOpen by mutableStateOf(false)
        private set
    var isQueueVisible by mutableStateOf(false)
        private set

    /** Track whose "…" menu is open, if any. */
    var menuTrack by mutableStateOf<Track?>(null)
        private set

    private val backStacks = mapOf(Tab.Library to mutableStateListOf<Route>(), Tab.Search to mutableStateListOf())

    val currentRoute: Route? get() = backStacks.getValue(tab).lastOrNull()

    val searchResults by derivedStateOf { library.search(searchQuery) }

    val canGoBack: Boolean get() = menuTrack != null || isNowPlayingOpen || currentRoute != null

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
            isNowPlayingOpen -> closeNowPlaying()
            else -> backStacks.getValue(tab).removeLastOrNull()
        }
    }

    fun openNowPlaying() {
        isNowPlayingOpen = true
    }

    fun closeNowPlaying() {
        isNowPlayingOpen = false
        isQueueVisible = false
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

    fun toggleQueue() {
        isQueueVisible = !isQueueVisible
    }
}
