package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BackupConfirmationTest {
    private val original = song("old", path = "Music/A.flac", title = "A")

    @Test fun newlyConflictingFileCannotDropReferencesAfterTheReviewedPreview() {
        val target = ObservedStore()
        val backup = DataBackup(target)
        val text = document(original)
        val reviewed = backup.preview(text, listOf(original))
        val replacement = song("old", path = "Music/Other.flac", title = "Other", artist = "Other")
        val current = backup.preview(text, listOf(replacement))
        assertEquals(1, reviewed.counts.favorites)
        assertEquals(1, current.tracks.conflicting)
        assertEquals(0, current.counts.favorites)
        rejectsWithoutStoreAccess(backup, target, reviewed, current)
    }

    @Test fun equalCountsStillRejectADifferentRemappingDestination() {
        val target = ObservedStore()
        val backup = DataBackup(target)
        val text = document(original)
        val first = original.copy(id = "first", uri = "content://first")
        val second = original.copy(id = "second", uri = "content://second")
        val reviewed = backup.preview(text, listOf(first))
        val current = backup.preview(text, listOf(second))
        assertEquals(reviewed.counts, current.counts)
        assertEquals(reviewed.tracks, current.tracks)
        assertEquals(reviewed.unresolved, current.unresolved)
        assertEquals("first", reviewed.entries["favorites"])
        assertEquals("second", current.entries["favorites"])
        rejectsWithoutStoreAccess(backup, target, reviewed, current)
    }

    @Test fun aPreviouslyMissingFileNeedsReviewEvenWhenTheWrittenIdsAreUnchanged() {
        val target = ObservedStore()
        val backup = DataBackup(target)
        val text = document(original)
        val reviewed = backup.preview(text, emptyList())
        val current = backup.preview(text, listOf(original))
        assertEquals(reviewed.entries, current.entries)
        assertEquals(reviewed.counts, current.counts)
        assertEquals(1, reviewed.tracks.missing)
        assertEquals(1, current.tracks.unchanged)
        rejectsWithoutStoreAccess(backup, target, reviewed, current)
    }

    @Test fun unrelatedLibraryGrowthAndReorderingKeepTheConfirmedPlanValid() {
        val target = ObservedStore()
        val backup = DataBackup(target)
        val text = document(original)
        val reviewed = backup.preview(text, listOf(original))
        val current = backup.preview(text, listOf(song("unrelated"), original))
        assertFalse(reviewed.libraryTrackCount == current.libraryTrackCount)
        assertTrue(reviewed.sameRestorePlanAs(current))
        backup.applyConfirmed(reviewed, current)
        assertEquals(1, target.snapshots)
        assertEquals(1, target.replacements)
        assertEquals(0, target.individualWrites)
        assertEquals(mapOf("favorites" to "old", "outside.backup" to "keep"), target.raw())
    }

    @Test fun anotherBackupCreationOrSourceIdentityCannotReuseAnEarlierConfirmation() {
        val target = ObservedStore()
        val backup = DataBackup(target)
        val reviewed = backup.preview(document(original), listOf(original))
        val differentTime = backup.preview(document(original, created = NOW + 1), listOf(original))
        assertEquals(reviewed.entries, differentTime.entries)
        rejectsWithoutStoreAccess(backup, target, reviewed, differentTime)

        // Cross-device URIs may differ while still resolving to the same file. Even when the
        // resulting write plan is identical, a different source document needs its own review.
        val differentSource = original.copy(uri = "content://another-device/source")
        val differentIdentity = backup.preview(document(differentSource), listOf(original))
        assertEquals(reviewed.entries, differentIdentity.entries)
        assertEquals(reviewed.tracks, differentIdentity.tracks)
        rejectsWithoutStoreAccess(backup, target, reviewed, differentIdentity)
    }

    @Test fun revisedPreviewCanBeConfirmedSeparatelyWithoutReusingTheOldDecision() {
        val target = ObservedStore()
        val backup = DataBackup(target)
        val text = document(original)
        val reviewed = backup.preview(text, listOf(original))
        val changed = backup.preview(text, emptyList())
        val rejected = assertFailsWith<BackupPreviewChangedException> { backup.applyConfirmed(reviewed, changed) }
        assertEquals(0, target.snapshots)
        assertEquals(0, target.replacements)
        val latest = backup.preview(text, emptyList())
        backup.applyConfirmed(rejected.preview, latest)
        assertEquals(1, target.replacements)
        assertEquals("old", target.raw()["favorites"])
    }

    private fun document(track: Track, created: Long = NOW): String =
        DataBackup(InMemoryKeyValueStore().apply { putString("favorites", track.id) }) { created }.export(listOf(track)).text

    private fun rejectsWithoutStoreAccess(backup: DataBackup, target: ObservedStore, reviewed: BackupPreview, current: BackupPreview) {
        val before = target.raw()
        assertFalse(reviewed.sameRestorePlanAs(current))
        val error = assertFailsWith<BackupPreviewChangedException> { backup.applyConfirmed(reviewed, current) }
        assertSame(current, error.preview)
        assertEquals(0, target.snapshots, "changed confirmation must not even snapshot the target store")
        assertEquals(0, target.replacements)
        assertEquals(0, target.individualWrites)
        assertEquals(before, target.raw())
    }

    private class ObservedStore : KeyValueStore {
        private val backing = InMemoryKeyValueStore().apply {
            putString("favorites", "existing")
            putString("settings.appearance", "dark")
            putString("outside.backup", "keep")
        }
        var snapshots = 0
        var replacements = 0
        var individualWrites = 0
        override fun getString(key: String): String? = backing.getString(key)
        override fun putString(key: String, value: String?) { individualWrites++; backing.putString(key, value) }
        override fun snapshot(): Map<String, String> { snapshots++; return backing.snapshot() }
        override fun replace(removing: Set<String>, writing: Map<String, String>) { replacements++; backing.replace(removing, writing) }
        fun raw(): Map<String, String> = backing.snapshot()
    }
}
