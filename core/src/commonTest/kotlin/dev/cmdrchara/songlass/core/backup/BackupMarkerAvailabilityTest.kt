package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.playback.MarkerBinding
import dev.cmdrchara.songlass.core.playback.SavedProgress
import dev.cmdrchara.songlass.core.playback.TrackBookmark
import dev.cmdrchara.songlass.core.playback.TrackMarkers
import dev.cmdrchara.songlass.core.playback.TrackMarkersCodec
import dev.cmdrchara.songlass.core.playback.TrackSignature
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupMarkerAvailabilityTest {
    private val original = song("11", "Music/Podcasts/Chapter.flac", "Chapter", "Narrator",
        "Podcast", durationMs = 1_800_000)

    @Test
    fun legacySourceMismatchReportsRetainedMarkersWithoutChangingMatchedFavorites() {
        val source = sourceStore(MarkerBinding.Legacy)
        val replacement = original.copy(folder = "Different recording with identical tags")
        val text = DataBackup(source).export(listOf(replacement)).text
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target)
        val preview = backup.preview(text, listOf(replacement))

        assertEquals(BackupTrackSummary(unchanged = 1, remapped = 0, missing = 0, conflicting = 0), preview.tracks)
        assertRetainedUnavailable(preview)
        assertTrue(target.snapshot().isEmpty(), "preview must not write")
        backup.applyConfirmed(preview, backup.preview(text, listOf(replacement)))
        assertEquals(original.id, target.getString("favorites"))
        val markers = TrackMarkers(target).apply { setLibrary(listOf(replacement), stableIds = true) }
        assertEquals(MarkerBinding.Unresolved, markers.state.value.bookmarks.single().track.binding)
        assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
    }

    @Test
    fun alreadyUnresolvedMarkersStayVisibleInPreviewEvenWhenTheirSongReferenceMatches() {
        val source = sourceStore(MarkerBinding.Unresolved)
        val text = DataBackup(source).export(listOf(original)).text
        val preview = DataBackup(InMemoryKeyValueStore()).preview(text, listOf(original))

        // The favorite supplies the valid source reference. Inert markers must not borrow it.
        assertEquals(1, preview.tracks.unchanged)
        assertEquals(0, preview.tracks.conflicting)
        assertTrue(preview.unresolved.isEmpty())
        assertRetainedUnavailable(preview)
        assertEquals(MarkerBinding.Unresolved, TrackMarkersCodec.decodeBookmarks(
            preview.entries[TrackMarkersCodec.BOOKMARKS_KEY]).single().track.binding)
    }

    @Test
    fun aMissingCanonicalSourceIsCountedFromItsRestoredLegacyBinding() {
        val text = DataBackup(sourceStore(MarkerBinding.Canonical)).export(listOf(original)).text
        val preview = DataBackup(InMemoryKeyValueStore()).preview(text, emptyList())

        assertEquals(1, preview.tracks.missing)
        assertRetainedUnavailable(preview)
        assertEquals(MarkerBinding.Legacy, TrackMarkersCodec.decodeProgress(
            preview.entries[TrackMarkersCodec.PROGRESS_KEY]).single().track.binding)
    }

    @Test
    fun validatedMatchesHaveNoUnavailableMarkersIncludingAfterRemapping() {
        for (binding in listOf(MarkerBinding.Legacy, MarkerBinding.Canonical)) {
            val text = DataBackup(sourceStore(binding)).export(listOf(original)).text
            val moved = original.copy(id = "37", uri = "content://media/external/audio/media/37")
            val preview = DataBackup(InMemoryKeyValueStore()).preview(text, listOf(moved))
            assertEquals(1, preview.tracks.remapped)
            assertEquals(0, preview.unavailableTrackProgress)
            assertEquals(0, preview.unavailableBookmarks)
            assertEquals(1, preview.counts.trackProgress)
            assertEquals(1, preview.counts.bookmarks)
            assertEquals(1, preview.counts.favorites)
            assertEquals(0, preview.droppedReferences)
            assertEquals(moved.id, preview.entries["favorites"])
        }
    }

    private fun sourceStore(binding: MarkerBinding): InMemoryKeyValueStore {
        val signature = TrackSignature.of(original).copy(binding = binding)
        return InMemoryKeyValueStore().apply {
            putString("favorites", original.id)
            putString(TrackMarkersCodec.PROGRESS_KEY, TrackMarkersCodec.encodeProgress(
                listOf(SavedProgress(signature, 340_000))))
            putString(TrackMarkersCodec.BOOKMARKS_KEY, TrackMarkersCodec.encodeBookmarks(
                listOf(TrackBookmark("chapter", signature, 350_000, "Chapter"))))
        }
    }

    private fun assertRetainedUnavailable(preview: BackupPreview) {
        assertEquals(1, preview.counts.trackProgress)
        assertEquals(1, preview.counts.bookmarks)
        assertEquals(1, preview.counts.favorites)
        assertEquals(1, preview.unavailableTrackProgress)
        assertEquals(1, preview.unavailableBookmarks)
        assertEquals(0, preview.droppedReferences, "retained unavailable markers are not dropped references")
        assertEquals(original.id, preview.entries["favorites"])
    }
}
