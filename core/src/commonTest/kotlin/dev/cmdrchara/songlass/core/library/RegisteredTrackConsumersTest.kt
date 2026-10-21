package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.TrackAvailability
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.playback.TrackMarkers
import dev.cmdrchara.songlass.core.playback.TrackSignature
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Registry IDs must remain authoritative all the way to queue, resume points and bookmarks. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisteredTrackConsumersTest {
    @Test
    fun identicalTagsAndPathCannotTransferSavedPointsToAReusedSourceIdAndOriginalCanReturn() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryKeyValueStore()
            val registry = TrackIdentityRegistry(store)
            val original = registry.canonicalize(listOf(raw("7"))).single()
            val markers = TrackMarkers(store).apply { setLibrary(listOf(original), stableIds = true) }
            val bookmark = markers.add(original, 88_000, "Original chapter")
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope, markers = markers,
                preferences = { AppPreferences(resumePerTrack = true) })
            controller.refreshTracks(listOf(original), stableIds = true)
            controller.playTracks(listOf(original))
            controller.seekTo(145_000)
            controller.pause()

            // Only the arrival date distinguishes this replacement; path fallback is unsafe.
            val replacementRaw = raw("7").copy(dateAddedMs = 2_000)
            val replacement = registry.canonicalize(listOf(replacementRaw)).single()
            assertNotEquals(original.id, replacement.id)
            markers.setLibrary(listOf(replacement), stableIds = true)
            controller.refreshTracks(listOf(replacement), stableIds = true)
            assertNull(TrackAvailability(listOf(replacement), stableIds = true).resolve(original))
            assertFalse(controller.queue.value.current!!.isAvailable)
            assertEquals(original, controller.queue.value.current!!.track)
            assertNull(markers.resolve(bookmark.track))
            assertNull(markers.progressFor(replacement, 600_000))
            assertTrue(markers.bookmarksFor(replacement).isEmpty())

            controller.stop()
            controller.playBookmark(original, bookmark.positionMs)
            assertNull(player.loadedTrack)
            controller.playTracks(listOf(replacement))
            assertEquals(replacement, player.loadedTrack)
            assertEquals(0L, player.state.value.positionMs)
            assertEquals(145_000L, markers.state.value.progress.single { it.track.id == original.id }.positionMs)

            // The original file returns at its known source identity after the replacement left.
            val returned = TrackIdentityRegistry(store).canonicalize(listOf(raw("7"))).single()
            assertEquals(original, returned)
            val restoredMarkers = TrackMarkers(store).apply { setLibrary(listOf(returned), stableIds = true) }
            assertEquals(returned, restoredMarkers.resolve(bookmark.track))
            assertEquals(listOf(bookmark), restoredMarkers.bookmarksFor(returned))
            assertEquals(145_000L, restoredMarkers.progressFor(returned, 600_000))
            markers.setLibrary(listOf(returned), stableIds = true)
            controller.refreshTracks(listOf(returned), stableIds = true)
            assertFalse(controller.queue.value.current!!.isAvailable)
            controller.playBookmark(returned, bookmark.positionMs)
            assertEquals(returned, player.loadedTrack)
            assertEquals(bookmark.positionMs, player.state.value.positionMs)
        }

    @Test
    fun canonicalReindexPreservesQueueAndMarkersButExplicitPlaybackReopensTheNewUri() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryKeyValueStore()
            val registry = TrackIdentityRegistry(store)
            val original = registry.canonicalize(listOf(raw("7"))).single()
            val markers = TrackMarkers(store).apply { setLibrary(listOf(original), stableIds = true) }
            val bookmark = markers.add(original, 88_000, "Chapter")
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope, markers = markers,
                preferences = { AppPreferences(resumePerTrack = true) })
            controller.refreshTracks(listOf(original), stableIds = true)
            controller.playTracks(listOf(original, original))
            controller.seekTo(185_000)
            controller.pause()
            val queueIds = controller.queue.value.entries.map { it.id }
            val currentId = controller.queue.value.current!!.id

            val reindexed = registry.canonicalize(listOf(raw("42").copy(dateAddedMs = 2_000))).single()
            assertEquals(original.id, reindexed.id)
            assertNotEquals(original.uri, reindexed.uri)
            markers.setLibrary(listOf(reindexed), stableIds = true)
            controller.refreshTracks(listOf(reindexed), stableIds = true)
            assertEquals(reindexed, TrackAvailability(listOf(reindexed), stableIds = true).resolve(original))
            assertEquals(queueIds, controller.queue.value.entries.map { it.id })
            assertEquals(currentId, controller.queue.value.current!!.id)
            assertTrue(controller.queue.value.entries.all { it.isAvailable && it.track == reindexed })
            assertEquals(original, player.loadedTrack)
            assertEquals(reindexed, markers.resolve(bookmark.track))
            assertEquals(listOf(bookmark), markers.bookmarksFor(reindexed))
            assertEquals(185_000L, markers.progressFor(reindexed, 600_000))

            controller.playBookmark(original, bookmark.positionMs)
            assertEquals(reindexed, player.loadedTrack)
            assertEquals(bookmark.positionMs, player.state.value.positionMs)
            assertEquals(queueIds, controller.queue.value.entries.map { it.id })
        }

    @Test
    fun registeredTagEditsPreserveSavedDataEvenWhenAllDisplayTagsChange() = runTest {
        val store = InMemoryKeyValueStore()
        val registry = TrackIdentityRegistry(store)
        val original = registry.canonicalize(listOf(raw("7"))).single()
        val markers = TrackMarkers(store).apply { setLibrary(listOf(original), stableIds = true) }
        val bookmark = markers.add(original, 88_000, "Chapter")
        markers.checkpoint(original, 145_000, 600_000, force = true)
        val editedRaw = raw("7").copy(title = "Edited", artist = "New artist", album = "New album")
        val edited = registry.canonicalize(listOf(editedRaw)).single()

        markers.setLibrary(listOf(edited), stableIds = true)
        assertEquals(edited, TrackAvailability(listOf(edited), stableIds = true).resolve(original))
        assertEquals(edited, markers.resolve(bookmark.track))
        assertEquals(listOf(bookmark), markers.bookmarksFor(edited))
        assertEquals(145_000L, markers.progressFor(edited, 600_000))
    }

    @Test
    fun duplicateCanonicalIdsFailClosedAndLegacyModeKeepsItsExplicitPathContract() =
        runTest(UnconfinedTestDispatcher()) {
            val original = raw("7")
            val rawReindexed = raw("42")
            val store = InMemoryKeyValueStore()
            val markers = TrackMarkers(store)
            val signature = TrackSignature.of(original)
            // Unregistered sources keep the pre-existing behavior by default.
            markers.setLibrary(listOf(rawReindexed))
            assertEquals(rawReindexed, markers.resolve(signature))
            assertEquals(rawReindexed, TrackAvailability(listOf(rawReindexed)).resolve(original))
            markers.setLibrary(listOf(rawReindexed), stableIds = true)
            assertNull(markers.resolve(signature))
            assertNull(TrackAvailability(listOf(rawReindexed), stableIds = true).resolve(original))

            val malformed = listOf(original, rawReindexed.copy(id = original.id))
            markers.setLibrary(malformed, stableIds = true)
            assertNull(markers.resolve(signature))
            assertNull(TrackAvailability(malformed, stableIds = true).resolve(original))
            val player = FakeAudioPlayer()
            val controller = PlaybackController(player, backgroundScope)
            controller.playTracks(listOf(original))
            controller.refreshTracks(malformed, stableIds = true)
            assertFalse(controller.queue.value.current!!.isAvailable)

            // Switching back clears the canonical-only index rather than retaining stale results.
            markers.setLibrary(listOf(rawReindexed))
            assertEquals(rawReindexed, markers.resolve(signature))
        }

    private fun raw(id: String) = Track(
        id, "Episode", "content://media/external/audio/media/$id", "Narrator", "Series",
        durationMs = 1_200_000, folder = "Podcasts", fileName = "episode.flac", dateAddedMs = 1_000,
    )
}
