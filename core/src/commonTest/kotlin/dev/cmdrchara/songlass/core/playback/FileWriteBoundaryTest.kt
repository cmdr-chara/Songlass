package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FileWriteBoundaryTest {
    @Test
    fun aWriteReconcilesThePhysicalJoinBeforeItsDecoderGenerationCanBeReplaced() = runTest {
        val first = track("a", durationMs = 1_200_000)
        val second = track("b", durationMs = 1_200_000)
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply { setLibrary(listOf(first, second)) }
        val player = FakeAudioPlayer()
        var userCommands = 0
        val controller = PlaybackController(player, backgroundScope, markers = markers,
            onUserTransport = { userCommands++ }, preferences = { AppPreferences(resumePerTrack = true) })
        controller.playTracks(listOf(first, second))
        runCurrent()
        player.finishTrack()
        player.advanceBy(42_321)
        // Neither the state observation nor the advance Flow has run since the physical join.
        controller.prepareForFileWrite()
        assertEquals(second, controller.queue.value.current?.track)
        assertNull(markers.progressFor(first, 600_000))
        assertEquals(42_321L, markers.progressFor(second, 600_000))
        val nativeGeneration = player.eventGeneration
        // Native file exclusion releases/reopens the same occurrence without a new user command.
        player.loadAt(second, true, 42_321)
        assertTrue(player.eventGeneration > nativeGeneration)
        runCurrent()
        assertEquals(second, controller.queue.value.current?.track)
        assertEquals(PlaybackTimeline(2, 0), controller.timeline.value)
        assertEquals(1, userCommands, "file maintenance must not pretend to be an explicit transport command")
    }

    @Test
    fun aWriteCheckpointsTheLatestPausedPositionWithoutChangingIntentOrOccurrence() = runTest {
        val track = track("a", durationMs = 1_200_000)
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply { setLibrary(listOf(track)) }
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, markers = markers) { AppPreferences(resumePerTrack = true) }
        controller.playTracks(listOf(track))
        runCurrent()
        player.advanceBy(31_234)
        player.pause()
        val before = player.state.value
        controller.prepareForFileWrite()
        assertEquals(31_234L, markers.progressFor(track, 600_000))
        assertEquals(before, player.state.value)
        assertEquals(PlaybackTimeline(1, 0), controller.timeline.value)
    }
}
