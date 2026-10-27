package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryIndexTest {
    @Test
    fun refreshReturnsAReadyFilteredSnapshotAndReusesItForEqualInputs() = runTest {
        val tracks = MutableStateFlow(listOf(track("z", title = "Zulu"), track("a", title = "Alpha"), track("short", durationMs = 100)))
        val index = LibraryIndex(tracks, { LibraryFilter(minimumDurationMs = 1_000) }, dispatcher = StandardTestDispatcher(testScheduler))
        index.refresh()
        val first = index.state.value
        assertEquals(listOf("a", "z"), first.tracks.map { it.id })
        index.refresh()
        assertSame(first, index.state.value)
        tracks.value = tracks.value.map { it.copy() }
        index.refresh()
        assertSame(first, index.state.value)
        tracks.value = tracks.value.map { if (it.id == "a") it.copy(title = "Renamed") else it }
        index.refresh()
        assertNotSame(first, index.state.value)
        assertEquals("Renamed", index.state.value.search("renamed").tracks.single().title)
    }

    @Test
    fun inputsChangedDuringDispatchAreIndexedWithoutPublishingTheObsoleteResult() = runTest {
        for (replaceTracks in listOf(false, true)) {
            val latest = listOf(track("keep", folder = "Music/Keep"), track("hidden", folder = "Music/Hide"))
            val tracks = MutableStateFlow(if (replaceTracks) listOf(track("obsolete")) else latest)
            var filter = LibraryFilter()
            val index = LibraryIndex(tracks, { filter }, dispatcher = StandardTestDispatcher(testScheduler))
            val emitted = mutableListOf<MusicLibrary>()
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { index.state.collect { emitted += it } }
            val job = launch(start = CoroutineStart.UNDISPATCHED) { index.refresh() }
            if (replaceTracks) tracks.value = latest
            filter = LibraryFilter(excludedFolders = setOf("Music/Hide"))
            advanceUntilIdle()
            job.join()
            assertEquals(listOf(emptyList(), listOf("keep")), emitted.map { it.tracks.map { track -> track.id } })
            observer.cancel()
        }
    }

    @Test
    fun concurrentRefreshesShareOnePublishedSnapshot() = runTest {
        val tracks = MutableStateFlow(listOf(track("one")))
        val index = LibraryIndex(tracks, { LibraryFilter() }, dispatcher = StandardTestDispatcher(testScheduler))
        val emitted = mutableListOf<MusicLibrary>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { index.state.collect { emitted += it } }
        val results = mutableListOf<MusicLibrary>()
        val jobs = List(3) { launch { index.refresh(); results += index.state.value } }
        advanceUntilIdle()
        jobs.forEach { it.join() }
        assertEquals(2, emitted.size)
        results.forEach { assertSame(emitted.last(), it) }
    }

    @Test
    fun aMinimumRatingKeepsOnlyTracksRatedAtLeastThatManyStars() = runTest {
        val tracks = MutableStateFlow(listOf(track("a", title = "Alpha"), track("b", title = "Bravo"), track("c", title = "Charlie")))
        var filter = LibraryFilter()
        // "c" has no vote: an unrated track counts as zero stars, not as "any rating".
        val votes = mapOf("a" to 5, "b" to 3)
        val index = LibraryIndex(tracks, { filter }, { votes }, dispatcher = StandardTestDispatcher(testScheduler))
        index.refresh()
        assertEquals(listOf("a", "b", "c"), index.state.value.tracks.map { it.id })
        filter = LibraryFilter(minimumRating = 3)
        index.refresh()
        assertEquals(listOf("a", "b"), index.state.value.tracks.map { it.id })
        filter = LibraryFilter(minimumRating = 4)
        index.refresh()
        assertEquals(listOf("a"), index.state.value.tracks.map { it.id })
        filter = LibraryFilter(minimumRating = 5)
        index.refresh()
        assertEquals(listOf("a"), index.state.value.tracks.map { it.id })
        filter = LibraryFilter(minimumRating = 1)
        index.refresh()
        assertEquals(listOf("a", "b"), index.state.value.tracks.map { it.id })
    }

    @Test
    fun aVoteChangeRebuildsTheLibraryOnlyWhileTheFilterUsesTheVotes() = runTest {
        val tracks = MutableStateFlow(listOf(track("a", title = "Alpha"), track("b", title = "Bravo")))
        var votes = mapOf("a" to 5)
        var filter = LibraryFilter(minimumRating = 3)
        val index = LibraryIndex(tracks, { filter }, { votes }, dispatcher = StandardTestDispatcher(testScheduler))
        index.refresh()
        val first = index.state.value
        assertEquals(listOf("a"), first.tracks.map { it.id })
        // The same votes in a new map are the same input: no new snapshot.
        votes = mapOf("a" to 5)
        index.refresh()
        assertSame(first, index.state.value)
        // A new vote joins the visible library, and lowering it takes the track out again.
        votes = mapOf("a" to 5, "b" to 4)
        index.refresh()
        assertNotSame(first, index.state.value)
        assertEquals(listOf("a", "b"), index.state.value.tracks.map { it.id })
        votes = mapOf("a" to 5, "b" to 2)
        index.refresh()
        assertEquals(listOf("a"), index.state.value.tracks.map { it.id })
        // Without a minimum rating the view does not read the votes: voting must not republish.
        filter = LibraryFilter()
        index.refresh()
        val unfiltered = index.state.value
        assertEquals(listOf("a", "b"), unfiltered.tracks.map { it.id })
        votes = mapOf("a" to 1, "b" to 2)
        index.refresh()
        assertSame(unfiltered, index.state.value)
    }

    @Test
    fun aVoteChangeDuringDispatchIsNotPublishedAsAnObsoleteLibrary() = runTest {
        val tracks = MutableStateFlow(listOf(track("a", title = "Alpha"), track("b", title = "Bravo")))
        var votes = mapOf("a" to 5)
        val index = LibraryIndex(tracks, { LibraryFilter(minimumRating = 3) }, { votes }, dispatcher = StandardTestDispatcher(testScheduler))
        val emitted = mutableListOf<MusicLibrary>()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { index.state.collect { emitted += it } }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { index.refresh() }
        votes = mapOf("a" to 5, "b" to 5)
        advanceUntilIdle()
        job.join()
        assertEquals(listOf(emptyList(), listOf("a", "b")), emitted.map { library -> library.tracks.map { it.id } })
        observer.cancel()
    }

    @Test
    fun cancellationKeepsThePreviousSnapshotAndDoesNotBlockLaterRefreshes() = runTest {
        val tracks = MutableStateFlow(listOf(track("one")))
        val index = LibraryIndex(tracks, { LibraryFilter() }, dispatcher = StandardTestDispatcher(testScheduler))
        val initial = index.state.value
        val job = launch(start = CoroutineStart.UNDISPATCHED) { index.refresh() }
        job.cancel()
        job.join()
        assertSame(initial, index.state.value)
        index.refresh()
        assertEquals(listOf("one"), index.state.value.tracks.map { it.id })
        tracks.value = emptyList()
        index.refresh()
        assertTrue(index.state.value.tracks.isEmpty())
    }
}
