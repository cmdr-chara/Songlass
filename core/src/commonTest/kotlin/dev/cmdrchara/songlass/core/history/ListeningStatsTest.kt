package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.*

class ListeningStatsTest {
    private val day = 86_400_000L
    private val now = 1000 * day
    @Test fun undatedCountsMigrateWithoutInventedEvents() {
        val store = InMemoryKeyValueStore()
        store.putString("history.counts", "a\u001E12\u001E10")
        val history = ListeningHistory(store) { now }
        assertEquals(12, history.state.value.totalPlays)
        assertTrue(history.state.value.datedPlays.isEmpty())
        history.recordPlay("a", null)
        assertEquals(listOf(DatedPlay("a", now)), ListeningHistory(store) { now }.state.value.datedPlays)
        assertEquals(13, history.state.value.totalPlays)
    }
    @Test fun periodBoundariesAndFutureEventsAndMissingFiles() {
        val library = MusicLibrary(listOf(track("a"), track("b")))
        val snapshot = HistorySnapshot(playCounts = mapOf("a" to 40), datedPlays = listOf(
            DatedPlay("a", now - 7 * day), DatedPlay("a", now - 7 * day - 1),
            DatedPlay("b", now), DatedPlay("b", now + 1), DatedPlay("missing", now)))
        val stats = ListeningStats.build(library, snapshot, StatsPeriod.Week, now)
        assertEquals(2, stats.totalPlays)
        assertEquals(2, stats.differentTracks)
        assertEquals(40, ListeningStats.build(library, snapshot, StatsPeriod.AllTime, now).totalPlays)
    }
    @Test fun retentionPrunesDatedEventsButKeepsLifetimeCounts() {
        val store = InMemoryKeyValueStore()
        var clock = now
        val history = ListeningHistory(store) { clock }
        history.recordPlay("a", null)
        clock += 732 * day
        history.recordPlay("b", null)
        assertEquals(listOf("b"), history.state.value.datedPlays.map { it.trackId })
        assertEquals(2, history.state.value.totalPlays)
    }
    @Test fun malformedDatedRecordsDoNotLoseOldTotals() {
        val store = InMemoryKeyValueStore()
        store.putString("history.dated_plays", "broken\u001Fa\u001Enope\u001Fb\u001E${now + 1}")
        assertTrue(ListeningHistory(store) { now }.state.value.datedPlays.isEmpty())
    }
    @Test fun artistCollaborationsDoNotMultiplyTotalPlays() {
        val library = MusicLibrary(listOf(track("a").copy(artist = "Alpha; Beta")))
        val stats = ListeningStats.build(library, HistorySnapshot(playCounts = mapOf("a" to 2)), StatsPeriod.AllTime, now)
        assertEquals(2, stats.totalPlays)
        assertEquals(2, stats.differentArtists)
        assertEquals(listOf(2, 2), stats.artists.map { it.plays })
    }
}
