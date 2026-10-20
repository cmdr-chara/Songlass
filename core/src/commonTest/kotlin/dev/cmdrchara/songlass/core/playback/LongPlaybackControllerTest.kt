package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LongPlaybackControllerTest {
    private fun episode(id: String) = Track(
        id = id, uri = "content://media/audio/$id", title = "Episode $id", artist = "Narrator",
        album = "Podcast", folder = "Podcasts", fileName = "$id.flac", durationMs = 1_200_000L,
    )

    @Test
    fun twoIndependentPointsSurviveControllerRecreationAndExplicitSelect() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryKeyValueStore()
            val settings = AppSettings(store).apply { setResumePerTrack(true) }
            val a = episode("a")
            val b = episode("b")
            val markers = TrackMarkers(store).apply { setLibrary(listOf(a, b)) }
            val first = FakeAudioPlayer()
            val controller = PlaybackController(first, backgroundScope, Random(1), markers) { settings.state.value }
            controller.playTracks(listOf(a, b))
            controller.seekTo(145_000)
            controller.skipToNext()
            controller.seekTo(320_000)
            controller.pause()
            assertEquals(145_000L, markers.progressFor(a, 600_000))
            assertEquals(320_000L, markers.progressFor(b, 600_000))
            val laterPlayer = FakeAudioPlayer()
            val laterMarkers = TrackMarkers(store).apply { setLibrary(listOf(a, b)) }
            val reopened = PlaybackController(laterPlayer, backgroundScope, Random(2), laterMarkers) { AppSettings(store).state.value }
            reopened.playTracks(listOf(a, b))
            assertEquals(145_000L, laterPlayer.state.value.positionMs)
            reopened.skipToNext()
            assertEquals(320_000L, laterPlayer.state.value.positionMs)
        }

    @Test
    fun turningOffOptInDoesNotDeleteSavedPointsOrResumeNewSelections() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryKeyValueStore()
            val settings = AppSettings(store).apply { setResumePerTrack(true) }
            val a = episode("a")
            val markers = TrackMarkers(store).apply { setLibrary(listOf(a)) }
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope, Random(0), markers) { settings.state.value }
            controller.playTracks(listOf(a))
            controller.seekTo(190_000)
            settings.setResumePerTrack(false)
            controller.playTracks(listOf(a))
            assertEquals(0L, player.state.value.positionMs)
            assertEquals(190_000L, markers.progressFor(a, 600_000))
        }

    @Test
    fun autoAdvanceDoesNotRestoreNextTracksSavedPosition() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryKeyValueStore()
            val settings = AppSettings(store).apply { setResumePerTrack(true) }
            val a = episode("a")
            val b = episode("b")
            val markers = TrackMarkers(store).apply {
                setLibrary(listOf(a, b))
                checkpoint(b, 350_000L, 600_000L, true)
            }
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope, Random(0), markers) { settings.state.value }
            controller.playTracks(listOf(a, b))
            player.finishTrack()
            assertEquals(b.id, player.loadedTrack?.id)
            assertEquals(0L, player.state.value.positionMs)
            assertEquals(350_000L, markers.progressFor(b, 600_000))
        }

    @Test
    fun seekBeginningAndPlayFromBeginningExplicitlyDiscardPreviousPoint() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryKeyValueStore()
            val settings = AppSettings(store).apply { setResumePerTrack(true) }
            val a = episode("a")
            val markers = TrackMarkers(store).apply { setLibrary(listOf(a)) }
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope, Random(0), markers) { settings.state.value }
            controller.playTracks(listOf(a))
            controller.seekTo(250_000)
            assertEquals(250_000L, markers.progressFor(a, 600_000))
            controller.seekTo(0)
            assertNull(markers.progressFor(a, 600_000))
            controller.seekTo(320_000)
            controller.playFromBeginning(a)
            assertEquals(0L, player.state.value.positionMs)
            assertNull(markers.progressFor(a, 600_000))
        }

    @Test
    fun bookmarkInOtherTrackUsesQueueWithoutChangingOrder() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryKeyValueStore()
            val settings = AppSettings(store)
            val a = episode("a")
            val b = episode("b")
            val markers = TrackMarkers(store).apply { setLibrary(listOf(a, b)) }
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope, Random(0), markers) { settings.state.value }
            controller.playTracks(listOf(a, b))
            val orderBefore = controller.queue.value.entries.map { it.id }
            controller.playBookmark(b, 165_000)
            assertEquals(b.id, player.loadedTrack?.id)
            assertEquals(165_000L, player.state.value.positionMs)
            assertEquals(orderBefore, controller.queue.value.entries.map { it.id })
            assertEquals(b.id, controller.queue.value.current?.track?.id)
            controller.skipBy(-10_000L)
            assertEquals(155_000L, player.state.value.positionMs)
            controller.skipBy(10_000L)
            assertEquals(165_000L, player.state.value.positionMs)
        }

    @Test
    fun storageFailureDoesNotBlockQueueOrPause() =
        runTest(UnconfinedTestDispatcher()) {
            val underlying = InMemoryKeyValueStore()
            val store = object : KeyValueStore by underlying {
                override fun putString(key: String, value: String?) {
                    if (key.startsWith("playback.")) error("Disk is full")
                    underlying.putString(key, value)
                }
            }
            val settings = AppSettings(store).apply { setResumePerTrack(true) }
            val a = episode("a")
            val b = episode("b")
            val markers = TrackMarkers(store).apply { setLibrary(listOf(a, b)) }
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope, Random(0), markers) { settings.state.value }
            controller.playTracks(listOf(a, b))
            controller.seekTo(175_000)
            controller.skipToNext()
            assertEquals("b", player.loadedTrack?.id)
            controller.pause()
            assertTrue(!player.state.value.playWhenReady)
        }
}
