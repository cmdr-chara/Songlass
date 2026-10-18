package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private val player = FakeAudioPlayer()

    private fun test(block: suspend TestScope.(PlaybackController, SleepTimer) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val controller = PlaybackController(player, backgroundScope, Random(0))
        block(controller, SleepTimer(controller, backgroundScope, clock = { testScheduler.currentTime }))
    }

    @Test
    fun timedTimerPausesAfterTheDuration() = test { controller, timer ->
        controller.playTracks(tracks(3))
        timer.start(minutes = 15)
        assertEquals(SleepTimerState.Until(15 * 60_000L), timer.state.value)
        advanceTimeBy(15 * 60_000L - 1)
        assertTrue(player.state.value.isPlaying)
        advanceTimeBy(2)
        assertFalse(player.state.value.isPlaying)
        assertEquals(SleepTimerState.Off, timer.state.value)
    }

    @Test
    fun cancelKeepsPlaying() = test { controller, timer ->
        controller.playTracks(tracks(3))
        timer.start(minutes = 1)
        timer.cancel()
        advanceTimeBy(120_000)
        assertTrue(player.state.value.isPlaying)
        assertEquals(SleepTimerState.Off, timer.state.value)
    }

    @Test
    fun endOfTrackStopsAtTheEndAndTurnsOff() = test { controller, timer ->
        controller.playTracks(tracks(3))
        timer.endOfTrack()
        assertEquals(SleepTimerState.EndOfTrack, timer.state.value)
        player.finishTrack()
        assertFalse(player.state.value.playWhenReady)
        assertEquals(SleepTimerState.Off, timer.state.value)
    }

    @Test
    fun startingATimerReplacesEndOfTrack() = test { controller, timer ->
        controller.playTracks(tracks(3))
        timer.endOfTrack()
        timer.start(minutes = 30)
        assertFalse(controller.stopAfterCurrent.value)
        assertEquals(SleepTimerState.Until(30 * 60_000L), timer.state.value)
    }
}
