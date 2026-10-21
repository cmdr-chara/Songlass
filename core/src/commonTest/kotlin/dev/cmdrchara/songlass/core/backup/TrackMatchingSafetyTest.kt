package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackMatchingSafetyTest {
    private val original = song("7", "Music/album/recording.flac", title = "Original", durationMs = 180_000)

    private fun resolve(source: Track = original, vararg current: Track): Resolution =
        TrackMatching.resolve(listOf(source.id), mapOf(source.id to sourceTrackOf(source)), current.toList()).getValue(source.id)

    private fun atNewId(track: Track, id: String = "42"): Track =
        track.copy(id = id, uri = "content://media/external/audio/media/$id")

    @Test
    fun sameIdUriAndPathCannotOverrideContradictoryRecordingFacts() {
        val replacements = listOf(
            original.copy(title = "Another recording"),
            original.copy(artist = "Another artist"),
            original.copy(album = "Another album"),
            original.copy(artist = null),
            original.copy(album = null),
            original.copy(durationMs = 181_001),
            original.copy(durationMs = 0),
            original.copy(durationMs = -1),
        )
        for (replacement in replacements) {
            assertEquals(Resolution.Conflict, resolve(current = arrayOf(replacement)), "Unsafe same-location match: $replacement")
        }
    }

    @Test
    fun aUniquePathToANewIdStillRequiresTheSameRecordingFacts() {
        val replacements = listOf(
            original.copy(title = "Another recording"),
            original.copy(artist = "Another artist"),
            original.copy(album = "Another album"),
            original.copy(durationMs = 181_001),
            original.copy(durationMs = 0),
        )
        for (replacement in replacements) {
            assertEquals(Resolution.Missing, resolve(current = arrayOf(atNewId(replacement))))
        }
    }

    @Test
    fun aKnownDurationAndNonblankTitleAreRequiredWhileAbsentOptionalTagsSupplyNoEvidence() {
        val unknownDurations = listOf(0L, -1L)
        for (duration in unknownDurations) {
            val source = original.copy(durationMs = duration)
            assertEquals(Resolution.Conflict, resolve(source, original))
            assertEquals(Resolution.Missing, resolve(source, atNewId(original)))
        }
        for (title in listOf("", " ")) {
            val source = original.copy(title = title)
            assertEquals(Resolution.Conflict, resolve(source, source))
            assertEquals(Resolution.Missing, resolve(source, atNewId(source)))
        }
        val withoutOptionalTags = original.copy(artist = null, album = null)
        val reindexed = atNewId(withoutOptionalTags)
        assertEquals(Resolution.Moved(reindexed), resolve(withoutOptionalTags, reindexed))
        assertEquals(Resolution.Missing, resolve(withoutOptionalTags.copy(durationMs = 0), reindexed))
    }

    @Test
    fun knownRecordingFactsAllowReindexAndSameIdRenameWithinDurationTolerance() {
        for (duration in listOf(179_000L, 180_000L, 181_000L)) {
            val reindexed = atNewId(original.copy(durationMs = duration))
            assertEquals(Resolution.Moved(reindexed), resolve(current = arrayOf(reindexed)))
            val renamed = original.copy(folder = "Music/Renamed", durationMs = duration)
            assertEquals(Resolution.Same, resolve(current = arrayOf(renamed)))
        }
        // URIs are platform locations, and may legitimately differ on the target device.
        val anotherPlatformUri = original.copy(uri = "file:///destination/recording.flac")
        assertEquals(Resolution.Same, resolve(current = arrayOf(anotherPlatformUri)))
    }

    @Test
    fun aReusedIdCannotReacceptItsConflictingFileButCanMoveToADistinctVerifiedTarget() {
        val impostor = original.copy(title = "Replacement")
        assertEquals(Resolution.Conflict, resolve(current = arrayOf(impostor)))
        val reindexed = atNewId(original)
        // Even the verified candidate must have a unique path: two equal paths cannot choose.
        assertEquals(Resolution.Conflict, resolve(current = arrayOf(impostor, reindexed)))
        val relocatedImpostor = impostor.copy(folder = "Music/Other")
        assertEquals(Resolution.Moved(reindexed), resolve(current = arrayOf(relocatedImpostor, reindexed)))
        assertEquals(Resolution.Moved(reindexed), resolve(current = arrayOf(reindexed, relocatedImpostor)))
    }

    @Test
    fun duplicateCurrentIdsOrPathsNeverPickTheFirstOrLastCandidate() {
        val conflictingDuplicate = original.copy(title = "Other", fileName = "other.flac")
        for (scan in listOf(listOf(original, conflictingDuplicate), listOf(conflictingDuplicate, original))) {
            assertEquals(Resolution.Conflict, resolve(current = scan.toTypedArray()))
        }
        val reindexed = atNewId(original)
        val duplicatePath = atNewId(original, "43")
        assertEquals(Resolution.Missing, resolve(current = arrayOf(reindexed, duplicatePath)))
        // Different paths do not excuse a target ID that identifies two current rows.
        val duplicateTargetId = reindexed.copy(fileName = "elsewhere.flac")
        assertEquals(Resolution.Missing, resolve(current = arrayOf(reindexed, duplicateTargetId)))
    }

    @Test
    fun repeatingAReferencedIdDoesNotInventCompetingOwners() {
        val reindexed = atNewId(original)
        val result = TrackMatching.resolve(listOf(original.id, original.id),
            mapOf(original.id to sourceTrackOf(original)), listOf(reindexed))
        assertEquals(mapOf(original.id to Resolution.Moved(reindexed)), result)
    }

    @Test
    fun unknownDurationIsReportedBeforeAnOccupiedReferenceCanBeRestored() {
        val unknown = original.copy(durationMs = 0)
        val source = InMemoryKeyValueStore()
        Favorites(source).toggle(unknown.id)
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target)
        val preview = backup.preview(DataBackup(source).export(listOf(unknown)).text, listOf(unknown))
        assertEquals(0, preview.tracks.unchanged)
        assertEquals(1, preview.tracks.conflicting)
        assertEquals(1, preview.droppedReferences)
        assertEquals(UnresolvedKind.Conflict, preview.unresolved.single().kind)
        assertTrue(target.snapshot().isEmpty(), "preview cannot mutate stored data")
        backup.apply(preview)
        assertTrue(Favorites(target).ids.value.isEmpty())
    }

    @Test
    fun actualBackupDoesNotTransferFavoritesOrHistoricalCountsToAReplacementAtTheOldPath() {
        val source = InMemoryKeyValueStore()
        Favorites(source).toggle(original.id)
        ListeningHistory(source) { NOW }.recordPlay(original.id, null)
        val text = DataBackup(source).export(listOf(original)).text

        for (idReused in listOf(false, true)) {
            val replacement = original.copy(title = "Replacement").let { if (idReused) it else atNewId(it) }
            val target = InMemoryKeyValueStore()
            val backup = DataBackup(target)
            val preview = backup.preview(text, listOf(replacement))
            assertEquals(0, preview.tracks.remapped)
            assertEquals(if (idReused) 1 else 0, preview.tracks.conflicting)
            assertEquals(if (idReused) 0 else 1, preview.tracks.missing)
            backup.apply(preview)
            val favorites = Favorites(target).ids.value
            val counts = ListeningHistory(target) { NOW }.state.value.playCounts
            if (idReused) {
                assertTrue(favorites.isEmpty())
                assertTrue(counts.isEmpty())
            } else {
                assertEquals(listOf(original.id), favorites)
                assertEquals(mapOf(original.id to 1), counts)
                assertTrue(replacement.id !in favorites && replacement.id !in counts)
            }
        }
    }
}
