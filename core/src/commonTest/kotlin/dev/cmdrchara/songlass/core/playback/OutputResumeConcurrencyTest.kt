package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerEvent
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Match AppGraph's queued event/state wiring, including native notification before pause state. */
@OptIn(ExperimentalCoroutinesApi::class)
class OutputResumeConcurrencyTest {
    @Test
    fun explicitPauseCancelsADisconnectWhoseNotificationIsStillQueued() = runTest {
        val session = playingSession()
        runCurrent()
        session.player.disconnectOutput()
        session.controller.pause()
        runCurrent()
        assertFalse(session.reconnect())
        assertFalse(session.player.state.value.playWhenReady)
    }

    @Test
    fun explicitStopCancelsADisconnectWhoseNotificationIsStillQueued() = runTest {
        val session = playingSession()
        runCurrent()
        session.player.disconnectOutput()
        session.controller.stop()
        runCurrent()
        assertFalse(session.reconnect())
        assertEquals(PlayerStatus.Idle, session.player.state.value.status)
    }

    @Test
    fun explicitSeekCancelsADisconnectWhoseNotificationIsStillQueued() = runTest {
        val session = playingSession()
        runCurrent()
        session.player.disconnectOutput()
        session.controller.seekTo(42_000)
        runCurrent()
        assertFalse(session.reconnect())
        assertEquals(42_000L, session.player.state.value.positionMs)
        assertFalse(session.player.state.value.playWhenReady)
    }

    @Test
    fun aLaterFreshDisconnectCanResumeOnceWithoutRearmingItsDelayedNotification() = runTest {
        val session = playingSession()
        runCurrent()
        session.player.disconnectOutput()
        session.controller.pause()
        runCurrent()
        assertFalse(session.reconnect())
        session.controller.resume()
        runCurrent()
        session.now = 200
        session.player.disconnectOutput()
        // The connection callback can precede the notification collector this time.
        assertTrue(session.reconnect())
        assertTrue(session.player.state.value.playWhenReady)
        runCurrent()
        session.player.pause() // A subsequent pause without a new output disconnect.
        runCurrent()
        assertFalse(session.reconnect(), "the delayed copy of the consumed occurrence cannot re-arm")
        assertFalse(session.player.state.value.playWhenReady)
    }

    @Test
    fun nativePlaybackStartInvalidatesADisconnectEvenWhenThePauseStateIsConflated() = runTest {
        val session = playingSession()
        runCurrent()
        session.player.disconnectOutput()
        session.player.play() // Focus/platform playback start, without a controller callback.
        runCurrent()
        assertTrue(session.player.state.value.playWhenReady)
        assertFalse(session.reconnect())
        session.player.pause()
        runCurrent()
        assertFalse(session.reconnect(), "an unrelated later pause does not revive the old disconnect")
    }

    @Test
    fun delayedDeliveryUsesTheOriginalDisconnectTimeRatherThanOpeningANewWindow() = runTest {
        val session = playingSession()
        runCurrent()
        session.player.disconnectOutput()
        session.now = 5_000
        runCurrent()
        assertFalse(session.reconnect())
        assertFalse(session.player.state.value.playWhenReady)
    }

    @Test
    fun reconnectCanConsumeTheNativeOccurrenceBeforeItsNotificationArrives() = runTest {
        val session = playingSession()
        runCurrent()
        session.player.disconnectOutput()
        session.now = 900
        assertTrue(session.reconnect())
        runCurrent()
        assertFalse(session.reconnect())
        assertTrue(session.player.state.value.playWhenReady)
    }

    @Test
    fun aConnectionWhileThePreferenceIsOffConsumesItsSourceOccurrence() = runTest {
        val session = playingSession()
        runCurrent()
        session.player.disconnectOutput()
        assertFalse(session.reconnect(enabled = false))
        runCurrent()
        assertFalse(session.reconnect(enabled = true))
        assertFalse(session.player.state.value.playWhenReady)
    }

    private fun TestScope.playingSession() = Session(backgroundScope).also {
        it.controller.playTracks(tracks(2))
    }

    private class Session(scope: CoroutineScope) {
        var now = 0L
        val player = FakeAudioPlayer { now }
        private val resume = OutputResume(
            clock = { player.monotonicTimeMs }, windowMs = 1_000,
            latestDisconnect = { player.latestDisconnect },
        )
        val controller = PlaybackController(player, scope, onUserTransport = resume::cancel)

        init {
            scope.launch {
                player.events.collect { if (it == PlayerEvent.PausedByDisconnect) resume.onPausedByDisconnect() }
            }
            scope.launch {
                player.state.map { it.playWhenReady }.distinctUntilChanged().collect { if (it) resume.cancel() }
            }
        }

        fun reconnect(enabled: Boolean = true): Boolean = resume.onOutputConnected(enabled).also {
            if (it) controller.resume()
        }
    }
}
