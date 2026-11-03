package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.core.browse.CategoryTarget
import dev.cmdrchara.songlass.core.browse.neighborCategory
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Artist
import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.library.Folder
import dev.cmdrchara.songlass.core.library.Playlist
import dev.cmdrchara.songlass.core.library.Ratings
import dev.cmdrchara.songlass.core.library.LibraryFacetKind
import dev.cmdrchara.songlass.core.library.AlphabetSection
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.isSameFileAs
import dev.cmdrchara.songlass.core.sound.DefaultSoundTarget
import dev.cmdrchara.songlass.core.model.TrackAvailability
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.playlists.PlaylistAddResult
import dev.cmdrchara.songlass.core.playlists.PlaylistInsertion
import dev.cmdrchara.songlass.core.queue.QueuePlaylistSnapshot
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.core.storage.MutationTicket
import dev.cmdrchara.songlass.core.settings.SettingsArea
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.core.settings.TrackClickAction

enum class Tab { Home, Library, Search }

enum class PlayerPage { Artwork, Queue, Lyrics }

enum class TrackMenuPage { Main, More, Artists, UseAs, Rating }

/** Collections the open track menu could switch to; each is null when there is no neighbour. */
data class CategoryTargets(val previous: CategoryTarget? = null, val next: CategoryTarget? = null)

/** Identity, rather than structural equality, binds asynchronous results to this confirmation. */
class DefaultSoundRequest(val track: Track, val target: DefaultSoundTarget)

enum class AudioInformationPage { Overview, File, Processing, Output }

/** A settings visit borrowed from the player, not a second navigation stack. */
private data class PlayerSettingsReturn(
    val tab: Tab, val depth: Int, val page: PlayerPage,
    val settingsPane: SettingsPane, val settingsSearch: SettingsSearchSession,
)

/** Library sections, in display order. Folders first: that is how many people organise music. */
/** Library categories listed above the folders, each opening its own page. */
enum class LibraryCategory { Artists, Albums, Songs, Favorites }

sealed interface PlaylistPrompt {
    data class Create(val trackIds: List<String>) : PlaylistPrompt
    data class CreateFromQueue(val snapshot: QueuePlaylistSnapshot) : PlaylistPrompt
    data class Rename(val id: String, val currentName: String) : PlaylistPrompt
}
enum class PlaylistNameResult { Saved, QueueSelectionChanged }
data class SavedQueuePlaylist(val id: String, val name: String)

data class PlaylistInsertionRequest(
    val playlistId: String, val trackIds: List<String>, val mutationTicket: MutationTicket? = null,
)
data class PlaylistAdditionNotice(val playlistName: String, val result: PlaylistAddResult)
data class PlaylistRemovalRequest(
    val playlistId: String,
    val missingOnly: Boolean,
    /** Snapshot of the references the user is being asked to remove, never expanded later. */
    val missingCandidateIds: List<String> = emptyList(),
    val mutationTicket: MutationTicket? = null,
)
data class AlphabetJumpRequest(val sections: List<AlphabetSection>, val onSelect: (Int) -> Unit)

sealed interface Route {
    data object About : Route
    data object Settings : Route
    data object HomeCustomization : Route
    data object Backup : Route
    data class Bookmarks(val track: Track? = null) : Route
    data class TagEditor(val track: Track) : Route
    data object Equalizer : Route
    data object AutoEqCatalog : Route
    data class AutoEqDetail(val entry: dev.cmdrchara.songlass.core.equalizer.AutoEqEntry) : Route
    data class SmartPlaylistDetail(val id: String) : Route
    data class SmartPlaylistEditor(val id: String? = null) : Route
    data object LibrarySettings : Route
    data object ListeningStatistics : Route

    /** The user's rated tracks grouped by stars; a Library sub-page. */
    data object Ratings : Route
    data object PossibleDuplicates : Route

