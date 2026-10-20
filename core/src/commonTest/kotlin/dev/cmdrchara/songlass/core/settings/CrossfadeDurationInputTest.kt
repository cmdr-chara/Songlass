package dev.cmdrchara.songlass.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrossfadeDurationInputTest {
    @Test
    fun acceptsOffAndExactMillisecondValuesIncludingBounds() {
        for ((input, expected) in listOf("0" to 0L, "250" to 250L, "1250" to 1_250L,
            "12000" to 12_000L, " 1250 " to 1_250L, "001250" to 1_250L)) {
            assertEquals(expected, parseCrossfadeDurationMs(input), input)
        }
    }

    @Test
    fun rejectsInvalidInputInsteadOfRoundingClampingOrDisabling() {
        for (input in listOf("", " ", "1", "249", "12001", "-250", "+250", "1.25", "1,250",
            "1250 ms", "1e3", "1 250", "9223372036854775808", "１２５０")) {
            assertNull(parseCrossfadeDurationMs(input), input)
        }
    }
}
