package dev.cmdrchara.songlass.core.model

import kotlin.test.*

class EditableTagsTest {
    @kotlin.test.Test
    fun explicitArtistCreditsChangeOnlyWithArtist() {
        val original = EditableTags("Title", artist = "Old artist")
        kotlin.test.assertNull(original.copy(title = "New title").artistCreditsChangedFrom(original))
        kotlin.test.assertEquals(listOf("One", "Two"), original.copy(artist = "One; Two").artistCreditsChangedFrom(original))
        kotlin.test.assertEquals(emptyList(), original.copy(artist = "").artistCreditsChangedFrom(original))
    }

    @Test fun changedFieldsDoNotRewriteUnrelatedOrUnusualOriginalTags() {
        val original = EditableTags("Song", artist = "Artist", date = "Summer 1995", trackNumber = "A1")
        val edited = original.copy(artist = "New Artist", album = " New Album ")
        assertTrue(edited.isValidComparedTo(original))
        assertEquals(mapOf("ARTIST" to "New Artist", "ALBUM" to "New Album"), edited.changesFrom(original))
        assertEquals(mapOf("ARTIST" to ""), original.copy(artist = "").changesFrom(original))
    }
    @Test fun rejectsInvalidEditsBeforeOpeningAFileForWriting() {
        val original = EditableTags("Song")
        assertFalse(original.copy(title = " ").isValidComparedTo(original))
        assertFalse(original.copy(date = "2025-02-29").isValidComparedTo(original))
        assertFalse(original.copy(trackNumber = "0/12").isValidComparedTo(original))
        assertFalse(original.copy(discNumber = "-1").isValidComparedTo(original))
        assertTrue(original.copy(date = "2024-02-29", trackNumber = "3/12", discNumber = "1").isValidComparedTo(original))
        assertFalse(original.copy(artist = "A\u0000B").isValidComparedTo(original))
    }
}
