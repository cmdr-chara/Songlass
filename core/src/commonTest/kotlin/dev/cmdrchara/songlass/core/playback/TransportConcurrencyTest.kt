package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Events deliberately wait while the real decoder state and explicit commands move ahead. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportConcurrencyTest {
    @Test
    fun seekAfterAnUndeliveredEndKeepsItsTrackPositionAndSleepRequest() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        controller.setStopAfterCurrent(true)
        runCurrent()
        player.finishTrack()
        val endedGeneration = player.eventGeneration
        controller.seekTo(42_000)
        runCurrent()
        assertTrue(player.eventGeneration > endedGeneration)
        assertEquals("1", controller.queue.value.current?.track?.id)
        assertEquals(controller.queue.value.current?.track, player.loadedTrack)
        assertEquals(PlayerStatus.Ready, player.state.value.status)
        assertEquals(42_000L, player.state.value.positionMs)
        assertTrue(controller.stopAfterCurrent.value)
        // Only the new, actual end fulfils the pending end-of-track request.
        player.finishTrack()
        runCurrent()
        assertEquals("2", player.loadedTrack?.id)
        assertFalse(player.state.value.playWhenReady)
        assertFalse(controller.stopAfterCurrent.value)
    }

    @Test
    fun restartAfterAnUndeliveredEndCannotAdvanceTheQueueAgain() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        runCurrent()
        player.setNext(null) // A platform fallback ends before its next decoder is ready.
        player.finishTrack()
        controller.playFromBeginning(track("1"))
        runCurrent()
        assertEquals("1", player.loadedTrack?.id)
        assertEquals("1", controller.queue.value.current?.track?.id)
        assertEquals(0L, player.state.value.positionMs)
        assertTrue(player.state.value.playWhenReady)
        assertEquals(PlaybackTimeline(2, 1), controller.timeline.value)
    }

    @Test
    fun bookmarkForTheOutgoingTrackReloadsItAfterAnUndeliveredNativeJoin() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val first = track("a")
        val second = track("b")
        controller.playTracks(listOf(first, second))
        runCurrent()
        player.finishTrack()
        assertEquals(second, player.loadedTrack)
        controller.playBookmark(first, 42_000)
        assertEquals(first, player.loadedTrack, "the bookmark must never seek the already-started second file")
        runCurrent()
        assertEquals(first, controller.queue.value.current?.track)
        assertEquals(42_000L, player.state.value.positionMs)
        assertEquals(listOf(first, second), controller.queue.value.entries.map { it.track })
        assertEquals(PlaybackTimeline(3, 1), controller.timeline.value)
    }

    @Test
    fun seekAfterNativeJoinUsesItsIncomingIdentityBeforeInvalidatingTheOldEvent() = runTest {
        val session = markedPlayback()
        with(session) {
            runCurrent()
            controller.seekTo(125_000)
            runCurrent()
            player.finishTrack()
            controller.seekTo(42_000)
            controller.pause()
            runCurrent()
            assertEquals(second, controller.queue.value.current?.track)
            assertEquals(second, player.loadedTrack)
            assertEquals(42_000L, player.state.value.positionMs)
            assertNull(markers.progressFor(first, 600_000))
            assertEquals(42_000L, markers.progressFor(second, 600_000))
            assertEquals(PlaybackTimeline(2, 2), controller.timeline.value)
        }
    }

    @Test
    fun aStateObserverAttributesIncomingProgressBeforeTheAdvanceFlowIsDelivered() = runTest {
        val session = markedPlayback()
        with(session) {
            runCurrent()
            controller.seekTo(125_000)
            runCurrent()
            player.finishTrack()
            player.advanceBy(42_321)
            runCurrent()
            assertEquals(second, controller.queue.value.current?.track)
            assertNull(markers.progressFor(first, 600_000))
            assertEquals(42_321L, markers.progressFor(second, 600_000))
            assertEquals(PlaybackTimeline(2, 1), controller.timeline.value)
        }
    }

    @Test
    fun pauseAfterNativeJoinFlushesTheIncomingPointExactlyOnce() = runTest {
        val session = markedPlayback()
        with(session) {
            runCurrent()
            controller.seekTo(125_000)
            runCurrent()
            player.finishTrack()
            player.advanceBy(42_321)
            controller.pause()
            assertEquals(second, controller.queue.value.current?.track)
            assertEquals(42_321L, markers.progressFor(second, 600_000))
            runCurrent()
            assertFalse(player.state.value.playWhenReady)
            assertNull(markers.progressFor(first, 600_000))
            assertEquals(42_321L, markers.progressFor(second, 600_000))
            assertEquals(PlaybackTimeline(2, 1), controller.timeline.value,
                "the subsequently delivered copy of the join is not a second occurrence")
        }
    }

    @Test
    fun stopAfterNativeJoinRetainsTheIncomingPointForExplicitResume() = runTest {
        val session = markedPlayback()
        with(session) {
            runCurrent()
            player.finishTrack()
            player.advanceBy(42_321)
            controller.stop()
            runCurrent()
            assertEquals(second, controller.queue.value.current?.track)
            assertEquals(PlayerStatus.Idle, player.state.value.status)
            assertEquals(42_321L, markers.progressFor(second, 600_000))
            assertNull(markers.progressFor(first, 600_000))
            controller.resume()
            runCurrent()
            assertEquals(second, player.loadedTrack)
            assertEquals(42_321L, player.state.value.positionMs)
        }
    }

    @Test
    fun pauseAfterAnUndeliveredFallbackEndKeepsTheContinuationPaused() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        runCurrent()
        player.setNext(null) // No prepared decoder; the controller must choose the continuation.
        player.finishTrack()
        controller.pause()
        runCurrent()
        assertEquals("2", controller.queue.value.current?.track?.id)
        assertEquals(controller.queue.value.current?.track, player.loadedTrack)
        assertFalse(player.state.value.playWhenReady)
    }

    @Test
    fun pauseAfterAnUndeliveredRepeatEndDoesNotRestartAudio() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        while (controller.queue.value.repeatMode != RepeatMode.One) controller.cycleRepeatMode()
        runCurrent()
        player.setNext(null)
        player.finishTrack()
        controller.pause()
        runCurrent()
        assertEquals("1", controller.queue.value.current?.track?.id)
        assertEquals(0L, player.state.value.positionMs)
        assertFalse(player.state.value.playWhenReady)
        assertEquals(PlaybackTimeline(2, 0), controller.timeline.value)
    }

    @Test
    fun nextAfterUndeliveredAdvanceSkipsTheActuallyPlayingOccurrence() = runTest {
        val player = FakeAudioPlayer()
        var commands = 0
        val controller = PlaybackController(player, backgroundScope, onUserTransport = { commands++ })
        controller.playTracks(tracks(3))
        runCurrent()
        player.finishTrack()
        controller.skipToNext()
        runCurrent()
        assertEquals("3", player.loadedTrack?.id)
        assertEquals("3", controller.queue.value.current?.track?.id)
        assertEquals(PlaybackTimeline(3, 0), controller.timeline.value)
        assertEquals(2, commands, "reconciling a native join is not another user command")
    }

    @Test
    fun indexMoveKeepsCapturedSlotsWhenAnUndeliveredTemporaryAdvanceRemovesARow() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        controller.addToQueue(listOf(track("x"), track("y"), track("z"), track("w")))
        runCurrent()
        player.finishTrack()
        runCurrent()
        assertEquals("x", controller.queue.value.current?.track?.id)
        val before = controller.queue.value.entries
        val from = before.indexOfFirst { it.track.id == "w" }
        val to = before.indexOfFirst { it.track.id == "z" }
        player.finishTrack()
        controller.moveInQueue(from, to)
        runCurrent()
        assertEquals("y", controller.queue.value.current?.track?.id)
        assertEquals(controller.queue.value.current?.track, player.loadedTrack)
        assertEquals(listOf("1", "y", "w", "z", "2"), controller.queue.value.entries.map { it.track.id })
    }

    private fun TestScope.markedPlayback(): MarkedPlayback {
        val first = track("a", durationMs = 1_200_000)
        val second = track("b", durationMs = 1_200_000)
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply { setLibrary(listOf(first, second)) }
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, markers = markers) {
            AppPreferences(resumePerTrack = true)
        }
        controller.playTracks(listOf(first, second))
        return MarkedPlayback(controller, player, markers, first, second)
    }

    private data class MarkedPlayback(
        val controller: PlaybackController,
        val player: FakeAudioPlayer,
        val markers: TrackMarkers,
        val first: Track,
        val second: Track,
    )
}
