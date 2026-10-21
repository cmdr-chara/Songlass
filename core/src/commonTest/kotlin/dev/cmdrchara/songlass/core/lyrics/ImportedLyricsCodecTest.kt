package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportedLyricsCodecTest {
    private val record = ImportedLyricsRecord.bound(LyricsSelection(Track("song.1/😀", "日本:Title", "content://7",
        artist = "Artist\nName", album = null, durationMs = 123_456, folder = "Music", fileName = "a.flac"), true),
        "[offset:20]\n[00:01.123]Neon 😀\n[00:02.000]Nuvole\n")

    @Test fun unicodeSeparatorsNullTagsAndOriginalDocumentRoundTripExactly() {
        val encoded = ImportedLyricsCodec.encode(record)
        assertEquals(record, ImportedLyricsCodec.decode(encoded))
        assertEquals(encoded, ImportedLyricsCodec.encode(ImportedLyricsCodec.decode(encoded)!!))
    }

    @Test fun truncatedTrailingUnknownAndNonCanonicalFieldsCannotBecomeBindings() {
        val encoded = ImportedLyricsCodec.encode(record)
        for (value in listOf(encoded.dropLast(1), encoded + "extra", encoded.replace("LYRICS-1", "LYRICS-2"),
            encoded.replace("9:Canonical", "09:Canonical"), encoded.replace("6:123456", "2:-1"))) {
            assertNull(ImportedLyricsCodec.decode(value), value.take(40))
        }
    }

    @Test fun oversizedRecordAndLengthPrefixFailBeforeAllocatingClaimedContent() {
        assertNull(ImportedLyricsCodec.decode("SONGLASS-LYRICS-1\n2147483647:x"))
        assertNull(ImportedLyricsCodec.decode("SONGLASS-LYRICS-1\n" + "x".repeat(170_000)))
        assertNull(ImportedLyricsCodec.trackKey("x".repeat(241)))
    }

    @Test fun escapedKeysAreDistinctEvenForSeparatorsSupplementaryCharactersAndLiteralHex() {
        val first = ImportedLyricsCodec.trackKey("a.b/😀")!!
        val second = ImportedLyricsCodec.trackKey("0061.0062")!!
        assertNotEquals(first, second)
        assertTrue(ImportedLyricsCodec.isKey(first))
        assertTrue(ImportedLyricsCodec.isKey(second))
    }
}
