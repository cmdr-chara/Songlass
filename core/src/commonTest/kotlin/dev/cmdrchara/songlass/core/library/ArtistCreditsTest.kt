package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.ArtistTags
import dev.cmdrchara.songlass.core.model.ReleaseDate
import dev.cmdrchara.songlass.core.track
import kotlin.test.*

class ArtistCreditsTest {
    @Test fun namesPreservePunctuationAndExplicitCreditsWin() {
        assertEquals(listOf("AC/DC", "Earth, Wind & Fire"), ArtistTags.names("AC/DC; Earth, Wind & Fire"))
        assertEquals(listOf("Alpha", "Beta"), ArtistTags.names("Alpha feat. Beta"))
        assertEquals(listOf("A & B", "C"), ArtistTags.names("Wrong", listOf("A & B", "C", "c")))
    }
    @Test fun collaboratorsAreIndexedOnceAndCompilationAlbumsStayTogether() {
        val a = track("a", artist = "Alpha feat. Beta", album = "Mix", albumArtist = "Various Artists")
        val b = track("b", artist = "Beta", album = "Mix", albumArtist = "Various Artists")
        val library = MusicLibrary(listOf(a, b))
        assertEquals(1, library.albums.size)
        assertEquals(listOf("a", "b"), library.artists.single { it.name == "Beta" }.tracks.map { it.id })
        assertEquals(listOf("a", "b"), library.search("Beta").tracks.map { it.id })
        assertEquals("Various Artists", library.artistOf(a)?.name)
    }
    @Test fun collaborationWithoutAlbumArtistHasAReachablePrimaryArtist() {
        val a = track("a").copy(artist = "Alpha feat. Beta")
        val library = MusicLibrary(listOf(a))
        assertEquals("Alpha", library.artistOf(a)?.name)
        assertEquals(listOf("Alpha", "Beta"), library.artistsOf(a).map { it.name })
    }
    @Test fun partialAndFullReleaseDatesAreValidated() {
        assertEquals(ReleaseDate(2000), ReleaseDate.parse("2000"))
        assertEquals(ReleaseDate(2000, 2, 29), ReleaseDate.parse("2000-02-29"))
        assertNull(ReleaseDate.parse("1900-02-29"))
        assertNull(ReleaseDate.parse("2020-04-31"))
        assertNull(ReleaseDate.parse("0000"))
        assertNull(ReleaseDate.parse("2020-99"))
    }
}
