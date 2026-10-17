package dev.cmdrchara.songlass.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsTest {
    private fun timed(vararg times: Long, text: String = "Words") = Lyrics(
        lines = times.map { LyricsLine(it, text) },
        source = LyricsOrigin.File,
    )

    @Test
    fun revealFinishesBeforeTheFollowingTimestamp() {
        val lyrics = timed(1_000, 3_000, 5_000)
        assertEquals(150f / 1_400f, lyrics.lineProgressAt(0, 1_000, 6_000), 0.0001f)
        assertEquals(0.5f, lyrics.lineProgressAt(0, 1_550, 6_000))
        assertEquals(1f, lyrics.lineProgressAt(0, 2_250, 6_000))
        assertEquals(1f, lyrics.lineProgressAt(0, 3_000, 6_000))
        assertEquals(1f, lyrics.lineProgressAt(0, Long.MAX_VALUE, 6_000))
        // A known next timestamp determines the interval, regardless of duration metadata.
        assertEquals(0.5f, lyrics.lineProgressAt(0, 1_550, 0))
    }

    @Test
    fun beforeTheLineBeginsProgressIsZeroDespiteTheLead() {
        val lyrics = timed(1_000, 3_000)
        assertEquals(0f, lyrics.lineProgressAt(0, Long.MIN_VALUE, 5_000))
        assertEquals(0f, lyrics.lineProgressAt(0, 900, 5_000))
        assertEquals(0f, lyrics.lineProgressAt(0, 999, 5_000))
        assertTrue(lyrics.lineProgressAt(0, 1_000, 5_000) > 0f)
    }

    @Test
    fun longInstrumentalPauseDoesNotStretchAShortLyric() {
        val lyrics = timed(1_000, 61_000, text = "Short lyric")
        assertEquals(0.5f, lyrics.lineProgressAt(0, 1_850, 65_000))
        assertEquals(1f, lyrics.lineProgressAt(0, 2_850, 65_000))
        assertEquals(1f, lyrics.lineProgressAt(0, 60_000, 65_000))
    }

    @Test
    fun phraseCapCountsWhitespaceWordsAndAllowsLongerUnspacedText() {
        val words = timed(1_000, 31_000, text = "One\ttwo\nthree\u2003four")
        assertEquals(0.5f, words.lineProgressAt(0, 1_950, 40_000))
        assertEquals(1f, words.lineProgressAt(0, 3_050, 40_000))
        val unspaced = timed(1_000, 31_000, text = "歌".repeat(20))
        assertEquals(0.5f, unspaced.lineProgressAt(0, 2_650, 40_000))
        assertEquals(1f, unspaced.lineProgressAt(0, 4_450, 40_000))
    }

    @Test
    fun progressIsMonotonicAndSeekingBackwardsImmediatelyRewindsIt() {
        val lyrics = timed(1_000, 5_000)
        val progress = listOf(0L, 999L, 1_000L, 1_350L, 1_850L, 2_350L, 2_850L, 5_000L)
            .map { lyrics.lineProgressAt(0, it, 8_000) }
        assertTrue(progress.all { it.isFinite() && it in 0f..1f })
        assertTrue(progress.zipWithNext().all { (before, after) -> before <= after })
        assertEquals(0.75f, lyrics.lineProgressAt(0, 2_350, 8_000))
        assertEquals(0.25f, lyrics.lineProgressAt(0, 1_350, 8_000))
        assertEquals(0f, lyrics.lineProgressAt(0, 0, 8_000))
    }

    @Test
    fun duplicateTimestampsShareTheNextStrictlyLaterEndpoint() {
        val lyrics = timed(1_000, 1_000, 1_000, 3_000)
        for (index in 0..2) {
            assertEquals(0f, lyrics.lineProgressAt(index, 999, 5_000))
            assertEquals(0.5f, lyrics.lineProgressAt(index, 1_550, 5_000))
            assertEquals(1f, lyrics.lineProgressAt(index, 2_250, 5_000))
        }
        val finalDuplicates = timed(3_000, 3_000)
        assertEquals(0.5f, finalDuplicates.lineProgressAt(0, 3_550, 5_000))
        assertEquals(0.5f, finalDuplicates.lineProgressAt(1, 3_550, 5_000))
    }

    @Test
    fun finalLineUsesTrackDurationOrAnEstimatedPhraseWindow() {
        val lyrics = timed(1_000, 3_000)
        assertEquals(0f, lyrics.lineProgressAt(1, 2_999, 5_000))
        assertEquals(0.5f, lyrics.lineProgressAt(1, 3_550, 5_000))
        assertEquals(1f, lyrics.lineProgressAt(1, 4_250, 5_000))
        for (unknownDuration in listOf(-1L, 0L, 2_000L, 3_000L)) {
            assertEquals(0f, lyrics.lineProgressAt(1, 2_999, unknownDuration))
            assertEquals(0.075f, lyrics.lineProgressAt(1, 3_000, unknownDuration))
            assertEquals(0.5f, lyrics.lineProgressAt(1, 3_850, unknownDuration))
            assertEquals(1f, lyrics.lineProgressAt(1, 4_850, unknownDuration))
        }
    }

    @Test
    fun untimedEmptyAndInvalidLinesHaveNoProgress() {
        val plain = Lyrics(listOf(LyricsLine(null, "Words")), LyricsOrigin.Embedded)
        val mixed = Lyrics(listOf(LyricsLine(0, "Timed"), LyricsLine(null, "Plain")), LyricsOrigin.File)
        val instrumental = Lyrics(emptyList(), LyricsOrigin.LrcLib, instrumental = true)
        assertEquals(0f, plain.lineProgressAt(0, 1_000, 5_000))
        assertEquals(0f, mixed.lineProgressAt(0, 1_000, 5_000))
        assertEquals(0f, instrumental.lineProgressAt(0, 1_000, 5_000))
        assertEquals(0f, timed(0).lineProgressAt(-1, 1_000, 5_000))
        assertEquals(0f, timed(0).lineProgressAt(1, 1_000, 5_000))
    }

    @Test
    fun timestampArithmeticAvoidsOverflowAndKeepsPrecisionNearLongLimits() {
        val wide = timed(Long.MIN_VALUE, Long.MAX_VALUE)
        assertEquals(0.075f, wide.lineProgressAt(0, Long.MIN_VALUE, Long.MAX_VALUE))
        assertEquals(1f, wide.lineProgressAt(0, 0, Long.MAX_VALUE))
        assertEquals(1f, wide.lineProgressAt(0, Long.MAX_VALUE, Long.MAX_VALUE))
        val nearMaximum = timed(Long.MAX_VALUE - 2_000, Long.MAX_VALUE)
        assertEquals(0.5f, nearMaximum.lineProgressAt(0, Long.MAX_VALUE - 1_450, Long.MAX_VALUE))
        val nearMinimum = timed(Long.MIN_VALUE, Long.MIN_VALUE + 2_000)
        assertEquals(0.5f, nearMinimum.lineProgressAt(0, Long.MIN_VALUE + 550, Long.MAX_VALUE))
        val tiny = timed(Long.MAX_VALUE - 1, Long.MAX_VALUE)
        assertEquals(0f, tiny.lineProgressAt(0, Long.MAX_VALUE - 2, Long.MAX_VALUE))
        assertEquals(1f, tiny.lineProgressAt(0, Long.MAX_VALUE - 1, Long.MAX_VALUE))
        val final = timed(Long.MAX_VALUE)
        assertEquals(0.075f, final.lineProgressAt(0, Long.MAX_VALUE, 0))
    }

    @Test
    fun timedBlankGapsUseTheSameIntervalRules() {
        val lyrics = Lyrics(listOf(LyricsLine(1_000, ""), LyricsLine(3_000, "Words")), LyricsOrigin.File)
        assertEquals(0.5f, lyrics.lineProgressAt(0, 1_550, 5_000))
    }

    @Test
    fun scrollPreviewBeginsAtEightHundredMillisecondsWithoutChangingTheActiveLine() {
        val lyrics = timed(1_000, 5_000)
        assertEquals(0, lyrics.scrollLineAt(4_199, 8_000))
        assertEquals(1, lyrics.scrollLineAt(4_200, 8_000))
        assertEquals(1, lyrics.scrollLineAt(4_999, 8_000))
        assertEquals(0, lyrics.activeLineAt(4_200))
        assertEquals(1, lyrics.activeLineAt(5_000))
    }

    @Test
    fun scrollPreviewWaitsUntilTheCurrentSweepIsComplete() {
        val lyrics = timed(1_000, 3_000)
        assertTrue(lyrics.lineProgressAt(0, 2_200, 5_000) < 1f)
        assertEquals(0, lyrics.scrollLineAt(2_200, 5_000))
        assertEquals(1f, lyrics.lineProgressAt(0, 2_250, 5_000))
        assertEquals(1, lyrics.scrollLineAt(2_250, 5_000))
    }

    @Test
    fun scrollPreviewWaitsThroughALongGapUntilItsLastEightHundredMilliseconds() {
        val lyrics = timed(1_000, 61_000, text = "Short lyric")
        assertEquals(1f, lyrics.lineProgressAt(0, 2_850, 65_000))
        assertEquals(0, lyrics.scrollLineAt(2_850, 65_000))
        assertEquals(0, lyrics.scrollLineAt(60_199, 65_000))
        assertEquals(1, lyrics.scrollLineAt(60_200, 65_000))
    }

    @Test
    fun blankAndMusicOnlyMarkersNeverTriggerScrollPreview() {
        for (marker in listOf("", " \t", "♪", "♫", "···", "…", "♪ ♫ …")) {
            val currentMarker = Lyrics(
                listOf(LyricsLine(1_000, marker), LyricsLine(5_000, "Words")),
                LyricsOrigin.File,
            )
            val nextMarker = Lyrics(
                listOf(LyricsLine(1_000, "Words"), LyricsLine(5_000, marker)),
                LyricsOrigin.File,
            )
            assertEquals(0, currentMarker.scrollLineAt(4_200, 8_000))
            assertEquals(0, nextMarker.scrollLineAt(4_200, 8_000))
        }
        val wordsWithNotes = timed(1_000, 5_000, text = "♪ Sung words ♪")
        assertEquals(1, wordsWithNotes.scrollLineAt(4_200, 8_000))
    }

    @Test
    fun scrollPreviewUsesTheLastDuplicateAndChecksEveryLineInTheNextGroup() {
        val duplicates = timed(1_000, 1_000, 5_000, 5_000, 5_000)
        assertEquals(1, duplicates.activeLineAt(4_200))
        assertEquals(4, duplicates.scrollLineAt(4_200, 8_000))
        assertEquals(duplicates.activeLineAt(5_000), duplicates.scrollLineAt(4_200, 8_000))
        for (marker in listOf("", "♫")) {
            val interruptedGroup = Lyrics(
                listOf(
                    LyricsLine(1_000, "Words"),
                    LyricsLine(5_000, marker),
                    LyricsLine(5_000, "Next words"),
                ),
                LyricsOrigin.File,
            )
            assertEquals(0, interruptedGroup.scrollLineAt(4_200, 8_000))
        }
    }

    @Test
    fun scrollPreviewNeverSkipsABlankTimestampGroup() {
        val lyrics = Lyrics(
            listOf(LyricsLine(1_000, "Words"), LyricsLine(3_000, ""), LyricsLine(5_000, "Next words")),
            LyricsOrigin.File,
        )
        assertEquals(0, lyrics.scrollLineAt(2_250, 8_000))
        assertEquals(1, lyrics.scrollLineAt(4_200, 8_000))
    }

    @Test
    fun scrollPreviewRewindsOnSeekAndFallsBackBeforeFirstForPlainAndAtFinal() {
        val lyrics = timed(1_000, 5_000)
        assertEquals(1, lyrics.scrollLineAt(4_200, 8_000))
        assertEquals(0, lyrics.scrollLineAt(3_000, 8_000))
        assertEquals(-1, lyrics.scrollLineAt(999, 8_000))
        assertEquals(1, lyrics.scrollLineAt(5_000, 8_000))
        assertEquals(1, lyrics.scrollLineAt(Long.MAX_VALUE, 8_000))
        val plain = Lyrics(listOf(LyricsLine(null, "Words")), LyricsOrigin.File)
        val empty = Lyrics(emptyList(), LyricsOrigin.LrcLib, instrumental = true)
        assertEquals(-1, plain.scrollLineAt(1_000, 8_000))
        assertEquals(-1, empty.scrollLineAt(1_000, 8_000))
    }

    @Test
    fun scrollPreviewWindowIsSafeAtBothLongLimitsAndAcrossZero() {
        val maximum = timed(Long.MAX_VALUE - 5_000, Long.MAX_VALUE)
        assertEquals(0, maximum.scrollLineAt(Long.MAX_VALUE - 801, Long.MAX_VALUE))
        assertEquals(1, maximum.scrollLineAt(Long.MAX_VALUE - 800, Long.MAX_VALUE))
        val minimum = timed(Long.MIN_VALUE, Long.MIN_VALUE + 5_000)
        assertEquals(0, minimum.scrollLineAt(Long.MIN_VALUE + 4_199, 0))
        assertEquals(1, minimum.scrollLineAt(Long.MIN_VALUE + 4_200, 0))
        val nearMinimum = timed(Long.MIN_VALUE, Long.MIN_VALUE + 400)
        assertEquals(1, nearMinimum.scrollLineAt(Long.MIN_VALUE + 130, 0))
        val wide = timed(Long.MIN_VALUE, Long.MAX_VALUE)
        assertEquals(0, wide.scrollLineAt(Long.MIN_VALUE + 5_000, Long.MAX_VALUE))
        assertEquals(0, wide.scrollLineAt(Long.MAX_VALUE - 801, Long.MAX_VALUE))
        assertEquals(1, wide.scrollLineAt(Long.MAX_VALUE - 800, Long.MAX_VALUE))
    }
}
