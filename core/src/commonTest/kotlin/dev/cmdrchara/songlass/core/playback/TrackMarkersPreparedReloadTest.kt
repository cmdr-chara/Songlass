package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackMarkersPreparedReloadTest {
    @Test fun migrationUsesTheStagingStoreAndPublicationDoesNotRestoreRevokedAvailability() {
        val song = track("canonical", durationMs = 1_200_000, folder = "Music").copy(fileName = "episode.flac")
        val backing = InMemoryKeyValueStore()
        var writes = 0
        val live = object : KeyValueStore {
            override fun getString(key: String) = backing.getString(key)
            override fun snapshot() = backing.snapshot()
            override fun putString(key: String, value: String?) { writes++; backing.putString(key, value) }
        }
        val markers = TrackMarkers(live).apply { setLibrary(listOf(song), stableIds = true) }
        val staged = InMemoryKeyValueStore()
        TrackMarkers(staged).apply {
            setLibrary(listOf(song))
            checkpoint(song, 120_000, 600_000, force = true)
            add(song, 120_000, "Chapter")
        }
        assertTrue(staged.getString(TrackMarkersCodec.PROGRESS_KEY)!!.startsWith("v1"))
        val prepare = markers.reloadPreparation()
        val publish = prepare(staged)
        assertEquals(0, writes)
        assertTrue(markers.state.value.progress.isEmpty())
        assertEquals(MarkerBinding.Canonical,
            TrackMarkersCodec.decodeProgress(staged.getString(TrackMarkersCodec.PROGRESS_KEY)).single().track.binding)
        assertEquals(MarkerBinding.Canonical,
            TrackMarkersCodec.decodeBookmarks(staged.getString(TrackMarkersCodec.BOOKMARKS_KEY)).single().track.binding)
        // A permission revocation is an independent current device fact. Publishing decoded
        // records after commit must not put the captured old availability map back.
        markers.setLibrary(emptyList(), stableIds = true)
        live.replace(emptySet(), staged.snapshot())
        val committedWrites = writes
        publish()
        assertEquals(committedWrites, writes)
        assertEquals(120_000L, markers.state.value.progress.single().positionMs)
        assertEquals("Chapter", markers.state.value.bookmarks.single().title)
        assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
    }
}
