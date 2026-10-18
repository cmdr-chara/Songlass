package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderSortTest {
    private val folders = MusicLibrary(
        listOf(
            track("a", album = null).copy(folder = "Music/Beta", dateAddedMs = 10),
            track("b", album = null).copy(folder = "Music/Alpha", dateAddedMs = 30),
            track("c", album = null).copy(folder = "Music/Gamma/Sub", dateAddedMs = 20),
        ),
    ).folders

    @Test
    fun byName() = assertEquals(listOf("Alpha", "Beta", "Gamma"), FolderSort.Name.sort(folders, emptyMap()).map { it.name })

    @Test
    fun byRecentlyAddedLooksInsideSubFolders() =
        assertEquals(listOf("Alpha", "Gamma", "Beta"), FolderSort.RecentlyAdded.sort(folders, emptyMap()).map { it.name })

    @Test
    fun byMostPlayedThenName() =
        assertEquals(listOf("Gamma", "Alpha", "Beta"), FolderSort.MostPlayed.sort(folders, mapOf("c" to 3)).map { it.name })
}
