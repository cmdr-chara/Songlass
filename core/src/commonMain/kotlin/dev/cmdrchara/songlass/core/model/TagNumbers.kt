package dev.cmdrchara.songlass.core.model

/** Helpers for the numeric tags reported by platform media stores. */
object TagNumbers {

    /**
     * Parses a "number" or "number/total" tag such as `"3"` or `"3/12"`.
     * Returns `null` for missing, malformed or non-positive values.
     */
    fun parse(value: String?): Int? =
        value?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    /**
     * Splits Android's legacy `TRACK` column, which encodes `disc * 1000 + track`
     * (e.g. `2005` is disc 2, track 5). Returns (disc, track), each `null` when absent.
     */
    fun splitLegacyTrack(value: Int?): Pair<Int?, Int?> {
        if (value == null || value <= 0) return null to null
        val disc = (value / 1000).takeIf { it > 0 }
        val track = (value % 1000).takeIf { it > 0 }
        return disc to track
    }
}
