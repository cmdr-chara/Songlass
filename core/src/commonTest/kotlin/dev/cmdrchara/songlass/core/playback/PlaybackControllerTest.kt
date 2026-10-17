package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
}
