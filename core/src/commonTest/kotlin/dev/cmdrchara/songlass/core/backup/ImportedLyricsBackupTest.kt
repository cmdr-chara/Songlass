package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.lyrics.ImportedLyricsCodec
import dev.cmdrchara.songlass.core.lyrics.ImportedLyricsRecord
import dev.cmdrchara.songlass.core.lyrics.LyricsBinding
import dev.cmdrchara.songlass.core.lyrics.LyricsRepository
import dev.cmdrchara.songlass.core.lyrics.LyricsResult
import dev.cmdrchara.songlass.core.lyrics.LyricsSelection
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportedLyricsBackupTest {
    private val original = song("7", path = "Music/original.flac", title = "Original")
    private val words = "[00:01.00]Original words 😀\n[00:02.00]Another line\n"
    private fun repository(store: KeyValueStore, library: List<Track>) = LyricsRepository(store = store,
        resolveSelection = { requested -> library.singleOrNull { it.id == requested.id }?.let { LyricsSelection(it, true) } })
    private fun newStore() = InMemoryKeyValueStore().also { assertTrue(repository(it, listOf(original)).import(original, words)) }

    @Test fun legacyExportNeverBorrowsIdentityFromAReplacementAtTheSameUri() {
        val store = InMemoryKeyValueStore()
        val key = ImportedLyricsCodec.LEGACY_PREFIX + original.uri
        store.putString(key, words)
        val replacement = original.copy(title = "Replacement", fileName = "replacement.flac")
        val exported = DataBackup(store).export(listOf(replacement))
        val parsed = BackupFormat.read(exported.text)
        assertTrue(parsed.sources.isEmpty())
        assertEquals(words, parsed.entries[key])
        val preview = DataBackup(InMemoryKeyValueStore()).preview(exported.text, listOf(replacement))
        assertEquals(1, preview.counts.importedLyrics)
        assertEquals(1, preview.unavailableImportedLyrics)
        assertEquals(0, preview.droppedReferences)
        assertEquals(words, preview.entries[key])
    }

    @Test fun legacyBackupSourceThatPerfectlyMatchesTodaysFileStillDoesNotProveLyricsOwnership() = runTest {
        val key = ImportedLyricsCodec.LEGACY_PREFIX + original.uri
        val legacy = BackupFormat.write(NOW, mapOf(key to words), listOf(sourceTrackOf(original)))
        val store = InMemoryKeyValueStore()
        val backup = DataBackup(store)
        val preview = backup.preview(legacy, listOf(original))
        backup.apply(preview)
        assertEquals(1, preview.unavailableImportedLyrics)
        assertEquals(BackupTrackSummary(0, 0, 0, 0), preview.tracks)
        assertEquals(LyricsResult.NotFound, repository(store, listOf(original)).load(original))
        assertEquals(words, repository(store, listOf(original)).recoverableImports().single().text)
    }

    @Test fun provenCrossDeviceRemapHasFinalCanonicalBindingWithoutASecondReloadCommit() = runTest {
        val document = DataBackup(newStore()).export(listOf(original)).text
        val targetTrack = original.copy(id = "42", uri = "content://second-device/42")
        val backing = InMemoryKeyValueStore()
        var commits = 0
        val target = object : KeyValueStore by backing {
            override fun replace(removing: Set<String>, writing: Map<String, String>) { commits++; backing.replace(removing, writing) }
        }
        val backup = DataBackup(target)
        val preview = backup.preview(document, listOf(targetTrack))
        assertEquals(0, preview.unavailableImportedLyrics)
        assertEquals(1, preview.tracks.remapped)
        backup.applyConfirmed(preview, backup.preview(document, listOf(targetTrack)))
        val lyrics = repository(target, listOf(targetTrack))
        lyrics.reload()
        assertIs<LyricsResult.Found>(lyrics.load(targetTrack))
        assertEquals(1, commits)
        val record = ImportedLyricsCodec.decode(backing.getString(ImportedLyricsCodec.trackKey(targetTrack.id)!!)!!)!!
        assertEquals(LyricsBinding.Canonical, record.binding)
        assertEquals(words, record.text)
        assertEquals(targetTrack.uri, record.uri)
    }

    @Test fun conflictingMissingAndAmbiguousTargetsRetainTheWholeDocumentUnassigned() = runTest {
        val document = DataBackup(newStore()).export(listOf(original)).text
        val scenarios = listOf(emptyList(), listOf(original.copy(title = "Replacement", fileName = "other.flac")),
            listOf(original.copy(id = "8", uri = "content://8"), original.copy(id = "9", uri = "content://9")))
        for (tracks in scenarios) {
            val store = InMemoryKeyValueStore()
            val backup = DataBackup(store)
            val preview = backup.preview(document, tracks)
            assertEquals(1, preview.unavailableImportedLyrics)
            assertEquals(1, preview.counts.importedLyrics)
            assertEquals(0, preview.droppedReferences)
            backup.apply(preview)
            assertEquals(words, repository(store, tracks).recoverableImports().single().text)
            for (track in tracks) assertEquals(LyricsResult.NotFound, repository(store, tracks).load(track))
        }
    }

    @Test fun unassignedRecordCannotBecomeBoundAfterAnotherBackupEvenWhenOriginalFileReturns() = runTest {
        val missingStore = InMemoryKeyValueStore()
        val first = DataBackup(missingStore)
        first.apply(first.preview(DataBackup(newStore()).export(listOf(original)).text, emptyList()))
        val retained = first.export(listOf(original))
        assertTrue(BackupFormat.read(retained.text).sources.isEmpty())
        val target = InMemoryKeyValueStore()
        val second = DataBackup(target)
        val preview = second.preview(retained.text, listOf(original))
        second.apply(preview)
        assertEquals(1, preview.unavailableImportedLyrics)
        assertEquals(LyricsResult.NotFound, repository(target, listOf(original)).load(original))
    }

    @Test fun remapIntoAnInertRecordsKeyPreservesBothDocumentsWithDeterministicPreview() {
        val source = newStore()
        val destination = original.copy(id = "42", uri = "content://42")
        val retained = ImportedLyricsRecord.bound(LyricsSelection(destination, true), "Another retained text").unassigned()
        source.putString(ImportedLyricsCodec.trackKey(destination.id)!!, ImportedLyricsCodec.encode(retained))
        val document = DataBackup(source).export(listOf(original)).text
        val backup = DataBackup(InMemoryKeyValueStore())
        val preview = backup.preview(document, listOf(destination))
        assertTrue(preview.sameRestorePlanAs(backup.preview(document, listOf(destination))))
        assertEquals(2, preview.counts.importedLyrics)
        assertEquals(1, preview.unavailableImportedLyrics)
        val records = preview.entries.values.mapNotNull(ImportedLyricsCodec::decode)
        assertEquals(setOf(words, "Another retained text"), records.map { it.text }.toSet())
        assertEquals(1, records.count { it.binding == LyricsBinding.Canonical })
        assertEquals(1, records.count { it.binding == LyricsBinding.Unassigned })
    }

    @Test fun malformedUnknownAndMismatchedKeyRecordsAreRetainedWithoutImplicitRepair() = runTest {
        val mismatched = ImportedLyricsCodec.trackKey("another")!!
        val damaged = ImportedLyricsCodec.PREFIX + "r.1"
        val unknown = ImportedLyricsCodec.PREFIX + "r.2"
        val store = InMemoryKeyValueStore()
        store.putString(mismatched, ImportedLyricsCodec.encode(ImportedLyricsRecord.bound(LyricsSelection(original, true), words)))
        store.putString(damaged, "SONGLASS-LYRICS-1\ntruncated")
        store.putString(unknown, "SONGLASS-LYRICS-9\nfuture document")
        val exported = DataBackup(store).export(listOf(original))
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target)
        val preview = backup.preview(exported.text, listOf(original))
        assertEquals(3, preview.unavailableImportedLyrics)
        backup.apply(preview)
        assertEquals("SONGLASS-LYRICS-1\ntruncated", target.getString(damaged))
        assertEquals("SONGLASS-LYRICS-9\nfuture document", target.getString(unknown))
        assertEquals(LyricsResult.NotFound, repository(target, listOf(original)).load(original))
        assertEquals(3, repository(target, listOf(original)).recoverableImports().size)
    }

    @Test fun aStructurallyValidRecordWithInvalidLyricsRemainsVisibleForRecovery() = runTest {
        val key = ImportedLyricsCodec.trackKey(original.id)!!
        val raw = ImportedLyricsCodec.encode(ImportedLyricsRecord.bound(LyricsSelection(original, true), "<tt><broken>"))
        val store = InMemoryKeyValueStore().apply { putString(key, raw) }
        val local = repository(store, listOf(original))
        assertEquals(LyricsResult.NotFound, local.load(original))
        assertFalse(local.recoverableImports().single().canLink)
        val exported = DataBackup(store).export(listOf(original))
        val preview = DataBackup(InMemoryKeyValueStore()).preview(exported.text, listOf(original))
        assertEquals(1, preview.unavailableImportedLyrics)
        assertEquals("<tt><broken>", ImportedLyricsCodec.decode(preview.entries.getValue(key))!!.text)
    }

    @Test fun previewBindingChangeRequiresNewConfirmationBeforeAnyStoreAccess() {
        val document = DataBackup(newStore()).export(listOf(original)).text
        val backing = InMemoryKeyValueStore().apply { putString("outside.backup", "Keep") }
        var snapshots = 0
        var commits = 0
        val store = object : KeyValueStore by backing {
            override fun snapshot(): Map<String, String> { snapshots++; return backing.snapshot() }
            override fun replace(removing: Set<String>, writing: Map<String, String>) { commits++; backing.replace(removing, writing) }
        }
        val backup = DataBackup(store)
        val reviewed = backup.preview(document, listOf(original))
        val changed = backup.preview(document, emptyList())
        assertEquals(reviewed.counts, changed.counts)
        assertEquals(0, reviewed.unavailableImportedLyrics)
        assertEquals(1, changed.unavailableImportedLyrics)
        assertFalse(reviewed.sameRestorePlanAs(changed))
        assertFailsWith<BackupPreviewChangedException> { backup.applyConfirmed(reviewed, changed) }
        assertEquals(0, snapshots)
        assertEquals(0, commits)
        assertEquals(mapOf("outside.backup" to "Keep"), backing.snapshot())
    }

    @Test fun bothLegacyAndNewNamespaceParticipateInExplicitOversizeOmission() {
        val store = InMemoryKeyValueStore()
        val large = "x".repeat(1024 * 1024)
        repeat(9) { index ->
            store.putString(ImportedLyricsCodec.LEGACY_PREFIX + "content://$index", large)
            store.putString(ImportedLyricsCodec.PREFIX + "r." + index.toString(16), large)
        }
        store.putString("settings.appearance", "dark")
        val backup = DataBackup(store).export(emptyList())
        assertEquals(18, backup.omittedLyrics)
        assertEquals(0, backup.counts.importedLyrics)
        assertEquals(19, store.snapshot().size)
    }
}
