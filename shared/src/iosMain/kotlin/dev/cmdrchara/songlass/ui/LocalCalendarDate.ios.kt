package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.model.ReleaseDate
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun localCalendarDate(atMs: Long): ReleaseDate {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
    }
    return requireNotNull(ReleaseDate.parse(formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(atMs / 1000.0))))
}

actual fun localDayStarts(atMs: Long, days: Int): List<Long> {
    val calendar = platform.Foundation.NSCalendar.currentCalendar
    val today = calendar.startOfDayForDate(NSDate.dateWithTimeIntervalSince1970(atMs / 1000.0))
    return (days - 1 downTo -1).map { ago ->
        val date = requireNotNull(calendar.dateByAddingUnit(platform.Foundation.NSCalendarUnitDay, -ago.toLong(), today, 0u))
        (date.timeIntervalSince1970 * 1000).toLong()
    }
}
