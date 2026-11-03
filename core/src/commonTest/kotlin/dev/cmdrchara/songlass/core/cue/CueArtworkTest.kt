package dev.cmdrchara.songlass.core.cue

import dev.cmdrchara.songlass.core.artwork.ArtworkAvailability
import dev.cmdrchara.songlass.core.model.Track
import kotlin.test.*

class CueArtworkTest {
    @Test fun verifiedSegmentsSharePhysicalArtWithoutAllowingUnrelatedUriAliases() {
        val source = Track("physical", "Album", "content://media/1", durationMs = 10_000, fileName = "album.flac")
        val sheet = assertNotNull(CueSheets.parse("FILE \"album.flac\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nINDEX 01 00:05:00"))
        val clips = assertNotNull(CueSheets.resolve(sheet, listOf(source)))
        val good = ArtworkAvailability.Index.build(listOf(source) + clips)
        assertEquals(3, good.byId.size)
        val invalid = ArtworkAvailability.Index.build(listOf(source) + clips.map { it.copy(cueSourceId = "other") })
        assertTrue(invalid.byId.isEmpty())
        val aliases = ArtworkAvailability.Index.build(listOf(source, source.copy(id = "unrelated")))
        assertTrue(aliases.byId.isEmpty())
    }
}
