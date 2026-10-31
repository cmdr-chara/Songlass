package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.net.FakeHttpTransport
import dev.cmdrchara.songlass.core.net.HttpResult
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SingleArtworkTest {
    private val single = Track("stable-a", "Bad Weekend", "content://media/audio/41", artist = "Krubb Wenkroist")

    @Test fun missingAlbumUsesOneCanonicalTrackNotTheUnknownAlbumGroup() {
        val subject = assertNotNull(ArtworkSubject.forTrack(single))
        assertEquals("Bad Weekend", subject.album)
        assertEquals("stable-a", subject.trackId)
        assertNotEquals(subject.key, ArtworkSubject.forTrack(single.copy(id = "stable-b"))?.key)
        assertEquals(subject.key, ArtworkSubject.forTrack(single.copy(uri = "content://media/audio/99"))?.key)
        assertEquals(subject, ArtworkSubject.forTrack(single.copy(album = "Album")))
    }

    @Test fun aSingleCoverPersistsWithoutBeingReinterpretedAsAnAlbumCover() = runTest {
        val store = FakeArtworkFileStore()
        val subject = assertNotNull(ArtworkSubject.forTrack(single))
        val covers = DownloadedArtworks(store, now = { 5 })
        assertNotNull(covers.save(subject, byteArrayOf(1, 2, 3)))
        val restarted = DownloadedArtworks(store, now = { 6 })
        restarted.load()
        assertEquals(subject, restarted.get(subject.key)?.subject)
        assertNull(restarted.get(artworkSubjectKey(single.artist, single.title)))
        assertContentEquals(byteArrayOf(1, 2, 3), restarted.bytesOf(subject.key))
    }

    @Test fun singleLookupSendsArtistAndTitleButNotCanonicalIdentityOrPath() = runTest {
        val url = ITunesArtwork.trackSearchUrl(single.artist, single.title)
        val transport = FakeHttpTransport(mapOf(url to HttpResult.Success(200, response)))
        val found = assertIs<ArtworkSearchResult.Found>(ArtworkSearch(transport).searchTrack(single.title, single.artist))
        assertEquals("Bad Weekend - Single", found.candidates.single().album)
        assertTrue("entity=song" in transport.requests.single().url)
        assertFalse(single.id in transport.requests.single().url)
        assertFalse("content" in transport.requests.single().url)
    }

    @Test fun singleLookupRejectsDifferentSongAndArtistAndInsecureImages() {
        assertEquals(1, ITunesArtwork.parseTrackCandidates(response, single.artist, single.title).size)
        assertTrue(ITunesArtwork.parseTrackCandidates(response, "Another Artist", single.title).isEmpty())
        assertTrue(ITunesArtwork.parseTrackCandidates(response, single.artist, "Another Song").isEmpty())
        assertTrue(ITunesArtwork.parseTrackCandidates(response.replace("https://", "http://"), single.artist, single.title).isEmpty())
    }

    @Test fun folderDerivedAlbumAndFilenameTitleSearchTheActualSong() = runTest {
        val indexed = single.copy(title = "Bad Weekend - Krubb Wenkroist", artist = "<unknown>", album = "Pop & Electronic")
        val subject = assertNotNull(ArtworkSubject.forTrack(indexed))
        assertEquals(indexed.title, subject.album)
        assertEquals("", subject.artist)
        val url = ITunesArtwork.trackSearchUrl(subject.artist, subject.album)
        val transport = FakeHttpTransport(mapOf(url to HttpResult.Success(200, response)))
        val found = assertIs<ArtworkSearchResult.Found>(ArtworkSearch(transport).searchTrack(subject.album, subject.artist))
        assertEquals("Bad Weekend - Single", found.candidates.single().album)
        assertFalse("Pop" in transport.requests.single().url)
        assertFalse("unknown" in transport.requests.single().url)
        assertTrue("entity=song" in transport.requests.single().url)
    }

    @Test fun filenameCreditsMustMatchTheCompleteReturnedArtistAndTitle() {
        assertEquals(1, ITunesArtwork.parseTrackCandidates(response, null, "Krubb Wenkroist - Bad Weekend").size)
        assertEquals(1, ITunesArtwork.parseTrackCandidates(response, "<unknown>", "Bad Weekend - Krubb Wenkroist").size)
        assertTrue(ITunesArtwork.parseTrackCandidates(response, null, "Bad Weekend - Another Artist").isEmpty())
        assertTrue(ITunesArtwork.parseTrackCandidates(response, null, "Bad Weekend Extra Words").isEmpty())
    }

    @Test fun explicitTrackOverrideLeavesAlbumSiblingsOnTheirAlbumSelection() = runTest {
        val track = single.copy(album = "Tagged Album")
        val sibling = track.copy(id = "sibling", title = "Another Song")
        val albumSubject = assertNotNull(ArtworkSubject.of(track.groupingArtist, track.album))
        val trackSubject = assertNotNull(ArtworkSubject.forTrack(track))
        val store = FakeArtworkFileStore()
        val covers = DownloadedArtworks(store, now = { 5 })
        val albumCover = assertNotNull(covers.save(albumSubject, byteArrayOf(1)))
        assertEquals(albumCover, ArtworkCovers.downloadedForTrack(track, covers.state.value))
        val trackCover = assertNotNull(covers.save(trackSubject, byteArrayOf(2)))
        assertEquals(trackCover, ArtworkCovers.downloadedForTrack(track, covers.state.value))
        assertEquals(albumCover, ArtworkCovers.downloadedForTrack(sibling, covers.state.value))
        assertNotEquals(albumSubject.key, trackSubject.key)
    }

    private val response = """{"results":[{"wrapperType":"track","kind":"song","trackId":9,
        "trackName":"Bad Weekend","artistName":"Krubb Wenkroist","collectionName":"Bad Weekend - Single",
        "artworkUrl100":"https://is1-ssl.mzstatic.com/image/100x100bb.jpg"}]}"""
}
