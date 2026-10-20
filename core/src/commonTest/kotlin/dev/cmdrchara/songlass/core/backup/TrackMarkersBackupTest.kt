package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.playback.TrackMarkers
import dev.cmdrchara.songlass.core.playback.TrackMarkersCodec
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TrackMarkersBackupTest {
    private val original = song("11", "Music/Podcasts/Chapter 1.flac", "Chapter 1", "Narrator",
        "My Podcast", durationMs = 1_800_000L)

    @Test
    fun pointsBookmarksAndOptInRoundTripWithoutChangingV1BackupContainer() {
        val store = InMemoryKeyValueStore()
        val markers = TrackMarkers(store) { NOW }.apply {
            setLibrary(listOf(original))
            checkpoint(original, 650_000L, 600_000L, true)
            add(original, 630_000L, null)
            add(original, 720_000L, "  Second chapter  ")
        }
        AppSettings(store).apply {
            setResumePerTrack(true)
            setLongTrackThreshold(300_000L)
        }
        val exported = DataBackup(store) { NOW }.export(listOf(original))
        assertTrue(exported.text.startsWith("SONGLASS-BACKUP 1\n"))
        assertEquals(1, exported.counts.trackProgress)
        assertEquals(2, exported.counts.bookmarks)
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target)
        val preview = backup.preview(exported.text, listOf(original))
        assertEquals(1, preview.counts.trackProgress)
        assertEquals(2, preview.counts.bookmarks)
        assertTrue(target.snapshot().isEmpty())
        backup.apply(preview)
        val restored = TrackMarkers(target).apply { setLibrary(listOf(original)) }
        assertEquals(markers.state.value, restored.state.value)
        assertEquals(650_000L, restored.progressFor(original, 300_000L))
        assertEquals(2, restored.bookmarksFor(original).size)
        val restoredSettings = AppSettings(target).state.value
        assertTrue(restoredSettings.resumePerTrack)
        assertEquals(300_000L, restoredSettings.longTrackThresholdMs)
        assertEquals(exported.text, DataBackup(target) { NOW }.export(listOf(original)).text)
    }

    @Test
    fun reindexRemapsOnlyUnambiguousStoragePathAndKeepsOriginalIdentity() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source) { NOW }.apply {
            setLibrary(listOf(original))
            checkpoint(original, 340_000L, 600_000L, true)
            add(original, 340_000L, "Middle")
        }
        val backupText = DataBackup(source) { NOW }.export(listOf(original)).text
        val indexed = original.copy(id = "37", uri = "content://media/external/audio/media/37")
        val destination = InMemoryKeyValueStore()
        val preview = DataBackup(destination).preview(backupText, listOf(indexed))
        assertEquals(1, preview.tracks.remapped)
        assertEquals(0, preview.droppedReferences)
        DataBackup(destination).apply(preview)
        val bookmarks = TrackMarkers(destination).apply { setLibrary(listOf(indexed)) }
        assertEquals("37", bookmarks.state.value.bookmarks.single().track.id)
        assertEquals(indexed, bookmarks.resolve(bookmarks.state.value.bookmarks.single().track))
        assertEquals(340_000L, bookmarks.progressFor(indexed, 600_000L))
        val duplicate = indexed.copy(id = "38", uri = "content://media/external/audio/media/38")
        bookmarks.setLibrary(listOf(indexed, duplicate))
        assertNull(bookmarks.resolve(bookmarks.state.value.bookmarks.single().track))
        assertNull(bookmarks.progressFor(indexed, 600_000L))
    }

    @Test
    fun missingAndRecycledIdsPreserveUnopenableRecordsRatherThanMisbinding() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source) { NOW }.apply {
            setLibrary(listOf(original))
            checkpoint(original, 340_000L, 600_000L, true)
            add(original, 340_000L, "Still here")
        }
        val text = DataBackup(source) { NOW }.export(listOf(original)).text
        val unrelated = song(original.id, "Music/Other/Not it.flac", "Wrong", "Someone else",
            "Different", durationMs = 900_000L)
        val target = InMemoryKeyValueStore()
        val preview = DataBackup(target).preview(text, listOf(unrelated))
        assertEquals(1, preview.tracks.conflicting)
        DataBackup(target).apply(preview)
        val markers = TrackMarkers(target).apply { setLibrary(listOf(unrelated)) }
        assertEquals(1, markers.state.value.bookmarks.size)
        assertEquals(1, markers.state.value.progress.size)
        assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
        assertNull(markers.progressFor(unrelated, 600_000L))
        markers.setLibrary(emptyList())
        assertEquals(1, markers.state.value.bookmarks.size)
        assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
    }

    @Test
    fun invalidMarkerRecordsAreRejectedBeforeRestoreChangesAnyKey() {
        val valid = InMemoryKeyValueStore()
        TrackMarkers(valid) { NOW }.apply {
            setLibrary(listOf(original))
            add(original, 250_000L, "One")
        }
        val encoded = requireNotNull(valid.getString(TrackMarkersCodec.BOOKMARKS_KEY))
        val corrupted = encoded.dropLast(1)
        assertFalse(TrackMarkersCodec.canonicalBookmarks(corrupted))
        val target = InMemoryKeyValueStore().apply { putString("favorites", "still-here") }
        val badFile = handBuilt(listOf(TrackMarkersCodec.BOOKMARKS_KEY to corrupted))
        assertFailsWith<BackupException> { DataBackup(target).preview(badFile, listOf(original)) }
        assertEquals("still-here", target.getString("favorites"))
    }

    @Test
    fun oldV1FilesRemainReadableAndClearAbsentMarkerKeysOnRestore() {
        val target = InMemoryKeyValueStore()
        TrackMarkers(target) { NOW }.apply {
            setLibrary(listOf(original))
            checkpoint(original, 150_000L, 600_000L, true)
            add(original, 150_000L, "Old marker")
        }
        val legacyText = DataBackup(InMemoryKeyValueStore().apply {
            putString("settings.crossfade_duration_ms", "1250")
        }) { NOW }.export(listOf(original)).text
        val preview = DataBackup(target).preview(legacyText, listOf(original))
        assertEquals(0, preview.counts.bookmarks)
        assertEquals(0, preview.counts.trackProgress)
        DataBackup(target).apply(preview)
        assertNull(target.getString(TrackMarkersCodec.BOOKMARKS_KEY))
        assertNull(target.getString(TrackMarkersCodec.PROGRESS_KEY))
        assertEquals("1250", target.getString("settings.crossfade_duration_ms"))
        assertFalse(AppSettings(target).state.value.resumePerTrack)
    }
}
