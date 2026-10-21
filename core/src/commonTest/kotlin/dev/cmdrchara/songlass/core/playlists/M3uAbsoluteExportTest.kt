package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M3uAbsoluteExportTest {
    private val original = track("a", "Song", "Artist", durationMs = 61_500).copy(
        folder = "Music/Album", fileName = "song.flac", uri = "content://media/external/audio/media/4",
        absoluteFilePath = "/mnt/shared/volume-a/Music/Album/song.flac",
    )

    @Test
    fun safExportUsesTheRealAbsolutePathWithoutGuessingTheOutputDirectory() {
        val exported = M3u.writeDetailed(listOf(original), M3uPathMode.Absolute)
        assertEquals(1, exported.exported)
        assertEquals(0, exported.skipped)
        assertEquals("#EXTM3U\n#EXTINF:61,Artist - Song\n/mnt/shared/volume-a/Music/Album/song.flac\n", exported.text)
        assertEquals(original.absoluteFilePath, M3u.parse(exported.text).single().path)
        assertEquals("content://media/external/audio/media/4", original.uri)
        assertEquals("Music/Album/song.flac", original.path)
    }

    @Test
    fun identicalStorageRelativePathsOnDifferentVolumesRoundTripInFileOrder() {
        val other = original.copy(id = "b", absoluteFilePath = "/removable/volume-b/Music/Album/song.flac")
        val exported = M3u.writeDetailed(listOf(other, original), M3uPathMode.Absolute)
        for (scan in listOf(listOf(original, other), listOf(other, original))) {
            val imported = M3u.resolve(M3u.parseDetailed(exported.text), scan)
            assertEquals(listOf("b", "a"), imported.tracks.map { it.id })
            assertTrue(imported.issues.isEmpty())
            assertEquals(1, M3u.resolve(M3u.parseDetailed("Music/Album/song.flac"), scan).count(M3uIssueKind.Ambiguous))
        }
    }

    @Test
    fun anAbsoluteKnownConflictNeverRelocatesToADifferentVolume() {
        val replacement = original.copy(id = "replacement", title = "Different")
        val elsewhere = original.copy(absoluteFilePath = "/removable/volume-b/Music/Album/song.flac")
        val exported = M3u.writeDetailed(listOf(original), M3uPathMode.Absolute)
        val imported = M3u.resolve(M3u.parseDetailed(exported.text), listOf(replacement, elsewhere))
        assertTrue(imported.tracks.isEmpty())
        assertEquals(1, imported.count(M3uIssueKind.Conflict))
    }

    @Test
    fun literalAbsoluteFileNamesKeepPercentPlusUnicodeAndUriPunctuation() {
        val special = original.copy(fileName = "Café + 100%20 #?.flac",
            absoluteFilePath = "/mnt/shared/volume-a/Music/Album/Café + 100%20 #?.flac")
        val exported = M3u.writeDetailed(listOf(special), M3uPathMode.Absolute)
        assertEquals(special.absoluteFilePath, M3u.parse(exported.text).single().path)
        assertEquals(listOf(special), M3u.resolve(M3u.parseDetailed(exported.text), listOf(special)).tracks)
        val encoded = "file:///mnt/shared/volume-a/Music/Album/Caf%C3%A9%20%2B%20100%2520%20%23%3F.flac"
        assertEquals(listOf(special), M3u.resolve(M3u.parseDetailed(encoded), listOf(special)).tracks)
    }

    @Test
    fun absentRelativeRemoteAndUnsafeAddressesAreOmittedWithoutFallback() {
        val unusable = listOf(
            null, "Music/Album/song.flac", "content://media/4", "file:///Music/Album/song.flac", "//server/song.flac",
            "/", "/Music/", "/Music/..", "/Music/song\nother.flac", "/Music/song\r.flac", "/Music/a\u0000.flac", "/Music/a\\b.flac",
        ).mapIndexed { index, path -> original.copy(id = "bad-$index", absoluteFilePath = path) }
        val exported = M3u.writeDetailed(unusable + original, M3uPathMode.Absolute)
        assertEquals(1, exported.exported)
        assertEquals(unusable.size, exported.skipped)
        assertEquals(listOf(original), M3u.resolve(M3u.parseDetailed(exported.text), unusable + original).tracks)
    }

    @Test
    fun omissionCountsDistinguishMissingReferencesAndUnavailableAbsolutePaths() {
        val withoutPath = original.copy(id = "no-path", absoluteFilePath = null)
        val playlist = UserPlaylist("p", "Mix", listOf("no-path", "gone", "a"), 1)
        val exported = M3u.writePlaylist(playlist, listOf(original, withoutPath), M3uPathMode.Absolute)
        assertEquals(1, exported.missing)
        assertEquals(1, exported.output.skipped)
        assertEquals(1, exported.output.exported)
        assertEquals(listOf("no-path", "gone", "a"), playlist.trackIds)
    }

    @Test
    fun aRealAbsolutePathDoesNotRequireInventingMissingRelativeMetadata() {
        val withoutRelative = original.copy(folder = null, fileName = null)
        val exported = M3u.writeDetailed(listOf(withoutRelative), M3uPathMode.Absolute)
        assertEquals(1, exported.exported)
        assertEquals(listOf(withoutRelative), M3u.resolve(M3u.parseDetailed(exported.text), listOf(withoutRelative)).tracks)
    }
}
