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
import dev.cmdrchara.songlass.core.settings.SettingsTarget

enum class Tab { Home, Library, Search }

enum class PlayerPage { Artwork, Queue, Lyrics }

/** Library sections, in display order. Folders first: that is how many people organise music. */
/** Library categories listed above the folders, each opening its own page. */
enum class LibraryCategory { Artists, Albums, Songs, Favorites }

sealed interface PlaylistPrompt {
    data class Create(val trackIds: List<String>) : PlaylistPrompt
    data class Rename(val id: String, val currentName: String) : PlaylistPrompt
}

sealed interface Route {
    data object About : Route
    data object Settings : Route
    data object HomeCustomization : Route
    data object Backup : Route
    data class TagEditor(val track: Track) : Route
    data object Equalizer : Route
    data object AutoEqCatalog : Route
    data class AutoEqDetail(val entry: dev.cmdrchara.songlass.core.equalizer.AutoEqEntry) : Route
    data class SmartPlaylistDetail(val id: String) : Route
    data class SmartPlaylistEditor(val id: String? = null) : Route
    data object LibrarySettings : Route
    data object ListeningStatistics : Route
    data class AlbumDetail(val album: Album) : Route
    data class ArtistDetail(val artist: Artist) : Route
    data class FolderDetail(val folder: Folder) : Route
    data class LibraryPage(val category: LibraryCategory) : Route
    data class UserPlaylistDetail(val id: String) : Route
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
    val pins = graph.pins
    val searchHistory = graph.searchHistory
    val settings = graph.settings
    val homeCustomization = graph.homeCustomization
    val tagEditor = graph.tagEditor
    val equalizer = graph.equalizer
    val smartPlaylists = graph.smartPlaylists
    val userPlaylists = graph.userPlaylists
    val sleepTimer = graph.sleepTimer
    val isLibraryLoading = graph.isLibraryLoading
    val history: ListeningHistory = graph.history
    val lyrics = graph.lyrics

    /** Latest library snapshot; updated by the UI from [AppGraph.library]. */
    var library by mutableStateOf(graph.library.value)

    /** Settings keeps its pane while an external editor (for example EQ) is on top. */
    var settingsPane by mutableStateOf(SettingsPane.Overview)
        private set
    fun selectSettingsPane(pane: SettingsPane) {
        if (pane != SettingsPane.Playback) settingsSearch.durationDraft = null
        settingsPane = pane
    }
    var settingsSearch by mutableStateOf(SettingsSearchSession())
        private set

    fun openSettingsResult(target: SettingsTarget) {
        settingsSearch.select(target)
        val pane = target.settingsPane
        if (pane != null) settingsPane = pane else open(target.settingsRoute)
    }

    fun settingsFocusFor(route: Route): SettingsFocusRequest? = settingsSearch.focus?.takeIf {
        currentRoute == route && it.target.settingsRoute == route && !isNowPlayingOpen && !hasOverlay
    }

    var tab by mutableStateOf(Tab.Home)
        private set
    /** Changes on every Search activation, including a repeated tap on its button. */
    var searchFocusRequest by mutableStateOf(0)
        private set
    var searchQuery by mutableStateOf("")
    var autoEqQuery by mutableStateOf("")
    var searchFilter by mutableStateOf(SearchFilter.All)
    var isNowPlayingOpen by mutableStateOf(false)
        private set
    var playerPage by mutableStateOf(PlayerPage.Artwork)
        private set
    var isArtworkExpanded by mutableStateOf(false)
        private set
    var isSongInfoVisible by mutableStateOf(false)
        private set
    fun updateArtworkExpansion(expanded: Boolean) { isArtworkExpanded = expanded }
    var isLibraryDurationPickerOpen by mutableStateOf(false)
        private set
    fun openLibraryDurationPicker() { isLibraryDurationPickerOpen = true }
    fun closeLibraryDurationPicker() { isLibraryDurationPickerOpen = false }
    var isSleepMenuOpen by mutableStateOf(false)
        private set
    fun openSleepMenu() { isSleepMenuOpen = true }
    fun closeSleepMenu() { isSleepMenuOpen = false }
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

    val canGoBack: Boolean get() = hasOverlay || editingPlaylistId != null || isSongInfoVisible || isArtworkExpanded || isNowPlayingOpen || currentRoute != null

