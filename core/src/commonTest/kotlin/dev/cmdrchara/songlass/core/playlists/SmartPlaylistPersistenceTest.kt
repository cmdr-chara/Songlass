package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.backup.BackupKeys
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SmartPlaylistPersistenceTest {
    @Test
    fun relatedValuesPublishTogetherAfterTheAcceptedBatchAndKeepTheExistingFormat() {
        val backing = InMemoryKeyValueStore()
        var batches = 0
        var expectedState = emptyList<SmartPlaylist>()
        lateinit var playlists: SmartPlaylists
        val store = object : KeyValueStore by backing {
            override fun putStrings(writing: Map<String, String>) {
                assertEquals(expectedState, playlists.playlists.value)
                backing.putStrings(writing)
                batches++
            }
        }
        playlists = SmartPlaylists(store) { 42 }
        val first = playlists.save(null, "  Étoile 🎵  ", SmartRules(artist = " One ", yearFrom = 1990))
        expectedState = playlists.playlists.value
        val second = playlists.save(null, "Other", SmartRules(playStatus = SmartPlayStatus.Never))
        assertEquals(2, batches)
        assertEquals("s42\u001Fs42-1", backing.getString("smart.index"))
        assertEquals("Étoile 🎵", backing.getString("smart.$first.name"))
        assertEquals(SmartRulesCodec.encode(SmartRules(artist = "One", yearFrom = 1990)), backing.getString("smart.$first.rules"))
        assertEquals(listOf(first, second), playlists.playlists.value.map { it.id })
        assertEquals(playlists.playlists.value, SmartPlaylists(backing) { 43 }.playlists.value)
    }

    @Test
    fun failedCreationDoesNotPublishOrLeaveAnIndexedPartialPlaylistAndRetryKeepsTheId() {
        val store = FailingStore()
        val playlists = SmartPlaylists(store) { 42 }
        store.backing.putString("unrelated", "kept")
        val before = store.backing.snapshot()
        store.failOnceOn("smart.s42.rules")
        assertFailsWith<IllegalStateException> { playlists.save(null, "New", SmartRules(genre = "Jazz")) }
        assertEquals(emptyList(), playlists.playlists.value)
        assertEquals(before, store.backing.snapshot())
        val id = playlists.save(null, "New", SmartRules(genre = "Jazz"))
        assertEquals("s42", id)
        assertEquals(playlists.playlists.value, SmartPlaylists(store) { 43 }.playlists.value)
    }

    @Test
    fun failedEditKeepsTheOldNameRulesAndOrderInMemoryAndStorage() {
        val store = FailingStore()
        val playlists = SmartPlaylists(store) { 42 }
        val first = playlists.save(null, "First", SmartRules(favoritesOnly = true))
        playlists.save(null, "Second", SmartRules())
        val before = playlists.playlists.value
        val persisted = store.backing.snapshot()
        store.failOnceOn("smart.$first.rules")
        assertFailsWith<IllegalStateException> { playlists.save(first, "Changed", SmartRules(genre = "Jazz")) }
        assertEquals(before, playlists.playlists.value)
        assertEquals(persisted, store.backing.snapshot())
        assertEquals(before, SmartPlaylists(store) { 43 }.playlists.value)
    }

    @Test
    fun failedDeletionRollsBackClearedContentAndRetryDoesNotExportInactiveKeys() {
        val store = FailingStore()
        val playlists = SmartPlaylists(store) { 42 }
        val deleted = playlists.save(null, "Private name", SmartRules(artist = "Private rule"))
        val kept = playlists.save(null, "Kept", SmartRules())
        val before = playlists.playlists.value
        val persisted = store.backing.snapshot()
        store.failOnceOn("smart.$deleted.rules")
        assertFailsWith<IllegalStateException> { playlists.delete(deleted) }
        assertEquals(before, playlists.playlists.value)
        assertEquals(persisted, store.backing.snapshot())
        playlists.delete(deleted)
        assertNull(playlists.get(deleted))
        assertEquals(listOf(kept), playlists.playlists.value.map { it.id })
        assertEquals("", store.backing.getString("smart.$deleted.name"))
        assertEquals("", store.backing.getString("smart.$deleted.rules"))
        val export = BackupKeys.exportable(store.backing.snapshot())
        assertFalse("smart.$deleted.name" in export)
        assertFalse("smart.$deleted.rules" in export)
        assertEquals(playlists.playlists.value, SmartPlaylists(store) { 43 }.playlists.value)
    }

    @Test
    fun reloadUsesOneSnapshotAndDoesNotFillAbsentKeysFromAnotherGeneration() {
        val rules = SmartRulesCodec.encode(SmartRules())
        var snapshots = 0
        var values = mapOf("smart.index" to "a\u001Fb\u001Fa", "smart.a.name" to "A", "smart.a.rules" to rules,
            "smart.b.name" to "Incomplete")
        val store = object : KeyValueStore {
            override fun snapshot() = values.also { snapshots++ }
            override fun getString(key: String): String? = error("Mixed-generation read")
            override fun putString(key: String, value: String?) = Unit
        }
        val playlists = SmartPlaylists(store) { 42 }
        assertEquals(listOf(SmartPlaylist("a", "A", SmartRules())), playlists.playlists.value)
        assertEquals(1, snapshots)
        values = mapOf("smart.index" to "b", "smart.b.name" to "B", "smart.b.rules" to rules)
        playlists.reload()
        assertEquals(listOf(SmartPlaylist("b", "B", SmartRules())), playlists.playlists.value)
        assertEquals(2, snapshots)
    }

    @Test
    fun staleEditorCannotSilentlyRecreateAPlaylistRemovedByDeletionOrRestore() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 42 }
        val id = playlists.save(null, "Old", SmartRules())
        playlists.delete(id)
        val afterDeletion = store.snapshot()
        assertFailsWith<IllegalArgumentException> { playlists.save(id, "Stale draft", SmartRules(favoritesOnly = true)) }
        assertEquals(afterDeletion, store.snapshot())
        assertEquals(emptyList(), playlists.playlists.value)
        val next = playlists.save(null, "New", SmartRules())
        store.putString("smart.index", "")
        playlists.reload()
        val afterRestore = store.snapshot()
        assertFailsWith<IllegalArgumentException> { playlists.save(next, "Stale draft", SmartRules()) }
        assertEquals(afterRestore, store.snapshot())
        assertEquals(emptyList(), playlists.playlists.value)
    }

    /** Do not delegate putStrings: exercise the fallback rollback through individual writes. */
    private class FailingStore : KeyValueStore {
        val backing = InMemoryKeyValueStore()
        private var failingKey: String? = null
        fun failOnceOn(key: String) { failingKey = key }
        override fun snapshot() = backing.snapshot()
        override fun getString(key: String) = backing.getString(key)
        override fun putString(key: String, value: String?) {
            if (key == failingKey) {
                failingKey = null
                error("Injected one-shot storage failure")
            }
            backing.putString(key, value)
        }
    }
}
