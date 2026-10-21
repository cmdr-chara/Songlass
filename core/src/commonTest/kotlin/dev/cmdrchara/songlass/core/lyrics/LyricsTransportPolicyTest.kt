package dev.cmdrchara.songlass.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsTransportPolicyTest {
    @Test fun numericRetryAfterRetainsShortDelaysAndCapsHugeOrOverflowingValues() {
        assertEquals(0L, LyricsTransportPolicy.retryAfterMillis("0", 0))
        assertEquals(2_000L, LyricsTransportPolicy.retryAfterMillis(" 2 ", 0))
        assertEquals(3_600_000L, LyricsTransportPolicy.retryAfterMillis("3600", 0))
        assertEquals(3_600_000L, LyricsTransportPolicy.retryAfterMillis("3601", 0))
        assertEquals(3_600_000L, LyricsTransportPolicy.retryAfterMillis(Long.MAX_VALUE.toString(), 0))
        assertEquals(3_600_000L, LyricsTransportPolicy.retryAfterMillis("9".repeat(256), 0))
    }

    @Test fun invalidNumericOrUnboundedHeadersDoNotBecomeNegativeOrPermanentBackoff() {
        for (value in listOf(null, "", " ", "-1", "+2", "1.5", "NaN", "١٢", "9".repeat(257))) {
            assertNull(LyricsTransportPolicy.retryAfterMillis(value, 0), value)
        }
    }

    @Test fun httpDatesAreMeasuredFromTheProvidedClockAndBounded() {
        assertEquals(1_000L, LyricsTransportPolicy.retryAfterMillis("date", 1_000) { 2_000 })
        assertEquals(0L, LyricsTransportPolicy.retryAfterMillis("date", 2_000) { 1_000 })
        assertEquals(0L, LyricsTransportPolicy.retryAfterMillis("date", 2_000) { 2_000 })
        assertEquals(3_600_000L, LyricsTransportPolicy.retryAfterMillis("date", 0) { Long.MAX_VALUE })
        assertNull(LyricsTransportPolicy.retryAfterMillis("broken date", 0) { null })
    }

    @Test fun extremeDatesAndClockValuesCannotWrapIntoShortOrNegativeDelays() {
        assertEquals(3_600_000L, LyricsTransportPolicy.retryAfterMillis("date", Long.MIN_VALUE) { Long.MAX_VALUE })
        assertEquals(3_600_000L, LyricsTransportPolicy.retryAfterMillis("date", Long.MIN_VALUE) { 0 })
        assertEquals(1L, LyricsTransportPolicy.retryAfterMillis("date", Long.MAX_VALUE - 1) { Long.MAX_VALUE })
        assertEquals(0L, LyricsTransportPolicy.retryAfterMillis("date", Long.MAX_VALUE) { Long.MIN_VALUE })
    }

    @Test fun validUnicodeIncludingReplacementCharacterIsPreservedExactly() {
        val text = "{\"plainLyrics\":\"Città 東京 😀 \uFFFD\"}"
        assertEquals(text, LyricsTransportPolicy.decodeUtf8(text.encodeToByteArray()))
        assertEquals("", LyricsTransportPolicy.decodeUtf8(byteArrayOf()))
    }

    @Test fun malformedUtf8IsRejectedInsteadOfCreatingDifferentWords() {
        for (bytes in listOf(
            byteArrayOf(0xc3.toByte(), 0x28), // Bad continuation.
            byteArrayOf(0xe2.toByte(), 0x82.toByte()), // Truncated sequence.
            byteArrayOf(0xc0.toByte(), 0xaf.toByte()), // Overlong slash.
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()), // Surrogate.
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()), // Beyond Unicode.
            byteArrayOf(0x80.toByte()), // Bare continuation.
        )) assertNull(LyricsTransportPolicy.decodeUtf8(bytes))
    }

    @Test fun byteLimitAcceptsTheBoundaryAndRejectsOneExtraByte() {
        assertEquals("abcd", LyricsTransportPolicy.decodeUtf8("abcd".encodeToByteArray(), maxBytes = 4))
        assertNull(LyricsTransportPolicy.decodeUtf8("abcde".encodeToByteArray(), maxBytes = 4))
        assertNull(LyricsTransportPolicy.decodeUtf8(ByteArray(LyricsTransportPolicy.MAX_RESPONSE_BYTES + 1)))
    }
}
