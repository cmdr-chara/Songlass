package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M3uDecimalDurationTest {
    private val original = track("a", "Song", "Artist", durationMs = 61_500)
        .copy(folder = "Music/Album", fileName = "song.flac")

    private fun document(duration: String, path: String = "Music/Album/song.flac") =
        M3u.parseDetailed("#EXTM3U\n#EXTINF:$duration,Artist - Song\n$path\n")

    private fun resolve(duration: String, tracks: List<Track> = listOf(original), path: String = "Music/Album/song.flac") =
        M3u.resolve(document(duration, path), tracks)

    @Test
    fun decimalExtinfImportsAnExactPathAndRetainsItsMillisecondValue() {
        val parsed = document("61.5")
        assertTrue(parsed.issues.isEmpty())
        assertEquals(61_500L, parsed.entries.single().durationMs)
        assertEquals(100L, parsed.entries.single().durationToleranceMs)
        assertEquals(listOf(original), M3u.resolve(parsed, listOf(original)).tracks)
    }

    @Test
    fun relocationUsesDecimalPrecisionWithoutGuessingBetweenNearRecordings() {
        val different = original.copy(id = "b", durationMs = 61_900, folder = "Other")
        for (scan in listOf(listOf(original, different), listOf(different, original))) {
            assertEquals(listOf(original), resolve("61.5", scan, "Moved/renamed.flac").tracks)
            assertEquals(1, resolve("61", scan, "Moved/renamed.flac").count(M3uIssueKind.Ambiguous))
        }
    }

    @Test
    fun decimalConflictAtKnownPathDoesNotFallBackToAMovedOriginal() {
        val replacement = original.copy(id = "replacement", durationMs = 61_900)
        val moved = original.copy(folder = "Moved")
        val imported = resolve("61.500", listOf(replacement, moved))
        assertTrue(imported.tracks.isEmpty())
        assertEquals(1, imported.count(M3uIssueKind.Conflict))
    }

    @Test
    fun comparisonAllowsOnlyTheDeclaredQuantization() {
        assertEquals(1, resolve("61.5", listOf(original.copy(durationMs = 61_599))).tracks.size)
        assertEquals(1, resolve("61.5", listOf(original.copy(durationMs = 61_601))).count(M3uIssueKind.Conflict))
        assertEquals(10L, document("61.50").entries.single().durationToleranceMs)
        val precise = document("61.5004")
        assertEquals(61_500L, precise.entries.single().durationMs)
        assertEquals(1L, precise.entries.single().durationToleranceMs)
        assertEquals(1, M3u.resolve(precise, listOf(original.copy(durationMs = 61_502))).count(M3uIssueKind.Conflict))
    }

    @Test
    fun aPositiveSubSecondDurationCanIdentifyARelocatedShortTrack() {
        val short = original.copy(durationMs = 500)
        assertEquals(listOf(short), resolve(".5", listOf(short), "Elsewhere/renamed.flac").tracks)
        assertEquals(listOf(short), resolve("+0.500", listOf(short), "Elsewhere/renamed.flac").tracks)
    }

    @Test
    fun unknownDurationSentinelsStayUnknownAndCannotProveRelocation() {
        for (duration in listOf("-1", "-1.0", "0", "0.000", "-0.0")) {
            assertEquals(listOf(original), resolve(duration).tracks, duration)
            val parsed = document(duration).entries.single()
            assertNull(parsed.durationMs, duration)
            assertNull(parsed.durationSec, duration)
            assertTrue(resolve(duration, path = "Moved/renamed.flac").tracks.isEmpty(), duration)
        }
    }

    @Test
    fun nonFiniteOverflowNegativeAndNumericGarbageCannotLoseTheirConflictMetadata() {
        for (duration in listOf(
            "NaN", "Infinity", "+Infinity", "-Infinity", "1e309", "6.15e1", "61junk", "61 junk",
            "61.5oops", "61.5.0", "-0.5", "-1.001", "2147483648", "2147483647.1", "", "-", ".",
        )) {
            val imported = resolve(duration)
            assertTrue(imported.tracks.isEmpty(), duration)
            assertEquals(1, imported.count(M3uIssueKind.Malformed), duration)
        }
    }

    @Test
    fun maximumAcceptedDurationDoesNotOverflowDuringMatching() {
        val maximum = original.copy(durationMs = Int.MAX_VALUE.toLong() * 1_000)
        val parsed = document("2147483647.000")
        assertEquals(maximum.durationMs, parsed.entries.single().durationMs)
        assertEquals(listOf(maximum), M3u.resolve(parsed, listOf(maximum)).tracks)
        assertEquals(1, M3u.resolve(parsed, listOf(original)).count(M3uIssueKind.Conflict))
    }
}
