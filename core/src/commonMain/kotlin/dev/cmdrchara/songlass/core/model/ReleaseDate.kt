package dev.cmdrchara.songlass.core.model

/** Partial dates remain partial: a year alone never creates an anniversary. */
data class ReleaseDate(val year: Int, val month: Int? = null, val day: Int? = null) {
    companion object {
        fun parse(value: String?): ReleaseDate? {
            val text = value?.trim() ?: return null
            val match = Regex("^(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?(?:[T ].*)?$").matchEntire(text) ?: return null
            val year = match.groupValues[1].toInt().takeIf { it in 1..9999 } ?: return null
            val month = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()
            val day = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            if (month != null && month !in 1..12) return null
            if (day != null) {
                val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
                val days = listOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
                if (month == null || day !in 1..days[month - 1]) return null
            }
            return ReleaseDate(year, month, day)
        }
    }
}
