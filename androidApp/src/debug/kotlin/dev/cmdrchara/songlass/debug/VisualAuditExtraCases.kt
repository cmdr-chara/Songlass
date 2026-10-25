package dev.cmdrchara.songlass.debug

import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.SmartRules
import dev.cmdrchara.songlass.ui.AppState
import dev.cmdrchara.songlass.ui.Route
import dev.cmdrchara.songlass.ui.Tab

/** In-memory UI scenarios: no platform player, real preferences or file operations. */
internal object VisualAuditExtraCases {
    val cases = setOf("access-denied", "welcome-access", "playlist-insert-confirm", "playlist-remove-confirm",
        "playlist-remove-missing-confirm", "smart-delete-confirm", "bookmarks-track", "queue-options", "lyrics-recovery-list", "lyrics-recovery-preview")
    val tracks = listOf(
        Track("qa-visual-a", "QA Synthetic First", "visual-audit://first", artist = "QA Artist", album = "QA Album", durationMs = 60_000),
        Track("qa-visual-b", "QA Synthetic Second", "visual-audit://second", artist = "QA Artist", album = "QA Album", durationMs = 90_000),
    )

    suspend fun prepare(graph: AppGraph, state: AppState, id: String): Boolean {
        if (id !in cases) return false
        when (id) {
            "access-denied" -> state.selectTab(Tab.Library)
            "welcome-access" -> graph.onboarding.restart()
            "playlist-insert-confirm" -> {
                val playlist = graph.userPlaylists.create("QA Synthetic Playlist", listOf(tracks[0].id))
                state.open(Route.UserPlaylistDetail(playlist))
                state.askPlaylistInsertion(playlist, listOf(tracks[1].id))
            }
            "playlist-remove-confirm", "playlist-remove-missing-confirm" -> {
                val playlist = graph.userPlaylists.create("QA Synthetic Playlist", listOf(tracks[0].id, "qa-visual-missing"))
                state.open(Route.UserPlaylistDetail(playlist))
                state.askPlaylistRemoval(playlist, missingOnly = id == "playlist-remove-missing-confirm")
            }
            "smart-delete-confirm" -> {
                val playlist = graph.smartPlaylists.save(null, "QA Synthetic Smart", SmartRules())
                state.open(Route.SmartPlaylistDetail(playlist))
                state.askDeleteSmartPlaylist(playlist)
            }
            "bookmarks-track" -> {
                graph.trackMarkers.add(tracks[0], 1_000, "QA Synthetic Marker")
                state.openBookmarks(tracks[0])
            }
            "lyrics-recovery-list", "lyrics-recovery-preview" -> {
                graph.controller.playTracks(listOf(tracks[0]))
                check(graph.controller.queue.value.current?.track?.id == tracks[0].id) { "QA current identity missing after selection" }
                graph.controller.pause()
                check(graph.controller.queue.value.current?.track?.id == tracks[0].id) { "QA current identity changed before ready" }
                state.openNowPlaying()
                state.toggleLyrics()
                // Open Recupera importazioni and its entry through real UI taps, not private state.
            }
            "queue-options" -> {
                graph.controller.playTracks(tracks)
                check(graph.controller.queue.value.current?.track?.id == tracks[0].id) { "QA current identity missing after selection" }
                graph.controller.pause()
                check(graph.controller.queue.value.current?.track?.id == tracks[0].id) { "QA current identity changed before ready" }
                graph.controller.addToQueue(listOf(tracks[0]))
                state.openNowPlaying()
                state.toggleQueue()
                // Options is QueuePane-local state: open using the real Opzioni coda button.
            }
        }
        return true
    }
}
