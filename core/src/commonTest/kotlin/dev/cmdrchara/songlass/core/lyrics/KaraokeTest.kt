package dev.cmdrchara.songlass.core.lyrics

import kotlin.test.*

class KaraokeTest {
    private fun parse(text: String) = assertNotNull(LyricsParser.parse(text, LyricsOrigin.File))

    @Test fun realBoundariesAndTerminalTagArePreserved() {
        val lyrics = parse("[00:01]<00:01>Hello <00:02>world<00:04>\n[00:06]Next")
        val line = lyrics.lines.first()
        assertEquals("Hello world", line.text)
        assertEquals(listOf(LyricsWord(0, 6, 1000, 2000), LyricsWord(6, 11, 2000, 4000)), line.words)
        assertEquals(0f, line.words[1].progressAt(1999))
        assertEquals(.5f, line.words[1].progressAt(3000))
        assertEquals(1f, line.words[1].progressAt(4000))
        assertTrue(lyrics.hasWordTiming)
        assertTrue(lyrics.hasEstimatedTiming)
        assertTrue(lyrics.lineProgressAt(0, 3000, 8000) < 1f)
        assertEquals(1f, lyrics.lineProgressAt(0, 4000, 8000))
    }

    @Test fun pauseAndBackwardSeekUseTheProvidedClockWithoutPrediction() {
        val word = LyricsWord(0, 4, 1000, 5000)
        assertEquals(.75f, word.progressAt(4000))
        repeat(3) { assertEquals(.25f, word.progressAt(2000)) }
        assertEquals(0f, word.progressAt(999))
        assertEquals(1f, word.progressAt(Long.MAX_VALUE))
        val final = LyricsWord(0, 4, 1000, null)
        assertEquals(0f, final.progressAt(999))
        assertEquals(1f, final.progressAt(1000)) // No invented duration for the final word.
    }

    @Test fun unicodeRangesAndWhitespaceMatchTheDisplayedText() {
        val line = parse("[00:01] <00:01>歌😀<00:02>かな<00:03> ").lines.single()
        assertEquals("歌😀かな", line.text)
        assertEquals(listOf("歌😀", "かな"), line.words.map { line.text.substring(it.start, it.end) })
        assertEquals(listOf(0, 3), line.words.map { it.start })
    }

    @Test fun offsetAndRepeatedLinesMoveWordsWithTheirLine() {
        val lines = parse("[00:01][00:10]<00:01>A <00:02>B<00:03>\n[offset:-500]").lines
        assertEquals(listOf(1500L, 10500L), lines.map { it.timeMs })
        assertEquals(listOf(1500L, 2500L), lines[0].words.map { it.startMs })
        assertEquals(listOf(10500L, 11500L), lines[1].words.map { it.startMs })
        assertEquals(12500L, lines[1].words.last().endMs)
    }

    @Test fun invalidAndCrossingWordTimesKeepReadableFallback() {
        for (text in listOf("[00:01]<00:02>A<00:01>B", "[00:01]<00:01>A<00:05>B\n[00:04]Next")) {
            val line = parse(text).lines.first()
            assertTrue(line.words.isEmpty())
            assertEquals("AB", line.text)
        }
        val malformed = parse("[00:01]A <00:99>B").lines.single()
        assertEquals("A <00:99>B", malformed.text)
        assertTrue(malformed.words.isEmpty())
    }

    @Test fun finalWordAndDuplicateBoundariesNeverDivideByZero() {
        val line = parse("[00:01]<00:01>A<00:01>B<00:02>C").lines.single()
        assertEquals(1f, line.words[0].progressAt(1000))
        assertEquals(.5f, line.words[1].progressAt(1500))
        assertEquals(1f, line.words[2].progressAt(2000))
        assertTrue(parse("[00:01]Line only").lines.single().words.isEmpty())
    }

    @Test fun offsetClampingAndHugeTimestampsStayFinite() {
        val line = parse("[00:01]<00:01>A<00:02>B<00:03>\n[offset:${Long.MIN_VALUE}]").lines.single()
        assertEquals(Long.MAX_VALUE, line.words.first().startMs)
        assertEquals(0f, line.words.first().progressAt(0))
        assertEquals(1f, line.words.first().progressAt(Long.MAX_VALUE))
    }

    @Test fun repeatedTimestampsCannotMultiplyWordRangesWithoutBound() {
        val repeated = "[00:01]".repeat(5000) + "<00:01>A<00:02>B<00:03>C<00:04>D<00:05>E<00:06>F<00:07>"
        assertNull(LyricsParser.parse(repeated, LyricsOrigin.File))
    }

    @Test fun squareWordTagsAndSectionLabelsRemainCompatible() {
        val line = parse("[00:01][Chorus] Hi [00:02]there[00:03]").lines.single()
        assertEquals("[Chorus] Hi there", line.text)
        assertEquals(listOf(1000L, 2000L), line.words.map { it.startMs })
    }
}
