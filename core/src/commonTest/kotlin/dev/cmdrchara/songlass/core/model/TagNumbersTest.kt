package dev.cmdrchara.songlass.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagNumbersTest {

    @Test
    fun parsesPlainAndSlashedNumbers() {
        assertEquals(3, TagNumbers.parse("3"))
        assertEquals(3, TagNumbers.parse(" 3/12 "))
    }

    @Test
    fun rejectsMissingMalformedAndZero() {
        assertNull(TagNumbers.parse(null))
        assertNull(TagNumbers.parse(""))
        assertNull(TagNumbers.parse("A1"))
        assertNull(TagNumbers.parse("0/10"))
    }

    @Test
    fun splitsLegacyTrackColumn() {
        assertEquals(2 to 5, TagNumbers.splitLegacyTrack(2005))
        assertEquals(null to 7, TagNumbers.splitLegacyTrack(7))
        assertEquals(1 to null, TagNumbers.splitLegacyTrack(1000))
        assertEquals(null to null, TagNumbers.splitLegacyTrack(0))
        assertEquals(null to null, TagNumbers.splitLegacyTrack(null))
    }
}
