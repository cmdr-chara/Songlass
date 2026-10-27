package dev.cmdrchara.songlass.core.browse

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.FolderSort
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.playlists.UserPlaylist
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryNavigationTest {

    @Test
    fun albumNeighboursFollowTheLibraryAlphabeticalOrder() {
        val library = threeAlbums()
        val beta = PlayContext.Album("Beta", "B")
        val next = neighborCategory(beta, library, forward = true)!!
        assertEquals(PlayContext.Album("Gamma", "G"), next.context)
        assertEquals("Gamma", next.title)
        assertEquals(listOf("g1", "g2"), next.tracks.map { it.id })
        assertEquals(PlayContext.Album("Alpha", "A"), neighborCategory(beta, library, forward = false)!!.context)
    }

    @Test
    fun albumsWithTheSameTitleAreOrderedByArtist() {
        val library = MusicLibrary(
            listOf(
                track("a1", title = "One", artist = "A", album = "Same"),
                track("a2", title = "Two", artist = "A", album = "Same"),
                track("b1", title = "One", artist = "B", album = "Same"),
                track("b2", title = "Two", artist = "B", album = "Same"),
            ),
        )
        assertEquals(
            PlayContext.Album("Same", "B"),
            neighborCategory(PlayContext.Album("Same", "A"), library, forward = true)!!.context,
        )
    }

    @Test
    fun albumBoundariesDoNotWrapAndASingleAlbumHasNoNeighbour() {
        val library = threeAlbums()
        assertNull(neighborCategory(PlayContext.Album("Alpha", "A"), library, forward = false))
        assertNull(neighborCategory(PlayContext.Album("Gamma", "G"), library, forward = true))
        val single = MusicLibrary(
            listOf(track("x1", album = "Only", artist = "X"), track("x2", album = "Only", artist = "X")),
        )
        assertNull(neighborCategory(PlayContext.Album("Only", "X"), single, forward = true))
        assertNull(neighborCategory(PlayContext.Album("Only", "X"), single, forward = false))
    }

    @Test
    fun artistNeighboursUseTheLibraryArtistOrderAndKeepUnknownArtistsLast() {
        val library = MusicLibrary(
            listOf(
                track("a1", title = "One", artist = "Alpha", album = "One"),
                track("a2", title = "Two", artist = "Alpha", album = "One"),
                track("b1", title = "One", artist = "Beta", album = "Two"),
                track("b2", title = "Two", artist = "Beta", album = "Two"),
                track("u1", title = "Untitled", artist = null, album = null),
            ),
        )
        val alpha = PlayContext.Artist("Alpha")
        assertEquals(PlayContext.Artist("Beta"), neighborCategory(alpha, library, forward = true)!!.context)
        assertEquals(PlayContext.Artist(null), neighborCategory(PlayContext.Artist("Beta"), library, forward = true)!!.context)
        assertNull(neighborCategory(PlayContext.Artist(null), library, forward = true))
        assertNull(neighborCategory(alpha, library, forward = false))
    }

    @Test
    fun foldersAreWalkedInPreOrderSoANestedFolderFollowsItsParent() {
        val library = folderLibrary()
        val chill = PlayContext.Playlist("Music/Chill")
        val gym = PlayContext.Playlist("Music/Gym")
        val cardio = PlayContext.Playlist("Music/Gym/Cardio")
        assertEquals(gym, neighborCategory(chill, library, forward = true)!!.context)
        assertEquals(cardio, neighborCategory(gym, library, forward = true)!!.context)
        assertEquals(gym, neighborCategory(cardio, library, forward = false)!!.context)
        assertEquals("Gym", neighborCategory(chill, library, forward = true)!!.title)
        // A folder plays everything below it, as its page does.
        assertEquals(listOf("g1", "g2", "k1"), neighborCategory(chill, library, forward = true)!!.tracks.map { it.id }.sorted())
        assertNull(neighborCategory(chill, library, forward = false))
        assertNull(neighborCategory(cardio, library, forward = true))
    }

    @Test
    fun theChosenFolderSortDecidesTheNeighbourOrder() {
        val library = folderLibrary()
        val chill = PlayContext.Playlist("Music/Chill")
        val gym = PlayContext.Playlist("Music/Gym")
        val cardio = PlayContext.Playlist("Music/Gym/Cardio")
        // Most played first: the Gym folder's songs were played more often, so Gym and its
        // child move before Chill and Cardio becomes Chill's previous neighbour.
        val counts = mapOf("g1" to 3, "g2" to 2, "k1" to 1)
        assertEquals(cardio, neighborCategory(gym, library, folderSort = FolderSort.MostPlayed, playCounts = counts, forward = true)!!.context)
        assertEquals(chill, neighborCategory(cardio, library, folderSort = FolderSort.MostPlayed, playCounts = counts, forward = true)!!.context)
        assertNull(neighborCategory(cardio, library, forward = true))
        // Recently added first: no dates here, so the library name order stays.
        assertEquals(gym, neighborCategory(chill, library, folderSort = FolderSort.RecentlyAdded, forward = true)!!.context)
    }

    @Test
    fun userPlaylistNeighboursFollowTheShelfOrderAndSkipEmptyPlaylists() {
        val library = MusicLibrary(listOf(track("a1", album = "One"), track("a2", album = "One")))
        val newest = UserPlaylist("p3", "Chill", listOf("a2"), createdMs = 3)
        val empty = UserPlaylist("p2", "Empty", emptyList(), createdMs = 2)
        val oldest = UserPlaylist("p1", "Road trip", listOf("a1"), createdMs = 1)
        val playlists = listOf(newest, empty, oldest)
        assertEquals(PlayContext.UserPlaylist("p1"), neighborCategory(PlayContext.UserPlaylist("p3"), library, playlists, forward = true)!!.context)
        assertEquals(PlayContext.UserPlaylist("p3"), neighborCategory(PlayContext.UserPlaylist("p1"), library, playlists, forward = false)!!.context)
        assertNull(neighborCategory(PlayContext.UserPlaylist("p1"), library, playlists, forward = true))
        assertNull(neighborCategory(PlayContext.UserPlaylist("p3"), library, playlists, forward = false))
        // The empty playlist has no place in the playable order, so it cannot be a source either.
        assertNull(neighborCategory(PlayContext.UserPlaylist("p2"), library, playlists, forward = true))
        assertEquals("Chill", neighborCategory(PlayContext.UserPlaylist("p1"), library, playlists, forward = false)!!.title)
    }

    @Test
    fun collectionsOutsideTheLibraryOrderAndUnknownSourcesHaveNoNeighbour() {
        val library = threeAlbums()
        assertNull(neighborCategory(null, library, forward = true))
        assertNull(neighborCategory(PlayContext.Favorites, library, forward = true))
        assertNull(neighborCategory(PlayContext.SmartPlaylist("s1"), library, forward = true))
        assertNull(neighborCategory(PlayContext.Facet(dev.cmdrchara.songlass.core.library.LibraryFacetKind.Year, "2020"), library, forward = true))
        // The playing collection may have been removed or renamed since it was started.
        assertNull(neighborCategory(PlayContext.Album("Gone", "A"), library, forward = true))
        assertNull(neighborCategory(PlayContext.Playlist("Music/Gone"), folderLibrary(), forward = true))
    }

    @Test
    fun startIndexResumesTheRememberedTrackOrFallsBackToTheFirstOne() {
        val target = CategoryTarget(
            PlayContext.Album("A", null), "A",
            listOf(track("a1", album = "A"), track("a2", album = "A"), track("a3", album = "A")),
        )
        assertEquals(2, target.startIndex("a3"))
        assertEquals(0, target.startIndex("gone"))
        assertEquals(0, target.startIndex(null))
    }

    private fun threeAlbums() = MusicLibrary(
        listOf(
            track("a1", title = "One", artist = "A", album = "Alpha"),
            track("a2", title = "Two", artist = "A", album = "Alpha"),
            track("b1", title = "One", artist = "B", album = "Beta"),
            track("b2", title = "Two", artist = "B", album = "Beta"),
            track("g1", title = "One", artist = "G", album = "Gamma"),
            track("g2", title = "Two", artist = "G", album = "Gamma"),
        ),
    )

    private fun folderLibrary() = MusicLibrary(
        listOf(
            track("c1", title = "Wave", artist = "A", album = "Ambient", folder = "Music/Chill"),
            track("g1", title = "Run", artist = "B", album = "Gym", folder = "Music/Gym"),
            track("g2", title = "Lift", artist = "B", album = "Gym", folder = "Music/Gym"),
            track("k1", title = "Sprint", artist = "B", album = "Cardio", folder = "Music/Gym/Cardio"),
        ),
    )
}
