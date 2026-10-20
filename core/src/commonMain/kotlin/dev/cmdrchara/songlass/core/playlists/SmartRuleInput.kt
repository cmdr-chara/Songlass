package dev.cmdrchara.songlass.core.playlists

/** Optional numeric form fields. Invalid or inverted ranges never become unbounded rules. */
data class SmartRuleInput(
    val days: String = "",
    val limit: String = "",
    val yearFrom: String = "",
    val yearTo: String = "",
    val durationFromSeconds: String = "",
    val durationToSeconds: String = "",
) {
    fun applyTo(base: SmartRules): SmartRules? {
        val fields = listOf(days, limit, yearFrom, yearTo, durationFromSeconds, durationToSeconds).map(String::trim)
        if (fields.any { it.isNotEmpty() && it.toIntOrNull() == null }) return null
        val values = fields.map { it.toIntOrNull() }
        return base.copy(addedWithinDays = values[0], limit = values[1], yearFrom = values[2], yearTo = values[3],
            durationFromSeconds = values[4], durationToSeconds = values[5]).takeIf { it.isValid }
    }
}
