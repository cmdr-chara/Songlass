package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals

class M3uTest {
    private val a = track("a", title = "Alpha", artist = "X").copy(folder = "Music/Chill", fileName = "01 Alpha.flac", durationMs = 61_500)
    private val b = track("b", title = "Beta", artist = "Y").copy(folder = "Music/Gym", fileName = "song.mp3")
    private val b2 = track("b2", title = "Beta 2", artist = "Z").copy(folder = "Music/Chill", fileName = "song.mp3")
    private val library = MusicLibrary(listOf(a, b, b2))

    @Test
    fun writesExtendedM3u() {
        assertEquals("#EXTM3U\n#EXTINF:61,X - Alpha\nMusic/Chill/01 Alpha.flac\n", M3u.write(listOf(a)))
    }

    @Test
    fun roundTrip() {
        val imported = M3u.resolve(M3u.parse(M3u.write(listOf(b2, a, b))), library)
        assertEquals(listOf("b2", "a", "b"), imported.tracks.map { it.id })
    }

    @Test
    fun matchesPathsFromOtherAppsByTheirTail() {
        val text = """
            #EXTM3U
            /storage/emulated/0/Music/Gym/song.mp3
            C:\Users\me\Music\Chill\01 Alpha.flac
            ../Chill/song.mp3
            http://radio.example/stream
            #EXTINF:200,Nobody - Missing
            Music/None/missing.ogg
        """.trimIndent()
        val imported = M3u.resolve(M3u.parse(text), library)
        assertEquals(listOf("b", "a", "b2"), imported.tracks.map { it.id })
        assertEquals(listOf("Missing"), imported.missing.map { it.title })
    }

    @Test
    fun fallsBackToTitleAndArtist() {
        // Relocation needs the recording's actual duration as well as its title/artist.
        val text = "#EXTINF:180,Y - Beta\nsomewhere/else/renamed.mp3\n"
        assertEquals(listOf("b"), M3u.resolve(M3u.parse(text), library).tracks.map { it.id })
    }

    @Test
    fun nameFromFile() = assertEquals("Road Trip", M3u.nameFromFile("Download/Road Trip.m3u8"))
}
