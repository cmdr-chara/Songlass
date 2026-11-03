package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PossibleDuplicatesTest {
    private fun track(id: String, duration: Long = 180_000) = Track(id, "A Song", "file://$id", artist = "An Artist", durationMs = duration)

    @Test fun matchesMetadataAcrossAlbumsAndFormatsWithoutTouchingInputs() {
        val original = track("a").copy(album = "Album", fileName = "song.flac")
        val copy = track("b", 181_000).copy(title = "  A   SONG ", artist = "an artist", album = "Compilation", fileName = "song.mp3")
        val input = listOf(original, copy, track("other").copy(title = "Other"))
        assertEquals(listOf(listOf(original, copy)), PossibleDuplicates.find(input))
        assertEquals(3, input.size)
        assertEquals(listOf(listOf(original, copy)), PossibleDuplicates.find(input.reversed()))
    }

    @Test fun excludesUnknownMetadataVersionsCueSegmentsAndRepeatedReferences() {
        val original = track("a")
        val nonMatches = listOf(original, original, original.copy(id = "alias"),
            track("live").copy(title = "A Song (Live)"), track("unknown").copy(artist = null),
            track("blank").copy(title = " "), track("zero", 0), track("different").copy(artist = "Other Artist"),
            track("cue").copy(clipStartMs = 1_000, clipEndMs = 181_000, cueSourceId = "source"))
        assertTrue(PossibleDuplicates.find(nonMatches).isEmpty())
    }

    @Test fun durationToleranceDoesNotChainAndArtistOrderIsIrrelevant() {
        val first = track("a").copy(artists = listOf("One", "Two"))
        val second = track("b", 182_000).copy(artists = listOf("two", "one"))
        val third = track("c", 184_000).copy(artists = listOf("One", "Two"))
        assertEquals(listOf(listOf(first, second)), PossibleDuplicates.find(listOf(third, second, first)))
        assertTrue(PossibleDuplicates.find(listOf(track("a"), track("b", Long.MAX_VALUE))).isEmpty())
    }
}
