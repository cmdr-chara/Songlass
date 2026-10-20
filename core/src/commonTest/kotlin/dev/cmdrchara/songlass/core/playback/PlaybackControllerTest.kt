package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerEvent
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {

    private val player = FakeAudioPlayer()

    private fun test(block: suspend TestScope.(PlaybackController) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            block(PlaybackController(player, backgroundScope, Random(0)))
        }

    private fun PlaybackController.setRepeat(mode: RepeatMode) {
        while (queue.value.repeatMode != mode) cycleRepeatMode()
    }

    @Test
    fun savedTagsRefreshDuplicateAndUnshuffledSlotsWithoutRestarting() = test { controller ->
        val first = track("1")
        controller.playTracks(listOf(first, track("2"), first))
        player.seekTo(1234)
        controller.setShuffle(true)
        val ids = controller.queue.value.entries.map { it.id }
        val current = controller.queue.value.current?.id
        val updated = first.copy(title = "Edited", artworkRevision = 42)
        controller.refreshTracks(listOf(updated))
        assertEquals(ids, controller.queue.value.entries.map { it.id })
        assertEquals(current, controller.queue.value.current?.id)
        assertEquals(1234, player.state.value.positionMs)
        assertTrue(player.state.value.isPlaying)
        assertTrue(controller.queue.value.entries.filter { it.track.id == "1" }.all { it.track == updated })
        controller.setShuffle(false)
        assertEquals(listOf(updated, track("2"), updated), controller.queue.value.entries.map { it.track })
    }

    @Test
    fun playTracksLoadsAndPlaysStartTrack() = test { controller ->
        controller.playTracks(tracks(3), startIndex = 1)
        assertEquals("2", player.loadedTrack?.id)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun trackEndAdvancesToNext() = test { controller ->
        controller.playTracks(tracks(3))
        player.finishTrack()
        assertEquals("2", player.loadedTrack?.id)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun lastTrackEndStopsWhenRepeatIsOff() = test { controller ->
        controller.playTracks(tracks(2), startIndex = 1)
        player.finishTrack()
        assertEquals("2", player.loadedTrack?.id)
        assertEquals(PlayerStatus.Ended, player.state.value.status)
    }

    @Test
    fun playAfterEndRestartsTrack() = test { controller ->
        controller.playTracks(tracks(1))
        player.finishTrack()
        controller.togglePlayPause()
        assertEquals(0, player.state.value.positionMs)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun repeatAllWrapsToFirstTrack() = test { controller ->
        controller.playTracks(tracks(2), startIndex = 1)
        controller.setRepeat(RepeatMode.All)
        player.finishTrack()
        assertEquals("1", player.loadedTrack?.id)
    }

    @Test
    fun repeatOneReplaysSameTrackButSkipMovesOn() = test { controller ->
        controller.playTracks(tracks(3))
        controller.setRepeat(RepeatMode.One)
        player.advanceBy(60_000)
        player.finishTrack()
        assertEquals("1", player.loadedTrack?.id)
        assertEquals(0, player.state.value.positionMs)
        assertTrue(player.state.value.isPlaying)

        controller.skipToNext()
        assertEquals("2", player.loadedTrack?.id)
    }

    @Test
    fun skipKeepsPausedState() = test { controller ->
        controller.playTracks(tracks(3))
        controller.togglePlayPause()
        controller.skipToNext()
        assertEquals("2", player.loadedTrack?.id)
        assertFalse(player.state.value.playWhenReady)
    }

    @Test
    fun previousRestartsTrackAfterThreshold() = test { controller ->
        controller.playTracks(tracks(3), startIndex = 1)
        player.advanceBy(PlaybackController.RESTART_THRESHOLD_MS + 1)
        controller.skipToPrevious()
        assertEquals("2", player.loadedTrack?.id)
        assertEquals(0, player.state.value.positionMs)
    }

    @Test
    fun previousGoesBackWithinThreshold() = test { controller ->
        controller.playTracks(tracks(3), startIndex = 1)
        player.advanceBy(1_000)
        controller.skipToPrevious()
        assertEquals("1", player.loadedTrack?.id)
    }

    @Test
    fun previousTrackIgnoresThreshold() = test { controller ->
        controller.playTracks(tracks(3), startIndex = 1)
        player.advanceBy(PlaybackController.RESTART_THRESHOLD_MS + 1)
        controller.skipToPreviousTrack()
        assertEquals("1", player.loadedTrack?.id)
    }

    @Test
    fun previousOnFirstTrackRestartsIt() = test { controller ->
        controller.playTracks(tracks(3))
        player.advanceBy(1_000)
        controller.skipToPrevious()
        assertEquals("1", player.loadedTrack?.id)
        assertEquals(0, player.state.value.positionMs)
    }

    @Test
    fun skipToNextAtEndDoesNothingWhenRepeatIsOff() = test { controller ->
        controller.playTracks(tracks(2), startIndex = 1)
        controller.skipToNext()
        assertEquals("2", player.loadedTrack?.id)
    }

    @Test
    fun togglePlayPause() = test { controller ->
        controller.playTracks(tracks(1))
        controller.togglePlayPause()
        assertFalse(player.state.value.isPlaying)
        controller.togglePlayPause()
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun shuffleTracksStartsShuffledQueue() = test { controller ->
        controller.shuffleTracks(tracks(10))
        val queue = controller.queue.value
        assertTrue(queue.isShuffled)
        assertEquals(0, queue.currentIndex)
        assertEquals(queue.current?.track, player.loadedTrack)
    }

    @Test
    fun togglingShuffleDoesNotInterruptCurrentTrack() = test { controller ->
        controller.playTracks(tracks(10), startIndex = 4)
        player.advanceBy(30_000)
        controller.setShuffle(true)
        controller.setShuffle(false)
        assertEquals("5", player.loadedTrack?.id)
        assertEquals(30_000, player.state.value.positionMs)
    }

    @Test
    fun repeatModeSurvivesNewQueue() = test { controller ->
        controller.setRepeat(RepeatMode.All)
        controller.playTracks(tracks(2))
        assertEquals(RepeatMode.All, controller.queue.value.repeatMode)
    }

    @Test
    fun addingToEmptyQueueStartsPlayback() = test { controller ->
        controller.addToQueue(listOf(track("x")))
        assertEquals("x", player.loadedTrack?.id)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun playNextDoesNotInterruptCurrentTrack() = test { controller ->
        controller.playTracks(tracks(2))
        controller.playNext(listOf(track("x")))
        assertEquals("1", player.loadedTrack?.id)
        player.finishTrack()
        assertEquals("x", player.loadedTrack?.id)
    }

    @Test
    fun removingCurrentLoadsFollowingTrack() = test { controller ->
        controller.playTracks(tracks(3))
        controller.removeFromQueue(controller.queue.value.current!!.id)
        assertEquals("2", player.loadedTrack?.id)
    }

    @Test
    fun removingLastEntryStopsPlayer() = test { controller ->
        controller.playTracks(tracks(1))
        controller.removeFromQueue(controller.queue.value.current!!.id)
        assertNull(player.loadedTrack)
        assertEquals(PlayerStatus.Idle, player.state.value.status)
    }

    @Test
    fun skipToEntryPlaysIt() = test { controller ->
        controller.playTracks(tracks(5))
        controller.togglePlayPause()
        controller.skipTo(controller.queue.value.entries[3].id)
        assertEquals("4", player.loadedTrack?.id)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun clearQueueStopsPlayback() = test { controller ->
        controller.playTracks(tracks(3))
        controller.clearQueue()
        assertNull(controller.queue.value.current)
        assertNull(player.loadedTrack)
    }

    @Test
    fun startingPlaybackRemembersItsContext() = test { controller ->
        val album = dev.cmdrchara.songlass.core.history.PlayContext.Album("X", "Y")
        controller.playTracks(tracks(2), context = album)
        assertEquals(album, controller.context)
        controller.shuffleTracks(tracks(3))
        assertNull(controller.context)
    }

    @Test
    fun nextTrackIsPreparedAndFollowsQueueChanges() = test { controller ->
        controller.playTracks(tracks(3))
        assertEquals("2", player.nextTrack?.id)
        controller.playNext(listOf(track("x")))
        assertEquals("x", player.nextTrack?.id)
        controller.skipTo(controller.queue.value.entries.last().id)
        assertNull(player.nextTrack)
    }

    @Test
    fun gaplessAdvanceMovesTheQueueWithoutReloading() = test { controller ->
        controller.playTracks(tracks(3))
        player.finishTrack()
        assertEquals("2", controller.queue.value.current?.track?.id)
        assertEquals("2", player.loadedTrack?.id)
        assertEquals("3", player.nextTrack?.id)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun repeatOneQueuesTheSameTrack() = test { controller ->
        controller.playTracks(tracks(3))
        controller.setRepeat(RepeatMode.One)
        assertEquals("1", player.nextTrack?.id)
    }

    @Test
    fun stopAfterCurrentPausesOnTheNextTrack() = test { controller ->
        controller.playTracks(tracks(3))
        controller.setStopAfterCurrent(true)
        assertNull(player.nextTrack)
        player.finishTrack()
        assertEquals("2", player.loadedTrack?.id)
        assertFalse(player.state.value.playWhenReady)
        assertFalse(controller.stopAfterCurrent.value)
        assertEquals("3", player.nextTrack?.id)
    }

    @Test
    fun explicitTrackChoicesUseManualTransitionsButInitialPlaybackDoesNot() = runTest(UnconfinedTestDispatcher()) {
        val audio = RecordingPlayer()
        val controller = PlaybackController(audio, backgroundScope, Random(0))
        controller.playTracks(tracks(4))
        assertEquals(listOf("1"), audio.loads)
        assertTrue(audio.manual.isEmpty())
        controller.skipToNext()
        controller.skipToPreviousTrack()
        controller.skipTo(controller.queue.value.entries[2].id)
        controller.playTracks(listOf(track("collection")))
        assertEquals(listOf("2", "1", "3", "collection"), audio.manual.map { it.first })
        assertEquals(0, controller.queue.value.currentIndex)
    }

    @Test
    fun manualSkipPreservesPauseAndPreviousRestartRemainsASeek() = runTest(UnconfinedTestDispatcher()) {
        val audio = RecordingPlayer()
        val controller = PlaybackController(audio, backgroundScope)
        controller.playTracks(tracks(3))
        controller.pause()
        controller.skipToNext()
        assertEquals("2" to false, audio.manual.single())
        controller.resume()
        audio.fake.seekTo(4_000)
        controller.skipToPrevious()
        assertEquals(0, audio.fake.state.value.positionMs)
        assertEquals(1, audio.manual.size)
        controller.skipToPrevious()
        assertEquals("1" to true, audio.manual.last())
    }

    @Test
    fun automaticFallbackRemovalAndErrorRecoveryStayImmediate() = runTest(UnconfinedTestDispatcher()) {
        val audio = RecordingPlayer()
        val controller = PlaybackController(audio, backgroundScope)
        controller.playTracks(tracks(4))
        audio.signal(PlayerEvent.TrackEnded(audio.eventGeneration))
        assertEquals("2", audio.fake.loadedTrack?.id)
        controller.removeFromQueue(controller.queue.value.current!!.id)
        assertEquals("3", audio.fake.loadedTrack?.id)
        audio.fake.fail("fixture")
        controller.togglePlayPause()
        assertEquals(listOf("1", "2", "3", "3"), audio.loads)
        assertTrue(audio.manual.isEmpty())
    }

    @Test
    fun bufferedAdvanceFromBeforeAManualSkipCannotAdvanceAgain() = runTest {
        val audio = RecordingPlayer()
        val controller = PlaybackController(audio, backgroundScope)
        runCurrent()
        controller.playTracks(tracks(4))
        runCurrent()
        audio.signal(PlayerEvent.AdvancedToNext(audio.eventGeneration))
        controller.skipToNext()
        runCurrent()
        assertEquals(1, controller.queue.value.currentIndex)
        assertEquals("2", audio.fake.loadedTrack?.id)
    }

    @Test
    fun bufferedEndFromBeforeAReplacementOrStopCannotChangeTheNewQueue() = runTest {
        val audio = RecordingPlayer()
        val controller = PlaybackController(audio, backgroundScope)
        runCurrent()
        controller.playTracks(tracks(4))
        runCurrent()
        audio.signal(PlayerEvent.TrackEnded(audio.eventGeneration))
        controller.skipTo(controller.queue.value.entries[2].id)
        runCurrent()
        assertEquals(2, controller.queue.value.currentIndex)
        audio.signal(PlayerEvent.TrackEnded(audio.eventGeneration))
        controller.clearQueue()
        controller.playTracks(tracks(3))
        runCurrent()
        assertEquals(0, controller.queue.value.currentIndex)
        assertEquals("1", audio.fake.loadedTrack?.id)
    }
}

/** Records the command boundary, and can deliver events after the user has changed the timeline. */
private class RecordingPlayer(val fake: FakeAudioPlayer = FakeAudioPlayer()) : AudioPlayer by fake {
    override val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    val loads = mutableListOf<String>()
    val manual = mutableListOf<Pair<String, Boolean>>()

    override fun load(track: Track, playWhenReady: Boolean) {
        loads += track.id
        fake.load(track, playWhenReady)
    }

    override fun transitionTo(track: Track, playWhenReady: Boolean) {
        manual += track.id to playWhenReady
        fake.load(track, playWhenReady)
    }

    fun signal(event: PlayerEvent) { check(events.tryEmit(event)) }
}
