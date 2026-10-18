package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FoldersTest {
    private fun inFolder(id: String, folder: String, album: String? = "Album $id") =
        track(id, album = album).copy(folder = folder)

    private val library = MusicLibrary(
        listOf(
            inFolder("c1", "Music/PC/Chill"),
            inFolder("s1", "Music/PC/Soundtracks"),
            inFolder("j1", "Music/PC/Soundtracks/JoJo/Part 1"),
            inFolder("j2", "Music/PC/Soundtracks/JoJo/Part 2"),
            inFolder("j3", "Music/PC/Soundtracks/JoJo/Part 2"),
        ),
    )

    @Test
    fun topLevelFoldersAreBelowTheCommonRoot() {
        assertEquals(listOf("Chill", "Soundtracks"), library.folders.map { it.name })
    }

    @Test
    fun intermediateFoldersWithoutOwnFilesAreKept() {
        val soundtracks = library.folders.first { it.name == "Soundtracks" }
        assertEquals(listOf("s1"), soundtracks.tracks.map { it.id })
        val jojo = soundtracks.children.single()
        assertEquals("JoJo", jojo.name)
        assertEquals(emptyList(), jojo.tracks)
        assertEquals(listOf("Part 1", "Part 2"), jojo.children.map { it.name })
    }

    @Test
    fun allTracksIncludeEverythingBelow() {
        val soundtracks = library.folder("Music/PC/Soundtracks")!!
        assertEquals(listOf("s1", "j1", "j2", "j3"), soundtracks.allTracks.map { it.id })
    }

    @Test
    fun foldersAreFoundByPath() {
        assertEquals("Part 2", library.folder("Music/PC/Soundtracks/JoJo/Part 2")?.name)
        assertEquals("JoJo", library.folder("Music/PC/Soundtracks/JoJo")?.name)
        assertNull(library.folder("Music/PC/Nope"))
    }

    @Test
    fun coverTracksComeFromDifferentAlbums() {
        val shared = MusicLibrary(
            listOf(
                inFolder("a", "Music/Mix", album = "One"),
                inFolder("b", "Music/Mix", album = "One"),
                inFolder("c", "Music/Mix", album = "Two"),
            ),
        )
        assertEquals(listOf("a", "c"), shared.folder("Music/Mix")!!.coverTracks().map { it.id })
    }
}
