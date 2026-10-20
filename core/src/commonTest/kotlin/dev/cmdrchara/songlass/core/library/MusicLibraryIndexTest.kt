package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.track
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class MusicLibraryIndexTest {
    @Test fun indexedMembershipsMatchScanWithCompilationsGuestsAndDuplicateIds() {
        val random = Random(42)
        val tracks = List(240) { i ->
            track(
                id = "${i % 237}", // Membership must still match IDs when a source repeats one.
                title = "Song ${random.nextInt(40)}",
                artist = if (i % 11 == 0) null else "Artist ${random.nextInt(15)}",
                album = if (i % 13 == 0) null else "Album ${random.nextInt(12)}",
                albumArtist = if (i % 3 == 0) "Compilation" else null,
                trackNumber = random.nextInt(1, 12),
            ).copy(artists = if (i % 5 == 0) listOf("Guest", "ARTIST 1") else emptyList())
        }
        assertMatchesScan(tracks)
        assertMatchesScan(tracks.reversed())
        assertMatchesScan(emptyList())
        assertMatchesScan(listOf(track("lone", album = "Single")))
    }

    private fun assertMatchesScan(tracks: List<Track>) {
        val library = MusicLibrary(tracks)
        val expectedArtists = tracks.flatMap { track ->
            (track.artistNames + listOfNotNull(track.albumArtist)).distinctBy { it.lowercase() }
                .ifEmpty { listOf(null) }.map { it to track }
        }.groupBy { it.first?.lowercase() }.values.map { credits ->
            val artistTracks = credits.map { it.second }.distinctBy { it.id }
            Artist(
                credits.first().first,
                library.albums.filter { album ->
                    album.tracks.any { t -> artistTracks.any { it.id == t.id } }
                },
                artistTracks.sortedBy { it.title.lowercase() },
            )
        }.sortedWith(compareBy(nullsLast()) { it.name?.lowercase() })
        assertEquals(expectedArtists, library.artists)
        // Lookups use metadata, including external queue tracks, rather than object identity/ID.
        (tracks + tracks.map { it.copy(id = "external-${it.id}") } +
            track("missing", album = "Absent")).forEach { track ->
            assertEquals(
                library.albums.firstOrNull { it.title == track.album && it.artist == track.groupingArtist },
                library.albumOf(track),
            )
        }
    }
}
