package dev.cmdrchara.songlass.core.browse

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.playback.TrackMarkers
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackResumptionTest {
    @Test
    fun externalPlayAfterStopKeepsTemporarySlotOrderModesContextAndEligibleCheckpoint() = runTest(UnconfinedTestDispatcher()) {
        val source = listOf("a", "b", "c").map { track(it, durationMs = 1_200_000) }
        val temporary = track("temporary", durationMs = 1_200_000)
        val historical = track("historical")
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply { setLibrary(source + temporary + historical) }
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, random = Random(7), markers = markers) {
            AppPreferences(resumePerTrack = true)
        }
        val context = PlayContext.UserPlaylist("retained-collection")
        controller.shuffleTracks(source, context)
        controller.addToQueue(listOf(temporary))
        controller.skipTo(controller.queue.value.entries.single { it.track == temporary }.id)
        controller.setRepeatMode(RepeatMode.All)
        controller.seekTo(42_321)
        controller.stop()
        val retained = controller.queue.value
        assertTrue(retained.isShuffled && retained.current!!.isTemporary)
        assertEquals(PlayerStatus.Idle, player.state.value.status)
        var historyReads = 0

        val resumed = controller.resumeForExternalPlayback {
            historyReads++
            PlayRequest(listOf(historical), 0, PlayContext.Artist("Earlier collection"))
        }

        assertEquals(0, historyReads)
        assertEquals(retained, controller.queue.value)
        assertEquals(retained.current, resumed)
        assertEquals(context, controller.context)
        assertNull(controller.listeningContext, "one-shot additions retain their separate attribution")
        assertEquals(temporary, player.loadedTrack)
        assertEquals(42_321L, player.state.value.positionMs)
        assertTrue(player.state.value.playWhenReady)
    }

    @Test
    fun unavailableRetainedEntryFailsWithoutSelectingOtherMusic() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val missing = track("missing")
        val historical = track("historical")
        controller.playTracks(listOf(missing))
        controller.refreshTracks(listOf(historical))
        controller.stop()
        val retained = controller.queue.value
        var historyReads = 0

        assertFailsWith<IllegalStateException> {
            controller.resumeForExternalPlayback {
                historyReads++
                PlayRequest(listOf(historical), 0, null)
            }
        }

        assertEquals(0, historyReads)
        assertEquals(retained, controller.queue.value)
        assertFalse(retained.current!!.isAvailable)
        assertNull(player.loadedTrack)
        assertEquals(PlayerStatus.Idle, player.state.value.status)
    }

    @Test
    fun unavailablePausedRetainedEntryFailsBeforeItsOpenDecoderCanResume() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val missing = track("missing")
        val historical = track("historical")
        controller.playTracks(listOf(missing))
        controller.seekTo(12_345)
        controller.pause()
        controller.refreshTracks(listOf(historical))
        val retained = controller.queue.value
        val state = player.state.value
        val generation = player.eventGeneration
        var historyReads = 0
        assertEquals(PlayerStatus.Ready, state.status)
        assertFalse(state.playWhenReady)
        assertFalse(retained.current!!.isAvailable)
        assertEquals(missing, player.loadedTrack)

        assertFailsWith<IllegalStateException> {
            controller.resumeForExternalPlayback {
                historyReads++
                PlayRequest(listOf(historical), 0, null)
            }
        }

        assertEquals(0, historyReads)
        assertEquals(retained, controller.queue.value)
        assertEquals(generation, player.eventGeneration)
        assertEquals(state, player.state.value, "rejection must not set Play before reporting unavailable")
        assertEquals(missing, player.loadedTrack, "the paused decoder must remain untouched")
    }

    @Test
    fun aPausedRetainedEntryResumesItsDecoderWithoutReloadingOrReadingHistory() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(listOf(track("retained")))
        controller.seekTo(12_345)
        controller.pause()
        val generation = player.eventGeneration
        val current = controller.queue.value.current

        assertEquals(current, controller.resumeForExternalPlayback { error("History must not replace a retained queue") })
        assertEquals(generation, player.eventGeneration)
        assertEquals(12_345L, player.state.value.positionMs)
        assertTrue(player.state.value.playWhenReady)
    }

    @Test
    fun emptyQueueUsesHistoryOnceAndReturnsItsAcceptedCoreSelection() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val tracks = listOf(track("a"), track("b"))
        val context = PlayContext.Album("History album", "Artist")
        var historyReads = 0

        val resumed = controller.resumeForExternalPlayback {
            historyReads++
            PlayRequest(tracks, 1, context)
        }

        assertEquals(1, historyReads)
        assertEquals(tracks[1], resumed.track)
        assertEquals(controller.queue.value.current, resumed)
        assertEquals(context, controller.context)
        assertEquals(tracks[1], player.loadedTrack)
        assertTrue(player.state.value.playWhenReady)
    }

    @Test
    fun missingHistoryLeavesAnEmptyQueueAndDecoder() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val generation = player.eventGeneration
        assertFailsWith<IllegalStateException> { controller.resumeForExternalPlayback { null } }
        assertNull(controller.queue.value.current)
        assertNull(player.loadedTrack)
        assertEquals(generation, player.eventGeneration)
    }

    @Test
    fun historyRejectedByCurrentAvailabilityCannotReturnAPhantomSelection() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.refreshTracks(listOf(track("available")))
        assertFailsWith<IllegalStateException> {
            controller.resumeForExternalPlayback { PlayRequest(listOf(track("missing")), 0, null) }
        }
        assertNull(controller.queue.value.current)
        assertNull(player.loadedTrack)
        assertEquals(PlayerStatus.Idle, player.state.value.status)
    }
}
