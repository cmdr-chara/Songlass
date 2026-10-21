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

    @Test
    fun insertionPreservesRequestOrderAndDoesNotMoveExistingTracks() {
        val store = InMemoryKeyValueStore()
        val lists = playlists(store)
        val id = lists.create("Mix", listOf("a", "b"))
        assertEquals(PlaylistAddResult(2, 1), lists.addDetailed(id, listOf("x", "b", "y", "x"), PlaylistInsertion.Start))
        assertEquals(listOf("x", "y", "a", "b"), lists.get(id)!!.trackIds)
        assertEquals(PlaylistAddResult(0, 2), lists.addDetailed(id, listOf("y", "a")))
        assertEquals(PlaylistAddResult(1, 1), lists.addDetailed(id, listOf("b", "z")))
        assertEquals(listOf("x", "y", "a", "b", "z"), playlists(store).get(id)!!.trackIds)
        assertEquals(PlaylistAddResult(0, 0, false), lists.addDetailed("deleted", listOf("a")))
    }

    @Test
    fun filteredAndMissingReferencesAreDistinguishedAndRetainedUntilExplicitRemoval() {
        val all = listOf(track("a"), track("hidden"), track("b"))
        val visible = MusicLibrary(all.filterNot { it.id == "hidden" })
        val store = InMemoryKeyValueStore()
        val lists = playlists(store)
        val id = lists.create("Mix", listOf("b", "gone", "hidden", "a"))
        val resolved = lists.get(id)!!.availability(visible, all)
        assertEquals(listOf("b", "a"), resolved.tracks.map { it.id })
        assertEquals(listOf("hidden"), resolved.filteredTrackIds)
        assertEquals(listOf("gone"), resolved.missingTrackIds)
        assertEquals(listOf("b", "gone", "hidden", "a"), playlists(store).get(id)!!.trackIds)
        // Reordering visible rows maps back to stored indices and retains hidden/missing entries.
        lists.move(id, lists.get(id)!!.trackIds.indexOf("a"), lists.get(id)!!.trackIds.indexOf("b"))
        assertEquals(listOf("a", "b", "gone", "hidden"), lists.get(id)!!.trackIds)
        assertEquals(1, lists.removeMissing(id, all, resolved.missingTrackIds))
        assertEquals(listOf("a", "b", "hidden"), playlists(store).get(id)!!.trackIds)
    }

    @Test
    fun cleanupUsesTheCurrentScanSoRestoredFilesSurvive() {
        val lists = playlists()
        val initial = listOf(track("present"), track("newly-gone"))
        val id = lists.create("Mix", listOf("returned", "still-gone", "present", "newly-gone"))
        val candidates = lists.get(id)!!.availability(MusicLibrary(initial), initial).missingTrackIds
        assertEquals(listOf("returned", "still-gone"), candidates)
        assertEquals(1, lists.removeMissing(id, listOf(track("returned"), track("present")), candidates))
        assertEquals(listOf("returned", "present", "newly-gone"), lists.get(id)!!.trackIds)
    }

    @Test
    fun unmountedSourceDoesNotExpandConfirmedRemovalOrRemoveLaterAdditions() {
        val store = InMemoryKeyValueStore()
        val lists = playlists(store)
        val initial = listOf(track("a"), track("b"))
        val id = lists.create("Mix", listOf("a", "gone", "b"))
        val candidates = lists.get(id)!!.availability(MusicLibrary(initial), initial).missingTrackIds
        assertEquals(listOf("gone"), candidates)
        // The dialog stays open while the collection changes and a mounted source disappears.
        lists.add(id, listOf("later-added"))
        assertEquals(1, lists.removeMissing(id, emptyList(), candidates))
        assertEquals(listOf("a", "b", "later-added"), playlists(store).get(id)!!.trackIds)
        assertEquals(0, lists.removeMissing(id, emptyList(), candidates))
    }

    @Test
    fun noConfirmedCandidatesNeverMeansRemoveEveryMissingReference() {
        val lists = playlists()
        val id = lists.create("Mix", listOf("a", "b"))
        assertEquals(0, lists.removeMissing(id, emptyList(), emptyList()))
        assertEquals(listOf("a", "b"), lists.get(id)!!.trackIds)
    }
}