    /** Last.fm account, credentials and pending scrobbles; a sub-page of Settings. */
    data object LastFm : Route
    data class AlbumDetail(val album: Album) : Route
    /** Cover picker for one album; the album snapshot is the one the page was opened from. */
    data class OnlineArtwork(val album: Album) : Route
    data class OnlineTrackArtwork(val track: Track) : Route
    data class ArtistDetail(val artist: Artist) : Route
    data class FacetList(val kind: LibraryFacetKind) : Route
    data class FacetDetail(val context: PlayContext.Facet) : Route
    data class FolderDetail(val folder: Folder) : Route
    data class LibraryPage(val category: LibraryCategory) : Route
    data class UserPlaylistDetail(val id: String) : Route
}

/** A stable snapshot so outgoing screens retain their route during navigation. */
data class ScreenDestination(val tab: Tab, val route: Route?, val depth: Int, val parentRoute: Route? = null)

/**
 * UI state that survives recomposition: which tab and page are shown, and whether Now Playing
 * is open. Playback and library logic stay in core; this class only holds navigation state.
 */
@Stable
class AppState(graph: AppGraph) {
    val controller: PlaybackController = graph.controller
    val trackMarkers = graph.trackMarkers
    val favorites: Favorites = graph.favorites
    val ratings: Ratings = graph.ratings
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
    val isRestoringBackup = graph.isRestoringBackup
    val restoreMutations = graph.restoreMutations
    val libraryRefreshFailure = graph.libraryRefreshFailure
    val requestLibraryRefresh: () -> Unit = graph::requestLibraryRefresh
    val importCueSheet = graph::importCueSheet
    val removeCueSheet = graph::removeCueSheet
    val cueSheets = graph.cueSheets
    val hasCompleteLibraryScan = graph.hasCompleteLibraryScan
    val allTracks = graph.allTracks
    val libraryAccess = graph.source.hasAccess
    val history: ListeningHistory = graph.history
    val lyrics = graph.lyrics

    /** Latest library snapshot; updated by the UI from [AppGraph.library]. */
    var library by mutableStateOf(graph.library.value)

    /** Settings keeps its pane while an external editor (for example EQ) is on top. */
    var settingsPane by mutableStateOf(SettingsPane.Overview)
        private set
    fun selectSettingsPane(pane: SettingsPane) {
        if (!restoreMutations.canMutate) return
        if (pane != SettingsPane.Sound) settingsSearch.durationDraft = null
        settingsPane = pane
    }
    var settingsSearch by mutableStateOf(SettingsSearchSession())
        private set

