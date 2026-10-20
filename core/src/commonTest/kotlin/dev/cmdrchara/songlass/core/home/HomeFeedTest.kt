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
    fun recentlyAddedPreservesLegacyOrderWithTiesCompilationsAndRepeatedIds() {
        val tracks = (0 until 160).map { i ->
            track("id-${i % 71}", title = "Song ${i % 13}", artist = "Guest ${i % 5}",
                album = if (i % 7 == 0) null else "Album ${i % 17}").copy(
                albumArtist = if (i % 7 == 0) null else "Album artist ${i % 3}",
                dateAddedMs = if (i % 11 == 0) null else (i % 9).toLong(),
                genre = "Genre $i",
            )
        }
        for (input in listOf(emptyList(), tracks, tracks.reversed(), tracks + tracks.take(10))) {
            val indexed = MusicLibrary(input)
            val reference = indexed.tracks.filter { it.dateAddedMs != null }
                .sortedByDescending { it.dateAddedMs }
                .map { song -> indexed.albumOf(song)?.let(HomeItem::AlbumItem) ?: HomeItem.TrackItem(song) }
                .distinct().take(HomeFeed.ROW_SIZE)
            assertEquals(reference, HomeFeed.build(indexed, HistorySnapshot(), emptyList(), now).recentlyAdded)
        }
    }

    @Test
    fun recentlyAddedReachesTwelveCardsPastDenseAlbumsWithoutCollapsingDifferentArtists() {
        val tracks = (0 until 400).map { i ->
            track("dense-$i", artist = "A", album = "Shared title").copy(dateAddedMs = 1_000L)
        } + (0 until 15).flatMap { i ->
            listOf(track("pair-$i-a", artist = "Artist $i", album = "Shared title"),
                track("pair-$i-b", artist = "Artist $i", album = "Shared title"))
                .map { it.copy(dateAddedMs = 100L - i) }
        }
        val result = HomeFeed.build(MusicLibrary(tracks), HistorySnapshot(), emptyList(), now).recentlyAdded
        assertEquals(HomeFeed.ROW_SIZE, result.size)
        assertEquals(listOf("A") + (0 until 11).map { "Artist $it" }, result.map { (it as HomeItem.AlbumItem).album.artist })
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
        assertEquals(HomeFeed.SHELF_SIZE, pace.tracks.size)
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
            assertEquals("Album B", pick?.album?.title)
            assertNull(pick?.idleDays)
        }
    }

    @Test
    fun rediscoverTellsHowLongTheAlbumWasIdle() {
        val old = HistorySnapshot(lastPlayedMs = mapOf("a1" to now - 45 * day, "b1" to now - 90 * day, "b2" to now - 40 * day))
        val pick = HomeFeed.build(library, old, emptyList(), now).rediscover!!
        val expected = if (pick.album.title == "Album A") 45 else 40
        assertEquals(expected, pick.idleDays)
    }

    @Test
    fun shortcutsStartWithRecentContextsThenFavoritesAndPlaylists() {
        val contexts = listOf(PlayContext.Playlist("Music/Gym"), PlayContext.Album("Album A", "A"))
        val feed = HomeFeed.build(library, history(listOf("a1"), contexts), favoriteIds = listOf("b1"), nowMs = now)
        assertIs<HomeItem.PlaylistItem>(feed.shortcuts[0])
        assertIs<HomeItem.AlbumItem>(feed.shortcuts[1])
        assertIs<HomeItem.FavoritesItem>(feed.shortcuts[2])
        // The Gym playlist is already there; only Chill is added.
        assertEquals(listOf("Music/Chill"), feed.shortcuts.drop(3).map { (it as HomeItem.PlaylistItem).playlist.id })
    }

    @Test
    fun shortcutsNeedAtLeastTwoItems() {
        val single = MusicLibrary(listOf(track("z", album = null).copy(folder = "Music")))
        assertTrue(HomeFeed.build(single, HistorySnapshot(), emptyList(), now).shortcuts.isEmpty())
    }

    @Test
    fun favoritesAreNewestFirst() {
        val feed = HomeFeed.build(library, HistorySnapshot(), listOf("a1", "b2"), now)
        assertEquals(listOf("b2", "a1"), feed.favorites.map { it.id })
    }
}
