package dev.cmdrchara.songlass.core.tags

import dev.cmdrchara.songlass.core.model.Track
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagEditSelectionTest {
    private val selected = Track("local-opaque-id", "A", "content://media/external/audio/media/23",
        fileName = "song.flac", folder = "Music/Album", dateAddedMs = 12_000, durationMs = 40_000)

    @Test
    fun completedReindexAndReusedUrisCannotRebindAnOldEditorRoute() {
        val reindexed = selected.copy(uri = "content://media/external/audio/media/24")
        val replacement = selected.copy(id = "new-file-id")
        assertFalse(TagEditSelection.isCurrent(selected, listOf(reindexed, replacement)))
        assertFalse(TagEditSelection.isCurrent(selected, listOf(replacement)))
        assertFalse(TagEditSelection.isCurrent(selected, emptyList()))
        assertFalse(TagEditSelection.isCurrent(selected, listOf(selected, selected)))
        assertTrue(TagEditSelection.isCurrent(selected, listOf(selected.copy(title = "Fresh tag"))))
    }

    @Test
    fun aRecycledUriCannotIgnoreKnownPathDateOrDurationConflicts() {
        assertTrue(matches(selected))
        assertFalse(matches(selected, fileName = "another.flac"))
        assertFalse(matches(selected, folder = "Music/Other"))
        assertFalse(matches(selected, dateAddedMs = 13_000))
        assertFalse(matches(selected, durationMs = 41_001))
        assertFalse(matches(selected, uri = "content://media/external/audio/media/24"))
    }

    @Test
    fun missingPreviouslyKnownFactsDoNotAuthorizeEditingAndOpaqueAppIdsAreNotRowIds() {
        assertFalse(matches(selected, fileName = null))
        assertFalse(matches(selected, folder = null))
        assertFalse(matches(selected, dateAddedMs = null))
        assertFalse(matches(selected, durationMs = null))
        assertFalse(matches(selected, durationMs = 0))
        assertTrue(matches(selected.copy(id = "different-canonical-namespace", title = "Refreshed tag")))
    }

    @Test
    fun unknownLibraryFactsStayUnknownWhileDurationRoundingAndFolderSeparatorsAreTolerated() {
        val sparse = selected.copy(fileName = null, folder = null, dateAddedMs = null, durationMs = 0)
        assertTrue(matches(sparse, fileName = null, folder = null, dateAddedMs = null, durationMs = null))
        assertTrue(matches(selected, folder = "/Music/Album/", durationMs = 39_000))
        assertTrue(matches(selected, durationMs = 41_000))
        assertFalse(matches(selected, durationMs = Long.MIN_VALUE))
        assertFalse(matches(selected, durationMs = Long.MAX_VALUE))
    }

    private fun matches(
        track: Track,
        uri: String = selected.uri,
        fileName: String? = selected.fileName,
        folder: String? = selected.folder,
        dateAddedMs: Long? = selected.dateAddedMs,
        durationMs: Long? = selected.durationMs,
    ) = TagEditSelection.matches(track, uri, fileName, folder, dateAddedMs, durationMs)
}
