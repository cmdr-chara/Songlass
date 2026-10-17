package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.history.RecentContext
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeFeedTest {
    private val day = 86_400_000L
    private val now = 400 * day

    private val library = MusicLibrary(
        listOf(
            track("a1", artist = "A", album = "Album A", trackNumber = 1).copy(dateAddedMs = 10, folder = "Music/Chill"),
            track("a2", artist = "A", album = "Album A", trackNumber = 2).copy(dateAddedMs = 11, folder = "Music/Chill"),
            track("b1", artist = "B", album = "Album B", trackNumber = 1).copy(dateAddedMs = 30, folder = "Music/Gym"),
            track("b2", artist = "B", album = "Album B", trackNumber = 2).copy(dateAddedMs = 31, folder = "Music/Gym"),
            track("s1", artist = "C", album = "Single C").copy(dateAddedMs = 50, folder = "Music/Gym"),
        ) + (1..10).map { track("x$it", artist = "X$it", album = null).copy(folder = "Music/Chill") },
    )

    private fun history(plays: List<String>, contexts: List<PlayContext> = emptyList()) = HistorySnapshot(
        recentContexts = contexts.mapIndexed { i, c -> RecentContext(c, now - i) },
        playCounts = plays.groupingBy { it }.eachCount(),
        lastPlayedMs = plays.associateWith { now },
        recentPlays = plays,
    )

    @Test
    fun emptyHistoryShowsNoHistorySections() {
        val feed = HomeFeed.build(library, HistorySnapshot(), emptyList(), now)
        assertTrue(feed.recentlyPlayed.isEmpty())
        assertTrue(feed.mostPlayed.isEmpty())
        assertNull(feed.changeOfPace)
        assertEquals(library.playlists, feed.playlists)
    }

    @Test
    fun recentlyAddedIsNewestFirstWithAlbumsGrouped() {
        val feed = HomeFeed.build(library, HistorySnapshot(), emptyList(), now)
        val added = feed.recentlyAdded
        assertEquals("s1", (added[0] as HomeItem.TrackItem).track.id)
        assertEquals("Album B", (added[1] as HomeItem.AlbumItem).album.title)
        assertEquals("Album A", (added[2] as HomeItem.AlbumItem).album.title)
        assertEquals(3, added.size)
    }

    @Test
    fun recentlyPlayedResolvesContextsAndSkipsMissingOnes() {
        val contexts = listOf(
            PlayContext.Playlist("Music/Gym"),
            PlayContext.Album("Gone", "Nobody"),
            PlayContext.Album("Album A", "A"),
            PlayContext.Favorites,
        )
        val feed = HomeFeed.build(library, history(listOf("a1"), contexts), favoriteIds = listOf("b1"), nowMs = now)
        assertEquals(3, feed.recentlyPlayed.size)
        assertIs<HomeItem.PlaylistItem>(feed.recentlyPlayed[0])
        assertIs<HomeItem.AlbumItem>(feed.recentlyPlayed[1])
        assertIs<HomeItem.FavoritesItem>(feed.recentlyPlayed[2])
    }

    @Test
    fun mostPlayedIsByCount() {
        val feed = HomeFeed.build(library, history(listOf("b1", "a1", "b1", "b1", "a1", "s1")), emptyList(), now)
        assertEquals(listOf("b1", "a1", "s1"), feed.mostPlayed.map { it.id })
    }

    @Test
    fun dominantArtistLeadsToAChangeOfPaceWithoutThatArtist() {
        val plays = List(6) { if (it % 2 == 0) "a1" else "a2" }
        val pace = HomeFeed.build(library, history(plays), emptyList(), now).changeOfPace!!
        assertEquals(ChangeOfPaceReason.MostlyOneArtist("A"), pace.reason)
        assertTrue(pace.tracks.none { it.groupingArtist == "A" })
        assertEquals(HomeFeed.SHORT_LIST, pace.tracks.size)
    }

    @Test
    fun withoutADominantArtistNeverPlayedSongsAreTheReason() {
        val pace = HomeFeed.build(library, history(listOf("a1", "b1", "s1")), emptyList(), now).changeOfPace!!
        assertEquals(ChangeOfPaceReason.NeverPlayed(library.tracks.size - 3), pace.reason)
        assertTrue(pace.tracks.none { it.id in setOf("a1", "b1", "s1") })
    }

    @Test
    fun changeOfPaceIsStableWithinADayAndVariesAcrossDays() {
        val h = history(listOf("a1", "b1", "s1"))
        val morning = HomeFeed.build(library, h, emptyList(), now + 1000).changeOfPace!!.tracks
        val evening = HomeFeed.build(library, h, emptyList(), now + day - 1000).changeOfPace!!.tracks
        assertEquals(morning, evening)
        val days = (0 until 7).map { HomeFeed.build(library, h, emptyList(), now + it * day).changeOfPace!!.tracks }.toSet()
        assertTrue(days.size > 1)
    }

    @Test
    fun rediscoverSkipsRecentlyPlayedAlbums() {
        val played = history(listOf("a1"))
        repeat(10) { offset ->
            val pick = HomeFeed.build(library, played, emptyList(), now + offset * day).rediscover
            assertEquals("Album B", pick?.title)
        }
    }

    @Test
    fun favoritesAreNewestFirst() {
        val feed = HomeFeed.build(library, HistorySnapshot(), listOf("a1", "b2"), now)
        assertEquals(listOf("b2", "a1"), feed.favorites.map { it.id })
    }
}
