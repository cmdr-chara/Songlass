package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.track
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MusicLibrarySearchTest {
    @Test
    fun indexedSearchPreservesEveryResultAndItsOrder() {
        val random = Random(27)
        val tags = listOf(null, "", "Alpha", "Guest; Beta", "Mixed feat. Guest", "İSTANBUL", "ΟΣ", "Björk", "東京", "Straße")
        val tracks = List(180) { i ->
            track(
                id = "duplicate-${i % 70}",
                title = "${tags[random.nextInt(tags.size)] ?: "Unknown"} ${i % 4}",
                artist = tags[random.nextInt(tags.size)],
                album = tags[random.nextInt(tags.size)],
                albumArtist = tags[random.nextInt(tags.size)],
                folder = "Music/${tags[random.nextInt(tags.size)] ?: "Other"}",
            ).copy(artists = if (i % 3 == 0) listOf("Separate; Credit", "Another\u0000Guest") else emptyList())
        }.shuffled(random)
        val library = MusicLibrary(tracks)
        val queries = listOf("", " ", "  ALPHA ", "guest", "feat.", "credit", "another", "null", "İ", "i", "i̇", "ΟΣ", "ος", "σ", "ö", "東京", "ß", "ss", "no-such-song") +
            tracks.map { it.title }.distinct().flatMap { listOf(it, it.take(3), it.takeLast(2)) }
        for (query in queries) {
            assertEquals(legacySearch(library, query), library.search(query), "query=$query")
        }
        val query = "0"
        library.search(query).tracks.zip(legacySearch(library, query).tracks).forEach { (actual, original) ->
            assertSame(original, actual)
        }
    }

    @Test
    fun fieldsNeverMatchAcrossBoundariesAndNewSnapshotsUseUpdatedTags() {
        val original = track("1", title = "abcd", artist = "efgh", album = null, albumArtist = "Hidden credit")
        val library = MusicLibrary(listOf(original))
        assertTrue(library.search("de").tracks.isEmpty())
        assertTrue(library.search("d e").tracks.isEmpty())
        assertTrue(library.search("null").tracks.isEmpty())
        assertTrue(library.search("hidden").tracks.isEmpty())
        val changed = MusicLibrary(listOf(original.copy(title = "Renamed", artist = "New Artist")))
        assertTrue(changed.search("abcd").tracks.isEmpty())
        assertEquals(listOf("1"), changed.search("new artist").tracks.map { it.id })
        assertEquals(listOf(original), library.search("abcd").tracks)
    }

    private fun legacySearch(library: MusicLibrary, query: String): SearchResults {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return SearchResults()
        fun String?.matches() = this != null && lowercase().contains(needle)
        return SearchResults(
            tracks = library.tracks.filter { it.title.matches() || it.artist.matches() || it.artistNames.any { name -> name.matches() } || it.album.matches() },
            albums = library.albums.filter { it.title.matches() || it.artist.matches() },
            artists = library.artists.filter { it.name.matches() },
            playlists = library.playlists.filter { it.name.matches() },
        )
    }
}
