package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track
import kotlin.test.*

class WordLyricsMatchTest {
    private val track = Track("1", "Écho!", "file", artist = "Singer", album = "Record")
    private fun candidate(id: String = "a", keys: Set<String> = emptySet(), date: Long = 0) =
        WordLyricsCandidate(id, listOf("Écho!"), listOf("Singer"), listOf("Record"), keys, date)

    @Test fun normalizesOnlyCaseAndWhitespaceAndPreservesOriginal() {
        val original = candidate().copy(titles = listOf("alias", "  ÉCHO! "), artists = listOf(" SINGER "))
        val input = listOf(original)
        val snapshot = input.toList()
        assertSame(original, WordLyricsMatch.select(track, input))
        assertEquals(snapshot, input)
        assertNull(WordLyricsMatch.select(track, listOf(original.copy(titles = listOf("Echo!")))))
        assertNull(WordLyricsMatch.select(track, listOf(original.copy(titles = listOf("Écho")))))
        assertNull(WordLyricsMatch.select(track, listOf(original.copy(titles = listOf("Écho! (Live)")))))
        val spaced = track.copy(title = " A \t B ")
        assertNotNull(WordLyricsMatch.select(spaced, listOf(candidate().copy(titles = listOf("a b")))))
    }

    @Test fun requiresSongArtistAndKnownAlbumAndTitle() {
        assertNull(WordLyricsMatch.select(track.copy(title = " "), listOf(candidate())))
        assertNull(WordLyricsMatch.select(track.copy(artist = null, albumArtist = "Singer"), listOf(candidate())))
        for (invalid in listOf(candidate().copy(titles = emptyList()), candidate().copy(artists = emptyList()),
            candidate().copy(albums = emptyList()), candidate().copy(albums = listOf("Other")))) {
            assertNull(WordLyricsMatch.select(track, listOf(invalid)))
        }
        assertNotNull(WordLyricsMatch.select(track.copy(album = null), listOf(candidate().copy(albums = emptyList()))))
    }

    @Test fun artistCreditsAreAnExactSetIncludingGuests() {
        val duet = track.copy(artists = listOf("Singer", "Guest"))
        assertNull(WordLyricsMatch.select(duet, listOf(candidate())))
        val full = candidate().copy(artists = listOf(" guest ", "SINGER", "Singer"))
        assertSame(full, WordLyricsMatch.select(duet, listOf(full)))
        assertNull(WordLyricsMatch.select(track, listOf(full)))
    }

    @Test fun rejectsTruncationAndMetadataAmbiguity() {
        assertNull(WordLyricsMatch.select(track, listOf(candidate()), truncated = true))
        assertNull(WordLyricsMatch.select(track, listOf(candidate(), candidate("b"))))
        assertNull(WordLyricsMatch.select(track, listOf(candidate(keys = setOf(" ")), candidate("b", setOf(" ")))))
    }

    @Test fun sharedRecordingHistoryUsesNewestThenStableId() {
        val old = candidate("old", setOf("isrc:ABC"), 1)
        val latest = candidate("a", setOf("isrc:ABC", "platform:123"), 2)
        val tie = candidate("b", setOf("isrc:ABC"), 2)
        assertSame(latest, WordLyricsMatch.select(track, listOf(tie, old, latest)))
    }

    @Test fun transitiveRecordingIdsDoNotEstablishOneRecording() {
        assertNull(WordLyricsMatch.select(track, listOf(candidate("a", setOf("isrc:A")),
            candidate("b", setOf("isrc:A", "platform:B")), candidate("c", setOf("platform:B")))))
    }
}
