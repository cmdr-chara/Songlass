package dev.cmdrchara.songlass.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerTagsTest {
    @Test fun recognisesComposerFieldsWithoutInferringArtistOrWriterCredits() {
        listOf("TCOM", "TCM", "COMPOSER", "composers", "©wrt").forEach { assertTrue(ComposerTags.isComposerField(it)) }
        listOf(null, "ARTIST", "ALBUMARTIST", "LYRICIST", "TPE1", "unknown").forEach { assertFalse(ComposerTags.isComposerField(it)) }
    }

    @Test fun normalizesActualMultiValueCreditsConservatively() {
        assertEquals(listOf("J.S. Bach", "Lennon/McCartney", "Earth, Wind & Fire"),
            ComposerTags.names(listOf(" J.S. Bach;Lennon/McCartney", "j.s. bach\u0000Earth, Wind & Fire", "", "<unknown>")))
        assertTrue(ComposerTags.names(emptyList()).isEmpty())
    }
}
