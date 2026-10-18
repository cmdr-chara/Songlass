package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserPlaylistsTest {
    private var now = 1000L
    private fun playlists(store: InMemoryKeyValueStore = InMemoryKeyValueStore()) = UserPlaylists(store) { now++ }

    @Test
    fun createAddMoveRemoveAndPersist() {
        val store = InMemoryKeyValueStore()
        val lists = playlists(store)
        val id = lists.create("  Road trip ", listOf("a", "b"))
        assertEquals(1, lists.add(id, listOf("b", "c")))
        lists.move(id, from = 2, to = 0)
        lists.remove(id, "a")
        val restored = playlists(store).get(id)!!
        assertEquals("Road trip", restored.name)
        assertEquals(listOf("c", "b"), restored.trackIds)
    }

    @Test
    fun newestFirstRenameAndDelete() {
        val lists = playlists()
        val first = lists.create("One")
        val second = lists.create("Two")
        assertEquals(listOf(second, first), lists.playlists.value.map { it.id })
        lists.rename(first, "Uno")
        lists.rename(first, "   ")
        assertEquals("Uno", lists.get(first)?.name)
        lists.delete(second)
        assertNull(lists.get(second))
    }

    @Test
    fun missingTracksAreSkipped() {
        val library = MusicLibrary(listOf(track("a"), track("b")))
        val lists = playlists()
        val id = lists.create("Mix", listOf("b", "gone", "a"))
        assertEquals(listOf("b", "a"), lists.get(id)!!.tracksIn(library).map { it.id })
    }
}
