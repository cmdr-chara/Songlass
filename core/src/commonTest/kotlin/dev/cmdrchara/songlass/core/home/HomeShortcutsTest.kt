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

class HomeShortcutsTest {
    private val day = 86_400_000L
    private val now = 400 * day

    private val tracks = listOf(
        track("a1", artist = "A", album = "Album A", trackNumber = 1).copy(dateAddedMs = 10, folder = "Music/Chill"),
        track("a2", artist = "A", album = "Album A", trackNumber = 2).copy(dateAddedMs = 11, folder = "Music/Chill"),
        track("b1", artist = "B", album = "Album B", trackNumber = 1).copy(dateAddedMs = 30, folder = "Music/Gym"),
        track("b2", artist = "B", album = "Album B", trackNumber = 2).copy(dateAddedMs = 31, folder = "Music/Gym"),
        track("s1", artist = "C", album = "Single C").copy(dateAddedMs = 50, folder = "Music/Gym"),
    ) + (1..10).map { track("x$it", artist = "X$it", album = null).copy(folder = "Music/Chill") }
    private val library = MusicLibrary(tracks)

    private val albumA = PlayContext.Album("Album A", "A")
    private val gym = PlayContext.Playlist("Music/Gym")

    private fun history(contexts: List<PlayContext> = emptyList()) = HistorySnapshot(
        recentContexts = contexts.mapIndexed { i, c -> RecentContext(c, now - i) },
        playCounts = mapOf("a1" to 1),
        lastPlayedMs = mapOf("a1" to now),
        recentPlays = listOf("a1"),
    )

    private fun shortcutContexts(feed: HomeFeed) = feed.shortcuts.map { HomeShortcuts.contextOf(it) }

    @Test
    fun withoutChoicesTheFeedIsTheOriginalOne() {
        val recent = history(listOf(gym, albumA))
        val original = HomeFeed.build(library, recent, listOf("b1"), now)
        assertEquals(original, HomeFeed.build(library, recent, listOf("b1"), now, selectedShortcuts = emptyList()))
        assertEquals(
            listOf(gym, albumA, PlayContext.Favorites, PlayContext.Playlist("Music/Chill")),
            shortcutContexts(original),
        )
    }

    @Test
    fun withoutChoicesAShortcutAloneIsStillHidden() {
        val single = MusicLibrary(listOf(track("z", album = null).copy(folder = "Music")))
        assertTrue(HomeFeed.build(single, HistorySnapshot(), emptyList(), now).shortcuts.isEmpty())
    }

    @Test
    fun chosenShortcutsComeFirstAndAreNotRepeatedByTheAutomaticOnes() {
        val recent = history(listOf(gym, albumA, PlayContext.Favorites))
        val feed = HomeFeed.build(library, recent, listOf("b1"), now, selectedShortcuts = listOf(albumA, gym))
        assertEquals(
            listOf(albumA, gym, PlayContext.Favorites, PlayContext.Playlist("Music/Chill")),
            shortcutContexts(feed),
        )
        assertEquals(feed.shortcuts.size, feed.shortcuts.toSet().size)
    }

    @Test
    fun aChosenShortcutOverridesWhatRecentPlaybackWouldHaveRanked() {
        val recent = history(listOf(gym, albumA))
        val feed = HomeFeed.build(library, recent, emptyList(), now, selectedShortcuts = listOf(PlayContext.Artist("X3")))
        assertEquals(PlayContext.Artist("X3"), shortcutContexts(feed).first())
        assertEquals(listOf(gym, albumA), shortcutContexts(feed).drop(1).take(2))
    }

    @Test
    fun aSingleChosenShortcutIsShown() {
        val single = MusicLibrary(listOf(track("z", album = null).copy(folder = "Music")))
        val feed = HomeFeed.build(single, HistorySnapshot(), emptyList(), now, selectedShortcuts = listOf(PlayContext.Playlist("Music")))
        assertEquals(listOf<PlayContext?>(PlayContext.Playlist("Music")), shortcutContexts(feed))
    }

