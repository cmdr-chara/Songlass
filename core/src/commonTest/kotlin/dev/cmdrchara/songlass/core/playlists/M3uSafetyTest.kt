package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M3uSafetyTest {
    private val original = track("canonical-a", "Song", "Artist", durationMs = 61_500)
        .copy(folder = "Music/Album", fileName = "song.flac")
    private fun resolve(text: String, tracks: List<Track> = listOf(original)) =
        M3u.resolve(M3u.parseDetailed(text), tracks)

    @Test
    fun aTiedSuffixNeverDependsOnScanOrder() {
        val other = original.copy(id = "canonical-b", folder = "Other/Album")
        for (scan in listOf(listOf(original, other), listOf(other, original))) {
            val result = resolve("/old/device/Album/song.flac", scan)
            assertTrue(result.tracks.isEmpty())
            assertEquals(1, result.count(M3uIssueKind.Ambiguous))
        }
    }

    @Test
    fun uniqueExactPathWinsOverShorterSuffixAndPreservesOrder() {
        val other = original.copy(id = "b", folder = "Other/Album", title = "Other")
        assertEquals(listOf("b", "canonical-a"), resolve("Other/Album/song.flac\nMusic/Album/song.flac", listOf(original, other)).tracks.map { it.id })
    }

    @Test
    fun knownConflictingFactsPreventReusedPathFromSelectingReplacement() {
        for (replacement in listOf(
            original.copy(title = "Replacement"), original.copy(artist = "Different"), original.copy(durationMs = 80_000),
        )) {
            val result = resolve(M3u.write(listOf(original)), listOf(replacement, original.copy(id = "moved", folder = "Moved")))
            assertTrue(result.tracks.isEmpty())
            assertEquals(1, result.count(M3uIssueKind.Conflict))
        }
    }

    @Test
    fun unknownMetadataDoesNotInvalidateAUniqueExactPath() {
        assertEquals(listOf(original), resolve("Music/Album/song.flac").tracks)
        val unknown = original.copy(title = "", artist = null, durationMs = 0)
        assertEquals(listOf(unknown), resolve(M3u.write(listOf(original)), listOf(unknown)).tracks)
    }

    @Test
    fun relocationRequiresKnownDurationAndUniqueRecordingFacts() {
        val exact = "#EXTINF:61,Artist - Song\nMoved/renamed.flac"
        assertEquals(listOf(original), resolve(exact).tracks)
        assertTrue(resolve(exact, listOf(original, original.copy(id = "duplicate", folder = "Copy"))).tracks.isEmpty())
        assertEquals(1, resolve(exact, listOf(original, original.copy(id = "duplicate", folder = "Copy"))).count(M3uIssueKind.Ambiguous))
        assertTrue(resolve(exact.replace(":61,", ":-1,")).tracks.isEmpty())
        assertTrue(resolve(exact, listOf(original.copy(durationMs = 0))).tracks.isEmpty())
        assertEquals(1, resolve(exact.replace(":61,", ":90,")).count(M3uIssueKind.Conflict))
    }

    @Test
    fun basenameAloneDoesNotOverrideKnownDifferentFolders() {
        val result = resolve("Other/unrelated/song.flac")
        assertTrue(result.tracks.isEmpty())
        assertEquals(1, result.count(M3uIssueKind.Ambiguous))
        assertEquals(listOf(original), resolve("song.flac").tracks)
    }

    @Test
    fun knownFactsCanDisambiguateSuffixButUnknownCompetitorRemainsAmbiguous() {
        val another = original.copy(id = "b", folder = "Other/Album", title = "Another")
        val text = "#EXTINF:61,Artist - Song\n/old/Album/song.flac"
        assertEquals(listOf(original), resolve(text, listOf(original, another)).tracks)
        assertEquals(1, resolve(text, listOf(original, another.copy(title = "", artist = null, durationMs = 0))).count(M3uIssueKind.Ambiguous))
    }

    @Test
    fun duplicateApplicationIdsAreNeverArbitrarilyChosen() {
        val result = resolve("Music/Album/song.flac", listOf(original, original.copy(folder = "Other")))
        assertTrue(result.tracks.isEmpty())
        assertEquals(1, result.count(M3uIssueKind.Ambiguous))
    }

    @Test
    fun repeatedEntriesKeepFirstOccurrenceOrderAndReportDuplicates() {
        val second = original.copy(id = "b", folder = "Second", fileName = "second.flac")
        val result = resolve("Second/second.flac\nMusic/Album/song.flac\nSecond/second.flac\nMusic/Album/song.flac", listOf(original, second))
        assertEquals(listOf("b", "canonical-a"), result.tracks.map { it.id })
        assertEquals(2, result.duplicates)
    }

    @Test
    fun localFileUrisDecodeUtf8OnceWithoutTreatingPlusAsSpace() {
        val special = original.copy(fileName = "Café + 100%20.flac")
        assertEquals(listOf(special), resolve("FILE://LOCALHOST/storage/Music/Album/Caf%C3%A9%20+%20100%2520.flac", listOf(special)).tracks)
        assertEquals(listOf(special), resolve("Music/Album/Café + 100%20.flac", listOf(special)).tracks)
    }

    @Test
    fun remoteAuthoritiesAndStreamsAreExplicitUnsupportedEntries() {
        val result = resolve("file://server/Music/Album/song.flac\nhttps://example.test/song.flac\ncontent://media/external/audio/media/7\n\\\\server\\Music\\song.flac")
        assertTrue(result.tracks.isEmpty())
        assertEquals(4, result.count(M3uIssueKind.Unsupported))
    }

    @Test
    fun invalidEscapesNullAndEncodedLineBreaksAreRejected() {
        val result = resolve("file:///song%GG.flac\nfile:///song%00.flac\nfile:///song%0A.flac\nfile:///song%0D.flac\nfile:///song%C3.flac\nfile:///song.flac?download=1")
        assertTrue(result.tracks.isEmpty())
        assertEquals(6, result.count(M3uIssueKind.Malformed))
    }

    @Test
    fun malformedMetadataCannotBeDiscardedToAcceptAReusedPath() {
        val result = resolve("#EXTINF:garbage,Old - Recording\nMusic/Album/song.flac")
        assertTrue(result.tracks.isEmpty())
        assertEquals(1, result.count(M3uIssueKind.Malformed))
        assertEquals(1, resolve("#EXTINF:61,Artist - Song").count(M3uIssueKind.Malformed))
    }

    @Test
    fun metadataSeparatorsAndCommentLikeNamesRoundTrip() {
        val special = original.copy(artist = "Artist - Duo", title = "Song - Part 2", folder = null, fileName = " #track.flac")
        assertEquals(listOf(special), resolve(M3u.write(listOf(special)), listOf(special)).tracks)
    }

    @Test
    fun exportedPathsCannotInjectPlaylistEntries() {
        val invalid = original.copy(fileName = "song.flac\r\nMusic/Other.flac")
        val escaped = original.copy(title = "Song\r\nInjected", artist = "Artist\rOther")
        val result = M3u.writeDetailed(listOf(invalid, original.copy(fileName = null), escaped))
        assertEquals(1, result.exported)
        assertEquals(2, result.skipped)
        assertFalse(result.text.contains('\r'))
        assertEquals(1, M3u.parse(result.text).size)
    }

    @Test
    fun strictUtf8AndNamedLegacyEncodingDoNotSilentlyCorruptPaths() {
        val latin1 = byteArrayOf('C'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xe9.toByte())
        assertEquals("Café", M3u.decodeText(latin1, "old.M3U"))
        assertNull(M3u.decodeText(latin1, "current.m3u8"))
        assertNull(M3u.decodeText(latin1, null))
        assertEquals("Café", M3u.decodeText("Café".encodeToByteArray(), "current.m3u8"))
        assertNull(M3u.decodeText(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 65, 0), "old.m3u"))
    }

    @Test
    fun limitsRejectTheWholeDocumentRatherThanImportingATruncatedPrefix() {
        assertEquals(M3uFileFailure.TooLarge, M3u.parseDetailed("a".repeat(M3u.MAX_FILE_BYTES + 1)).failure)
        val many = M3u.parseDetailed("song.flac\n".repeat(M3u.MAX_ENTRIES + 1))
        assertEquals(M3uFileFailure.TooManyEntries, many.failure)
        assertTrue(many.entries.isEmpty())
        assertTrue(M3u.resolve(many, listOf(original)).tracks.isEmpty())
    }

    @Test
    fun fullScanExportIncludesFilteredTracksButRetainsMissingReferences() {
        val hidden = original.copy(id = "hidden", folder = "Hidden", fileName = "hidden.flac")
        val playlist = UserPlaylist("p", "Mix", listOf("hidden", "gone", original.id), 1)
        val visible = MusicLibrary(listOf(original))
        assertEquals(listOf(original), playlist.tracksIn(visible))
        val exported = M3u.writePlaylist(playlist, listOf(original, hidden))
        assertEquals(1, exported.missing)
        assertEquals(listOf("hidden", original.id), resolve(exported.output.text, listOf(original, hidden)).tracks.map { it.id })
        assertEquals(listOf("hidden", "gone", original.id), playlist.trackIds)
    }

    @Test
    fun paddingCannotTurnARemoteUriIntoALocalSuffixMatch() {
        val result = resolve("  https://remote/Music/Album/song.flac\n  FILE://server/Music/Album/song.flac")
        assertTrue(result.tracks.isEmpty())
        assertEquals(2, result.count(M3uIssueKind.Unsupported))
        assertEquals(listOf(original), resolve("  file://localhost/Music/Album/song.flac").tracks)
    }

    @Test
    fun schemeLikeLocalFileNamesRoundTripAsPaths() {
        for (name in listOf("note:song.flac", "file:track.flac", "  https:local.flac")) {
            val local = original.copy(folder = null, fileName = name)
            val exported = M3u.writeDetailed(listOf(local))
            assertEquals(1, exported.exported)
            assertEquals(listOf(local), resolve(exported.text, listOf(local)).tracks)
        }
    }

}
