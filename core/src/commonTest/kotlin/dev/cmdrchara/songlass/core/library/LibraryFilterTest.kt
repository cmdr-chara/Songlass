package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.*

class LibraryFilterTest {
    private val a = track("a").copy(folder = "Music/Voice", durationMs = 29_999)
    private val b = track("b").copy(folder = "Music/Voice/Child", durationMs = 30_000)
    private val c = track("c").copy(folder = "Music/Voiceover", durationMs = 0)

    @Test fun folderExclusionsRespectSegmentBoundaries() {
        assertEquals(listOf(c), LibraryFilter(setOf("Music/Voice")).apply(listOf(a, b, c)))
    }
    @Test fun durationIncludesBoundaryAndUnknown() {
        assertEquals(listOf(b, c), LibraryFilter(minimumDurationMs = 30_000).apply(listOf(a, b, c)))
    }
    @Test fun foldersIncludeAncestorsOnce() {
        assertEquals(listOf("Music", "Music/Voice", "Music/Voice/Child", "Music/Voiceover"), LibraryFilter.foldersIn(listOf(a, b, c)))
    }
    @Test fun settingsSurviveRestartAndRestoreExcludedTracks() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setMinimumDuration(30_000)
        settings.setFolderExcluded("/Music/Voice/", true)
        val restarted = AppSettings(store)
        assertEquals(LibraryFilter(setOf("Music/Voice"), 30_000), restarted.state.value.libraryFilter)
        restarted.setFolderExcluded("Music/Voice", false)
        assertEquals(listOf(b, c), restarted.state.value.libraryFilter.apply(listOf(a, b, c)))
    }
    @Test fun folderPickerKeepsHierarchyCountsAndUnavailableExclusions() {
        val roots = LibraryFilter.folderTree(listOf(a, b, c), setOf("Other/Missing"))
        val music = roots.first { it.path == "Music" }
        assertEquals(3, music.trackCount)
        assertEquals(listOf("Voice", "Voiceover"), music.children.map { it.name })
        assertEquals(2, music.children.first().trackCount)
        assertEquals("Child", music.children.first().children.single().name)
        assertEquals(0, roots.first { it.path == "Other" }.children.single().trackCount)
    }
    @Test fun malformedPreferencesFallBackAndRootCannotBeExcluded() {
        val store = InMemoryKeyValueStore()
        store.putString("library.minimum_duration_ms", "-1")
        store.putString("library.excluded_folders", "/")
        val settings = AppSettings(store)
        settings.setFolderExcluded("/", true)
        assertEquals(LibraryFilter(), settings.state.value.libraryFilter)
    }
}
