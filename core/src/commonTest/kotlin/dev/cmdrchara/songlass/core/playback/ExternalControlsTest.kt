package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.browse.PlayRequest
import dev.cmdrchara.songlass.core.browse.play
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Delayed row callbacks and remote mode changes must address the authoritative core queue. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExternalControlsTest {
    @Test
    fun manualIntentRevisionChangesForTransportButNotTicksOrNativeJoins() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        runCurrent()
        val initial = controller.transportIntentRevision
        player.advanceBy(1000)
        runCurrent()
        player.finishTrack()
        runCurrent()
        assertEquals(initial, controller.transportIntentRevision)
        controller.pause()
        assertTrue(controller.transportIntentRevision > initial)
        val paused = controller.transportIntentRevision
        controller.seekTo(5000)
        assertTrue(controller.transportIntentRevision > paused)
        val sought = controller.transportIntentRevision
        controller.resume()
        assertTrue(controller.transportIntentRevision > sought)
        val resumed = controller.transportIntentRevision
        controller.skipTo(-99)
        controller.removeFromQueue(-99)
        assertEquals(resumed, controller.transportIntentRevision)
        controller.clearQueue()
        assertTrue(controller.transportIntentRevision > resumed)
    }

    @Test
    fun explicitBrowserPositionOverridesResumeIncludingAZeroRestart() = runTest {
        val files = listOf(track("a", durationMs = 1_200_000), track("b", durationMs = 1_200_000))
        for (shuffle in listOf(false, true)) for (position in listOf(0L, 90_000L)) {
            val markers = TrackMarkers(InMemoryKeyValueStore()).apply {
                setLibrary(files)
                files.forEach { checkpoint(it, 250_000, 600_000, force = true) }
            }
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope, Random(0), markers = markers,
                preferences = { AppPreferences(resumePerTrack = true) })
            controller.play(PlayRequest(files, 1, null, shuffle), position)
            runCurrent()
            assertEquals(position, player.state.value.positionMs)
            if (!shuffle) assertEquals(files[1], player.loadedTrack)
            else assertTrue(player.loadedTrack in files)
            assertEquals(1L, controller.timeline.value.discontinuityId)
            if (position == 0L) assertNull(markers.progressFor(player.loadedTrack!!, 600_000))
        }
    }

    @Test
    fun unspecifiedBrowserPositionPreservesTheResumePolicy() = runTest {
        val file = track("a", durationMs = 1_200_000)
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply {
            setLibrary(listOf(file))
            checkpoint(file, 250_000, 600_000, force = true)
        }
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, markers = markers,
            preferences = { AppPreferences(resumePerTrack = true) })
        controller.play(PlayRequest(listOf(file), 0, null))
        runCurrent()
        assertEquals(250_000L, player.state.value.positionMs)
        assertEquals(0L, controller.timeline.value.discontinuityId)
    }

    @Test
    fun rejectedBrowserSelectionCannotSeekTheOutgoingDecoder() = runTest {
        val files = tracks(2)
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.refreshTracks(listOf(files[0]))
        controller.playTracks(listOf(files[0]))
        controller.seekTo(42_000)
        controller.pause()
        runCurrent()
        val before = player.state.value
        val timeline = controller.timeline.value
        val generation = player.eventGeneration
        for (shuffle in listOf(false, true)) {
            controller.play(PlayRequest(listOf(files[1]), 0, null, shuffle), 1000)
            runCurrent()
            assertEquals(before, player.state.value)
            assertEquals(files[0], player.loadedTrack)
            assertEquals(timeline, controller.timeline.value)
            assertEquals(generation, player.eventGeneration)
        }
    }

    @Test
    fun staleRowActionsCannotSelectRemoveOrMoveSlotsInAReplacementQueue() = runTest {
        val player = FakeAudioPlayer()
        var userCommands = 0
        val controller = PlaybackController(player, backgroundScope, onUserTransport = { userCommands++ })
        controller.playTracks(tracks(3))
        val stale = controller.queue.value.entries[1].id
        controller.playTracks(listOf(track("a"), track("b"), track("c")))
        controller.pause()
        controller.seekTo(42_000)
        runCurrent()
        val queue = controller.queue.value
        val state = player.state.value
        val timeline = controller.timeline.value
        val generation = player.eventGeneration
        val commandsBefore = userCommands

        controller.skipTo(stale)
        controller.removeFromQueue(stale)
        assertFalse(controller.moveQueueEntry(stale, 1))
        runCurrent()

        assertEquals(queue, controller.queue.value)
        assertEquals(state, player.state.value)
        assertEquals(timeline, controller.timeline.value)
        assertEquals(generation, player.eventGeneration)
        assertEquals(commandsBefore, userCommands, "a stale row is not a transport command")
    }

    @Test
    fun entryIdentitySurvivesEveryQueueReplacementAndEmptyingPath() = runTest {
        val controller = PlaybackController(FakeAudioPlayer(), backgroundScope, Random(0))
        val seen = mutableSetOf<Long>()
        fun recordNewQueue() {
            val ids = controller.queue.value.entries.map { it.id }
            assertTrue(ids.isNotEmpty())
            assertEquals(ids.size, ids.toSet().size)
            assertTrue(ids.all { seen.add(it) }, "no new slot may reuse an earlier lifetime id")
        }

        controller.playTracks(tracks(3))
        recordNewQueue()
        controller.clearQueue()
        controller.playTracks(emptyList())
        controller.addToQueue(listOf(track("added")))
        recordNewQueue()
        controller.removeFromQueue(controller.queue.value.current!!.id)
        controller.shuffleTracks(tracks(4))
        recordNewQueue()
        controller.playBookmark(track("outside"), 15_000)
        recordNewQueue()
        controller.shuffleTracks(emptyList())
        controller.playNext(listOf(track("last"), track("last")))
        recordNewQueue()
    }

    @Test
    fun selectingTheSameFilesAgainStillCreatesDifferentOccurrences() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        val same = track("same")
        controller.playTracks(listOf(same, same))
        val stale = controller.queue.value.entries.last().id
        controller.playTracks(listOf(same, same))
        val before = controller.queue.value
        controller.removeFromQueue(stale)
        controller.skipTo(stale)
        assertEquals(before, controller.queue.value)
        assertEquals(2, controller.queue.value.entries.size)
        assertEquals(same, player.loadedTrack)
    }

    @Test
    fun staleRemovalOnClearedQueueDoesNotStopAnotherTime() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(1))
        val stale = controller.queue.value.current!!.id
        controller.clearQueue()
        val generation = player.eventGeneration
        controller.removeFromQueue(stale)
        assertEquals(generation, player.eventGeneration)
        assertNull(controller.queue.value.current)
    }

    @Test
    fun directRepeatSelectionRearmsNativeNextBeforeTheFlowCollectorRuns() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(2))
        runCurrent()
        assertEquals("2", player.nextTrack?.id)

        controller.setRepeatMode(RepeatMode.One)
        assertEquals("1", player.nextTrack?.id)
        player.finishTrack()
        runCurrent()
        assertEquals("1", controller.queue.value.current?.track?.id)
        assertEquals("1", player.nextTrack?.id, "repeat one rearms the same occurrence")

        controller.setRepeatMode(RepeatMode.Off)
        assertEquals("2", player.nextTrack?.id)
        controller.skipToNext()
        runCurrent()
        assertNull(player.nextTrack)
        controller.setRepeatMode(RepeatMode.All)
        assertEquals("1", player.nextTrack?.id)
        controller.setRepeatMode(RepeatMode.Off)
        assertNull(player.nextTrack)
    }

    @Test
    fun modeChangesReconcileAnAlreadyStartedNativeOccurrenceFirst() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        runCurrent()
        player.finishTrack()
        controller.setRepeatMode(RepeatMode.One)
        assertEquals("2", controller.queue.value.current?.track?.id)
        assertEquals("2", player.nextTrack?.id)
        runCurrent()
        assertEquals("2", controller.queue.value.current?.track?.id)
        assertEquals(RepeatMode.One, controller.queue.value.repeatMode)
    }

    @Test
    fun shuffleChangesPrefetchSynchronouslyWithoutRestartingOrResuming() = runTest {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, Random(0))
        controller.playTracks(tracks(8))
        controller.pause()
        controller.seekTo(25_000)
        runCurrent()
        val generation = player.eventGeneration
        val current = controller.queue.value.current
        controller.setShuffle(true)
        val shuffled = controller.queue.value
        assertTrue(shuffled.isShuffled)
        assertEquals(shuffled.entries[shuffled.nextIndex(false)!!].track, player.nextTrack)
        controller.setShuffle(false)
        assertEquals("2", player.nextTrack?.id)
        assertEquals(current, controller.queue.value.current)
        assertEquals(generation, player.eventGeneration)
        assertEquals(25_000L, player.state.value.positionMs)
        assertFalse(player.state.value.playWhenReady)
    }
}
