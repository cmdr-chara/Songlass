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
    @Test fun equalPlayCountsShareACompetitionRank() {
        val library = MusicLibrary(listOf(track("a"), track("b"), track("c"), track("d")))
        val snapshot = HistorySnapshot(playCounts = mapOf("a" to 5, "b" to 3, "c" to 3, "d" to 1))
        val stats = ListeningStats.build(library, snapshot, StatsPeriod.AllTime, now)
        assertEquals(listOf(1, 2, 2, 4), stats.tracks.map { it.rank })
        assertEquals(listOf("a", "b", "c", "d"), stats.tracks.map { it.track.id })
    }
    @Test fun periodStartingBeforeTrackingIsPartial() {
        val library = MusicLibrary(listOf(track("a")))
        val snapshot = HistorySnapshot(trackingStartedMs = now - 2 * day, datedPlays = listOf(DatedPlay("a", now)))
        assertFalse(ListeningStats.build(library, snapshot, StatsPeriod.Week, now, now - 7 * day).coversWholePeriod)
        assertTrue(ListeningStats.build(library, snapshot, StatsPeriod.Week, now, now - day).coversWholePeriod)
        assertTrue(ListeningStats.build(library, snapshot, StatsPeriod.AllTime, now).coversWholePeriod)
    }
    @Test fun comparisonUsesTheEarlierHalfOpenPeriod() {
        val library = MusicLibrary(listOf(track("a"), track("b")))
        val snapshot = HistorySnapshot(trackingStartedMs = 0, datedPlays = listOf(
            DatedPlay("a", now - 14 * day), DatedPlay("b", now - 8 * day), DatedPlay("a", now - 7 * day), DatedPlay("a", now)))
        val current = ListeningStats.build(library, snapshot, StatsPeriod.Week, now, now - 7 * day)
        val comparison = assertNotNull(ListeningStats.compare(library, snapshot, StatsPeriod.Week, current, now - 14 * day, now - 7 * day))
        assertEquals(StatChange(previous = 2, current = 2), comparison.plays)
        assertEquals(0, comparison.plays.difference)
        assertEquals(StatChange(previous = 2, current = 1), comparison.tracks)
        assertEquals(-1, comparison.tracks.difference)
    }
    @Test fun comparisonPreservesDuplicateRowsWithoutMultiplyingArtistMemberships() {
        val first = track("a").copy(artist = "Alpha", artists = listOf("Alpha", "Beta", "Beta"), albumArtist = null)
        val second = track("b").copy(artist = "Beta", artists = listOf("Beta", "Gamma"), albumArtist = null)
        val library = MusicLibrary(listOf(first, first.copy(title = "Repeated source row"), second))
        val previousFrom = now - 14 * day
        val from = now - 7 * day
        val history = HistorySnapshot(trackingStartedMs = 0, datedPlays = listOf(
            DatedPlay("a", previousFrom), DatedPlay("a", from - 1), DatedPlay("b", previousFrom + day),
            DatedPlay("missing", previousFrom + 1), DatedPlay("b", previousFrom - 1),
            DatedPlay("a", from), DatedPlay("b", now + 1),
        ))
        val current = ListeningStats.build(library, history, StatsPeriod.Week, now, from)
        val comparison = assertNotNull(ListeningStats.compare(library, history, StatsPeriod.Week, current, previousFrom, from))
        assertEquals(StatChange(5, 2), comparison.plays)
        assertEquals(StatChange(3, 2), comparison.tracks)
        assertEquals(StatChange(3, 2), comparison.artists)
    }

    @Test fun comparisonIsWithheldWhenTheEarlierPeriodIsNotFullyRecorded() {
        val library = MusicLibrary(listOf(track("a")))
        val current = ListeningStats.build(library, HistorySnapshot(), StatsPeriod.Week, now, now - 7 * day)
        val lateStart = HistorySnapshot(trackingStartedMs = now - 10 * day, datedPlays = listOf(DatedPlay("a", now)))
        assertNull(ListeningStats.compare(library, lateStart, StatsPeriod.Week, current, now - 14 * day, now - 7 * day))
        assertNull(ListeningStats.compare(library, HistorySnapshot(trackingStartedMs = 0), StatsPeriod.AllTime, current, 0, now))
        val pruned = HistorySnapshot(trackingStartedMs = 0, datedPlays = List(50_000) { DatedPlay("a", now - it) })
        assertNull(ListeningStats.compare(library, pruned, StatsPeriod.Week, current, now - 14 * day, now - 7 * day))
        val quiet = HistorySnapshot(trackingStartedMs = 0)
        assertEquals(StatChange(0, 0), ListeningStats.compare(library, quiet, StatsPeriod.Week, current, now - 14 * day, now - 7 * day)?.plays)
    }
}
