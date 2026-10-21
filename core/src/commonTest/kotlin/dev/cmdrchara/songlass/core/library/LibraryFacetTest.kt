package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.browse.BrowseLabels
import dev.cmdrchara.songlass.core.browse.BrowseTree
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.home.HomeItem
import dev.cmdrchara.songlass.core.home.HomeShortcuts
import dev.cmdrchara.songlass.core.model.ReleaseDate
import dev.cmdrchara.songlass.core.search.SearchHistory
import dev.cmdrchara.songlass.core.search.SearchItem
import dev.cmdrchara.songlass.core.search.SearchTarget
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryFacetTest {
    private val tracks = listOf(
        track("a", title = "Alpha", artist = "Performer", albumArtist = "Ensemble").copy(releaseDate = ReleaseDate(2024), composers = listOf("Writer; Guest", "writer")),
        track("b", title = "Beta", artist = "Second", albumArtist = "ensemble").copy(releaseDate = ReleaseDate(1999), composers = listOf("Writer")),
        track("c", title = "Gamma", artist = "Ensemble", albumArtist = null).copy(releaseDate = ReleaseDate(2024)),
        track("d", title = "Delta", artist = null, albumArtist = " "),
    )

    @Test fun facetsUseOnlyRealTagsDeduplicateCreditsAndKeepYearsInDescendingOrder() {
        val library = MusicLibrary(tracks)
        val albumArtist = library.facets(LibraryFacetKind.AlbumArtist).single()
        assertEquals("Ensemble", albumArtist.value)
        assertEquals(listOf("a", "b"), albumArtist.tracks.map { it.id })
        assertEquals(listOf("2024", "1999"), library.facets(LibraryFacetKind.Year).map { it.value })
        assertEquals(listOf("Guest", "Writer"), library.facets(LibraryFacetKind.Composer).map { it.value })
        assertEquals(listOf("a", "b"), library.facets(LibraryFacetKind.Composer).last().tracks.map { it.id })
        assertEquals(listOf("a", "b"), library.search("writer").tracks.map { it.id })
        assertEquals(listOf("a", "c"), library.search("2024").tracks.map { it.id })
        // Existing performer/guest indexing is unchanged; no composer is made a performer.
        assertTrue(library.artists.none { it.name == "Writer" })
        assertEquals(listOf("a", "b", "c"), library.artists.first { it.name.equals("Ensemble", true) }.tracks.map { it.id })
    }

    @Test fun homePinsSearchAndBrowseResolveTheSameFilteredMembershipAndRestoreIt() {
        val full = MusicLibrary(tracks)
        val visible = MusicLibrary(tracks.filterNot { it.id == "b" })
        val context = full.facets(LibraryFacetKind.Composer).first { it.value == "Writer" }.context
        val store = InMemoryKeyValueStore()
        Pins(store).toggle(context)
        SearchHistory(store).record(SearchTarget.Facet(context))
        val labels = BrowseLabels("Home", "Playlists", "Albums", "Artists", "Favorites", "Shuffle", "Unknown", "Unknown album",
            facetTitles = mapOf(LibraryFacetKind.Composer to "Composers", LibraryFacetKind.Year to "Years", LibraryFacetKind.AlbumArtist to "Album artists")) { "$it songs" }
        val browse = BrowseTree(visible, emptyList(), emptyList(), listOf(context), labels)
        val home = assertIs<HomeItem.FacetItem>(HomeShortcuts.resolve(visible, context, emptyList()))
        val pin = assertIs<PinnedItem.FacetPin>(Pins(store).resolve(visible).single())
        val search = assertIs<SearchItem.FacetItem>(SearchHistory(store).resolve(visible).single())
        assertEquals(listOf("a"), home.facet.tracks.map { it.id })
        assertEquals(home.facet.tracks, pin.facet.tracks)
        assertEquals(home.facet.tracks, search.facet.tracks)
        assertEquals(home.facet.tracks, browse.tracksOf(context))
        assertEquals(home.facet.tracks, browse.resolveQuery("writer")!!.tracks)
        assertEquals(context, browse.resume("a")!!.context)
        val composers = browse.children(BrowseTree.ARTISTS)!!.first { it.id == BrowseTree.FACET_COMPOSERS }
        val writer = browse.children(composers.id)!!.first { it.title == "Writer" }
        assertEquals(home.facet.tracks, browse.resolvePlay(writer.id)!!.tracks)
        assertEquals(4, browse.children(BrowseTree.ROOT)!!.size)
        assertTrue(Pins(store).resolve(MusicLibrary(emptyList())).isEmpty())
        assertTrue(SearchHistory(store).resolve(MusicLibrary(emptyList())).isEmpty())
        assertEquals(context, Pins(store).contexts.value.single())
        assertEquals(listOf("a", "b"), assertIs<SearchItem.FacetItem>(SearchHistory(store).resolve(full).single()).facet.tracks.map { it.id })
    }

    @Test fun facetIdentitiesSurviveCaseChangesAndMalformedKeysAreRejected() {
        for (kind in LibraryFacetKind.entries) {
            val context = PlayContext.Facet(kind, if (kind == LibraryFacetKind.Year) "2024" else "Å name")
            assertEquals(context, PlayContext.fromKey(context.key))
        }
        val before = MusicLibrary(tracks).facets(LibraryFacetKind.Composer).last().context
        val after = MusicLibrary(tracks.map { it.copy(composers = it.composers.map(String::uppercase)) }).facet(before)
        assertEquals(before, after!!.context)
        listOf("facet", "facet\u001Dunknown\u001Dx", "facet\u001Dyear\u001D0", "facet\u001Dyear\u001Dword",
            "facet\u001Dcomposer\u001D", "facet\u001Dcomposer\u001Dx\u001Dy").forEach { assertNull(PlayContext.fromKey(it)) }
    }

    @Test fun alphabeticalIndexHasStableFirstPositionsAndSafeUnicodeLabels() {
        assertEquals(listOf(AlphabetSection("#", 0), AlphabetSection("A", 2), AlphabetSection("B", 4), AlphabetSection("雪", 5)),
            AlphabeticalIndex.build(listOf(null, "🎵 theme", " Alpha", "alpine", "Beta", "雪")))
        assertTrue(AlphabeticalIndex.build(emptyList()).isEmpty())
    }
}
