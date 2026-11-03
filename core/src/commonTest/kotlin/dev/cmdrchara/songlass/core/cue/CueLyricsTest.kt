package dev.cmdrchara.songlass.core.cue

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.lyrics.*
import kotlin.test.*

class CueLyricsTest {
    @Test fun onlyLocalWholeFileLyricsAreShiftedAndClipped() {
        val track = Track("cue", "Cut", "file:///album.flac", clipStartMs = 1000, clipEndMs = 3000, durationMs = 2000)
        val lyrics = Lyrics(listOf(
            LyricsLine(500, "Before", listOf(LyricsWord(0, 6, 500, 1300))),
            LyricsLine(2000, "Inside", listOf(LyricsWord(0, 6, 2000, 3500))),
            LyricsLine(3000, "After")), LyricsOrigin.Embedded)
        val shifted = assertNotNull(lyrics.forSegment(track))
        assertEquals(listOf(0L, 1000L), shifted.lines.map { it.timeMs })
        assertEquals(300L, shifted.lines[0].words.single().endMs)
        assertEquals(2000L, shifted.lines[1].words.single().endMs)
        assertNull(Lyrics(listOf(LyricsLine(null, "Whole album text")), LyricsOrigin.File).forSegment(track))
    }
}
