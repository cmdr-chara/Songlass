package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

    @Test
    fun rejectedIdentityWritesLeaveExistingCollectionsAndStorageIntact() {
        val store = InMemoryKeyValueStore()
        val original = track("original")
        val markers = TrackMarkers(store).apply {
            setLibrary(listOf(original))
            checkpoint(original, 125_000, 600_000, true)
            add(original, 125_000, "Keep me")
        }
        val before = markers.state.value
        val stored = store.snapshot()
        val oversized = "x".repeat(4_097)
        val invalid = listOf(
            track("other").copy(title = oversized),
            track("other").copy(artist = oversized),
            track("other").copy(album = oversized),
            track("other").copy(fileName = oversized),
            track("other").copy(id = ""),
            track("other").copy(uri = ""),
        )
        invalid.forEach { bad ->
            assertFailsWith<IllegalArgumentException> { markers.add(bad, 150_000, null) }
            assertFailsWith<IllegalArgumentException> { markers.checkpoint(bad, 150_000, 600_000, true) }
            assertEquals(before, markers.state.value)
            assertEquals(stored, store.snapshot())
        }
        assertEquals(before, TrackMarkers(store).state.value)
    }

    @Test
    fun encoderRejectsCollectionsThatExceedTheReadersTotalBound() {
        val wide = "x".repeat(4_096)
        val signature = TrackSignature("id", wide, wide, wide, wide, wide, 1_200_000)
        val bookmarks = List(1_000) { TrackBookmark(it.toString(), signature, 250_000, null) }
        // Every field and the record count are individually valid; their aggregate exceeds 16 MiB.
        assertTrue(bookmarks.size < TrackMarkersCodec.MAX_BOOKMARKS)
        assertFailsWith<IllegalArgumentException> { TrackMarkersCodec.encodeBookmarks(bookmarks) }
        val progress = bookmarks.map { SavedProgress(it.track.copy(id = it.id), it.positionMs) }
        assertFailsWith<IllegalArgumentException> { TrackMarkersCodec.encodeProgress(progress) }
    }

    @Test
    fun codecChecksDuplicateRecordsAndKnownDurationInBothDirections() {
        val signature = TrackSignature.of(track("a"))
        val bookmark = TrackBookmark("one", signature, 250_000, "Chapter")
        val progress = SavedProgress(signature, 250_000)
        assertFailsWith<IllegalArgumentException> { TrackMarkersCodec.encodeBookmarks(listOf(bookmark, bookmark)) }
        assertFailsWith<IllegalArgumentException> { TrackMarkersCodec.encodeProgress(listOf(progress, progress)) }
        assertFailsWith<IllegalArgumentException> {
            TrackMarkersCodec.encodeBookmarks(listOf(bookmark.copy(positionMs = 1_200_001)))
        }
        assertFailsWith<IllegalArgumentException> {
            TrackMarkersCodec.encodeProgress(listOf(progress.copy(positionMs = 1_200_001)))
        }
        val brokenBookmark = TrackMarkersCodec.encodeBookmarks(listOf(bookmark)).replace("6:250000", "7:1200001")
        val brokenProgress = TrackMarkersCodec.encodeProgress(listOf(progress)).replace("6:250000", "7:1200001")
        assertFalse(TrackMarkersCodec.canonicalBookmarks(brokenBookmark))
        assertFalse(TrackMarkersCodec.canonicalProgress(brokenProgress))
        assertFailsWith<IllegalArgumentException> { TrackMarkersCodec.decodeBookmarks(brokenBookmark) }
        assertFailsWith<IllegalArgumentException> { TrackMarkersCodec.decodeProgress(brokenProgress) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun availabilityChangesAreObservableEvenWithoutMarkerOrPlayerChanges() = runTest(UnconfinedTestDispatcher()) {
        val original = track("old")
        val reindexed = original.copy(id = "new", uri = "content://media/audio/new")
        val markers = TrackMarkers(InMemoryKeyValueStore())
        val bookmark = markers.add(original, 150_000, null)
        val before = markers.state.value
        val resolved = mutableListOf<Track?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            markers.libraryRevision.collect { resolved += markers.resolve(bookmark.track) }
        }
        markers.setLibrary(listOf(original))
        markers.setLibrary(emptyList())
        markers.setLibrary(listOf(reindexed))
        markers.setLibrary(listOf(reindexed, reindexed.copy(id = "duplicate")))
        assertEquals(listOf(null, original, null, reindexed, null), resolved)
        assertEquals(before, markers.state.value)
    }
}
