package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.library.MusicLibrary
import kotlin.math.ceil

data class ListeningBucket(val fromMs: Long, val untilMs: Long, val plays: Int, val recorded: Boolean = true)

/** Local-midnight boundaries are supplied by the platform, so DST days need not be 24h.
 * Buckets are half-open; today's bucket also stops at now. A zero is real absence of plays.
 */
object ListeningTrend {
    fun days(period: StatsPeriod, history: HistorySnapshot, nowMs: Long): Int = period.days ?: run {
        val oldest = history.datedPlays.minOfOrNull { it.atMs } ?: nowMs
        (ceil((nowMs - oldest).coerceAtLeast(0) / 86_400_000.0).coerceAtMost(731.0).toInt() + 1)
    }

    /** Day starts are chronological, including tomorrow's boundary. */
    fun build(library: MusicLibrary, history: HistorySnapshot, dayStarts: List<Long>, nowMs: Long): List<ListeningBucket> {
        require(dayStarts.size >= 2 && dayStarts.zipWithNext().all { (a, b) -> a < b })
        val dayCount = dayStarts.size - 1
        val groupDays = when { dayCount <= 30 -> 1; dayCount <= 365 -> 7; else -> 14 }
        val ids = library.tracks.map { it.id }.toSet()
        val counts = IntArray(dayCount)
        for (play in history.datedPlays) {
            if (play.trackId !in ids || play.atMs > nowMs || play.atMs < dayStarts.first() || play.atMs >= dayStarts.last()) continue
            val day = dayStarts.binarySearch(play.atMs).let { if (it >= 0) it else -it - 2 }
            counts[day]++
        }
        return (0 until dayCount step groupDays).map { first ->
            val end = minOf(first + groupDays, dayCount)
            ListeningBucket(dayStarts[first], dayStarts[end], (first until end).sumOf { counts[it] },
                history.trackingStartedMs?.let { dayStarts[end] > it } ?: true)
        }
    }
}
