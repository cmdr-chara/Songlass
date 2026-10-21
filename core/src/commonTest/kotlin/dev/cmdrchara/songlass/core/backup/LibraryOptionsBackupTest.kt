package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.history.PlayCountRule
import dev.cmdrchara.songlass.core.library.LibraryFacetKind
import dev.cmdrchara.songlass.core.library.Pins
import dev.cmdrchara.songlass.core.search.SearchHistory
import dev.cmdrchara.songlass.core.search.SearchTarget
import dev.cmdrchara.songlass.core.settings.AlbumLayout
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryOptionsBackupTest {
    @Test fun facetsRulesAndLayoutRoundTripWithoutRewritingHistoricalCounts() {
        val tracks = listOf(song("1"), song("2"))
        val source = InMemoryKeyValueStore()
        val history = ListeningHistory(source) { NOW }
        history.recordPlay("1", PlayContext.Favorites)
        history.recordPlay("1", null)
        history.recordPlay("2", PlayContext.Album("Album", "Artist"))
        val historicalBytes = source.snapshot().filterKeys { it.startsWith("history.") }
        AppSettings(source).apply {
            setPlayCountRule(PlayCountRule.NearlyAll)
            setAlbumLayout(AlbumLayout.List)
        }
        assertEquals(historicalBytes, source.snapshot().filterKeys { it.startsWith("history.") })
        val facets = listOf(
            PlayContext.Facet(LibraryFacetKind.Composer, "a:b / é 😀"),
            PlayContext.Facet(LibraryFacetKind.AlbumArtist, "ensemble"),
            PlayContext.Facet(LibraryFacetKind.Year, "2024"),
        )
        facets.forEach { SearchHistory(source).record(SearchTarget.Facet(it)); Pins(source).toggle(it) }
        source.putString("track_identity.registry.v1", "device-specific source identity")
        val target = InMemoryKeyValueStore().apply {
            putString("track_identity.registry.v1", "keep destination identity")
        }
        val backup = DataBackup(target) { NOW }
        val export = DataBackup(source) { NOW }.export(tracks)
        assertFalse(export.text.contains("device-specific source identity"))
        val before = target.snapshot()
        val preview = backup.preview(export.text, tracks)
        assertEquals(3, preview.counts.searchItems)
        assertEquals(before, target.snapshot(), "preview must never update preferences or identity")
        backup.apply(preview)
        assertEquals(PlayCountRule.NearlyAll, AppSettings(target).state.value.playCountRule)
        assertEquals(AlbumLayout.List, AppSettings(target).state.value.albumLayout)
        assertEquals(history.state.value, ListeningHistory(target) { NOW }.state.value)
        assertEquals(historicalBytes, target.snapshot().filterKeys { it.startsWith("history.") })
        assertEquals(SearchHistory(source).targets.value, SearchHistory(target).targets.value)
        assertEquals(Pins(source).contexts.value, Pins(target).contexts.value)
        assertEquals("keep destination identity", target.getString("track_identity.registry.v1"))
    }

    @Test fun facetSearchEntriesSurviveTrackRemappingAlongsideSongEntries() {
        val old = song("old", path = "Music/A.mp3", title = "A")
        val replacement = old.copy(id = "canonical-new", uri = "content://media/external/audio/media/42")
        val source = InMemoryKeyValueStore()
        val facet = SearchTarget.Facet(PlayContext.Facet(LibraryFacetKind.Composer, "composer: é"))
        SearchHistory(source).apply { record(facet); record(SearchTarget.Song(old.id)) }
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target) { NOW }
        val preview = backup.preview(DataBackup(source) { NOW }.export(listOf(old)).text, listOf(replacement))
        assertEquals(1, preview.tracks.remapped)
        backup.apply(preview)
        assertEquals(listOf(SearchTarget.Song(replacement.id), facet), SearchHistory(target).targets.value)
    }

    @Test fun oldBackupUsesDefaultFutureRuleAndLeavesItsEarlierTotalsIntact() {
        val source = InMemoryKeyValueStore()
        ListeningHistory(source) { NOW }.recordPlay("1", null)
        val oldHistory = source.snapshot().filterKeys { it.startsWith("history.") }
        val target = InMemoryKeyValueStore()
        val settings = AppSettings(target).apply { setPlayCountRule(PlayCountRule.NearlyAll); setAlbumLayout(AlbumLayout.List) }
        val backup = DataBackup(target) { NOW }
        backup.apply(backup.preview(DataBackup(source) { NOW }.export(listOf(song("1"))).text, listOf(song("1"))))
        settings.reload()
        assertEquals(PlayCountRule.ThirtySecondsOrHalf, settings.state.value.playCountRule)
        assertEquals(AlbumLayout.Grid, settings.state.value.albumLayout)
        assertEquals(oldHistory, target.snapshot().filterKeys { it.startsWith("history.") })
        assertTrue(SearchHistory(target).targets.value.isEmpty())
    }
}