    @Test
    fun unavailableChoicesAreSkipped() {
        val feed = HomeFeed.build(
            library, HistorySnapshot(), emptyList(), now,
            selectedShortcuts = listOf(
                PlayContext.Album("Gone", "Nobody"),
                PlayContext.Artist("Nobody"),
                PlayContext.Playlist("Music/Missing"),
                PlayContext.Favorites,
                PlayContext.UserPlaylist("p1"),
                PlayContext.SmartPlaylist("s1"),
                gym,
            ),
        )
        // Favorites is empty and user/smart playlists are not resolved here: only the folder is chosen.
        assertEquals(gym, shortcutContexts(feed).first())
        assertEquals(setOf(gym, PlayContext.Playlist("Music/Chill")), shortcutContexts(feed).toSet())
    }

    @Test
    fun aChoiceIsAvailableAgainWhenTheLibraryComesBack() {
        val choices = listOf(PlayContext.Album("Album B", "B"))
        val filtered = MusicLibrary(tracks.filter { it.folder != "Music/Gym" })
        assertTrue(HomeFeed.build(filtered, HistorySnapshot(), emptyList(), now, selectedShortcuts = choices)
            .shortcuts.none { it is HomeItem.AlbumItem })
        val restored = HomeFeed.build(library, HistorySnapshot(), emptyList(), now, selectedShortcuts = choices)
        assertIs<HomeItem.AlbumItem>(restored.shortcuts.first())
    }

    @Test
    fun theGridNeverHasMoreThanSixEvenWithEightChoices() {
        val choices = (1..8).map { PlayContext.Artist("X$it") }
        val feed = HomeFeed.build(library, HistorySnapshot(), emptyList(), now, selectedShortcuts = choices)
        assertEquals(choices.take(6), shortcutContexts(feed))
    }

    @Test
    fun repeatedChoicesAppearOnce() {
        val feed = HomeFeed.build(library, HistorySnapshot(), emptyList(), now, selectedShortcuts = listOf(gym, gym, albumA, gym))
        assertEquals(listOf(gym, albumA), shortcutContexts(feed).take(2))
        assertEquals(1, shortcutContexts(feed).count { it == gym })
    }

    @Test
    fun resolvingAnItemsIdentityGivesTheItemBack() {
        val favorites = listOf(tracks.first())
        val items = listOf(
            HomeItem.AlbumItem(library.albums.first()),
            HomeItem.PlaylistItem(library.playlists.first()),
            HomeItem.ArtistItem(library.artists.first()),
            HomeItem.FavoritesItem(favorites),
        )
        items.forEach { item ->
            assertEquals(item, HomeShortcuts.resolve(library, HomeShortcuts.contextOf(item)!!, favorites))
        }
        assertNull(HomeShortcuts.contextOf(HomeItem.TrackItem(tracks.first())))
        assertNull(HomeShortcuts.resolve(library, PlayContext.Favorites, emptyList()))
    }

    @Test
    fun optionsListAlbumsArtistsAndFoldersAndCanBeSearched() {
        val options = HomeShortcuts.options(library)
        assertEquals(setOf(ShortcutKind.Album, ShortcutKind.Artist, ShortcutKind.Folder), options.map { it.kind }.toSet())
        assertEquals(listOf(gym), HomeShortcuts.filter(options, " GYM ").map { it.context })
        assertEquals(
            listOf("Album A", "Album B"),
            HomeShortcuts.filter(options, "album", ShortcutKind.Album).map { it.title },
        )
        // The artist of an album is searchable from the album row.
        assertTrue(HomeShortcuts.filter(options, "b", ShortcutKind.Album).any { it.title == "Album B" })
        assertEquals(options, HomeShortcuts.filter(options, "  "))
        assertTrue(HomeShortcuts.filter(options, "no such thing").isEmpty())
    }

    @Test
    fun describeKnowsFavoritesAndSkipsMissingMusic() {
        assertEquals(ShortcutKind.Favorites, HomeShortcuts.describe(library, PlayContext.Favorites)?.kind)
        assertEquals("Gym", HomeShortcuts.describe(library, gym)?.title)
        assertNull(HomeShortcuts.describe(library, PlayContext.Playlist("Music/Missing")))
    }
}
