package dev.cmdrchara.songlass.debug

import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.MusicSource
import dev.cmdrchara.songlass.core.model.FileTagEditor
import dev.cmdrchara.songlass.core.model.TagEditRequest
import dev.cmdrchara.songlass.core.model.TagEditSnapshot
import dev.cmdrchara.songlass.core.model.TagRecoveryInfo
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.ui.AppState
import dev.cmdrchara.songlass.ui.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Visual fixtures only: no real editor, storage replacement, playback or recovery file IO. */
internal object VisualAuditRecoveryFixtures {
    private val cases = setOf("tag-recovery", "tag-recovery-verified", "tag-recovery-empty", "tag-recovery-error", "restore-progress")

    fun create(realGraph: AppGraph, id: String): Fixture? {
        if (id !in cases && id !in VisualAuditExtraCases.cases) return null
        val tracks = if (id in VisualAuditExtraCases.cases) VisualAuditExtraCases.tracks else realGraph.library.value.tracks.toList()
        val preferences = realGraph.settings.state.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val source = object : MusicSource {
            override val hasAccess: StateFlow<Boolean> = MutableStateFlow(id !in setOf("access-denied", "welcome-access"))
            override val hasStableTrackIds = true
            override suspend fun loadTracks(): List<Track> = tracks
        }
        val store = InMemoryKeyValueStore()
        if (id == "lyrics-recovery-list" || id == "lyrics-recovery-preview") {
            // Same legacy URI-key format exercised by ImportedLyricsIdentityTest.
            store.putString("lyrics.import." + VisualAuditExtraCases.tracks[0].uri,
                "[00:01]QA synthetic legacy line\n\n[00:02]QA second legacy line")
        }
        val graph = AppGraph(FakeAudioPlayer(), source, store, scope,
            tagEditor = if (id in cases && id != "restore-progress") RecoveryEditor(id) else null)
        graph.settings.setInterfaceStyle(preferences.interfaceStyle)
        graph.settings.setAppearance(preferences.appearance)
        graph.settings.setArtworkStyle(preferences.artworkStyle)
        graph.settings.setReduceMotion(preferences.reduceMotion)
        graph.settings.setReduceTransparency(preferences.reduceTransparency)
        return Fixture(graph, scope, id)
    }

    class Fixture internal constructor(val graph: AppGraph, private val scope: CoroutineScope, private val id: String) {
        suspend fun prepare(state: AppState) {
            // Join the scan mutex directly: App also starts its initial refresh. A separate
            // reader job could complete one scan while that refresh invalidates availability.
            graph.refreshLibrary()
            check(!graph.source.hasAccess.value || (graph.hasCompleteLibraryScan.value && !graph.isLibraryLoading.value)) {
                "QA fixture library unavailable after refresh"
            }
            state.updateLibrary(graph.library.value)
            if (VisualAuditExtraCases.prepare(graph, state, id)) return
            if (id == "restore-progress") {
                state.open(Route.Backup)
                // Enter the real visual mutation gate, without ever running backup restore.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    graph.restoreMutations.restoring { awaitCancellation() }
                }
            } else state.open(Route.About)
        }

        fun close() { scope.cancel() }
    }

    private class RecoveryEditor(private val case: String) : FileTagEditor {
        override suspend fun recoveryInfo(): TagRecoveryInfo {
            if (case == "tag-recovery-error") error("Synthetic recovery status failure")
            val empty = case == "tag-recovery-empty"
            return TagRecoveryInfo(id = "visual-fixture-$case", retainedFiles = if (empty) 0 else 2,
                bytes = if (empty) 0L else 8_388_608L, hasVerifiedExport = case == "tag-recovery-verified")
        }

        override suspend fun read(track: Track): TagEditSnapshot = forbidden()
        override suspend fun save(request: TagEditRequest): Unit = forbidden()
        override suspend fun exportRecovery(recoveryId: String, destinationUri: String): Unit = forbidden()
        override suspend fun archiveRecovery(recoveryId: String): Unit = forbidden()
        private fun forbidden(): Nothing = error("Visual-only recovery fixture forbids file operations")
    }
}
