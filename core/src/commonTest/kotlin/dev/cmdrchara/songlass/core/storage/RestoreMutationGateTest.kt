package dev.cmdrchara.songlass.core.storage

import dev.cmdrchara.songlass.core.equalizer.Equalizer
import dev.cmdrchara.songlass.core.equalizer.EqPreset
import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.home.HomeCustomization
import dev.cmdrchara.songlass.core.home.HomeSection
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.library.Pins
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.playback.TrackMarkers
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.playlists.SmartPlaylists
import dev.cmdrchara.songlass.core.playlists.SmartRules
import dev.cmdrchara.songlass.core.playlists.UserPlaylists
import dev.cmdrchara.songlass.core.search.SearchHistory
import dev.cmdrchara.songlass.core.search.SearchTarget
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.AppearanceMode
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreMutationGateTest {
    @Test fun pendingManagedActionsCannotWriteOrPublishDuringReplacement() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val favorites = Favorites(store, gate).apply { toggle("old") }
        val pins = Pins(store, gate).apply { toggle(PlayContext.Artist("Old")) }
        val home = HomeCustomization(store, gate).apply { addShortcut(PlayContext.Favorites) }
        val settings = AppSettings(store, gate)
        val search = SearchHistory(store, gate).apply { record(SearchTarget.Song("old")) }
        val users = UserPlaylists(store, gate) { 1L }
        val userId = users.create("Old", listOf("old"))
        val smart = SmartPlaylists(store, gate) { 1L }
        val smartId = smart.save(null, "Old", SmartRules())
        val history = ListeningHistory(store, gate) { 1L }.apply { recordPlay("old", null) }
        val eq = Equalizer(store, gate).apply { applyPreset(EqPreset.Voice) }
        fun states() = listOf(favorites.ids.value, pins.contexts.value, home.state.value,
            settings.state.value, search.targets.value, users.playlists.value,
            smart.playlists.value, history.state.value, eq.state.value)
        val persisted = store.snapshot()
        val accepted = states()

        gate.restoring {
            // These calls model callbacks already queued before a modal gets recomposed.
            assertFalse(favorites.toggle("new"))
            assertTrue(favorites.toggle("old"))
            assertFalse(pins.toggle(PlayContext.Artist("New")))
            home.setSectionVisible(HomeSection.Statistics, false)
            assertFalse(home.addShortcut(PlayContext.Artist("New")))
            settings.setAppearance(AppearanceMode.Dark)
            settings.setFolderExcluded("Music", true)
            search.clear()
            search.record(SearchTarget.Song("new"))
            users.rename(userId, "Changed")
            users.delete(userId)
            assertFalse(users.addDetailed(userId, listOf("new")).accepted)
            assertFailsWith<RestoreMutationRejectedException> { users.create("Phantom") }
            smart.delete(smartId)
            assertFailsWith<RestoreMutationRejectedException> { smart.save(smartId, "Changed", SmartRules()) }
            history.recordPlay("new", PlayContext.Favorites)
            eq.applyPreset(EqPreset.MoreBass)
            eq.setEnabled(true)
            eq.setAutomaticOutputs(true)
            assertEquals(persisted, store.snapshot())
            assertEquals(accepted, states())
        }
        assertTrue(favorites.toggle("new"))
        assertEquals(listOf("old", "new"), favorites.ids.value)
        assertEquals(1, history.state.value.totalPlays)
    }

    @Test fun failureInvalidatesEarlierWorkAndNeverLetsNestedRestoreReleaseTheOuterGate() = runTest {
        val gate = RestoreMutationGate()
        val pending = assertNotNull(gate.ticket())
        assertTrue(gate.accepts(pending))
        assertFalse(RestoreMutationGate().accepts(pending))
        assertFailsWith<IllegalStateException> {
            gate.restoring {
                assertNull(gate.ticket())
                assertFalse(gate.accepts(pending))
                assertFailsWith<RestoreMutationRejectedException> { gate.restoring { error("Must not enter") } }
                assertTrue(gate.state.value.restoring)
                error("Preparation failed")
            }
        }
        assertTrue(gate.canMutate)
        assertFalse(gate.accepts(pending))
        val fresh = assertNotNull(gate.ticket())
        assertTrue(gate.accepts(fresh))
        gate.restoring { /* A restore may replace data by equal values. */ }
        assertFalse(gate.accepts(fresh))
    }

    @Test fun cancellationRestoresTheGateButDoesNotRevivePendingWork() = runTest {
        val gate = RestoreMutationGate()
        val old = assertNotNull(gate.ticket())
        val job = launch(start = CoroutineStart.UNDISPATCHED) { gate.restoring { awaitCancellation() } }
        assertFalse(gate.canMutate)
        job.cancelAndJoin()
        assertTrue(gate.canMutate)
        assertFalse(gate.accepts(old))
    }

    @Test fun systemPauseStillWorksWhileHistoryAndResumeWritesAreSuspended() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val song = track("long", durationMs = 1_200_000)
        val markers = TrackMarkers(store).apply { setLibrary(listOf(song), stableIds = true) }
        val history = ListeningHistory(store, gate) { 1L }
        val audio = FakeAudioPlayer()
        val controller = PlaybackController(audio, backgroundScope, markers = markers,
            preferences = { AppPreferences(resumePerTrack = true) })
        controller.playTracks(listOf(song))
        runCurrent()
        val previous = store.snapshot()
        gate.restoring {
            markers.writesSuspended = true
            try {
                audio.advanceBy(90_000)
                controller.pause()
                runCurrent()
                history.recordPlay(song.id, null)
                assertFalse(audio.state.value.isPlaying)
                assertFalse(audio.state.value.playWhenReady)
                assertEquals(song.id, controller.queue.value.current?.track?.id)
                assertEquals(previous, store.snapshot())
                assertTrue(markers.state.value.progress.isEmpty())
                assertEquals(0, history.state.value.totalPlays)
            } finally { markers.writesSuspended = false }
        }
    }
}
