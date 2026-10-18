package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.model.ReleaseDate

/** Calendar date in the device's time zone, not UTC; epoch milliseconds. */
expect fun localCalendarDate(atMs: Long): ReleaseDate

/** [days] calendar days ending today, then tomorrow's midnight. */
expect fun localDayStarts(atMs: Long, days: Int): List<Long>