    fun selectTab(newTab: Tab) {
        if (newTab == Tab.Search) searchFocusRequest += 1
        // Tapping the current tab again returns to its root, as in iOS.
        if (newTab == tab) backStacks.getValue(tab).clear()
        tab = newTab
    }

    /** Refresh route snapshots too: hidden songs must not survive in an older collection page. */
    fun updateLibrary(updated: dev.cmdrchara.songlass.core.library.MusicLibrary) {
        library = updated
        backStacks.values.forEach { stack ->
            for (index in stack.lastIndex downTo 0) {
                val original = stack[index]
                val refreshed: Route? = when (original) {
                    is Route.AlbumDetail -> updated.albums.firstOrNull { it.title == original.album.title && it.artist == original.album.artist }?.let(Route::AlbumDetail)
                    is Route.ArtistDetail -> updated.artists.firstOrNull { it.name == original.artist.name }?.let(Route::ArtistDetail)
                    is Route.FolderDetail -> updated.folder(original.folder.id)?.let(Route::FolderDetail)
                    else -> original
                }
                if (refreshed == null) stack.removeAt(index) else if (refreshed != original) stack[index] = refreshed
            }
        }
    }

    fun open(route: Route) {
        if (route == Route.Settings) {
            settingsPane = SettingsPane.Overview
            settingsSearch = SettingsSearchSession()
        }
        backStacks.getValue(tab).add(route)
    }

    fun goBack() {
        when {
            deletingSmartPlaylistId != null -> closeDeleteSmartPlaylist()
            playlistPrompt != null -> closePlaylistPrompt()
            addToPlaylistTracks != null -> closeAddToPlaylist()
            playlistOptionsId != null -> closePlaylistOptions()
            isLibraryDurationPickerOpen -> closeLibraryDurationPicker()
            isSleepMenuOpen -> closeSleepMenu()
            menuTrack != null -> closeMenu()
            isSongInfoVisible -> closeSongInfo()
            isArtworkExpanded -> updateArtworkExpansion(false)
            isNowPlayingOpen -> closeNowPlaying()
            editingPlaylistId != null -> editingPlaylistId = null
            else -> {
                val removed = backStacks.getValue(tab).removeLastOrNull()
                if (currentRoute == Route.Settings && removed == settingsSearch.focus?.target?.settingsRoute) {
                    // Back can interrupt a reveal before its scroll finishes. Do not replay it later.
                    settingsSearch.returnToResults()
                }
            }
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

    /** The user playlist the open track menu was opened from, for "Remove from playlist". */
    var menuPlaylistId by mutableStateOf<String?>(null)
        private set

    fun openMenu(track: Track, fromPlaylist: String? = null) {
        menuTrack = track
        menuPlaylistId = fromPlaylist
    }

    /** Tracks waiting for the user to pick a playlist ("Add to a playlist"). */
    var addToPlaylistTracks by mutableStateOf<List<Track>?>(null)
        private set
    fun openAddToPlaylist(tracks: List<Track>) { addToPlaylistTracks = tracks }
    fun closeAddToPlaylist() { addToPlaylistTracks = null }

    /** A name being asked: for a new playlist (with its first tracks) or to rename one. */
    var playlistPrompt by mutableStateOf<PlaylistPrompt?>(null)
        private set
    fun askPlaylistName(prompt: PlaylistPrompt) { playlistPrompt = prompt }
    fun closePlaylistPrompt() { playlistPrompt = null }

    /** The "…" options of a user playlist page. */
    var playlistOptionsId by mutableStateOf<String?>(null)
        private set
    fun openPlaylistOptions(id: String) { playlistOptionsId = id }
    fun closePlaylistOptions() { playlistOptionsId = null }

    /** User playlist whose songs show drag handles and remove buttons. */
    var editingPlaylistId by mutableStateOf<String?>(null)

    var deletingSmartPlaylistId by mutableStateOf<String?>(null)
        private set
    fun askDeleteSmartPlaylist(id: String) { deletingSmartPlaylistId = id }
    fun closeDeleteSmartPlaylist() { deletingSmartPlaylistId = null }

    val hasOverlay: Boolean
        get() = deletingSmartPlaylistId != null || isLibraryDurationPickerOpen || isSleepMenuOpen || menuTrack != null || isSongInfoVisible || addToPlaylistTracks != null ||
            playlistPrompt != null || playlistOptionsId != null

    fun openTagEditor(track: Track) {
        closeNowPlaying()
        open(Route.TagEditor(track))
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
