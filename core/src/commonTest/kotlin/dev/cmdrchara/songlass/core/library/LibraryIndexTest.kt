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
        val index = LibraryIndex(tracks, { LibraryFilter(minimumDurationMs = 1_000) }, StandardTestDispatcher(testScheduler))
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
            val index = LibraryIndex(tracks, { filter }, StandardTestDispatcher(testScheduler))
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
        val index = LibraryIndex(tracks, { LibraryFilter() }, StandardTestDispatcher(testScheduler))
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
    fun cancellationKeepsThePreviousSnapshotAndDoesNotBlockLaterRefreshes() = runTest {
        val tracks = MutableStateFlow(listOf(track("one")))
        val index = LibraryIndex(tracks, { LibraryFilter() }, StandardTestDispatcher(testScheduler))
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
