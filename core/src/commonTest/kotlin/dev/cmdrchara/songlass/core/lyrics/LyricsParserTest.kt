package dev.cmdrchara.songlass.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsParserTest {
    private fun parse(text: String) = LyricsParser.parse(text, LyricsOrigin.File)

    @Test
    fun plainTextKeepsVerseBreaksAndNormalizesBomAndLineEndings() {
        val lyrics = assertNotNull(parse("\uFEFF \r\nFirst line\r\n\r\nSecond verse\rLast line\n "))
        assertEquals(listOf("First line", "", "Second verse", "Last line"), lyrics.lines.map { it.text })
        assertTrue(lyrics.lines.all { it.timeMs == null })
        assertFalse(lyrics.isSynced)
        assertEquals(-1, lyrics.activeLineAt(100_000))
        assertEquals(LyricsOrigin.File, lyrics.source)
    }

    @Test
    fun parsesRepeatedTimesAndFractionWidthsAndSortsStably() {
        val lyrics = assertNotNull(parse("""
            [ar:Artist]
            [ti:Title][al:Album]
            [01:02.123][00:03.4]Repeated
            [00:03.40]Same time
            [00:02]Earlier
        """.trimIndent()))
        assertTrue(lyrics.isSynced)
        assertEquals(listOf(2_000L, 3_400L, 3_400L, 62_123L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("Earlier", "Repeated", "Same time", "Repeated"), lyrics.lines.map { it.text })
        assertEquals(-1, lyrics.activeLineAt(1_999))
        assertEquals(0, lyrics.activeLineAt(2_000))
        assertEquals(2, lyrics.activeLineAt(3_400))
        assertEquals(3, lyrics.activeLineAt(Long.MAX_VALUE))
    }

    @Test
    fun offsetAppliesToEveryLineEvenWhenMetadataComesLast() {
        val early = assertNotNull(parse("[00:00.1]First\n[00:02]Second\n[offset:+250]"))
        assertEquals(listOf(0L, 1_750L), early.lines.map { it.timeMs })
        val late = assertNotNull(parse("[offset:-250]\n[00:00.1]First\n[00:02]Second"))
        assertEquals(listOf(350L, 2_250L), late.lines.map { it.timeMs })
    }

    @Test
    fun extremeOffsetsAndTimestampsCannotOverflow() {
        val positive = assertNotNull(parse("[00:01]Words\n[offset:${Long.MAX_VALUE}]"))
        val negative = assertNotNull(parse("[00:01]Words\n[offset:${Long.MIN_VALUE}]"))
        assertEquals(0L, positive.lines.single().timeMs)
        assertEquals(Long.MAX_VALUE, negative.lines.single().timeMs)
        val hugeTimestamp = assertNotNull(parse("[999999999999999999999999:00]Bad\n[00:02]Good"))
        assertEquals(listOf(LyricsLine(2_000, "Good")), hugeTimestamp.lines)
    }

    @Test
    fun timedBlankLinesClearPreviousHighlightDuringInstrumentalGap() {
        val lyrics = assertNotNull(parse("[00:01]Words\n[00:04]\n[00:09]Next words"))
        assertEquals(1, lyrics.activeLineAt(8_999))
        assertEquals("", lyrics.lines[lyrics.activeLineAt(8_999)].text)
        assertEquals(2, lyrics.activeLineAt(9_000))
    }

    @Test
    fun stripsEnhancedWordTagsButPreservesActualTextAndSectionLabels() {
        val lyrics = assertNotNull(parse("[00:01]<00:01.0>Hello <00:01.25>world <00:02.500>\n[00:03][Chorus] Hello [00:04]again"))
        assertEquals(listOf("Hello world", "[Chorus] Hello again"), lyrics.lines.map { it.text })
    }

    @Test
    fun malformedTagsDoNotProduceInvalidTimesOrHidePlainLyrics() {
        val synced = assertNotNull(parse("[00:99.00]Bad\n[00:02.1234]Bad\n[00:01]Valid\n[offset:nope]"))
        assertEquals(listOf(LyricsLine(1_000, "Valid")), synced.lines)
        val plain = assertNotNull(parse("[Chorus]\n[00:99]Still readable"))
        assertFalse(plain.isSynced)
        assertEquals(listOf("[Chorus]", "[00:99]Still readable"), plain.lines.map { it.text })
    }

    @Test
    fun emptyMetadataAndBlankTimedFilesDoNotPretendToContainLyrics() {
        assertNull(parse("\uFEFF \r\n"))
        assertNull(parse("[ar:Artist]\n[ti:Title]\n[offset:123]"))
        assertNull(parse("[00:01]\n[00:02.50]   "))
        val instrumental = Lyrics(emptyList(), LyricsOrigin.LrcLib, instrumental = true)
        assertTrue(instrumental.instrumental)
        assertFalse(instrumental.isSynced)
        assertEquals(-1, instrumental.activeLineAt(5_000))
    }

    @Test
    fun rejectsBinaryControlsWithoutRejectingTextWhitespace() {
        assertNull(parse("Words\u0000binary"))
        assertNull(parse("Words\u0007binary"))
        assertNull(parse("Words\u007Fbinary"))
        assertNotNull(parse("Words\twith tabs\nNext line"))
        val malformed = assertNotNull(parse("[00:01]Words <00:02] remain"))
        assertEquals("Words <00:02] remain", malformed.lines.single().text)
    }

    @Test
    fun rejectsOversizedInputWithoutReturningTruncatedLyrics() {
        assertNull(parse("a".repeat(LyricsParser.MAX_CHARACTERS + 1)))
        assertNull(parse(List(LyricsParser.MAX_LINES + 1) { "a" }.joinToString("\n")))
        assertNull(parse("[00:01]".repeat(LyricsParser.MAX_LINES + 1) + "Words"))
        assertNotNull(parse("a".repeat(LyricsParser.MAX_CHARACTERS)))
        assertNotNull(parse(List(LyricsParser.MAX_LINES) { "a" }.joinToString("\n")))
    }
}