    fun openSettingsResult(target: SettingsTarget) {
        if (!restoreMutations.canMutate) return
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
    private var consumedSearchFocusRequest = 0

    /** Consume a Search-button request once, including across result-screen navigation. */
    fun consumeSearchFocusRequest(request: Int): Boolean {
        if (!restoreMutations.canMutate) return false
        if (tab != Tab.Search || currentRoute != null || isNowPlayingOpen || hasOverlay ||
            request != searchFocusRequest || request == consumedSearchFocusRequest) return false
        consumedSearchFocusRequest = request
        return true
    }
    var searchQuery by mutableStateOf("")
    var autoEqQuery by mutableStateOf("")
    var searchFilter by mutableStateOf(SearchFilter.All)
    var isNowPlayingOpen by mutableStateOf(false)
        private set
    internal var collectionStatusBarAppearance by mutableStateOf<CollectionStatusBarAppearance?>(null)
    var playerPage by mutableStateOf(PlayerPage.Artwork)
        private set
    var isArtworkExpanded by mutableStateOf(false)
        private set
    var isSongInfoVisible by mutableStateOf(false)
        private set
    var audioInformationPage by mutableStateOf(AudioInformationPage.Overview)
        private set
    private var playerSettingsReturn by mutableStateOf<PlayerSettingsReturn?>(null)
    fun updateArtworkExpansion(expanded: Boolean) { isArtworkExpanded = expanded }
    var isLibraryDurationPickerOpen by mutableStateOf(false)
        private set
    fun openLibraryDurationPicker() { isLibraryDurationPickerOpen = true }
    fun closeLibraryDurationPicker() { isLibraryDurationPickerOpen = false }
    var alphabetJumpRequest by mutableStateOf<AlphabetJumpRequest?>(null)
        private set
    fun openAlphabetJump(sections: List<AlphabetSection>, onSelect: (Int) -> Unit) {
        alphabetJumpRequest = AlphabetJumpRequest(sections, onSelect)
    }
    fun closeAlphabetJump() { alphabetJumpRequest = null }
    var restoreDefaultsArea by mutableStateOf<SettingsArea?>(null)
        private set
    fun askRestoreDefaults(area: SettingsArea) { restoreDefaultsArea = area }
    fun closeRestoreDefaults() { restoreDefaultsArea = null }
    var isSleepMenuOpen by mutableStateOf(false)
        private set
    fun openSleepMenu() { isSleepMenuOpen = true }
    fun closeSleepMenu() { isSleepMenuOpen = false }
    fun showSongInfo() { audioInformationPage = AudioInformationPage.Overview; isSongInfoVisible = true }
    fun showAudioInformationPage(page: AudioInformationPage) { audioInformationPage = page }
    fun closeSongInfo() { isSongInfoVisible = false }
    fun backFromAudioInformation() {
        if (audioInformationPage == AudioInformationPage.Overview) closeSongInfo()
        else audioInformationPage = AudioInformationPage.Overview
    }

    fun returnsToPlayerFrom(destination: ScreenDestination): Boolean = playerSettingsReturn?.let {
        destination.tab == it.tab && destination.depth == it.depth + 1
    } == true

    /** Open the real control after creating its route; opening Settings resets its search session. */
    fun openPlayerSettings(target: SettingsTarget) {
        if (!restoreMutations.canMutate || !isNowPlayingOpen) return
        val origin = PlayerSettingsReturn(tab, backStacks.getValue(tab).size, playerPage, settingsPane, settingsSearch)
        closeSongInfo()
        closeNowPlaying()
        settingsSearch = SettingsSearchSession()
        open(target.settingsRoute)
        target.settingsPane?.let { settingsPane = it }
        settingsSearch.select(target)
        playerSettingsReturn = origin
    }

    /** End a borrowed settings visit without leaving its temporary routes or search state behind. */
    private fun finishPlayerSettings(): PlayerPage? {
        val origin = playerSettingsReturn ?: return null
        val stack = backStacks.getValue(origin.tab)
        while (stack.size > origin.depth) stack.removeLastOrNull()
        playerSettingsReturn = null
        settingsPane = origin.settingsPane
        settingsSearch = origin.settingsSearch
        return origin.page
    }
    val isQueueVisible: Boolean get() = playerPage == PlayerPage.Queue

    /** Track whose "…" menu is open, if any. */
    var menuTrack by mutableStateOf<Track?>(null)
        private set

    var trackMenuPage by mutableStateOf(TrackMenuPage.Main)
        private set

    /** Adjacent collections captured when the menu opened, so the rows stay stable while shown. */
    var categoryTargets by mutableStateOf(CategoryTargets())
        private set

    /** The last track heard inside each collection, for the category commands' start position. */
    private val categoryResume = mutableMapOf<String, String>()
    var defaultSoundRequest by mutableStateOf<DefaultSoundRequest?>(null, referentialEqualityPolicy())
        private set
    var defaultSoundApplying by mutableStateOf(false)
        private set
    var defaultSoundInvalidated by mutableStateOf(false)
        private set
    private val defaultSoundSourceCurrent: (Track) -> Boolean = { selected ->
        graph.isLibraryReadyForBackup() && graph.allTracks.value.singleOrNull { it.id == selected.id }
            ?.let { selected.isSameFileAs(it) } == true
    }

    private var defaultSoundTicket: MutationTicket? = null
    fun isDefaultSoundSourceCurrent(track: Track): Boolean = defaultSoundSourceCurrent(track) &&
        (defaultSoundRequest?.track !== track || (!defaultSoundInvalidated && restoreMutations.accepts(defaultSoundTicket)))
    fun showTrackMenuPage(page: TrackMenuPage) { if (menuTrack != null) trackMenuPage = page }
    fun backTrackMenu() {
        when (trackMenuPage) {
            TrackMenuPage.Main -> closeMenu()
            TrackMenuPage.More -> trackMenuPage = TrackMenuPage.Main
            TrackMenuPage.Artists, TrackMenuPage.UseAs -> trackMenuPage = TrackMenuPage.More
            // Rating opens from the main page (or straight from the player indicator).
            TrackMenuPage.Rating -> trackMenuPage = TrackMenuPage.Main
        }
    }
    fun askDefaultSound(track: Track, target: DefaultSoundTarget) {
        if (defaultSoundApplying) return
        defaultSoundTicket = restoreMutations.ticket()
        defaultSoundRequest = DefaultSoundRequest(track, target)
        defaultSoundInvalidated = !isDefaultSoundSourceCurrent(track)
        defaultSoundApplying = false
    }
    fun invalidateDefaultSound(request: DefaultSoundRequest) {
        if (defaultSoundRequest === request) defaultSoundInvalidated = true
    }
    fun beginDefaultSound(request: DefaultSoundRequest): Boolean {
        if (defaultSoundRequest !== request || defaultSoundApplying || defaultSoundInvalidated) return false
        if (!isDefaultSoundSourceCurrent(request.track)) { defaultSoundInvalidated = true; return false }
        defaultSoundApplying = true
        return true
    }
    fun finishDefaultSound(request: DefaultSoundRequest): Boolean {
        if (defaultSoundRequest !== request) return false
        defaultSoundApplying = false
        return true
    }
    fun closeDefaultSound() {
        if (defaultSoundApplying) return
        defaultSoundRequest = null
        defaultSoundTicket = null
        defaultSoundInvalidated = false
    }

    private val backStacks = Tab.entries.associateWith { mutableStateListOf<Route>() }

    val currentRoute: Route? get() = backStacks.getValue(tab).lastOrNull()
    val destination: ScreenDestination get() {
        val stack = backStacks.getValue(tab)
        return ScreenDestination(tab, stack.lastOrNull(), stack.size, stack.getOrNull(stack.lastIndex - 1))
    }

    /** Opens the folder a playlist stands for (playlists are folders with music in them). */
    fun openPlaylist(playlist: Playlist) {
        library.folder(playlist.id)?.let { open(Route.FolderDetail(it)) }
    }

    val searchResults by derivedStateOf { library.search(searchQuery) }

    val canGoBack: Boolean get() = hasOverlay || editingPlaylistId != null || isSongInfoVisible || isArtworkExpanded || isNowPlayingOpen || currentRoute != null

    fun selectTab(newTab: Tab) {
        if (!restoreMutations.canMutate) return
        finishPlayerSettings()
        closeAlphabetJump()
        if (newTab == Tab.Search) searchFocusRequest += 1
        // Tapping the current tab again returns to its root, as in iOS.
        if (newTab == tab) backStacks.getValue(tab).clear()
        tab = newTab
    }

    /** Refresh route snapshots too: hidden songs must not survive in an older collection page. */
    fun updateLibrary(updated: dev.cmdrchara.songlass.core.library.MusicLibrary) {
        if (library !== updated) closeAlphabetJump()
        library = updated
        backStacks.forEach { (stackTab, stack) ->
            for (index in stack.lastIndex downTo 0) {
                val original = stack[index]
                val refreshed: Route? = when (original) {
                    is Route.AlbumDetail -> updated.albums.firstOrNull { it.title == original.album.title && it.artist == original.album.artist }?.let(Route::AlbumDetail)
                    is Route.ArtistDetail -> updated.artists.firstOrNull { it.name == original.artist.name }?.let(Route::ArtistDetail)
                    is Route.FolderDetail -> updated.folder(original.folder.id)?.let(Route::FolderDetail)
                    is Route.FacetDetail -> original.takeIf { updated.facet(it.context) != null }
                    else -> original
                }
                if (refreshed == null) {
                    stack.removeAt(index)
                    // Refresh can remove a folder/album below the borrowed settings route.
                    // Keep the return boundary aligned so nested AutoEQ Back stays nested.
                    playerSettingsReturn?.takeIf { it.tab == stackTab && index < it.depth }?.let {
                        playerSettingsReturn = it.copy(depth = it.depth - 1)
                    }
                } else if (refreshed != original) stack[index] = refreshed
            }
        }
    }

    fun open(route: Route) {
        if (!restoreMutations.canMutate) return
        closeAlphabetJump()
        if (route == Route.Settings) {
            settingsPane = SettingsPane.Overview
            settingsSearch = SettingsSearchSession()
        }
        backStacks.getValue(tab).add(route)
    }

    fun goBack() {
        if (!restoreMutations.canMutate) return
        when {
            playlistMutationRejected -> closePlaylistMutationNotice()
            alphabetJumpRequest != null -> closeAlphabetJump()
            playlistAdditionNotice != null -> closePlaylistAdditionNotice()
            playlistRemovalRequest != null -> closePlaylistRemoval()
            playlistInsertionRequest != null -> closePlaylistInsertion()
            deletingSmartPlaylistId != null -> closeDeleteSmartPlaylist()
            playlistPrompt != null -> closePlaylistPrompt()
            addToPlaylistTracks != null -> closeAddToPlaylist()
            playlistOptionsId != null -> closePlaylistOptions()
            isLibraryDurationPickerOpen -> closeLibraryDurationPicker()
            restoreDefaultsArea != null -> closeRestoreDefaults()
            isSleepMenuOpen -> closeSleepMenu()
            defaultSoundRequest != null -> closeDefaultSound()
            menuTrack != null -> backTrackMenu()
            isSongInfoVisible -> backFromAudioInformation()
            isArtworkExpanded -> updateArtworkExpansion(false)
            isNowPlayingOpen -> closeNowPlaying()
            editingPlaylistId != null -> editingPlaylistId = null
            else -> {
                val removed = backStacks.getValue(tab).removeLastOrNull()
                val origin = playerSettingsReturn
                if (origin != null && tab == origin.tab && backStacks.getValue(tab).size == origin.depth) {
                    playerPage = finishPlayerSettings() ?: origin.page
                    isNowPlayingOpen = controller.queue.value.current != null
                    return
                }
                if (currentRoute == Route.Settings && removed == settingsSearch.focus?.target?.settingsRoute) {
                    // Back can interrupt a reveal before its scroll finishes. Do not replay it later.
                    settingsSearch.returnToResults()
                }
            }
        }
    }

    /** A tap on a song row: [index] in [tracks], handled as the user chose in Settings > Queue and shuffle. */
    fun tapTrack(tracks: List<Track>, index: Int, context: PlayContext? = null) {
        when (settings.state.value.trackClickAction) {
            TrackClickAction.PlayList -> controller.playTracks(tracks, index, context)
            TrackClickAction.PlayListAndOpenPlayer -> { controller.playTracks(tracks, index, context); openNowPlaying() }
            TrackClickAction.AddToQueue -> tracks.getOrNull(index)?.let { controller.addToQueue(listOf(it)) }
        }
    }

    fun openNowPlaying() {
        if (!restoreMutations.canMutate) return
        val returnedPage = finishPlayerSettings()
        if (!isNowPlayingOpen) playerPage = returnedPage ?: PlayerPage.Artwork
        isNowPlayingOpen = true
    }

    fun closeNowPlaying() {
        if (!restoreMutations.canMutate) return
        // Keep the outgoing queue intact until the sheet has left the screen.
        isArtworkExpanded = false
        isSongInfoVisible = false
        isNowPlayingOpen = false
        clearSavedQueuePlaylist()
    }

    /** The user playlist the open track menu was opened from, for "Remove from playlist". */
    var menuPlaylistId by mutableStateOf<String?>(null)
        private set

    fun openMenu(track: Track, fromPlaylist: String? = null, page: TrackMenuPage = TrackMenuPage.Main) {
        trackMenuPage = page
        menuTrack = track
        menuPlaylistId = fromPlaylist
        categoryTargets = CategoryTargets(previous = categoryNeighbor(forward = false), next = categoryNeighbor(forward = true))
    }

    /** The adjacent collection of the current playback source, or null at a boundary. */
    fun categoryNeighbor(forward: Boolean): CategoryTarget? {
        val source = controller.context ?: return null
        return neighborCategory(
            context = source,
            library = library,
            userPlaylists = userPlaylists.playlists.value,
            folderSort = settings.state.value.folderSort,
            playCounts = history.state.value.playCounts,
            forward = forward,
        )?.takeIf { it.tracks.isNotEmpty() }
    }

    /**
     * Replaces the queue with the adjacent collection, resuming the last track heard there (or
     * its first one). The repeat mode is kept and shuffle follows the "keep shuffle on
     * selection" preference, exactly as any other selection. Does nothing without a neighbour.
     */
    fun switchCategory(forward: Boolean) {
        if (!restoreMutations.canMutate) return
        val target = categoryNeighbor(forward) ?: return
        rememberCategoryPosition()
        controller.playTracks(target.tracks, target.startIndex(categoryResume[target.context.key]), target.context)
    }

    private fun rememberCategoryPosition() {
        val source = controller.context ?: return
        val entry = controller.queue.value.current ?: return
        // A temporary addition is not part of the collection it interrupts.
        if (entry.isTemporary) return
        categoryResume[source.key] = entry.track.id
    }

    /** Tracks waiting for the user to pick a playlist ("Add to a playlist"). */
    var addToPlaylistTracks by mutableStateOf<List<Track>?>(null)
        private set
    fun openAddToPlaylist(tracks: List<Track>) { addToPlaylistTracks = tracks }
    fun closeAddToPlaylist() { addToPlaylistTracks = null }

    var playlistInsertionRequest by mutableStateOf<PlaylistInsertionRequest?>(null)
        private set
    fun askPlaylistInsertion(id: String, trackIds: List<String>) {
        val ticket = restoreMutations.ticket() ?: return
        playlistInsertionRequest = PlaylistInsertionRequest(id, trackIds, ticket)
    }
    fun closePlaylistInsertion() { playlistInsertionRequest = null }
    var playlistAdditionNotice by mutableStateOf<PlaylistAdditionNotice?>(null)
        private set
    fun addToPlaylist(request: PlaylistInsertionRequest, insertion: PlaylistInsertion) {
        if (!restoreMutations.accepts(request.mutationTicket)) { rejectPlaylistMutation(); return }
        val name = userPlaylists.get(request.playlistId)?.name.orEmpty()
        playlistAdditionNotice = PlaylistAdditionNotice(name,
            userPlaylists.addDetailed(request.playlistId, request.trackIds, insertion))
    }
    fun closePlaylistAdditionNotice() { playlistAdditionNotice = null }

    var playlistRemovalRequest by mutableStateOf<PlaylistRemovalRequest?>(null)
        private set
    fun askPlaylistRemoval(id: String, missingOnly: Boolean = false) {
        val ticket = restoreMutations.ticket() ?: return
        val candidates = if (missingOnly) {
            if (!libraryAccess.value || !hasCompleteLibraryScan.value || isLibraryLoading.value) return
            val playlist = userPlaylists.get(id) ?: return
            playlist.availability(library, allTracks.value).missingTrackIds.takeIf { it.isNotEmpty() } ?: return
        } else emptyList()
        playlistRemovalRequest = PlaylistRemovalRequest(id, missingOnly, candidates, ticket)
    }
    fun closePlaylistRemoval() { playlistRemovalRequest = null }
    fun removePlaylist(request: PlaylistRemovalRequest) {
        if (!restoreMutations.accepts(request.mutationTicket)) { rejectPlaylistMutation(); return }
        if (request.missingOnly) {
            if (libraryAccess.value && hasCompleteLibraryScan.value && !isLibraryLoading.value) {
                userPlaylists.removeMissing(request.playlistId, allTracks.value, request.missingCandidateIds)
            }
        } else {
            userPlaylists.delete(request.playlistId)
            if (editingPlaylistId == request.playlistId) editingPlaylistId = null
            backStacks.values.forEach { stack -> stack.removeAll { it == Route.UserPlaylistDetail(request.playlistId) } }
        }
    }

    /** A name being asked: for a new playlist (with its first tracks) or to rename one. */
    var playlistPrompt by mutableStateOf<PlaylistPrompt?>(null, referentialEqualityPolicy())
        private set
    private var playlistPromptTicket: MutationTicket? = null
    fun askPlaylistName(prompt: PlaylistPrompt) {
        val ticket = restoreMutations.ticket() ?: return
        playlistPromptTicket = ticket
        playlistPrompt = prompt
    }
    fun canConfirmPlaylistPrompt(prompt: PlaylistPrompt?): Boolean {
        val current = prompt != null && prompt === playlistPrompt && restoreMutations.accepts(playlistPromptTicket)
        if (!current) rejectPlaylistMutation()
        return current
    }
    var savedQueuePlaylist by mutableStateOf<SavedQueuePlaylist?>(null)
        private set
    fun clearSavedQueuePlaylist() { savedQueuePlaylist = null }

    /** Mutate while the prompt ticket is still live; the view dismisses only after this returns. */
    fun confirmPlaylistName(prompt: PlaylistPrompt?, name: String): PlaylistNameResult? {
        if (name.isBlank() || !canConfirmPlaylistPrompt(prompt)) return null
        when (prompt) {
            is PlaylistPrompt.Create -> {
                val id = userPlaylists.create(name.trim(), prompt.trackIds)
                if (prompt.trackIds.isEmpty()) open(Route.UserPlaylistDetail(id))
            }
            is PlaylistPrompt.CreateFromQueue -> {
                if (!libraryAccess.value || !hasCompleteLibraryScan.value || isLibraryLoading.value) {
                    return PlaylistNameResult.QueueSelectionChanged
                }
                val ids = prompt.snapshot.currentTrackIds(TrackAvailability(allTracks.value))
                if (ids.isNullOrEmpty()) return PlaylistNameResult.QueueSelectionChanged
                val id = userPlaylists.create(name.trim(), ids)
                savedQueuePlaylist = SavedQueuePlaylist(id, name.trim())
            }
            is PlaylistPrompt.Rename -> userPlaylists.rename(prompt.id, name.trim())
            null -> return null
        }
        return PlaylistNameResult.Saved
    }
    fun openSavedQueuePlaylist() {
        if (!restoreMutations.canMutate) return
        val saved = savedQueuePlaylist ?: return
        if (userPlaylists.playlists.value.any { it.id == saved.id }) {
            closeNowPlaying()
            open(Route.UserPlaylistDetail(saved.id))
        }
        clearSavedQueuePlaylist()
    }
    fun closePlaylistPrompt() { playlistPrompt = null; playlistPromptTicket = null }

    var playlistMutationRejected by mutableStateOf(false)
        private set
    fun closePlaylistMutationNotice() { playlistMutationRejected = false }
    private fun rejectPlaylistMutation() {
        playlistMutationRejected = true
        closePlaylistPrompt()
        closePlaylistInsertion()
        closePlaylistRemoval()
    }

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
        get() = playlistMutationRejected || alphabetJumpRequest != null || playlistInsertionRequest != null || playlistAdditionNotice != null || playlistRemovalRequest != null ||
            deletingSmartPlaylistId != null || isLibraryDurationPickerOpen || isSleepMenuOpen || restoreDefaultsArea != null || defaultSoundRequest != null || menuTrack != null || isSongInfoVisible || addToPlaylistTracks != null ||
            playlistPrompt != null || playlistOptionsId != null

    fun openBookmarks(track: Track? = null) {
        closeMenu()
        closeNowPlaying()
        open(Route.Bookmarks(track))
    }

    fun openRatings() {
        open(Route.Ratings)
    }

    fun openArtworkSearch(track: Track) {
        closeMenu()
        closeNowPlaying()
        open(Route.OnlineTrackArtwork(track))
    }

    fun openTagEditor(track: Track) {
        if (track.isCueTrack) return
        closeNowPlaying()
        open(Route.TagEditor(track))
    }

    fun closeMenu() {
        menuTrack = null
        menuPlaylistId = null
        trackMenuPage = TrackMenuPage.Main
        categoryTargets = CategoryTargets()
    }

    /** Leaves Now Playing if needed and opens the album of [track] in the current tab. */
    fun showAlbumOf(track: Track) {
        val album = library.albumOf(track) ?: return
        closeNowPlaying()
        open(Route.AlbumDetail(album))
    }

    fun showArtistOf(track: Track) {
        library.artistOf(track)?.let(::showArtist)
    }

    /** Primary and additional credits leave the player before revealing their destination. */
    fun showArtist(artist: Artist) {
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
