package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Deliberately use the queued test dispatcher: native callbacks and UI actions can interleave. */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueConcurrencyTest {
    @Test
    fun removalBetweenNativeAdvanceAndDeliveryLoadsTheEditedContinuation() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        runCurrent()
        val consumed = controller.queue.value.entries[1].id
        val generation = player.eventGeneration
        player.finishTrack()
        assertEquals("2", player.loadedTrack?.id)
        controller.removeFromQueue(consumed)
        runCurrent()
        assertEquals("3", controller.queue.value.current?.track?.id)
        assertEquals(controller.queue.value.current?.track, player.loadedTrack)
        assertTrue(player.eventGeneration > generation, "different continuation requires a real load")
    }

    @Test
    fun clearingPendingAfterNativeStartCannotLabelTheSourceAsThatTemporaryDecoder() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        controller.addToQueue(listOf(track("x")))
        runCurrent()
        player.finishTrack()
        controller.clearTemporaryQueue()
        runCurrent()
        assertEquals("2", controller.queue.value.current?.track?.id)
        assertEquals(controller.queue.value.current?.track, player.loadedTrack)
        assertTrue(controller.queue.value.temporaryEntries.isEmpty())
    }

    @Test
    fun removingConsumedLastAdditionStopsInsteadOfKeepingAnUnrepresentedDecoder() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(1))
        controller.addToQueue(listOf(track("x")))
        runCurrent()
        player.finishTrack()
        controller.clearTemporaryQueue()
        runCurrent()
        assertEquals(PlayerStatus.Idle, player.state.value.status)
        assertNull(player.loadedTrack)
        assertTrue(controller.queue.value.temporaryEntries.isEmpty())
    }

    @Test
    fun reorderWhileAdvanceWaitsFollowsTheActuallyConsumedSlot() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        runCurrent()
        val consumed = controller.queue.value.entries[1].id
        val generation = player.eventGeneration
        player.finishTrack()
        controller.moveInQueue(2, 1)
        runCurrent()
        assertEquals(consumed, controller.queue.value.current?.id)
        assertEquals(player.loadedTrack, controller.queue.value.current?.track)
        assertEquals(generation, player.eventGeneration, "the correct native decoder is retained")
    }

    @Test
    fun duplicateFileSlotsCannotSubstituteForTheRemovedConsumedOccurrence() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        controller.addToQueue(listOf(track("x"), track("x")))
        runCurrent()
        val pending = controller.queue.value.temporaryEntries
        val generation = player.eventGeneration
        player.finishTrack()
        controller.removeFromQueue(pending.first().id)
        runCurrent()
        assertEquals(pending.last().id, controller.queue.value.current?.id)
        assertEquals("x", player.loadedTrack?.id)
        assertTrue(player.eventGeneration > generation, "the next occurrence starts independently")
        assertEquals("2", player.nextTrack?.id)
    }

    @Test
    fun endOfTrackSleepRequestedAfterNativeJoinPausesItsContinuation() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        runCurrent()
        player.finishTrack()
        controller.setStopAfterCurrent(true)
        runCurrent()
        assertEquals("2", controller.queue.value.current?.track?.id)
        assertEquals(controller.queue.value.current?.track, player.loadedTrack)
        assertFalse(player.state.value.playWhenReady)
        assertFalse(controller.stopAfterCurrent.value)
    }

    @Test
    fun rowActionsResolveTheLiveSlotAfterTemporaryRowsHaveBeenConsumed() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        controller.addToQueue(listOf(track("x"), track("y"), track("z"), track("w")))
        runCurrent()
        player.finishTrack()
        runCurrent()
        val z = controller.queue.value.entries.first { it.track.id == "z" }.id
        val x = controller.queue.value.current!!.id
        // An old composed row can still invoke its action after x has disappeared.
        player.finishTrack()
        runCurrent()
        assertFalse(controller.moveQueueEntry(z, -1), "must not move ahead of active y")
        assertTrue(controller.moveQueueEntry(z, 1))
        assertEquals(listOf("1", "y", "w", "z", "2"), controller.queue.value.entries.map { it.track.id })
        assertFalse(controller.moveQueueEntry(x, 1), "stale removed slot is a no-op")
        assertEquals("y", player.loadedTrack?.id)
    }
}
