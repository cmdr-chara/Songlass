package dev.cmdrchara.songlass.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsPresentationTest {
    private val lyrics = Lyrics(listOf(
        LyricsLine(0, "First", listOf(LyricsWord(0, 5, 0, 1_000))),
        LyricsLine(1_000, "Second", listOf(LyricsWord(0, 6, 1_000, 2_000))),
    ), LyricsOrigin.File)

    @Test fun positiveOffsetDelaysLinesAndWordsTogether() {
        val presentation = LyricsPresentation(offsetMs = 500)
        assertEquals(-1, lyrics.activeLineAt(presentation.displayPosition(499)))
        assertEquals(0, lyrics.activeLineAt(presentation.displayPosition(500)))
        assertEquals(0, lyrics.activeLineAt(presentation.displayPosition(1_499)))
        assertEquals(1, lyrics.activeLineAt(presentation.displayPosition(1_500)))
        assertEquals(.5f, lyrics.lines[1].words[0].progressAt(presentation.displayPosition(2_000)))
        assertEquals(1_500, presentation.seekPosition(1_000, 10_000))
    }

    @Test fun negativeOffsetAnticipatesAndTapUsesInverseClock() {
        val presentation = LyricsPresentation(offsetMs = -500)
        assertEquals(1, lyrics.activeLineAt(presentation.displayPosition(500)))
        assertEquals(500L, presentation.seekPosition(1_000, 10_000))
        assertEquals(0L, presentation.seekPosition(0, 10_000))
        assertEquals(900L, presentation.seekPosition(2_000, 900))
    }

    @Test fun everyAllowedOffsetRoundTripsWithoutChangingTheSource() {
        val original = lyrics.copy(lines = lyrics.lines.toList())
        for (offset in -5_000..5_000 step 50) {
            val presentation = LyricsPresentation(offsetMs = offset)
            for (media in listOf(5_000L, 12_345L, 60_000L)) {
                assertEquals(media, presentation.seekPosition(presentation.displayPosition(media), 0))
            }
        }
        assertEquals(original, lyrics)
    }

    @Test fun mediaBoundariesDoNotOverflow() {
        assertEquals(Long.MIN_VALUE, LyricsPresentation(offsetMs = 5_000).displayPosition(Long.MIN_VALUE))
        assertEquals(Long.MAX_VALUE, LyricsPresentation(offsetMs = -5_000).displayPosition(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, LyricsPresentation(offsetMs = 5_000).seekPosition(Long.MAX_VALUE, 0))
        assertEquals(0L, LyricsPresentation(offsetMs = -5_000).seekPosition(Long.MIN_VALUE, -1))
    }

    @Test fun sliderLimitsStepsAndNonFiniteInputsAreSafe() {
        assertEquals(-5_000, LyricsPresentation.offsetFromSlider(-1f))
        assertEquals(5_000, LyricsPresentation.offsetFromSlider(2f))
        assertEquals(0, LyricsPresentation.offsetFromSlider(Float.NaN))
        assertEquals(0, LyricsPresentation.offsetFromSlider(Float.POSITIVE_INFINITY))
        assertEquals(80, LyricsPresentation.textSizeFromSlider(0f))
        assertEquals(160, LyricsPresentation.textSizeFromSlider(1f))
        assertEquals(100, LyricsPresentation.textSizeFromSlider(Float.NaN))
        for (step in 0..1_000) {
            assertEquals(0, LyricsPresentation.offsetFromSlider(step / 1_000f) % 50)
            assertEquals(0, LyricsPresentation.textSizeFromSlider(step / 1_000f) % 5)
        }
    }
}
