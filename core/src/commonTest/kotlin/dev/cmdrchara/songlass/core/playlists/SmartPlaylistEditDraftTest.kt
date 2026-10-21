package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartPlaylistEditDraftTest {
    @Test
    fun completedRestoreWithTheSameIdCannotBeOverwrittenByAnOlderDraft() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 42 }
        val id = playlists.save(null, "Original", SmartRules(artist = "A"))
        val draft = SmartPlaylistEditDraft.open(playlists.get(id)).copy(name = "My unsaved name")
        store.putStrings(mapOf("smart.$id.name" to "Restored", "smart.$id.rules" to SmartRulesCodec.encode(SmartRules(genre = "Jazz"))))
        playlists.reload()
        val afterRestore = store.snapshot()
        assertFailsWith<SmartPlaylistChangedException> { draft.saveTo(playlists, id) }
        assertEquals(afterRestore, store.snapshot())
        assertEquals(SmartPlaylist(id, "Restored", SmartRules(genre = "Jazz")), playlists.get(id))
        assertEquals("My unsaved name", draft.name)
        assertEquals("A", draft.rules.artist)
    }

    @Test
    fun deletedSourceStaysDeletedRatherThanRecreatingTheOldDraft() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 42 }
        val id = playlists.save(null, "Original", SmartRules())
        val draft = SmartPlaylistEditDraft.open(playlists.get(id))
        playlists.delete(id)
        val deleted = store.snapshot()
        assertFailsWith<SmartPlaylistChangedException> { draft.saveTo(playlists, id) }
        assertEquals(deleted, store.snapshot())
        assertTrue(playlists.playlists.value.isEmpty())
    }

    @Test
    fun draftRestoredUnderADifferentRouteCannotEditEitherPlaylist() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 42 }
        val first = playlists.save(null, "A", SmartRules())
        val second = playlists.save(null, "B", SmartRules())
        val draft = SmartPlaylistEditDraft.open(playlists.get(first)).copy(name = "Wrong route")
        val before = store.snapshot()
        assertFailsWith<SmartPlaylistChangedException> { draft.saveTo(playlists, second) }
        assertFailsWith<SmartPlaylistChangedException> { draft.saveTo(playlists, null) }
        assertFailsWith<SmartPlaylistChangedException> { SmartPlaylistEditDraft.open(null).copy(name = "Lost baseline").saveTo(playlists, first) }
        assertEquals(before, store.snapshot())
    }

    @Test
    fun changesToOtherPlaylistsDoNotInvalidateThisDraft() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 42 }
        val id = playlists.save(null, "Original", SmartRules())
        val draft = SmartPlaylistEditDraft.open(playlists.get(id)).copy(name = "My edit", rules = SmartRules(favoritesOnly = true))
        val other = playlists.save(null, "Unrelated", SmartRules(genre = "Rock"))
        assertEquals(id, draft.saveTo(playlists, id))
        assertEquals("My edit", playlists.get(id)?.name)
        assertEquals(SmartRules(favoritesOnly = true), playlists.get(id)?.rules)
        assertEquals("Unrelated", playlists.get(other)?.name)
    }

    @Test
    fun explicitReopenUsesTheRestoredBaselineAndCanSaveANewEdit() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 42 }
        val id = playlists.save(null, "Original", SmartRules())
        val stale = SmartPlaylistEditDraft.open(playlists.get(id)).copy(name = "Old draft")
        playlists.save(id, "Restored", SmartRules(artist = "B"))
        assertFailsWith<SmartPlaylistChangedException> { stale.saveTo(playlists, id) }
        val reopened = SmartPlaylistEditDraft.open(playlists.get(id)).copy(name = "New edit")
        assertEquals(id, reopened.saveTo(playlists, id))
        assertEquals(SmartPlaylist(id, "New edit", SmartRules(artist = "B")), playlists.get(id))
    }

    @Test
    fun storageFailureKeepsTheSameDraftAndBaselineAvailableForRetry() {
        val backing = InMemoryKeyValueStore()
        var fail = false
        val store = object : KeyValueStore by backing {
            override fun putStrings(writing: Map<String, String>) {
                if (fail) { fail = false; error("Injected rejected batch") }
                backing.putStrings(writing)
            }
        }
        val playlists = SmartPlaylists(store) { 42 }
        val id = playlists.save(null, "Original", SmartRules())
        val draft = SmartPlaylistEditDraft.open(playlists.get(id)).copy(name = "Retained")
        val before = backing.snapshot()
        fail = true
        assertFailsWith<IllegalStateException> { draft.saveTo(playlists, id) }
        assertEquals(before, backing.snapshot())
        assertTrue(draft.isCurrent(playlists, id))
        assertEquals(id, draft.saveTo(playlists, id))
        assertEquals("Retained", playlists.get(id)?.name)
    }

    @Test
    fun recreationRestoresTheOldBaselineTogetherWithEditedValues() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 42 }
        val id = playlists.save(null, "Original", SmartRules(artist = "A"))
        val draft = SmartPlaylistEditDraft.open(playlists.get(id)).copy(name = "Edited", rules = SmartRules(genre = "Jazz"))
        val saved = SmartPlaylistEditDraftCodec.encode(draft)
        playlists.save(id, "Changed during recreation", SmartRules(artist = "B"))
        val recreated = requireNotNull(SmartPlaylistEditDraftCodec.decode(saved))
        assertEquals(draft, recreated)
        val before = store.snapshot()
        assertFailsWith<SmartPlaylistChangedException> { recreated.saveTo(playlists, id) }
        assertEquals(before, store.snapshot())
    }

    @Test
    fun incompleteOrCorruptSavedProvenanceCannotRestoreOnlyTheEditableFields() {
        val original = SmartPlaylist("a", "Original", SmartRules())
        val saved = SmartPlaylistEditDraftCodec.encode(SmartPlaylistEditDraft.open(original).copy(name = "Unsaved"))
        assertNull(SmartPlaylistEditDraftCodec.decode(saved.drop(1)))
        assertNull(SmartPlaylistEditDraftCodec.decode(saved.toMutableList().also { it[1] = "" }))
        assertNull(SmartPlaylistEditDraftCodec.decode(saved.toMutableList().also { it[3] = "broken rules" }))
        val newDraft = SmartPlaylistEditDraft.open(null).copy(name = "New")
        assertEquals(newDraft, SmartPlaylistEditDraftCodec.decode(SmartPlaylistEditDraftCodec.encode(newDraft)))
    }
}
