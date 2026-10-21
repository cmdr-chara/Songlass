package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TemporaryPlaybackTest {
    @Test
    fun repeatedSameSlotRearmsGaplessWithoutFallbackLoad() = runTest(UnconfinedTestDispatcher()) {
        for (mode in listOf(RepeatMode.All, RepeatMode.One)) {
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope)
            controller.playTracks(tracks(1))
            while (controller.queue.value.repeatMode != mode) controller.cycleRepeatMode()
            val generation = player.eventGeneration
            repeat(4) {
                assertEquals("1", player.nextTrack?.id)
                player.finishTrack()
                assertEquals(generation, player.eventGeneration, "no decoder reload during native repeat")
                assertEquals((it + 2).toLong(), controller.timeline.value.occurrenceId)
            }
            assertEquals("1", player.nextTrack?.id)
        }
    }

    @Test
    fun duplicateSlotsRearmThePreparedNativeNext() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        controller.addToQueue(listOf(track("x"), track("x"), track("x")))
        repeat(3) {
            assertEquals("x", player.nextTrack?.id)
            player.finishTrack()
            assertEquals("x", controller.queue.value.current?.track?.id)
        }
        assertEquals("2", player.nextTrack?.id)
    }

    @Test
    fun aRecycledUriIsNeverReopenedOrPreparedFromTheStaleQueue() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val old = track("1").copy(uri = "content://media/1", folder = "Music", fileName = "original.flac")
        val replacement = old.copy(fileName = "different.flac", title = "Replacement")
        controller.playTracks(listOf(old, track("2")))
        controller.refreshTracks(listOf(replacement, track("2")))
        assertFalse(controller.queue.value.current!!.isAvailable)
        assertEquals(old, player.loadedTrack, "an already opened file may finish safely")
        controller.stop()
        controller.resume()
        assertNull(player.loadedTrack, "the recycled URI must not be opened again")
        controller.skipToNext()
        assertEquals("2", player.loadedTrack?.id)
        assertNull(player.nextTrack)
    }

    @Test
    fun fullScanRemapsUniqueReindexedFilesWithoutChangingQueueSlots() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val old = track("1").copy(uri = "content://media/1", folder = "Music", fileName = "same.flac")
        val reindexed = old.copy(id = "new", uri = "content://media/new")
        controller.playTracks(listOf(old, track("2")))
        val slots = controller.queue.value.entries.map { it.id }
        controller.refreshTracks(listOf(reindexed, track("2")))
        assertEquals(slots, controller.queue.value.entries.map { it.id })
        assertEquals(reindexed, controller.queue.value.current?.track)
        controller.stop()
        controller.resume()
        assertEquals(reindexed, player.loadedTrack)
        controller.stop()
        controller.playBookmark(old, 42_000)
        assertEquals(slots, controller.queue.value.entries.map { it.id }, "old captured bookmark resolves into the existing collection")
        assertEquals(reindexed, player.loadedTrack)
        assertEquals(42_000L, player.state.value.positionMs)
    }

    @Test
    fun unavailableSelectionsDoNotReplaceHealthyPlaybackAndShuffleStartsAtAvailableSlot() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val healthy = track("healthy")
        val missing = track("missing")
        controller.refreshTracks(listOf(healthy))
        controller.playTracks(listOf(healthy))
        val before = controller.queue.value
        controller.playTracks(listOf(missing))
        controller.playBookmark(missing, 2_000)
        controller.shuffleTracks(listOf(missing))
        assertEquals(before, controller.queue.value)
        assertEquals(healthy, player.loadedTrack)
        controller.shuffleTracks(listOf(missing, healthy))
        assertEquals(healthy, player.loadedTrack)
        assertTrue(controller.queue.value.current!!.isAvailable)
    }

    @Test
    fun nativeGaplessAdvancesConsumeEachAdditionExactlyOnce() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x"), track("y")))
        assertEquals("x", player.nextTrack?.id)
        player.finishTrack()
        assertEquals("x", controller.queue.value.current?.track?.id)
        assertEquals("y", player.nextTrack?.id)
        player.finishTrack()
        assertEquals("y", controller.queue.value.current?.track?.id)
        assertEquals("2", player.nextTrack?.id)
        player.finishTrack()
        assertEquals("2", controller.queue.value.current?.track?.id)
        assertEquals(listOf("1", "2", "3"), controller.queue.value.entries.map { it.track.id })
        assertEquals("3", player.nextTrack?.id)
    }

    @Test
    fun manualSkipAndReturnKeepPauseState() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x"), track("y")))
        controller.pause()
        controller.skipToNext()
        assertEquals("x", player.loadedTrack?.id)
        assertFalse(player.state.value.playWhenReady)
        controller.returnToSource()
        assertEquals("2", player.loadedTrack?.id)
        assertFalse(player.state.value.playWhenReady)
        assertTrue(controller.queue.value.temporaryEntries.isEmpty())
    }

    @Test
    fun finalTemporaryCompletionStopsWithoutReplayingSource() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(1))
        controller.playNext(listOf(track("x")))
        player.finishTrack()
        player.finishTrack()
        assertEquals(PlayerStatus.Idle, player.state.value.status)
        assertNull(controller.queue.value.current)
        assertEquals(listOf("1"), controller.queue.value.entries.map { it.track.id })
    }

    @Test
    fun additionsToAnEmptyQueueStartAndRepeatAllDoesNotRepeatThem() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.cycleRepeatMode()
        assertEquals(RepeatMode.All, controller.queue.value.repeatMode)
        controller.addToQueue(listOf(track("x")))
        assertEquals("x", player.loadedTrack?.id)
        player.finishTrack()
        assertNull(player.loadedTrack)
        assertTrue(controller.queue.value.entries.isEmpty())
    }

    @Test
    fun clearingPendingDoesNotReloadCurrentAndReplacesPreparedNext() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x"), track("y")))
        controller.skipToNext()
        controller.seekTo(14_000)
        val generation = player.eventGeneration
        controller.clearTemporaryQueue()
        assertEquals(generation, player.eventGeneration)
        assertEquals(14_000L, player.state.value.positionMs)
        assertEquals("2", player.nextTrack?.id)
    }

    @Test
    fun replacingCollectionDiscardsPendingAdditions() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x")))
        controller.playTracks(listOf(track("a"), track("b")))
        assertEquals(listOf("a", "b"), controller.queue.value.entries.map { it.track.id })
        assertEquals("b", player.nextTrack?.id)
    }

    @Test
    fun stopAfterCurrentRespectsTemporaryBoundaryAndPreparesPausedContinuation() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        controller.addToQueue(listOf(track("x")))
        controller.skipToNext()
        controller.setStopAfterCurrent(true)
        assertNull(player.nextTrack)
        player.finishTrack()
        assertEquals("2", player.loadedTrack?.id)
        assertFalse(player.state.value.playWhenReady)
        assertFalse(controller.stopAfterCurrent.value)
    }
    @Test
    fun temporaryTrackDoesNotClaimTheOriginalPlaylistListeningContext() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val context = PlayContext.UserPlaylist("source")
        controller.playTracks(tracks(3), context = context)
        controller.addToQueue(listOf(track("x")))
        assertEquals(context, controller.listeningContext)
        controller.skipToNext()
        assertNull(controller.listeningContext)
        controller.skipToNext()
        assertEquals(context, controller.listeningContext)
    }

}
