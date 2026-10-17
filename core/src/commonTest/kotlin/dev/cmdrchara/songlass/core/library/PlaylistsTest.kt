package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistsTest {

    private val library = MusicLibrary(
        listOf(
            track("c2", title = "Zephyr", album = "X", folder = "Music/Chill/"),
            track("c1", title = "Aurora", album = "Y", folder = "Music/Chill"),
            track("g1", title = "Lift", folder = "Music/Gym"),
            track("o2", title = "Second", album = "OST", trackNumber = 2, folder = "Music/Soundtracks/Show/Season 1"),
            track("o1", title = "First", album = "OST", trackNumber = 1, folder = "Music/Soundtracks/Show/Season 1"),
            track("n1", title = "Nowhere", folder = null),
        ),
    )

    @Test
    fun oneSortedPlaylistPerFolderWithoutCommonRoot() {
        assertEquals(listOf("Chill", "Gym", "Season 1"), library.playlists.map { it.name })
        assertEquals(listOf("Soundtracks", "Show"), library.playlists.last().location)
        assertTrue(library.playlists.first().location.isEmpty())
    }

    @Test
    fun trailingSlashesDoNotSplitFolders() {
        assertEquals(listOf("c1", "c2"), library.playlists.first().tracks.map { it.id })
    }

    @Test
    fun singleAlbumFolderKeepsTrackOrderMixedFolderSortsByTitle() {
        assertEquals(listOf("o1", "o2"), library.playlists.single { it.name == "Season 1" }.tracks.map { it.id })
        assertEquals(listOf("Aurora", "Zephyr"), library.playlists.single { it.name == "Chill" }.tracks.map { it.title })
    }

    @Test
    fun aSingleFolderKeepsItsName() {
        val single = MusicLibrary(listOf(track("a", folder = "Music/Chill")))
        assertEquals(listOf("Chill"), single.playlists.map { it.name })
        assertTrue(single.playlists.single().location.isEmpty())
    }

    @Test
    fun tracksWithoutFolderAreInNoPlaylist() {
        assertTrue(library.playlists.none { p -> p.tracks.any { it.id == "n1" } })
    }

    @Test
    fun searchFindsPlaylistsByName() {
        assertEquals(listOf("Gym"), library.search("gym").playlists.map { it.name })
    }
}
