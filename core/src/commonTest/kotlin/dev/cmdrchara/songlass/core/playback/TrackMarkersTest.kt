package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TrackMarkersTest {
    private fun track(id: String, path: String = "Music/$id.flac", duration: Long = 1_200_000L,
        title: String = "Episode $id") = Track(
        id = id, uri = "content://media/audio/$id", title = title, artist = "Narrator", album = "Series",
        durationMs = duration, folder = path.substringBeforeLast('/'), fileName = path.substringAfterLast('/'),
    )

    @Test
    fun twoTracksSurviveRecreationIndependentlyWithoutOverwritingEachOther() {
        val first = track("one")
        val second = track("two")
        val store = InMemoryKeyValueStore()
        TrackMarkers(store).apply {
            setLibrary(listOf(first, second))
            checkpoint(first, 145_000, 600_000, force = true)
            checkpoint(second, 635_000, 600_000, force = true)
        }
        TrackMarkers(store).apply {
            setLibrary(listOf(first, second))
            assertEquals(145_000L, progressFor(first, 600_000))
            assertEquals(635_000L, progressFor(second, 600_000))
            assertEquals(2, state.value.progress.size)
        }
    }

    @Test
    fun defaultAndShortTracksNeverCreateProgress() {
        val short = track("short", duration = 240_000)
        val store = InMemoryKeyValueStore()
        TrackMarkers(store).apply {
            setLibrary(listOf(short))
            checkpoint(short, 120_000, 600_000, force = true)
            assertTrue(state.value.progress.isEmpty())
            assertNull(progressFor(short, 600_000))
        }
        assertNull(store.getString(TrackMarkersCodec.PROGRESS_KEY))
    }

    @Test
    fun onlyMilestonesAndExplicitCheckpointsWriteToDisk() {
        val long = track("long")
        var writes = 0
        val backing = InMemoryKeyValueStore()
        val store = object : KeyValueStore by backing {
            override fun putString(key: String, value: String?) {
                writes++
                backing.putString(key, value)
            }
        }
        val markers = TrackMarkers(store)
        markers.setLibrary(listOf(long))
        for (at in 10_000L..50_000L step 500L) markers.checkpoint(long, at, 600_000)
        assertEquals(1, writes)
        markers.checkpoint(long, 70_000L, 600_000)
        assertEquals(2, writes)
        markers.checkpoint(long, 70_500L, 600_000)
        assertEquals(2, writes)
        markers.checkpoint(long, 70_500L, 600_000, force = true)
        assertEquals(3, writes)
    }

    @Test
    fun naturalEndAndExplicitBeginningClearOnlyTheirOwnPoint() {
        val one = track("one")
        val two = track("two")
        val markers = TrackMarkers(InMemoryKeyValueStore())
        markers.setLibrary(listOf(one, two))
        markers.checkpoint(one, 200_000, 600_000, true)
        markers.checkpoint(two, 300_000, 600_000, true)
        markers.completed(one)
        assertNull(markers.progressFor(one, 600_000))
        assertEquals(300_000L, markers.progressFor(two, 600_000))
        markers.restart(two)
        assertNull(markers.progressFor(two, 600_000))
    }

    @Test
    fun seekToLastSecondsDiscardsResumePointButBookmarkRemains() {
        val episode = track("episode")
        val markers = TrackMarkers(InMemoryKeyValueStore())
        markers.setLibrary(listOf(episode))
        markers.checkpoint(episode, 180_000, 600_000, true)
        val mark = markers.add(episode, 180_000, "Part 2")
        markers.checkpoint(episode, 1_198_000, 600_000, true)
        assertNull(markers.progressFor(episode, 600_000))
        assertEquals(listOf(mark), markers.bookmarksFor(episode))
    }

    @Test
    fun bookmarksCanBeCreatedRenamedClearedAndDeletedWithoutAudioWrites() {
        val episode = track("episode")
        var nextClock = 42L
        val backing = InMemoryKeyValueStore()
        val markers = TrackMarkers(backing) { nextClock }
        markers.setLibrary(listOf(episode))
        val first = markers.add(episode, 45_000, null)
        val second = markers.add(episode, 85_000, "  Chapter 1  ")
        assertEquals("42", first.id)
        assertEquals("42-1", second.id)
        assertNull(first.title)
        assertEquals("Chapter 1", second.title)
        assertTrue(markers.rename(first.id, "Opening"))
        assertTrue(markers.rename(second.id, " "))
        assertEquals(listOf("Opening", null), markers.bookmarksFor(episode).map { it.title })
        assertFalse(markers.rename("absent", "Never"))
        assertTrue(markers.delete(first.id))
        assertFalse(markers.delete(first.id))
        val again = TrackMarkers(backing).apply { setLibrary(listOf(episode)) }
        assertEquals(1, again.state.value.bookmarks.size)
        assertEquals(second.id, again.state.value.bookmarks.single().id)
        assertFailsWith<IllegalArgumentException> { markers.add(episode, 50_000, "a".repeat(201)) }
    }

    @Test
    fun missingReindexedAndRecycledIdsDoNotOpenIncorrectRecordings() {
        val original = track("17", "Podcasts/Season 1/ep.flac")
        val newId = track("29", "Podcasts/Season 1/ep.flac").copy(
            title = original.title, uri = "content://media/audio/29",
        )
        val unrelated = track("17", "Music/other.flac", title = "Another recording")
        val markers = TrackMarkers(InMemoryKeyValueStore())
        markers.setLibrary(listOf(original))
        markers.checkpoint(original, 385_000, 600_000, true)
        val bookmark = markers.add(original, 385_000, "Checkpoint")
        markers.setLibrary(emptyList())
        assertNull(markers.resolve(bookmark.track))
        assertEquals(bookmark, markers.state.value.bookmarks.single())
        markers.setLibrary(listOf(unrelated, newId))
        assertNull(markers.resolve(TrackSignature.of(unrelated).copy(title = original.title)))
        assertEquals(newId, markers.resolve(bookmark.track))
        assertEquals(385_000L, markers.progressFor(newId, 600_000))
        markers.setLibrary(listOf(unrelated, newId, newId.copy(id = "30", uri = "content://media/audio/30")))
        assertNull(markers.resolve(bookmark.track))
        assertNull(markers.progressFor(newId, 600_000))
        assertEquals(1, markers.state.value.bookmarks.size)
    }

    @Test
    fun corruptDataNeverBecomesAValidBookmarkAndRestoreSuspensionStopsWrites() {
        val store = InMemoryKeyValueStore().apply {
            putString(TrackMarkersCodec.BOOKMARKS_KEY, "v1|1:broken")
        }
        val markers = TrackMarkers(store)
        assertTrue(markers.state.value.bookmarks.isEmpty())
        assertFalse(TrackMarkersCodec.canonicalBookmarks("v1|1:broken"))
        val episode = track("a")
        markers.setLibrary(listOf(episode))
        markers.writesSuspended = true
        markers.checkpoint(episode, 180_000, 600_000, true)
        assertTrue(markers.state.value.progress.isEmpty())
        assertFailsWith<IllegalArgumentException> { markers.add(episode, 10_000, null) }
    }
}
