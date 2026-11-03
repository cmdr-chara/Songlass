package dev.cmdrchara.songlass.core.cue

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.isSameFileAs
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.playlists.M3u
import kotlin.test.*

class CueSheetTest {
    private val source = Track("stable:file", "Album image", "content://audio/7", durationMs = 180_000,
        fileName = "album.flac", folder = "Music/Test")
    private val text = """
        PERFORMER "Artist"
        TITLE "Album"
        FILE "Music/Test/album.flac" WAVE
          TRACK 01 AUDIO
            TITLE "First"
            INDEX 01 00:00:00
          TRACK 02 AUDIO
            TITLE "Second"
            INDEX 00 00:59:00
            INDEX 01 01:00:37
    """.trimIndent()

    @Test fun framePrecisionAndMetadataProduceDifferentPlayableSegments() {
        val sheet = assertNotNull(CueSheets.parse("\uFEFF$text"))
        val tracks = assertNotNull(CueSheets.resolve(sheet, listOf(source)))
        assertEquals(listOf("First", "Second"), tracks.map { it.title })
        assertEquals(listOf(0L, 60_493L), tracks.map { it.clipStartMs })
        assertEquals(listOf(60_493L, 180_000L), tracks.map { it.clipEndMs })
        assertEquals(listOf(60_493L, 119_507L), tracks.map { it.durationMs })
        assertTrue(tracks.all { it.uri == source.uri && it.album == "Album" && it.artist == "Artist" })
        assertFalse(tracks[0].isSameFileAs(tracks[1]))
        assertFalse(tracks[0].isSameFileAs(tracks[0].copy(clipEndMs = 60_500)))
        assertEquals(2, M3u.writeDetailed(tracks).skipped)
    }

    @Test fun invalidOrAmbiguousBoundariesNeverReturnPartialAlbums() {
        for (bad in listOf(text.replace("01:00:37", "01:60:37"), text.replace("01:00:37", "01:00:75"),
            text.replace("01:00:37", "00:00:00"), text.replace("INDEX 01 01:00:37", "INDEX 00 01:00:37"),
            text.replace("TRACK 02 AUDIO", "TRACK 02 MODE1/2352"), "x".repeat(CueSheets.MAX_CHARS + 1))) {
            assertNull(CueSheets.parse(bad))
        }
        val sheet = assertNotNull(CueSheets.parse(text))
        assertNull(CueSheets.resolve(sheet, emptyList()))
        assertNull(CueSheets.resolve(sheet, listOf(source, source.copy(id = "another"))))
        assertNull(CueSheets.resolve(sheet, listOf(source.copy(durationMs = 50_000))))
    }

    @Test fun multipleFilesResolveOnceAndMissingFilesRejectTheWholeSheet() {
        val sheet = assertNotNull(CueSheets.parse(text + "\nFILE \"disc2.flac\" WAVE\nTRACK 03 AUDIO\nTITLE \"Third\"\nINDEX 01 00:00:00"))
        val second = source.copy(id = "second", uri = "content://audio/8", fileName = "disc2.flac", durationMs = 20_000)
        assertNull(CueSheets.resolve(sheet, listOf(source)))
        val cuts = assertNotNull(CueSheets.resolve(sheet, listOf(source, second)))
        assertEquals(3, cuts.size)
        assertEquals(second.uri, cuts.last().uri)
        assertEquals(20_000L, cuts.last().durationMs)
        val m3u = M3u.resolve(M3u.parseDetailed("Music/Test/album.flac"), listOf(source, second) + cuts)
        assertEquals(listOf(source), m3u.tracks)
        assertTrue(m3u.issues.isEmpty())
    }

    @Test fun persistenceKeepsOriginalFilesAndRebindsToFreshLibraryUris() {
        val store = InMemoryKeyValueStore()
        val catalog = CueCatalog(store)
        assertTrue(catalog.save("Album", text))
        assertTrue(catalog.save("Album", text))
        val loaded = CueCatalog(store).expand(listOf(source))
        assertEquals(3, loaded.size)
        assertEquals(source, loaded.first())
        val changed = source.copy(uri = "content://audio/99")
        val rescanned = CueCatalog(store).expand(listOf(changed))
        assertEquals(loaded.map { it.id }, rescanned.map { it.id })
        assertTrue(rescanned.all { it.uri == changed.uri })
        assertEquals(emptyList(), catalog.expand(emptyList()))
        val saved = catalog.entries().single()
        assertTrue(catalog.remove(saved.id))
        assertEquals(listOf(source), catalog.expand(listOf(source)))
        assertFalse(catalog.remove(saved.id))
    }
}
