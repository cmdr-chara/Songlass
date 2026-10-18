package dev.cmdrchara.songlass.core.search

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchHistoryTest {
    private val store = InMemoryKeyValueStore()
    private val library = MusicLibrary(listOf(
        track("one", artist = "Artist", album = "Album").copy(folder = "Music/Folder"),
        track("two", artist = "Artist", album = "Album").copy(folder = "Music/Folder"),
    ))

    @Test fun reopeningMovesToFrontWithoutDuplicatesAndSurvivesRestart() {
        val history = SearchHistory(store)
        history.record(SearchTarget.Song("one"))
        history.record(SearchTarget.ArtistName("Artist"))
        history.record(SearchTarget.Song("one"))
        assertEquals(listOf(SearchTarget.Song("one"), SearchTarget.ArtistName("Artist")), SearchHistory(store).targets.value)
    }

    @Test fun capsHistoryRemovesOneAndClearsPersistently() {
        val history = SearchHistory(store)
        repeat(SearchHistory.MAX + 3) { history.record(SearchTarget.Song("$it")) }
        assertEquals(SearchHistory.MAX, SearchHistory(store).targets.value.size)
        history.remove(SearchTarget.Song("22"))
        assertEquals(SearchTarget.Song("21"), SearchHistory(store).targets.value.first())
        history.clear()
        assertTrue(SearchHistory(store).targets.value.isEmpty())
    }

    @Test fun resolvesEveryTypeAgainstCurrentLibraryAndPreservesTemporarilyHiddenEntries() {
        val history = SearchHistory(store)
        history.record(SearchTarget.Song("one"))
        history.record(SearchTarget.AlbumId("Album", "Artist"))
        history.record(SearchTarget.ArtistName("Artist"))
        history.record(SearchTarget.FolderId("Music/Folder"))
        val items = history.resolve(library)
        assertIs<SearchItem.FolderItem>(items[0])
        assertIs<SearchItem.ArtistItem>(items[1])
        assertIs<SearchItem.AlbumItem>(items[2])
        assertIs<SearchItem.Song>(items[3])
        assertTrue(history.resolve(MusicLibrary(emptyList())).isEmpty())
        assertEquals(items, SearchHistory(store).resolve(library))
        val updated = MusicLibrary(library.tracks.map { it.copy(title = "Renamed") })
        assertEquals("Renamed", (history.resolve(updated).last() as SearchItem.Song).track.title)
    }

    @Test fun codecPreservesUnicodeSeparatorsNullAndEmptyTags() {
        val targets = listOf(SearchTarget.AlbumId("雪:🎵\u001F\u001D", ""), SearchTarget.ArtistName(null),
            SearchTarget.ArtistName(""), SearchTarget.AlbumId(null, null), SearchTarget.Song("0:-1:\n"))
        targets.reversed().forEach { SearchHistory(store).record(it) }
        assertEquals(targets, SearchHistory(store).targets.value)
    }

    @Test fun corruptTailDoesNotDiscardValidPrefixOrCrash() {
        SearchHistory(store).record(SearchTarget.Song("one"))
        val key = "search.opened.v1"
        store.putString(key, store.getString(key) + "1:a999:broken")
        assertEquals(listOf(SearchTarget.Song("one")), SearchHistory(store).targets.value)
        store.putString(key, "-2:")
        assertTrue(SearchHistory(store).targets.value.isEmpty())
    }

    @Test fun suggestionsAreBoundedStableLocalAndPreferRecentAdditions() {
        val tracks = (1..10).flatMap { n -> listOf("a", "b").map {
            track("$n$it", artist = "Artist$n", album = "Album$n").copy(dateAddedMs = n.toLong())
        } }
        val suggestions = SearchSuggestions.from(MusicLibrary(tracks))
        assertEquals(6, suggestions.artists.size)
        assertEquals(6, suggestions.albums.size)
        assertEquals("Artist10", suggestions.artists.first().name)
        assertEquals("Album10", suggestions.albums.first().title)
        assertEquals(suggestions, SearchSuggestions.from(MusicLibrary(tracks.reversed())))
        assertTrue(suggestions.songs.isEmpty())
        val loneSongs = MusicLibrary(listOf(track("single", artist = null, album = null)))
        assertEquals(loneSongs.tracks, SearchSuggestions.from(loneSongs).songs)
        assertTrue(SearchSuggestions.from(MusicLibrary(emptyList())).songs.isEmpty())
    }
}
