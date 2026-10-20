package dev.cmdrchara.songlass.core.equalizer

/** Result of reading a parametric equalizer file: the filters, and lines that could not be used. */
data class ParametricImport(val filters: List<EqFilter>, val skipped: Int)

/**
 * Reads the parametric format of Equalizer APO, which AutoEQ (autoeq.app) exports for each
 * headphone model ("… ParametricEQ.txt"):
 *
 *     Preamp: -6.2 dB
 *     Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
 *     Filter 2: ON PK Fc 2000 Hz Gain -3.1 dB Q 1.41
 *
 * Peaking and shelf filters are kept; other types (low/high pass, notch) and disabled filters are
 * skipped. The preamp line is ignored: [EqChain.withHeadroom] computes the attenuation itself.
 */
object AutoEq {
    const val MAX_FILTERS = 20

    fun parse(text: String, rejectOutOfRange: Boolean = false): ParametricImport {
        val filters = mutableListOf<EqFilter>()
        var skipped = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith("Filter", ignoreCase = true)) continue
            val match = FILTER.find(line)
            val type = match?.groupValues?.get(2)?.let(::typeOf)
            if (match == null || type == null || !match.groupValues[1].equals("ON", ignoreCase = true) || filters.size >= MAX_FILTERS) {
                skipped++
                continue
            }
            val frequency = match.groupValues[3].toDoubleOrNull()
            val gain = match.groupValues[4].toDoubleOrNull() ?: 0.0
            val q = match.groupValues[5].toDoubleOrNull() ?: DEFAULT_SHELF_Q
            if (frequency == null || frequency <= 0.0 || !frequency.isFinite() || !gain.isFinite() || !q.isFinite() ||
                (rejectOutOfRange && (frequency !in 10.0..22_000.0 || gain !in -MAX_GAIN_DB..MAX_GAIN_DB || q !in 0.1..10.0 || match.range.last != line.lastIndex))) {
                skipped++
                continue
            }
            filters += EqFilter(
                type = type,
                frequencyHz = frequency.coerceIn(10.0, 22_000.0),
                gainDb = gain.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB),
                q = q.coerceIn(0.1, 10.0),
            )
        }
        return ParametricImport(filters, skipped)
    }

    /** "Sennheiser HD 600 ParametricEQ.txt" → "Sennheiser HD 600". */
    fun nameFromFile(fileName: String): String = fileName
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .replace(SUFFIX, "")
        .trim()
        .ifEmpty { fileName }

    private fun typeOf(code: String): FilterType? = when (code.uppercase()) {
        "PK", "PEQ" -> FilterType.Peaking
        "LS", "LSC", "LSQ" -> FilterType.LowShelf
        "HS", "HSC", "HSQ" -> FilterType.HighShelf
        else -> null
    }

    private const val MAX_GAIN_DB = 24.0
    private const val DEFAULT_SHELF_Q = 0.71
    private val FILTER = Regex(
        """Filter\s*\d*\s*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val SUFFIX = Regex("""[\s_-]*parametric\s*eq\s*$""", RegexOption.IGNORE_CASE)
}
