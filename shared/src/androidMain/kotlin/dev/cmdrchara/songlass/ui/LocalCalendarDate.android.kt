package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.model.ReleaseDate
import java.time.Instant
import java.time.ZoneId

actual fun localCalendarDate(atMs: Long): ReleaseDate {
    val date = Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return ReleaseDate(date.year, date.monthValue, date.dayOfMonth)
}

actual fun localDayStarts(atMs: Long, days: Int): List<Long> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
    return (days - 1 downTo -1).map { ago -> today.minusDays(ago.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
}
