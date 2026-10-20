package dev.cmdrchara.songlass.core.lyrics

import kotlin.test.*

class LyricsSharingTest {
    private fun lyrics(vararg text: String) = Lyrics(text.map { LyricsLine(null, it) }, LyricsOrigin.File)

    @Test fun excerptFollowsSongOrderAndDeselectionWorks() {
        val lyrics = lyrics("A", "B", "C")
        val selected = LyricsSharing.toggle(lyrics, setOf(2), 0)
        assertEquals(listOf("A", "C"), LyricsSharing.excerpt(lyrics, selected))
        assertEquals(setOf(0), LyricsSharing.toggle(lyrics, selected, 2))
    }
    @Test fun fifthLineAndOversizeAreRefusedWithoutTruncation() {
        val lyrics = lyrics("A", "B", "C", "D", "E", "x".repeat(241))
        assertEquals(setOf(0,1,2,3), LyricsSharing.toggle(lyrics, setOf(0,1,2,3), 4))
        assertEquals(emptySet(), LyricsSharing.toggle(lyrics, emptySet(), 5))
        assertTrue(LyricsSharing.excerpt(lyrics, setOf(0,1,2,3,4)).isEmpty())
    }
    @Test fun emptyAndStaleIndicesAreNotSelectable() {
        val lyrics = lyrics("", " ", "Valid")
        assertEquals(emptySet(), LyricsSharing.toggle(lyrics, emptySet(), 0))
        assertEquals(emptySet(), LyricsSharing.toggle(lyrics, emptySet(), 10))
        assertEquals(listOf("Valid"), LyricsSharing.excerpt(lyrics, setOf(-1, 0, 2, 30)))
    }
    @Test fun characterBudgetCountsUnicodeCodePointsAndIsInclusive() {
        val lyrics = lyrics("😀".repeat(240), "a")
        assertEquals(setOf(0), LyricsSharing.toggle(lyrics, emptySet(), 0))
        assertEquals(setOf(0), LyricsSharing.toggle(lyrics, setOf(0), 1))
    }
}
