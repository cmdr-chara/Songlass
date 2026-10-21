package dev.cmdrchara.songlass.core.equalizer

/** Result of reading a parametric equalizer file: the filters, and lines that could not be used. */
data class ParametricImport(val filters: List<EqFilter>, val skipped: Int)

/**
 * Reads AutoEQ's Equalizer APO parametric exports. Peaking and shelf filters are retained;
 * unsupported/disabled filters are reported as skipped. The file's preamp is ignored because
 * [EqChain.withHeadroom] computes attenuation for the actual combined correction and graphic EQ.
 */
object AutoEq {
    const val MAX_FILTERS = 20
    /** Matches the existing document picker's limit, with ample room for normal text exports. */
    const val MAX_INPUT_CHARS = 1024 * 1024
    /** A filter has a handful of numeric fields; long malformed lines never enter token parsing. */
    const val MAX_LINE_CHARS = 4096
    const val MAX_FILE_NAME_CHARS = 4096

    fun parse(text: String, rejectOutOfRange: Boolean = false): ParametricImport {
        if (text.length > MAX_INPUT_CHARS) return ParametricImport(emptyList(), 1)
        val filters = mutableListOf<EqFilter>()
        var skipped = 0
        var firstLine = true
        for (raw in text.lineSequence()) {
            val line = (if (firstLine) raw.removePrefix("\uFEFF") else raw).trim()
            firstLine = false
            if (!line.startsWith("Filter", ignoreCase = true)) continue
            if (line.length > MAX_LINE_CHARS || filters.size >= MAX_FILTERS) {
                skipped++
                continue
            }
            val filter = readFilter(line)
            if (filter == null || filter.frequencyHz <= 0 || filter.q <= 0 ||
                !filter.frequencyHz.isFinite() || !filter.gainDb.isFinite() || !filter.q.isFinite() ||
                (rejectOutOfRange && !filter.isSupported)) {
                skipped++
                continue
            }
            filters += filter.copy(
                frequencyHz = filter.frequencyHz.coerceIn(10.0, 22_000.0),
                gainDb = filter.gainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB),
                q = filter.q.coerceIn(0.1, 10.0),
            )
        }
        return ParametricImport(filters, skipped)
    }

    /**
     * Sequential parsing avoids the old overlapping whitespace repetitions around an optional
     * filter number. Every character is examined a bounded number of times, even on malformed input.
     */
    private fun readFilter(line: String): EqFilter? {
        val cursor = FilterCursor(line)
        if (!cursor.word("Filter")) return null
        cursor.optionalNumber()
        if (!cursor.character(':') || !cursor.word("ON")) return null
        val type = cursor.readWord()?.let(::typeOf) ?: return null
        if (!cursor.word("Fc")) return null
        val frequency = cursor.number() ?: return null
        if (!cursor.word("Hz")) return null
        var gain = 0.0
        var q = DEFAULT_SHELF_Q
        if (cursor.nextWordIs("Gain")) {
            cursor.word("Gain")
            gain = cursor.number() ?: return null
            if (!cursor.word("dB")) return null
        }
        if (cursor.nextWordIs("Q")) {
            cursor.word("Q")
            q = cursor.number() ?: return null
        }
        if (!cursor.finished()) return null
        return EqFilter(type, frequency, gain, q)
    }

    /** "Sennheiser HD 600 ParametricEQ.txt" → "Sennheiser HD 600", without a suffix regex. */
    fun nameFromFile(fileName: String): String {
        val base = fileName.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.').trim()
        var name = base
        if (name.endsWith("eq", ignoreCase = true)) {
            val beforeEq = name.dropLast(2).trimEnd()
            if (beforeEq.endsWith("parametric", ignoreCase = true)) {
                name = beforeEq.dropLast("parametric".length).trimEnd { it.isWhitespace() || it == '_' || it == '-' }
            }
        }
        return name.trim().ifEmpty { fileName }
    }

    private fun typeOf(code: String): FilterType? = when (code.uppercase()) {
        "PK", "PEQ" -> FilterType.Peaking
        "LS", "LSC", "LSQ" -> FilterType.LowShelf
        "HS", "HSC", "HSQ" -> FilterType.HighShelf
        else -> null
    }

    private class FilterCursor(private val text: String) {
        private var at = 0
        private fun spaces() { while (at < text.length && text[at].isWhitespace()) at++ }
        fun optionalNumber() {
            spaces()
            while (at < text.length && text[at] in '0'..'9') at++
        }
        fun character(expected: Char): Boolean {
            spaces()
            if (at >= text.length || text[at] != expected) return false
            at++
            return true
        }
        fun readWord(): String? {
            spaces()
            val start = at
            while (at < text.length && (text[at] in 'a'..'z' || text[at] in 'A'..'Z')) at++
            return if (at == start) null else text.substring(start, at)
        }
        fun word(expected: String): Boolean = readWord()?.equals(expected, ignoreCase = true) == true
        fun nextWordIs(expected: String): Boolean {
            val previous = at
            val matches = word(expected)
            at = previous
            return matches
        }
        fun number(): Double? {
            spaces()
            val start = at
            if (at < text.length && (text[at] == '+' || text[at] == '-')) at++
            var digits = 0
            while (at < text.length && text[at] in '0'..'9') { at++; digits++ }
            if (at < text.length && text[at] == '.') {
                at++
                while (at < text.length && text[at] in '0'..'9') { at++; digits++ }
            }
            if (digits == 0) return null
            if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
                at++
                if (at < text.length && (text[at] == '+' || text[at] == '-')) at++
                val exponent = at
                while (at < text.length && text[at] in '0'..'9') at++
                if (at == exponent) return null
            }
            if (at - start > MAX_NUMBER_CHARS) return null
            return text.substring(start, at).toDoubleOrNull()
        }
        fun finished(): Boolean {
            spaces()
            return at == text.length || text[at] == '#'
        }
    }

    private const val MAX_GAIN_DB = 24.0
    private const val DEFAULT_SHELF_Q = 0.71
    private const val MAX_NUMBER_CHARS = 64
}
