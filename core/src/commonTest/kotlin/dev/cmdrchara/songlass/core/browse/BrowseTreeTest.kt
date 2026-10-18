package dev.cmdrchara.songlass.core.browse

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.playlists.UserPlaylist
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseTreeTest {
    private val library = MusicLibrary(
        listOf(
            track("a1", title = "Alpha", artist = "A", album = "One", trackNumber = 1).copy(folder = "Music/Chill"),
            track("a2", title = "Beta", artist = "A", album = "One", trackNumber = 2).copy(folder = "Music/Chill"),
            track("b1", title = "Gamma", artist = "B", album = null).copy(folder = "Music/Gym/Cardio"),
        ),
    )
    private val labels = BrowseLabels("Home", "Playlists", "Albums", "Artists", "Favorites", "Shuffle all", "Unknown", "Unknown album") { "$it songs" }

    private fun tree(favorites: List<String> = emptyList(), recent: List<PlayContext> = emptyList()) = BrowseTree(
        library,
        favorites = library.tracks.filter { it.id in favorites },
        userPlaylists = listOf(UserPlaylist("p1", "Mine", listOf("b1", "a2"), 0)),
        recent = recent,
        labels = labels,
    )

    @Test
    fun rootHasFourTabs() {
        assertEquals(listOf("home", "playlists", "albums", "artists"), tree().children(BrowseTree.ROOT)!!.map { it.id })
    }

    @Test
    fun homeOffersShuffleFavoritesAndRecent() {
        val home = tree(favorites = listOf("b1"), recent = listOf(PlayContext.Artist("B"), PlayContext.Favorites)).children(BrowseTree.HOME)!!
        assertEquals(listOf("Shuffle all", "Favorites", "B"), home.map { it.title })
        assertTrue(tree().resolvePlay(home.first().id)!!.shuffle)
    }

    @Test
    fun songInAnAlbumPlaysTheAlbumFromThere() {
        val tree = tree()
        val album = tree.children(BrowseTree.ALBUMS)!!.single()
        assertEquals("One", album.title)
        val songs = tree.children(album.id)!!
        assertEquals(listOf("Alpha", "Beta"), songs.map { it.title })
        val request = tree.resolvePlay(songs[1].id)!!
        assertEquals(listOf("a1", "a2"), request.tracks.map { it.id })
        assertEquals(1, request.startIndex)
        // A collection itself is playable too.
        assertEquals(0, tree.resolvePlay(album.id)!!.startIndex)
    }

    @Test
    fun foldersShowSubfoldersThenSongsAndPlayEverythingInside() {
        val tree = tree()
        val playlists = tree.children(BrowseTree.PLAYLISTS)!!
        assertEquals(listOf("Mine", "Chill", "Gym"), playlists.map { it.title })
        val gym = playlists.last()
        val gymChildren = tree.children(gym.id)!!
        assertEquals(listOf("Cardio"), gymChildren.map { it.title })
        val cardioSong = tree.children(gymChildren.single().id)!!.single()
        assertEquals("b1", tree.resolvePlay(cardioSong.id)!!.tracks.single().id)
        // A user playlist keeps its own order.
        assertEquals(listOf("Gamma", "Beta"), tree.children(playlists.first().id)!!.map { it.title })
    }

    @Test
    fun spokenQueriesPlayArtistsAlbumsOrSongs() {
        val tree = tree()
        assertEquals(listOf("b1"), tree.resolveQuery("b")!!.tracks.map { it.id })
        assertEquals(listOf("a1", "a2"), tree.resolveQuery("one")!!.tracks.map { it.id })
        assertEquals(1, tree.resolveQuery("beta")!!.startIndex)
        assertTrue(tree.resolveQuery("")!!.shuffle)
        assertNull(tree.resolveQuery("zzz"))
    }

    @Test
    fun searchFindsSongsWithinTheirAlbumAndRejectsUnknownIds() {
        val tree = tree()
        val results = tree.search("beta")
        assertEquals(listOf("Beta"), results.map { it.title })
        assertEquals(1, tree.resolvePlay(results.single().id)!!.startIndex)
        assertNull(tree.resolvePlay("t:nonsense"))
        assertNull(tree.children("c:nonsense"))
    }
}
