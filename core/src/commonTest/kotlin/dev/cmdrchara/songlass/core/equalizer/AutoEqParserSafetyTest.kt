package dev.cmdrchara.songlass.core.equalizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoEqParserSafetyTest {
    private val valid = "Filter 1: ON PK Fc 1000 Hz Gain -2 dB Q 1.41"
    private val expected = EqFilter(FilterType.Peaking, 1000.0, -2.0, 1.41)

    @Test fun normalAutoEqExportsKeepSupportedFamiliesAndIgnorePreamp() {
        val parsed = AutoEq.parse("""
            Preamp: -7.3 dB
            Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
            Filter 2: ON PK Fc 2084 Hz Gain -3.1 dB Q 1.41
            Filter 3: ON HSC Fc 10000 Hz Gain 1.2 dB Q 0.70
        """.trimIndent(), rejectOutOfRange = true)
        assertEquals(listOf(
            EqFilter(FilterType.LowShelf, 105.0, 5.5, 0.7),
            EqFilter(FilterType.Peaking, 2084.0, -3.1, 1.41),
            EqFilter(FilterType.HighShelf, 10000.0, 1.2, 0.7),
        ), parsed.filters)
        assertEquals(0, parsed.skipped)
    }

    @Test fun bomCrLfOptionalNumbersAliasesAndCommentsStayCompatible() {
        val codes = listOf("PK", "PEQ", "LS", "LSC", "LSQ", "HS", "HSC", "HSQ")
        val types = listOf(FilterType.Peaking, FilterType.Peaking,
            FilterType.LowShelf, FilterType.LowShelf, FilterType.LowShelf,
            FilterType.HighShelf, FilterType.HighShelf, FilterType.HighShelf)
        val text = "\uFEFF" + codes.mapIndexed { index, code ->
            val number = if (index % 2 == 0) "" else index.toString()
            "  fIlTeR$number:\tOn $code fC 1000Hz GaIn -2dB q 1.41 # local note"
        }.joinToString("\r\n")
        val parsed = AutoEq.parse(text, rejectOutOfRange = true)
        assertEquals(types.map { expected.copy(type = it) }, parsed.filters)
        assertEquals(0, parsed.skipped)
    }

    @Test fun finiteSignedDecimalAndExponentNumbersAreAccepted() {
        val parsed = AutoEq.parse("Filter : ON peq Fc +1e3 Hz Gain -2.5E0 dB Q +7.1e-1", true)
        assertEquals(listOf(EqFilter(FilterType.Peaking, 1000.0, -2.5, 0.71)), parsed.filters)
        assertEquals(0, parsed.skipped)
        assertEquals(listOf(EqFilter(FilterType.LowShelf, 100.0, 0.5, 1.0)),
            AutoEq.parse("Filter: ON LS Fc 100. Hz Gain +.5 dB Q 1").filters)
    }

    @Test fun omittedOptionalFieldsKeepEstablishedDefaults() {
        assertEquals(listOf(EqFilter(FilterType.LowShelf, 100.0, 0.0, 0.71)),
            AutoEq.parse("Filter: ON LS Fc 100 Hz").filters)
        assertEquals(listOf(EqFilter(FilterType.Peaking, 100.0, 0.0, 1.4)),
            AutoEq.parse("Filter: ON PK Fc 100 Hz Q 1.4").filters)
    }

    @Test fun brokenFieldsNeverBecomeAFlatOrPartiallyParsedCorrection() {
        val invalid = listOf(
            "Filter 1: ON PK Fc 1000 Hz Gain broken dB Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain 2 Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain 2 dB Q broken",
            "Filter 1: ON PK Fc 1000 Hz Gain 2 dB Q 1 unknown",
            "Filter 1: ON PK Fc 1000 Hz Gain 2 dB Q 1.2.3",
            "Filter 1: ON PK Fc 1000 Hz Gain --2 dB Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain 2e- dB Q 1",
            "Filter 1: ON PK Fc 1000 kHz Gain 2 dB Q 1",
            "Filter 1: ON PK Fc 1000 Hz Gain 2 dB Q 0",
            "Filter 1: ON PK Fc 1000 Hz Gain 2 dB Q -1",
            "Filter 1: ON PK Fc 0 Hz Gain 2 dB Q 1",
            "Filter 1: ON PK Fc -1 Hz Gain 2 dB Q 1",
        )
        for (line in invalid) {
            for (strict in listOf(false, true)) {
                val parsed = AutoEq.parse("$line\n$valid", strict)
                assertEquals(listOf(expected), parsed.filters, line)
                assertEquals(1, parsed.skipped, line)
            }
        }
    }

    @Test fun nonFiniteAndOversizedNumericTokensAreRejected() {
        for (value in listOf("NaN", "Infinity", "1e9999", "9".repeat(65))) {
            val parsed = AutoEq.parse("Filter: ON PK Fc $value Hz Gain 1 dB Q 1\n$valid")
            assertEquals(listOf(expected), parsed.filters, value)
            assertEquals(1, parsed.skipped, value)
        }
        for (field in listOf("Gain 1e9999 dB Q 1", "Gain 1 dB Q 1e9999")) {
            val parsed = AutoEq.parse("Filter: ON PK Fc 1000 Hz $field")
            assertTrue(parsed.filters.isEmpty())
            assertEquals(1, parsed.skipped)
        }
    }

    @Test fun manualRangeClampingAndCatalogStrictnessStayDistinct() {
        val text = "Filter: ON PK Fc 1 Hz Gain 30 dB Q 0.01\n" +
            "Filter: ON HSC Fc 23000 Hz Gain -30 dB Q 12"
        assertEquals(listOf(
            EqFilter(FilterType.Peaking, 10.0, 24.0, 0.1),
            EqFilter(FilterType.HighShelf, 22000.0, -24.0, 10.0),
        ), AutoEq.parse(text).filters)
        val strict = AutoEq.parse(text, rejectOutOfRange = true)
        assertTrue(strict.filters.isEmpty())
        assertEquals(2, strict.skipped)
    }

    @Test fun disabledUnsupportedAndOverflowFiltersAreReportedOnce() {
        val supported = (1..AutoEq.MAX_FILTERS + 2).joinToString("\n") { valid }
        val parsed = AutoEq.parse("Filter 1: OFF PK Fc 100 Hz\nFilter 2: ON HP Fc 20 Hz\n$supported")
        assertEquals(List(AutoEq.MAX_FILTERS) { expected }, parsed.filters)
        assertEquals(4, parsed.skipped)
    }

    @Test fun adversarialInteriorWhitespaceAndFilterNumbersRemainRejected() {
        // These are the former regex's overlapping whitespace spans. No elapsed-time
        // assertion: the same production-jar harness measures work before and after.
        for (size in listOf(500, 1000, 2000, 4000, 8000)) {
            val parsed = AutoEq.parse("Filter" + " ".repeat(size) + "X\n$valid")
            assertEquals(listOf(expected), parsed.filters)
            assertEquals(1, parsed.skipped)
        }
        val digits = "Filter" + "1".repeat(AutoEq.MAX_LINE_CHARS) + ": ON PK Fc 100 Hz"
        assertEquals(1, AutoEq.parse(digits).skipped)
    }

    @Test fun defensiveLimitsDoNotDiscardTheNextNormalFilter() {
        val oversizedLine = "Filter" + " ".repeat(AutoEq.MAX_LINE_CHARS) + "X"
        val bounded = AutoEq.parse("$oversizedLine\n$valid")
        assertEquals(listOf(expected), bounded.filters)
        assertEquals(1, bounded.skipped)
        val oversizedFile = valid + "\n" + "#".repeat(AutoEq.MAX_INPUT_CHARS)
        val rejected = AutoEq.parse(oversizedFile)
        assertTrue(rejected.filters.isEmpty())
        assertEquals(1, rejected.skipped)
    }

    @Test fun manyMalformedLinesKeepTheirCountWithoutHidingValidFilters() {
        val parsed = AutoEq.parse(("Filter : ON UNKNOWN Fc 100 Hz\n").repeat(10_000) + valid)
        assertEquals(listOf(expected), parsed.filters)
        assertEquals(10_000, parsed.skipped)
    }

    @Test fun fileNamesKeepUsefulNamesWithoutASuffixRegex() {
        assertEquals("Studio Phones", AutoEq.nameFromFile("Download/Studio Phones ParametricEQ.txt"))
        assertEquals("Studio Phones", AutoEq.nameFromFile("C:\\Music\\Studio Phones__-Parametric EQ.TXT"))
        assertEquals("Studio EQ", AutoEq.nameFromFile("Studio EQ.txt"))
        assertEquals("Studio Phones", AutoEq.nameFromFile("Studio Phones" + "- ".repeat(2000) + "Parametric  EQ.txt"))
        assertEquals("ParametricEQ.txt", AutoEq.nameFromFile("ParametricEQ.txt"))
    }
}
