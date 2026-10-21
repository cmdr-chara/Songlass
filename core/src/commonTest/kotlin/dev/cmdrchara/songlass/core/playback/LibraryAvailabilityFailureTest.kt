package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAvailabilityFailureTest {
    @Test
    fun invalidationRetainsOpenPlaybackButDisarmsNextAndRejectsEveryNewSelectionUntilRetry() = runTest(UnconfinedTestDispatcher()) {
        val original = track("canonical-a").copy(uri = "content://media/7")
        val next = track("canonical-b").copy(uri = "content://media/8")
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.refreshTracks(listOf(original, next), stableIds = true)
        controller.playTracks(listOf(original, next))
        controller.seekTo(12_000)
        val slots = controller.queue.value.entries.map { it.id }
        assertEquals(next, player.nextTrack)

        // This is the invalidation used by a failed/cancelled scan and a permission revoke.
        controller.refreshTracks(emptyList(), stableIds = true)
        assertEquals(original, player.loadedTrack)
        assertEquals(12_000L, player.state.value.positionMs)
        assertTrue(player.state.value.playWhenReady)
        assertNull(player.nextTrack)
        assertTrue(controller.queue.value.entries.none { it.isAvailable })
        val generation = player.eventGeneration
        controller.playTracks(listOf(next))
        controller.shuffleTracks(listOf(next, original))
        controller.playBookmark(next, 8_000)
        assertEquals(generation, player.eventGeneration)
        assertEquals(slots, controller.queue.value.entries.map { it.id })
        controller.pause()
        controller.resume()
        assertEquals(original, player.loadedTrack, "an open decoder may continue")
        controller.stop()
        controller.resume()
        assertNull(player.loadedTrack, "an unverified address cannot be reopened")

        val reindexed = original.copy(uri = "content://media/42")
        controller.refreshTracks(listOf(reindexed, next), stableIds = true)
        controller.resume()
        assertEquals(reindexed, player.loadedTrack)
        assertEquals(slots, controller.queue.value.entries.map { it.id })
        assertEquals(next, player.nextTrack)
    }

    @Test
    fun anUnavailableEndedFileCannotRestartThroughResumeOrToggleEvenWithRepeatOne() = runTest(UnconfinedTestDispatcher()) {
        for (repeat in listOf(false, true)) {
            for (useResume in listOf(false, true)) {
                val original = track("canonical-a").copy(uri = "content://media/7")
                val player = FakeAudioPlayer()
                val controller = PlaybackController(player, backgroundScope)
                controller.refreshTracks(listOf(original), stableIds = true)
                controller.playTracks(listOf(original))
                if (repeat) while (controller.queue.value.repeatMode != RepeatMode.One) controller.cycleRepeatMode()
                controller.refreshTracks(emptyList(), stableIds = true)
                assertNull(player.nextTrack)
                player.finishTrack()
                assertEquals(PlayerStatus.Ended, player.state.value.status)
                if (useResume) controller.resume() else controller.togglePlayPause()
                assertNull(player.loadedTrack)
                assertEquals(PlayerStatus.Idle, player.state.value.status)
                assertFalse(controller.queue.value.current!!.isAvailable)
            }
        }
    }

    @Test
    fun anExplicitResumeRestartsOrRetriesAnAvailableEndedOrFailedTrackWithRetainedPlayIntent() = runTest(UnconfinedTestDispatcher()) {
        for (failed in listOf(false, true)) {
            val original = track("canonical-a")
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope)
            controller.refreshTracks(listOf(original), stableIds = true)
            controller.playTracks(listOf(original))
            if (failed) player.fail("Read failure") else player.finishTrack()
            assertTrue(player.state.value.playWhenReady)
            assertEquals(if (failed) PlayerStatus.Error else PlayerStatus.Ended, player.state.value.status)
            controller.resume()
            assertEquals(PlayerStatus.Ready, player.state.value.status)
            assertEquals(0L, player.state.value.positionMs)
            assertEquals(original, player.loadedTrack)
            assertTrue(player.state.value.playWhenReady)
        }
    }
}
