package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncPlaybackIntentTest {
    @Test
    fun requestingPlaybackInvalidatesReconnectWithoutOpeningOrResumingAFile() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer { 1_000L }
        val reconnect = OutputResume({ 1_000L }, latestDisconnect = { player.latestDisconnect })
        val controller = PlaybackController(player, backgroundScope, onUserTransport = reconnect::cancel)
        controller.playTracks(listOf(track("a"), track("b")))
        player.disconnectOutput()
        reconnect.onPausedByDisconnect()
        val state = player.state.value
        val queue = controller.queue.value
        val generation = player.eventGeneration

        val requested = controller.beginAsyncPlaybackRequest()

        assertEquals(requested, controller.transportIntentRevision)
        assertEquals(state, player.state.value)
        assertEquals(queue, controller.queue.value)
        assertEquals(generation, player.eventGeneration)
        assertFalse(reconnect.onOutputConnected(enabled = true))
    }

    @Test
    fun twoPendingSourcesAndARepeatedFirstRequestNeverReuseTheFirstToken() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        var notifications = 0
        val controller = PlaybackController(player, backgroundScope, onUserTransport = { notifications++ })
        controller.playTracks(listOf(track("a")))
        controller.pause()
        val state = player.state.value
        val before = notifications

        val firstVoiceRequest = controller.beginAsyncPlaybackRequest()
        val remoteRequest = controller.beginAsyncPlaybackRequest()
        val repeatedVoiceRequest = controller.beginAsyncPlaybackRequest()

        assertTrue(firstVoiceRequest < remoteRequest && remoteRequest < repeatedVoiceRequest)
        assertNotEquals(firstVoiceRequest, controller.transportIntentRevision)
        assertNotEquals(remoteRequest, controller.transportIntentRevision)
        assertEquals(repeatedVoiceRequest, controller.transportIntentRevision)
        assertEquals(before + 3, notifications)
        assertEquals(state, player.state.value, "pending requests do not set play intent on the decoder")
    }

    @Test
    fun aFinalPauseSupersedesPendingPlayEvenIfAlreadyPaused() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(listOf(track("a")))
        controller.pause()
        val state = player.state.value
        val pending = controller.beginAsyncPlaybackRequest()

        controller.pause()

        assertNotEquals(pending, controller.transportIntentRevision)
        assertEquals(state, player.state.value)
        assertFalse(player.state.value.playWhenReady)
    }

    @Test
    fun stopAndClearEachSupersedePendingWorkWithoutAHistoryFallback() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(listOf(track("a")))
        val beforeStop = controller.beginAsyncPlaybackRequest()
        controller.stop()
        assertNotEquals(beforeStop, controller.transportIntentRevision)
        assertEquals("a", controller.queue.value.current?.track?.id)
        val beforeClear = controller.beginAsyncPlaybackRequest()
        controller.clearQueue()
        assertNotEquals(beforeClear, controller.transportIntentRevision)
        assertTrue(controller.queue.value.entries.isEmpty())
        assertEquals(null, player.loadedTrack)
    }

    @Test
    fun requestFirstReconcilesAnUndeliveredNativeAdvanceWithoutReloadingIt() = runTest {
        val player = FakeAudioPlayer()
        var observedAtIntent: String? = null
        lateinit var controller: PlaybackController
        controller = PlaybackController(player, backgroundScope, onUserTransport = {
            observedAtIntent = controller.queue.value.current?.track?.id
        })
        controller.playTracks(listOf(track("a"), track("b"), track("c")))
        runCurrent()
        player.finishTrack()
        assertEquals("a", controller.queue.value.current?.track?.id)
        assertEquals("b", player.loadedTrack?.id)
        val generation = player.eventGeneration

        val requested = controller.beginAsyncPlaybackRequest()

        assertEquals("b", observedAtIntent)
        assertEquals("b", controller.queue.value.current?.track?.id)
        assertEquals(requested, controller.transportIntentRevision)
        assertEquals(generation, player.eventGeneration)
        runCurrent()
        assertEquals(requested, controller.transportIntentRevision, "the queued event is not a new user intent")
        assertEquals("b", player.loadedTrack?.id)
    }
}
