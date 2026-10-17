package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicLibraryTest {

    private val library = MusicLibrary(
        listOf(
            track("a2", title = "Second", artist = "Alpha", album = "First Album", trackNumber = 2),
            track("a1", title = "first", artist = "Alpha", album = "First Album", trackNumber = 1),
            track("b1", title = "Bonus", artist = "Alpha", album = "First Album", trackNumber = 1, discNumber = 2),
            track("c1", title = "Guest Song", artist = "Guest", album = "Hits", albumArtist = "Various Artists"),
            track("c2", title = "Another", artist = "Beta", album = "Hits", albumArtist = "Various Artists"),
            track("d1", title = "Same Name", artist = "Beta", album = "First Album"),
            track("u1", title = "Untagged", artist = null, album = null),
        ),
    )

    @Test
    fun tracksAreSortedByTitleIgnoringCase() {
        assertEquals(
            listOf("Another", "Bonus", "first", "Guest Song", "Same Name", "Second", "Untagged"),
            library.tracks.map { it.title },
        )
    }

    @Test
    fun albumsWithSameTitleByDifferentArtistsStaySeparate() {
        val firstAlbums = library.albums.filter { it.title == "First Album" }
        assertEquals(listOf("Alpha", "Beta"), firstAlbums.map { it.artist })
    }

    @Test
    fun albumTracksAreOrderedByDiscThenTrackNumber() {
        val album = library.albums.first { it.title == "First Album" && it.artist == "Alpha" }
        assertEquals(listOf("a1", "a2", "b1"), album.tracks.map { it.id })
        assertEquals(3 * 180_000L, album.durationMs)
    }

    @Test
    fun compilationIsGroupedUnderAlbumArtist() {
        val hits = library.albums.single { it.title == "Hits" }
        assertEquals("Various Artists", hits.artist)
        assertEquals(2, hits.tracks.size)
        assertTrue(library.artists.none { it.name == "Guest" })
    }

    @Test
    fun untaggedValuesSortLast() {
        assertEquals(null, library.albums.last().title)
        assertEquals(null, library.artists.last().name)
    }

    @Test
    fun artistsListTheirAlbumsAndTracks() {
        val alpha = library.artists.single { it.name == "Alpha" }
        assertEquals(listOf("First Album"), alpha.albums.map { it.title })
        assertEquals(3, alpha.tracks.size)
    }

    @Test
    fun searchMatchesTitlesArtistsAndAlbumsIgnoringCase() {
        val results = library.search("  ALPHA ")
        assertEquals(setOf("a1", "a2", "b1"), results.tracks.map { it.id }.toSet())
        assertEquals(listOf("Alpha"), results.artists.map { it.name })
        assertEquals(listOf("First Album"), results.albums.map { it.title })

        assertEquals(listOf("Hits"), library.search("hit").albums.map { it.title })
        assertEquals(listOf("c1"), library.search("guest").tracks.map { it.id })
    }

    @Test
    fun blankSearchMatchesNothing() {
        assertTrue(library.search("   ").isEmpty)
    }
}
