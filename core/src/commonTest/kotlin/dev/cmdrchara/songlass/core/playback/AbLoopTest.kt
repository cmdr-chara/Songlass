package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AbLoopTest {
    private val first = Track("first", "First", "file://first", durationMs = 10_000)
    private val second = first.copy(id = "second", uri = "file://second")

    @Test fun repeatsWithoutAdvancingQueueAndRestoresPrefetchWhenCleared() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(listOf(first, second))
        controller.seekTo(2_000)
        assertTrue(controller.markLoopStart(first.id))
        controller.seekTo(4_000)
        assertTrue(controller.markLoopEnd(first.id))
        assertNull(player.nextTrack)
        repeat(3) {
            assertEquals(2_000, player.state.value.positionMs)
            player.advanceBy(2_100)
            assertEquals(first.id, controller.queue.value.current?.track?.id)
        }
        controller.clearAbLoop()
        assertEquals(second, player.nextTrack)
        player.finishTrack()
        assertEquals(second.id, controller.queue.value.current?.track?.id)
    }

    @Test fun validatesBoundsAndPreservesPauseButClearsForOutsideSeekAndTrackChange() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        assertFalse(controller.markLoopStart(first.id))
        controller.playTracks(listOf(first, second))
        assertFalse(controller.markLoopStart("stale"))
        controller.seekTo(2_000)
        assertTrue(controller.markLoopStart(first.id))
        controller.seekTo(2_999)
        assertFalse(controller.markLoopEnd(first.id))
        controller.seekTo(4_000)
        controller.pause()
        assertTrue(controller.markLoopEnd(first.id))
        assertFalse(player.state.value.playWhenReady)
        assertEquals(2_000, player.state.value.positionMs)
        controller.resume()
        player.advanceBy(2_100)
        assertEquals(2_000, player.state.value.positionMs)
        controller.seekTo(1_999)
        assertNull(controller.abLoop.value)
        controller.markLoopStart(first.id)
        controller.skipToNext()
        assertNull(controller.abLoop.value)
        assertFalse(controller.markLoopEnd(first.id))
    }

    @Test fun endBoundarySleepTimerStopAndUnavailableFileDoNotTrapPlayback() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(listOf(first, second))
        controller.seekTo(8_000)
        controller.markLoopStart(first.id)
        controller.seekTo(10_000)
        assertTrue(controller.markLoopEnd(first.id))
        player.finishTrack()
        assertEquals(8_000, player.state.value.positionMs)
        assertEquals(first.id, controller.queue.value.current?.track?.id)
        controller.setStopAfterCurrent(true)
        assertNull(controller.abLoop.value)
        player.finishTrack()
        assertFalse(player.state.value.playWhenReady)
        controller.seekTo(1_000)
        controller.markLoopStart(second.id)
        controller.refreshTracks(emptyList())
        assertNull(controller.abLoop.value)
        controller.stop()
        assertNull(controller.abLoop.value)
    }

    @Test fun pendingNativeSeekIsNotRestartedByPositionTicks() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeAudioPlayer()
        val seeks = mutableListOf<Long>()
        val player = object : AudioPlayer by fake {
            override fun seekTo(positionMs: Long) { seeks += positionMs }
        }
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(listOf(first, second))
        fake.seekTo(1_000)
        controller.markLoopStart(first.id)
        fake.seekTo(3_000)
        controller.markLoopEnd(first.id)
        repeat(3) { fake.advanceBy(100) }
        assertEquals(listOf(1_000L), seeks)
        fake.seekTo(seeks.last())
        fake.advanceBy(2_100)
        repeat(3) { fake.advanceBy(100) }
        assertEquals(listOf(1_000L, 1_000L), seeks)
    }
}
