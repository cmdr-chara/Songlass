package dev.cmdrchara.songlass.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackAvailabilityTest {
    private val original = Track(
        id = "17", title = "Original title", uri = "content://media/external/audio/media/17",
        artist = "Artist", album = "Album", durationMs = 180_000,
        folder = "Music/Album", fileName = "01 Original.flac", dateAddedMs = 1_000,
    )

    private fun reindexed(id: String = "18") = original.copy(
        id = id, uri = "content://media/external/audio/media/$id", dateAddedMs = 2_000,
    )

    @Test
    fun aStableFileAcceptsRealTagAndArtworkEdits() {
        val edited = original.copy(
            title = "Corrected title", artist = "Corrected artist", album = "Corrected album",
            albumArtist = "Album artist", trackNumber = 3, genre = "Jazz", artworkRevision = 4,
        )

        assertEquals(edited, TrackAvailability(listOf(edited)).resolve(original))
    }

    @Test
    fun aConflictingReusedIdentityCannotFallBackToAnOtherwiseMatchingPath() {
        val recovered = reindexed()
        val conflicting = listOf(
            original.copy(folder = "Other", fileName = "Different.flac"),
            original.copy(dateAddedMs = 3_000),
            original.copy(durationMs = original.durationMs + 1_001),
        )

        for (replacement in conflicting) {
            assertNull(
                TrackAvailability(listOf(replacement, recovered)).resolve(original),
                "A reused identity must not be bypassed through a second path match: $replacement",
            )
        }
    }

    @Test
    fun duplicateExactIdentitiesAreAmbiguousRegardlessOfInputOrder() {
        val conflicting = original.copy(fileName = "Another.flac")

        for (scan in listOf(listOf(original, conflicting), listOf(conflicting, original), listOf(original, original))) {
            assertNull(TrackAvailability(scan).resolve(original))
        }
    }

    @Test
    fun aUniqueUnchangedRecordingAtTheSamePathCanAcquireANewIdAndUri() {
        val recovered = reindexed().copy(durationMs = original.durationMs + 1_000)

        assertEquals(recovered, TrackAvailability(listOf(recovered)).resolve(original))
    }

    @Test
    fun aRelativePathCannotChooseBetweenMultipleReindexedFiles() {
        val one = reindexed("18")
        val two = reindexed("19")

        for (scan in listOf(listOf(one, two), listOf(two, one), listOf(one, two.copy(title = "Other recording")))) {
            assertNull(TrackAvailability(scan).resolve(original))
        }
    }

    @Test
    fun reindexingRequiresTheOriginalRecordingFactsAndLocation() {
        val recovered = reindexed()
        val incompatible = listOf(
            recovered.copy(title = "Other title"),
            recovered.copy(artist = null),
            recovered.copy(album = "Other album"),
            recovered.copy(durationMs = original.durationMs + 1_001),
            recovered.copy(durationMs = 0),
            recovered.copy(folder = "Other"),
        )

        for (candidate in incompatible) {
            assertNull(TrackAvailability(listOf(candidate)).resolve(original), "Unsafe path match: $candidate")
        }
        assertNull(TrackAvailability(emptyList()).resolve(original))
    }

    @Test
    fun pathlessContentUrisRequireTheExistingIdentityAndRecordingFacts() {
        val pathless = original.copy(folder = null, fileName = null)
        val artworkEdit = pathless.copy(artworkRevision = 5)

        assertEquals(artworkEdit, TrackAvailability(listOf(artworkEdit)).resolve(pathless))
        assertNull(TrackAvailability(listOf(pathless.copy(title = "Another recording"))).resolve(pathless))
        assertNull(TrackAvailability(listOf(reindexed().copy(folder = null, fileName = null))).resolve(pathless))
    }

    @Test
    fun anExactSafeIdentityStillDisambiguatesEqualRelativePaths() {
        val anotherVolume = reindexed("19").copy(uri = "content://media/another-volume/audio/media/19")
        val edited = original.copy(title = "Corrected title")

        assertEquals(edited, TrackAvailability(listOf(anotherVolume, edited)).resolve(original))
    }
}
