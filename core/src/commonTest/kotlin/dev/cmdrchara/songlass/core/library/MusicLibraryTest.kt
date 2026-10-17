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
            track("d2", title = "Other Name", artist = "Beta", album = "First Album"),
            track("u1", title = "Untagged", artist = null, album = null),
            track("u2", title = "Untagged Too", artist = null, album = null),
            track("s1", title = "Lone Single", artist = "Gamma", album = "Big Album"),
        ),
    )

    @Test
    fun tracksAreSortedByTitleIgnoringCase() {
        assertEquals(
            listOf("Another", "Bonus", "first", "Guest Song", "Lone Single", "Other Name", "Same Name", "Second", "Untagged", "Untagged Too"),
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
    fun untaggedTracksAreNotAnAlbumAndUnknownArtistSortsLast() {
        assertTrue(library.albums.none { it.title == null })
        assertEquals(null, library.artists.last().name)
        assertEquals(listOf("u1", "u2"), library.artists.last().tracks.map { it.id })
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

    @Test
    fun albumAndArtistOfTrackFollowGrouping() {
        val guest = library.tracks.single { it.id == "c1" }
        assertEquals("Hits", library.albumOf(guest)?.title)
        assertEquals("Various Artists", library.artistOf(guest)?.name)
        val sameName = library.tracks.single { it.id == "d1" }
        assertEquals("Beta", library.albumOf(sameName)?.artist)
    }

    @Test
    fun loneSongsAreNotAlbums() {
        val single = library.tracks.single { it.id == "s1" }
        assertTrue(library.albums.none { it.title == "Big Album" })
        assertEquals(null, library.albumOf(single))
        val gamma = library.artists.single { it.name == "Gamma" }
        assertEquals(emptyList(), gamma.albums)
        assertEquals(listOf("s1"), gamma.tracks.map { it.id })
        assertTrue(library.search("big album").albums.isEmpty())
        assertEquals(listOf("s1"), library.search("big album").tracks.map { it.id })
    }
}
