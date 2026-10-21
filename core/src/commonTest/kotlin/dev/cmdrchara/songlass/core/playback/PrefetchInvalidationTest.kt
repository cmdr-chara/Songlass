package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** MainScope dispatches collectors; safety boundaries cannot wait for the next queue emission. */
@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchInvalidationTest {
    private val a = track("canonical-a").copy(uri = "content://media/7")
    private val b = track("canonical-b").copy(uri = "content://media/8")

    @Test
    fun invalidationDisarmsBeforeAQueuedCollectorOrNativeEndCanRun() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.refreshTracks(listOf(a, b), stableIds = true)
        controller.playTracks(listOf(a, b))
        runCurrent()
        assertEquals(b, player.nextTrack)

        controller.refreshTracks(emptyList(), stableIds = true)
        assertNull(player.nextTrack, "invalidation is synchronous, without draining the dispatcher")
        player.finishTrack()
        controller.pause()
        assertEquals(a.id, controller.queue.value.current!!.track.id)
        assertFalse(controller.queue.value.current!!.isAvailable)
        runCurrent()
        assertNull(player.nextTrack, "queued Flow work cannot re-arm the invalidated address")
        assertEquals(a.id, controller.queue.value.current!!.track.id)
    }

    @Test
    fun aLateNativeAdvanceCannotAdoptAnEntryInvalidatedAfterItsPreparation() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.refreshTracks(listOf(a, b), stableIds = true)
        controller.playTracks(listOf(a, b))
        runCurrent()
        val staleSlot = controller.queue.value.entries[1].id
        controller.refreshTracks(emptyList(), stableIds = true)
        // Model a platform handover already in flight when its preparation was removed.
        player.setNext(b, staleSlot)
        player.finishTrack()
        controller.pause() // Reconciles retained native state before its Flow event is delivered.
        assertEquals(a.id, controller.queue.value.current!!.track.id)
        assertNull(player.loadedTrack)
        assertEquals(PlayerStatus.Idle, player.state.value.status)
        runCurrent()
        assertEquals(a.id, controller.queue.value.current!!.track.id)
        assertNull(player.nextTrack)
    }

    @Test
    fun aFreshScanRearmsOnlyItsLatestIdentityWithoutDuplicateNativeCommands() = runTest {
        val fake = FakeAudioPlayer()
        val commands = mutableListOf<Pair<Track?, Long?>>()
        val player = object : AudioPlayer by fake {
            override fun setNext(track: Track?, entryId: Long?) {
                commands += track to entryId
                fake.setNext(track, entryId)
            }
        }
        val controller = PlaybackController(player, backgroundScope)
        controller.refreshTracks(listOf(a, b), stableIds = true)
        controller.playTracks(listOf(a, b))
        runCurrent()
        val before = commands.size
        controller.refreshTracks(emptyList(), stableIds = true)
        val reindexed = b.copy(uri = "content://media/80")
        controller.refreshTracks(listOf(a, reindexed), stableIds = true)
        assertEquals(reindexed, fake.nextTrack)
        assertEquals(listOf(null, reindexed), commands.drop(before).map { it.first })
        runCurrent()
        controller.refreshTracks(listOf(a, reindexed), stableIds = true)
        runCurrent()
        assertEquals(listOf(null, reindexed), commands.drop(before).map { it.first },
            "unchanged scans and pending emissions must not interrupt native transitions")
    }

    @Test
    fun endOfTrackStopDisarmsImmediatelyAndCancellingItRearmsOnce() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(listOf(a, b))
        runCurrent()
        controller.setStopAfterCurrent(true)
        assertNull(player.nextTrack)
        runCurrent()
        assertNull(player.nextTrack)
        controller.setStopAfterCurrent(false)
        assertEquals(b, player.nextTrack)
        runCurrent()
        assertEquals(b, player.nextTrack)
    }
}
