package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.library.LibraryFilter
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.track
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PlaylistTrackIndexTest {
    @Test
    fun indexedLookupsKeepPlaylistOrderRepeatedReferencesAndSortedDuplicateIdWinner() {
        val first = track("same", "Alpha")
        val last = track("same", "Zulu")
        val other = track("other", "Middle")
        val library = MusicLibrary(listOf(last, other, first))
        val playlist = UserPlaylist("p", "Ordered", listOf("other", "missing", "same", "other"), 0)
        assertEquals(listOf(other, last, other), playlist.tracksIn(library))
        assertSame(last, library.track("same"))
        assertNull(library.track("missing"))
    }

    @Test
    fun newVisibleSnapshotDropsFilteredOrMissingFilesAndUsesFreshTrackMetadata() {
        val shown = track("a", "Shown", folder = "Music/Shown")
        val hidden = track("b", "Hidden", folder = "Music/Hidden")
        val playlist = UserPlaylist("p", "Mine", listOf("b", "a", "gone"), 0)
        val original = MusicLibrary(listOf(shown, hidden))
        val updated = shown.copy(title = "Renamed", uri = "content://reindexed/42")
        val filtered = MusicLibrary(LibraryFilter(setOf("Music/Hidden")).apply(listOf(updated, hidden)))
        assertEquals(listOf(hidden, shown), playlist.tracksIn(original))
        assertEquals(listOf(updated), playlist.tracksIn(filtered))
        assertNull(filtered.track("b"))
        assertEquals(listOf("b", "a", "gone"), playlist.trackIds, "lookup must not rewrite stored references")
    }

    @Test
    fun deterministicMixedLibrariesAndSelectionsMatchThePreviousResolutionExactly() {
        val random = Random(20261021)
        val tracks = List(320) { index ->
            track("id-${index % 301}", "Title ${random.nextInt(40)}", artist = if (index % 9 == 0) null else "Artist")
        }
        fun compare(input: List<Track>) {
            val library = MusicLibrary(input)
            val oldIndex = library.tracks.associateBy { it.id }
            repeat(24) { playlistIndex ->
                val ids = List(70) { "id-${random.nextInt(340)}" }
                val playlist = UserPlaylist("p$playlistIndex", "Synthetic", ids, 0)
                assertEquals(ids.mapNotNull { oldIndex[it] }, playlist.tracksIn(library))
            }
        }
        compare(tracks)
        compare(tracks.reversed())
        compare(tracks.filter { it.id.hashCode() % 3 == 0 })
        compare(emptyList())
    }
}
