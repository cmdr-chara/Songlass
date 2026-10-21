package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserPlaylistPersistenceTest {
    @Test
    fun failedCreationRollsBackPartialWritesAndNeverPublishesThePlaylist() {
        val backing = InMemoryKeyValueStore()
        var remainingWrites: Int? = null
        val store = object : KeyValueStore {
            override fun getString(key: String) = backing.getString(key)
            override fun snapshot() = backing.snapshot()
            override fun putString(key: String, value: String?) {
                remainingWrites?.let { remaining ->
                    if (remaining == 0) {
                        remainingWrites = null // Recovery writes are allowed after this one-shot failure.
                        error("Synthetic store failure")
                    }
                    remainingWrites = remaining - 1
                }
                backing.putString(key, value)
            }
        }
        val playlists = UserPlaylists(store) { 1L }
        playlists.create("Existing", listOf("kept", "missing"))
        val beforeState = playlists.playlists.value
        val beforeStore = backing.snapshot()
        remainingWrites = 2
        assertFailsWith<IllegalStateException> { playlists.create("Imported", listOf("new")) }
        assertEquals(beforeState, playlists.playlists.value)
        assertEquals(beforeStore, backing.snapshot())
        assertEquals(beforeState, UserPlaylists(store).playlists.value)
        playlists.create("Retry", listOf("new"))
        assertEquals(listOf("Retry", "Existing"), UserPlaylists(store).playlists.value.map { it.name })
    }

    @Test
    fun relatedKeysAreOneBatchAndReloadReadsOneSnapshot() {
        val backing = InMemoryKeyValueStore()
        var batches = 0
        var snapshots = 0
        val store = object : KeyValueStore {
            override fun getString(key: String): String? = error("Snapshot-supported load must not mix live reads")
            override fun putString(key: String, value: String?) = error("Playlist persistence must be batched")
            override fun putStrings(writing: Map<String, String>) { batches++; backing.putStrings(writing) }
            override fun snapshot(): Map<String, String> { snapshots++; return backing.snapshot() }
        }
        val playlists = UserPlaylists(store) { 10L }
        val id = playlists.create("Names stay intact — 日本語", listOf("stable:a", "missing", "stable:b"))
        playlists.move(id, 2, 0)
        playlists.rename(id, "Renamed")
        assertEquals(3, batches)
        assertEquals("stable:b\u001Fstable:a\u001Fmissing", backing.getString("playlists.$id.tracks"))
        val roundtrip = UserPlaylists(store)
        assertEquals(playlists.playlists.value, roundtrip.playlists.value)
        roundtrip.reload()
        assertEquals(3, snapshots)
        // Inactive keys retain the previous format and lifetime; no unrelated cleanup is added.
        playlists.delete(id)
        assertEquals("Renamed", backing.getString("playlists.$id.name"))
        assertEquals(emptyList(), UserPlaylists(store).playlists.value)
    }
}
