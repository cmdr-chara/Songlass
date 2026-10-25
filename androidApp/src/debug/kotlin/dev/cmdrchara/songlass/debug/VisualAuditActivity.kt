package dev.cmdrchara.songlass.debug

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import dev.cmdrchara.songlass.App
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.core.library.LibraryFacetKind
import dev.cmdrchara.songlass.core.playlists.SmartRules
import dev.cmdrchara.songlass.core.settings.SettingsArea
import dev.cmdrchara.songlass.core.sound.DefaultSoundTarget
import dev.cmdrchara.songlass.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shell-only debug navigation harness. It never applies sounds, restores data or edits audio. */
class VisualAuditActivity : ComponentActivity() {
    private var selection by mutableStateOf("home" to 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        selection = (intent.getStringExtra("case") ?: "home") to 0
        val app = application as SonglassApplication
        prepareOnboarding(app, selection.first)
        setContent {
            val request = selection
            val fixture = remember(request) { VisualAuditRecoveryFixtures.create(app.graph, request.first) }
            DisposableEffect(fixture) { onDispose { fixture?.close() } }
            val graph = fixture?.graph ?: app.graph
            val state = remember(graph, request) { AppState(graph) }
            key(graph, request) {
                App(graph = graph, onRequestLibraryAccess = { /* Visual-only: never opens permission settings. */ }, artworkLoader = if (fixture == null) app.artworkLoader else null, navigationState = state)
            }
            LaunchedEffect(request, fixture) {
                try {
                    // Let the keyed App mount and start its initial effects before joining its scan.
                    withFrameNanos { }
                    if (fixture != null) fixture.prepare(state)
                    else {
                        app.graph.refreshLibrary()
                        check(app.graph.source.hasAccess.value && app.graph.hasCompleteLibraryScan.value &&
                            !app.graph.isLibraryLoading.value && app.graph.libraryRefreshFailure.value == null) {
                            "Visual audit requires a completed, available library scan"
                        }
                        state.updateLibrary(app.graph.library.value)
                        showCase(app, state, request.first)
                    }
                    Log.i("VisualAudit", "READY ${request.first}${if (fixture != null) " isolated-visual-fixture" else ""} tracks=${graph.library.value.tracks.size} albums=${graph.library.value.albums.size}")
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e("VisualAudit", "UNAVAILABLE ${request.first}: ${failure.javaClass.simpleName}")
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        prepareOnboarding(application as SonglassApplication, intent.getStringExtra("case") ?: "home")
        selection = (intent.getStringExtra("case") ?: "home") to (selection.second + 1)
    }

    private fun prepareOnboarding(app: SonglassApplication, id: String) {
        // Normalize before App composition so completion cannot mount a late refresh after staging.
        if (id == "welcome") app.graph.onboarding.restart() else app.graph.onboarding.complete()
    }

    private suspend fun showCase(app: SonglassApplication, state: AppState, id: String) {
        val graph = app.graph
        // Use real Back actions before changing roots; a previous popup must not survive.
        repeat(80) { if (state.canGoBack) state.goBack() }
        state.selectTab(Tab.Home)
        state.selectTab(Tab.Home)
        state.searchQuery = ""
        if (id == "welcome") return
        val library = state.library
        fun track() = graph.allTracks.value.firstOrNull { it.title.contains("Songlass Karaoke QA", true) }
            ?: error("Songlass Karaoke QA fixture missing")
        fun playlist(empty: Boolean = false): String {
            val name = if (empty) "QA Visual Empty" else "QA Visual Playlist"
            return graph.userPlaylists.playlists.value.firstOrNull { it.name == name }?.id
                ?: graph.userPlaylists.create(name, if (empty) emptyList() else graph.allTracks.value.take(8).map { it.id })
        }
        fun smart(): String = graph.smartPlaylists.playlists.value.firstOrNull { it.name == "QA Visual Smart" }?.id
            ?: graph.smartPlaylists.save(null, "QA Visual Smart", SmartRules())
        if (id.startsWith("player") || id.startsWith("audio") || id.startsWith("menu") || id == "tag" || id == "lyrics-share") {
            val selected = track()
            graph.controller.playTracks(listOf(selected))
            check(graph.controller.queue.value.current?.track?.id == selected.id) { "QA current identity missing after selection" }
            graph.controller.pause()
            check(graph.controller.queue.value.current?.track?.id == selected.id) { "QA current identity changed before ready" }
        }
        // Every supported metadata kind has an explicit route; no genre collection is invented.
        val facetKind = LibraryFacetKind.entries.firstOrNull { id == "facet-list-${it.storageKey}" || id == "facet-${it.storageKey}" }
        if (facetKind != null) {
            if (id.startsWith("facet-list-")) state.open(Route.FacetList(facetKind))
            else state.open(Route.FacetDetail(library.facets(facetKind).first().context))
            return
        }
        if (id.startsWith("folder-root-")) {
            val index = id.removePrefix("folder-root-").toIntOrNull() ?: error("Invalid folder index")
            state.open(Route.FolderDetail(library.folders.getOrNull(index) ?: error("Folder fixture missing")))
            return
        }
        when (id) {
            "prepare-documents" -> VisualAuditDocuments.prepare(app)
            "home" -> Unit
            "library" -> state.selectTab(Tab.Library)
            "search-idle" -> state.selectTab(Tab.Search)
            "search-results" -> { state.selectTab(Tab.Search); state.searchQuery = graph.allTracks.value.firstOrNull()?.title.orEmpty() }
            "search-empty" -> { state.selectTab(Tab.Search); state.searchQuery = "QA_No_Matching_Recording_741859" }
            "artists" -> state.open(Route.LibraryPage(LibraryCategory.Artists))
            "albums" -> state.open(Route.LibraryPage(LibraryCategory.Albums))
            "songs" -> state.open(Route.LibraryPage(LibraryCategory.Songs))
            "favorites" -> state.open(Route.LibraryPage(LibraryCategory.Favorites))
            "album" -> state.open(Route.AlbumDetail(library.albums.first()))
            "artist" -> state.open(Route.ArtistDetail(library.artists.first()))
            "folder" -> state.open(Route.FolderDetail(library.folders.first()))
            "facet-list" -> state.open(Route.FacetList(LibraryFacetKind.AlbumArtist))
            "facet" -> state.open(Route.FacetDetail(library.facets(LibraryFacetKind.AlbumArtist).first().context))
            "playlist" -> state.open(Route.UserPlaylistDetail(playlist()))
            "playlist-empty" -> state.open(Route.UserPlaylistDetail(playlist(true)))
            "playlist-missing" -> state.open(Route.UserPlaylistDetail("qa-does-not-exist"))
            "smart" -> state.open(Route.SmartPlaylistDetail(smart()))
            "smart-new" -> state.open(Route.SmartPlaylistEditor())
            "smart-edit" -> state.open(Route.SmartPlaylistEditor(smart()))
            "bookmarks" -> { val t = track(); if (graph.trackMarkers.bookmarksFor(t).none { it.title == "QA Visual Marker" }) graph.trackMarkers.add(t, 1_000, "QA Visual Marker"); state.openBookmarks() }
            "tag" -> state.openTagEditor(track())
            "eq" -> state.open(Route.Equalizer)
            "autoeq" -> state.open(Route.AutoEqCatalog)
            "autoeq-detail" -> { val catalog = withContext(Dispatchers.IO) { graph.autoEq.catalog() }; state.open(Route.AutoEqDetail(catalog.entries.first())) }
            "home-customization" -> state.open(Route.HomeCustomization)
            "library-settings" -> state.open(Route.LibrarySettings)
            "backup" -> state.open(Route.Backup)
            "about", "licenses" -> state.open(Route.About)
            "stats" -> state.open(Route.ListeningStatistics)
            "settings", "appearance", "sound", "playback", "queue-settings" -> {
                state.open(Route.Settings)
                state.selectSettingsPane(when (id) { "appearance" -> SettingsPane.Appearance; "sound" -> SettingsPane.Sound; "playback" -> SettingsPane.Player; "queue-settings" -> SettingsPane.Queue; else -> SettingsPane.Overview })
            }
            "player", "player-queue", "player-lyrics", "lyrics-share" -> {
                state.openNowPlaying()
                when (id) { "player-queue" -> state.toggleQueue(); "player-lyrics", "lyrics-share" -> state.toggleLyrics(); else -> Unit }
            }
            "audio", "audio-file", "audio-processing", "audio-output" -> {
                state.openNowPlaying(); state.showSongInfo()
                state.showAudioInformationPage(when (id) { "audio-file" -> AudioInformationPage.File; "audio-processing" -> AudioInformationPage.Processing; "audio-output" -> AudioInformationPage.Output; else -> AudioInformationPage.Overview })
            }
            "menu", "menu-more", "menu-artists", "menu-use-as" -> {
                state.openMenu(track()); state.showTrackMenuPage(when (id) { "menu-more" -> TrackMenuPage.More; "menu-artists" -> TrackMenuPage.Artists; "menu-use-as" -> TrackMenuPage.UseAs; else -> TrackMenuPage.Main })
            }
            "sound-ringtone", "sound-alarm" -> state.askDefaultSound(track(), if (id == "sound-ringtone") DefaultSoundTarget.Ringtone else DefaultSoundTarget.Alarm)
            "timer" -> state.openSleepMenu()
            "name-new" -> state.askPlaylistName(PlaylistPrompt.Create(emptyList()))
            "name-rename" -> { val p = graph.userPlaylists.get(playlist())!!; state.askPlaylistName(PlaylistPrompt.Rename(p.id, p.name)) }
            "playlist-add" -> state.openAddToPlaylist(listOf(track()))
            "playlist-options" -> state.openPlaylistOptions(playlist())
            "restore-confirm" -> state.askRestoreDefaults(SettingsArea.Appearance)
            "library-duration" -> state.openLibraryDurationPicker()
            else -> error("Unknown visual case")
        }
    }
}
