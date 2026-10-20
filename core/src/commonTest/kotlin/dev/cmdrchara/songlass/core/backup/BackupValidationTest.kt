package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupValidationTest {
    private val library = listOf(song("1"), song("2"))
    private val existing = arrayOf(
        "favorites" to "2",
        "settings.appearance" to "light",
        "playlists.index" to "pOld",
        "playlists.pOld.name" to "Old",
        "future.pref" to "keep",
    )
    private val valid = DataBackup(
        storeOf("favorites" to "1", "settings.appearance" to "dark", "home.layout" to ODD),
    ) { NOW }.export(library).text

    /** The file must be refused with [error] and the store must still hold exactly what it did. */
    private fun assertRejected(error: BackupError, text: String) {
        val store = storeOf(*existing)
        val before = store.snapshot()
        val thrown = assertFailsWith<BackupException> { DataBackup(store).preview(text, library) }
        assertEquals(error, thrown.error)
        assertEquals(before, store.snapshot())
    }

    @Test
    fun theValidFixtureIsAccepted() {
        val preview = DataBackup(InMemoryKeyValueStore()).preview(valid, library)
        assertEquals(3, preview.entries.size)
    }

    @Test
    fun filesThatAreNotBackupsOrAreFromAnotherVersionAreRefused() {
        assertRejected(BackupError.NotABackup, "")
        assertRejected(BackupError.NotABackup, "hello")
        assertRejected(BackupError.NotABackup, "{\"favorites\":[\"1\"]}\n")
        assertRejected(BackupError.UnsupportedVersion, "SONGLASS-BACKUP 2" + valid.removePrefix("SONGLASS-BACKUP 1"))
        assertRejected(BackupError.Malformed, "SONGLASS-BACKUP one\n")
    }

    @Test
    fun truncatedFilesAreRefusedWhereverTheyAreCut() {
        assertRejected(BackupError.Malformed, valid.dropLast(1))
        assertRejected(BackupError.Malformed, valid.take(valid.length / 2))
        // Cut exactly between records.
        assertRejected(BackupError.Malformed, valid.substring(0, valid.lastIndexOf("1:E")))
    }

    @Test
    fun damageAndAppendedDataAreRefused() {
        assertRejected(BackupError.ChecksumMismatch, valid.replace("4:dark", "4:dork"))
        assertRejected(BackupError.TrailingContent, valid + "x")
        assertRejected(BackupError.TrailingContent, valid + "\n")
        assertRejected(BackupError.TrailingContent, valid + valid)
    }

    @Test
    fun oversizeInputIsRefusedBeforeBeingParsed() {
        assertRejected(BackupError.TooLarge, "x".repeat(DataBackup.MAX_BYTES + 1))
    }

    @Test
    fun duplicatesAndCountMismatchesAreRefused() {
        assertRejected(BackupError.DuplicateEntry, handBuilt(listOf("settings.a" to "1", "settings.a" to "2")))
        val track = listOf("1", "content://a", null, "T", null, null, "5")
        assertRejected(BackupError.DuplicateEntry, handBuilt(emptyList(), listOf(track, track)))
        assertRejected(BackupError.DuplicateEntry, handBuilt(emptyList(), listOf(track, track.toMutableList().also { it[0] = "2" })))
        assertRejected(BackupError.Malformed, handBuilt(listOf("settings.a" to "1"), declaredEntries = 2))
        assertRejected(BackupError.Malformed, handBuilt(listOf("settings.a" to "1"), declaredEntries = 0))
    }

    @Test
    fun keysOutsideTheAllowlistAreRefused() {
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("queue.state" to "x")))
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("autoeq.cache.index" to "x")))
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("history.unknown" to "x")))
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("" to "x")))
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("settings." to "x")))
        // One bad entry spoils the file even when the rest is fine.
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("favorites" to "1", "future.pref" to "x")))
    }

    @Test
    fun valuesThatAreNotInTheAppsOwnFormAreRefused() {
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("favorites" to "1\u001F\u001F2")))
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("history.counts" to "a\u001E0\u001E1")))
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("history.dated_plays" to "a\u001Enope")))
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("search.opened.v1" to "garbage")))
        assertRejected(BackupError.InvalidEntry, handBuilt(listOf("playlists.p1.tracks" to "a\u001F\u001Fb")))
    }

    @Test
    fun failedWriteLeavesTheStoreExactlyAsItWasWhereverItFails() {
        val before = mapOf(*existing)
        val preview = DataBackup(storeOf(*existing)).preview(valid, library)
        // Removing 2 stale playlist keys, then writing every entry.
        val puts = 2 + preview.entries.size
        for (failAt in 1..puts) {
            val inner = storeOf(*existing)
            val error = assertFailsWith<BackupException> { DataBackup(FailingStore(inner, failAt)).apply(preview) }
            assertEquals(BackupError.WriteFailed, error.error, "failing put $failAt")
            assertEquals(before, inner.snapshot(), "failing put $failAt")
        }
        // And without a failure the same preview does apply.
        val inner = storeOf(*existing)
        DataBackup(inner).apply(preview)
        assertEquals("1", inner.getString("favorites"))
    }

    @Test
    fun aStoreThatFailsToReplaceIsReportedAsWriteFailed() {
        val preview = DataBackup(storeOf(*existing)).preview(valid, library)
        val broken = object : KeyValueStore {
            override fun getString(key: String): String? = null
            override fun putString(key: String, value: String?) = Unit
            override fun snapshot(): Map<String, String> = emptyMap()
            override fun replace(removing: Set<String>, writing: Map<String, String>) {
                error("disk full")
            }
        }
        val error = assertFailsWith<BackupException> { DataBackup(broken).apply(preview) }
        assertEquals(BackupError.WriteFailed, error.error)
        assertEquals("WriteFailed", error.message, "the message must never carry user data")
    }

    @Test
    fun inMemoryReplaceRemovesThenWritesAndLeavesOtherKeys() {
        val store = storeOf("a" to "1", "b" to "2", "c" to "3")
        store.replace(removing = setOf("a", "b"), writing = mapOf("b" to "new", "d" to "4"))
        assertEquals(mapOf("b" to "new", "c" to "3", "d" to "4"), store.snapshot())
    }

    @Test
    fun defaultReplaceRestoresPreviousValuesIncludingAbsentKeys() {
        val inner = storeOf("a" to "1", "b" to "2")
        val store = FailingStore(inner, failOnPut = 3)
        assertFailsWith<IllegalStateException> {
            store.replace(removing = setOf("a"), writing = mapOf("b" to "new", "c" to "added"))
        }
        assertEquals(mapOf("a" to "1", "b" to "2"), inner.snapshot())
        assertTrue(inner.getString("c") == null)
    }
}
