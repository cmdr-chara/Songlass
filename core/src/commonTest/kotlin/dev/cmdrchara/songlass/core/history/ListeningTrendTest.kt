package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.track
import kotlin.test.*

class ListeningTrendTest {
    private val library = MusicLibrary(listOf(track("a")))
    @Test fun midnightBelongsToNextBucketAndFutureOrHiddenTracksAreExcluded() {
        val history = HistorySnapshot(datedPlays = listOf(DatedPlay("a", 0), DatedPlay("a", 99), DatedPlay("a", 100),
            DatedPlay("a", 150), DatedPlay("a", 151), DatedPlay("hidden", 110)))
        val trend = ListeningTrend.build(library, history, listOf(0, 100, 200), 150)
        assertEquals(listOf(2, 2), trend.map { it.plays })
    }
    @Test fun dstDaysUseProvidedLocalMidnightBoundaries() {
        val hour = 3_600_000L
        val history = HistorySnapshot(datedPlays = listOf(DatedPlay("a", 23 * hour), DatedPlay("a", 47 * hour)))
        assertEquals(listOf(0, 1, 1), ListeningTrend.build(library, history,
            listOf(0, 23 * hour, 47 * hour, 72 * hour), 48 * hour).map { it.plays })
    }
    @Test fun weeklyGroupingRetainsLastPartialBucketAndTotal() {
        val starts = (0L..365L).toList()
        val history = HistorySnapshot(datedPlays = (0L..364L).map { DatedPlay("a", it) })
        val trend = ListeningTrend.build(library, history, starts, 364)
        assertEquals(53, trend.size)
        assertEquals(365, trend.sumOf { it.plays })
        assertEquals(1, trend.last().plays)
    }
    @Test fun datesBeforeTrackingAreUnknownRatherThanZeroListens() {
        val history = HistorySnapshot(trackingStartedMs = 150, datedPlays = listOf(DatedPlay("a", 151)))
        val buckets = ListeningTrend.build(library, history, listOf(0, 100, 200), 160)
        assertEquals(listOf(false, true), buckets.map { it.recorded })
        assertEquals(1, buckets.last().plays)
    }
    @Test fun allTimeWindowIsBoundedAndEmptyDataRemainsZero() {
        assertEquals(1, ListeningTrend.days(StatsPeriod.AllTime, HistorySnapshot(), 0))
        assertEquals(732, ListeningTrend.days(StatsPeriod.AllTime, HistorySnapshot(datedPlays = listOf(DatedPlay("a", 0))), Long.MAX_VALUE))
        assertTrue(ListeningTrend.build(library, HistorySnapshot(), listOf(0, 100), 50).all { it.plays == 0 })
    }
}
