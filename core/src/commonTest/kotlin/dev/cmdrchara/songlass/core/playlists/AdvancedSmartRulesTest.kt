package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.home.HomeCustomization
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.ReleaseDate
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.*

class AdvancedSmartRulesTest {
    private val now = 100L * 86_400_000
    private val tracks = listOf(
        track("a", "Alpha", artist = "One", folder = "Music/Live").copy(genre = "Alternative Rock", releaseDate = ReleaseDate(1990), durationMs = 120000),
        track("b", "Beta", artist = "Two", folder = "Music/Live/Sub").copy(genre = "Pop", releaseDate = ReleaseDate(2000), durationMs = 180000),
        track("c", "Gamma", artist = "One", folder = "Music/Lively").copy(genre = "Rock", releaseDate = ReleaseDate(2010), durationMs = 240001),
        track("d", "Unknown", artist = "Guest").copy(durationMs = 0),
    )
    private val library = MusicLibrary(tracks)
    private fun ids(rules: SmartRules, favorites: Set<String> = emptySet(), votes: Map<String, Int> = emptyMap()) =
        rules.resolve(library, HistorySnapshot(), favorites, now, votes).map { it.id }

    @Test fun anyIncludesOnlyEnabledRulesAndExclusionsAlwaysWin() {
        val any = SmartRules(favoritesOnly = true, genre = "rock", match = SmartMatch.Any)
        assertEquals(listOf("a", "b", "c"), ids(any, setOf("b")))
        assertEquals(emptyList(), ids(any.copy(match = SmartMatch.All), setOf("b")))
        assertEquals(listOf("a", "c"), ids(any.copy(excludeFolder = " Music/Live/Sub/ "), setOf("b", "c")))
        assertEquals(listOf("b"), ids(any.copy(excludeGenre = "ROCK"), setOf("b", "c")))
        assertEquals(tracks.map { it.id }, ids(SmartRules(match = SmartMatch.Any)))
    }

    @Test fun inclusiveRangesRequireKnownMetadata() {
        assertEquals(listOf("a", "b"), ids(SmartRules(yearFrom = 1990, yearTo = 2000, durationFromSeconds = 120, durationToSeconds = 180)))
        assertEquals(listOf("b"), ids(SmartRules(yearFrom = 2000, yearTo = 2000)))
        assertEquals(listOf("b", "c"), ids(SmartRules(match = SmartMatch.Any, yearFrom = 2000, yearTo = 2010)))
        assertEquals(listOf("a", "b"), ids(SmartRules(match = SmartMatch.Any, durationFromSeconds = 120, durationToSeconds = 180)))
        assertEquals(listOf("a", "b"), ids(SmartRules(durationToSeconds = 240)))
        assertEquals(listOf("a", "b", "c"), ids(SmartRules(durationFromSeconds = 0)))
        assertEquals(listOf("a", "b", "c"), ids(SmartRules(yearTo = 9999)))
        assertEquals(listOf("a", "b", "c"), ids(SmartRules(genre = "rock", durationFromSeconds = 120, match = SmartMatch.Any)))
    }

    @Test fun exclusionsUseMultiArtistCreditsAndFolderSegments() {
        val duet = tracks[0].copy(artists = listOf("One", "Guest"))
        assertTrue(SmartRules(excludeArtist = "guest").resolve(MusicLibrary(listOf(duet)), HistorySnapshot(), emptySet(), now, emptyMap()).isEmpty())
        assertEquals(listOf("c", "d"), ids(SmartRules(excludeFolder = "Music/Live")))
        assertEquals(listOf("a", "b", "c", "d"), ids(SmartRules(excludeGenre = "ambient")))
        assertEquals(listOf("b", "d"), ids(SmartRules(excludeGenre = "rock")))
    }

    @Test fun invalidDraftsDoNotTurnIntoUnboundedRules() {
        assertNull(SmartRuleInput(days = "a").applyTo(SmartRules()))
        assertNull(SmartRuleInput(limit = "0").applyTo(SmartRules()))
        assertNull(SmartRuleInput(yearFrom = "2001", yearTo = "1999").applyTo(SmartRules()))
        assertNull(SmartRuleInput(yearTo = "10000").applyTo(SmartRules()))
        assertNull(SmartRuleInput(durationFromSeconds = "121", durationToSeconds = "120").applyTo(SmartRules()))
        assertNull(SmartRuleInput(durationToSeconds = "2147483648").applyTo(SmartRules()))
        assertEquals(SmartRules(yearFrom = 1990, durationToSeconds = 180), SmartRuleInput(yearFrom = " 1990 ", durationToSeconds = "180").applyTo(SmartRules()))
        assertTrue(ids(SmartRules(yearFrom = 2001, yearTo = 1990)).isEmpty())
        assertFailsWith<IllegalArgumentException> { SmartPlaylists(InMemoryKeyValueStore()) { now }.save(null, "Invalid", SmartRules(yearTo = 0)) }
    }

    @Test fun aMinimumRatingNarrowsInclusionsAndCombinesWithAllOrAny() {
        // Only "a" and "b" are rated; "c" and "d" count as unrated (zero stars).
        val votes = mapOf("a" to 5, "b" to 2)
        assertEquals(listOf("a"), ids(SmartRules(minimumRating = 3), votes = votes))
        assertEquals(listOf("a", "b"), ids(SmartRules(minimumRating = 1), votes = votes))
        assertEquals(listOf("a"), ids(SmartRules(minimumRating = 5), votes = votes))
        assertEquals(emptyList(), ids(SmartRules(minimumRating = 1)))
        // All: the rating is one more inclusion, so the other rule must match too.
        assertEquals(listOf("a"), ids(SmartRules(minimumRating = 4, genre = "rock"), votes = votes))
        assertEquals(emptyList(), ids(SmartRules(minimumRating = 4, genre = "pop"), votes = votes))
        // Any: either rule is enough, and an unrated but matching song still qualifies.
        assertEquals(listOf("a", "b"), ids(SmartRules(match = SmartMatch.Any, minimumRating = 4, genre = "pop"), votes = votes))
        // An exclusion always vetoes, whatever the rating.
        assertEquals(listOf("b"), ids(SmartRules(minimumRating = 1, excludeGenre = "rock"), votes = votes))
        // Rules without a minimum rating keep ignoring the votes.
        assertEquals(tracks.map { it.id }, ids(SmartRules(), votes = votes))
    }

    @Test fun anOutOfRangeMinimumRatingInvalidatesTheWholeRule() {
        for (stars in listOf(0, -1, 6)) {
            val rules = SmartRules(minimumRating = stars)
            assertFalse(rules.isValid)
            assertTrue(rules.resolve(library, HistorySnapshot(), emptySet(), now, mapOf("a" to 5)).isEmpty())
            assertFailsWith<IllegalArgumentException> { SmartPlaylists(InMemoryKeyValueStore()) { now }.save(null, "Bad", rules) }
            assertFailsWith<IllegalArgumentException> { SmartRulesCodec.encode(rules) }
        }
    }

    @Test fun aMinimumRatingIsWrittenAsV3WhileTheOlderLayoutsStayTheSame() {
        val basic = SmartRules(artist = "One")
        val advanced = SmartRules(genre = "Rock", match = SmartMatch.Any)
        val rated = SmartRules(minimumRating = 4)
        assertTrue(SmartRulesCodec.encode(basic).startsWith("v1:"))
        assertTrue(SmartRulesCodec.encode(advanced).startsWith("v2:"))
        val encoded = SmartRulesCodec.encode(rated)
        assertTrue(encoded.startsWith("v3:"))
        assertEquals(rated, SmartRulesCodec.decode(encoded))

        val full = SmartRules(genre = "Rock", match = SmartMatch.Any, yearFrom = 1990, yearTo = 2010,
            durationFromSeconds = 120, durationToSeconds = 240, excludeArtist = "Guest", excludeFolder = "Music/Live",
            excludeGenre = "Pop", limit = 2, minimumRating = 3)
        val fullText = SmartRulesCodec.encode(full)
        assertTrue(fullText.startsWith("v3:"))
        assertEquals(full, SmartRulesCodec.decode(fullText))
        val store = InMemoryKeyValueStore()
        val id = SmartPlaylists(store) { now }.save(null, "Rated", full)
        assertEquals(full, SmartPlaylists(store) { now + 1 }.get(id)?.rules)

        // Truncated, overlong and mislabelled records are rejected, not silently widened.
        assertNull(SmartRulesCodec.decode(encoded.dropLast(1)))
        assertNull(SmartRulesCodec.decode(encoded + "ignored"))
        assertNull(SmartRulesCodec.decode(fullText.replaceFirst("v3:", "v2:")))
        // The rating field itself must be a whole 1..5 value.
        for (bad in listOf("1:0", "1:6", "0:")) {
            assertNull(SmartRulesCodec.decode(encoded.dropLast(3) + bad))
        }
    }

    @Test fun bothPreviousFormatsRemainReadableAndBasicRulesStillWriteV1() {
        val v1 = "v1:5:false3:Any0:3:One11:Music/Chill5:Title0:"
        val expected = SmartRules(artist = "One", folder = "Music/Chill")
        assertEquals(expected, SmartRulesCodec.decode(v1))
        assertEquals(expected, SmartRulesCodec.decode("false\u001FAny\u001F\u001FOne\u001FMusic/Chill\u001FTitle\u001F"))
        assertEquals(v1, SmartRulesCodec.encode(expected))
    }

    @Test fun advancedRulesSurviveProcessRecreationAndMalformedDataIsRejected() {
        val rules = SmartRules(genre = "Rock", match = SmartMatch.Any, yearFrom = 1990, yearTo = 2010,
            durationFromSeconds = 120, durationToSeconds = 240, excludeArtist = "A:Guest\u001F🎧", excludeFolder = "Music/Live", excludeGenre = "Pop", limit = 2)
        val store = InMemoryKeyValueStore();val repo = SmartPlaylists(store) { now };val id = repo.save(null, "Advanced", rules)
        val encoded = store.getString("smart.$id.rules")!!
        assertTrue(encoded.startsWith("v2:"))
        assertEquals(rules, SmartPlaylists(store) { now + 1 }.get(id)?.rules)
        assertNull(SmartRulesCodec.decode(encoded.dropLast(1)))
        assertNull(SmartRulesCodec.decode(encoded + "ignored"))
        assertNull(SmartRulesCodec.decode("v2:999999999999:bad"))
        assertNull(SmartRulesCodec.decode("v9:unsupported"))
        assertEquals(listOf("a", "c"), ids(rules.copy(excludeFolder = "")))
    }

    @Test fun advancedRulesAndHomePlaylistReferencesRoundTripThroughBackup() {
        val source = InMemoryKeyValueStore()
        val personal = UserPlaylists(source) { now }.create("Personal", listOf("a", "d"))
        val rules = SmartRules(match = SmartMatch.Any, genre = "Rock", yearFrom = 2000, durationToSeconds = 180, excludeFolder = "Music/Live/Sub")
        val smart = SmartPlaylists(source) { now }.save(null, "Smart", rules)
        HomeCustomization(source).apply { addShortcut(PlayContext.UserPlaylist(personal)); addShortcut(PlayContext.SmartPlaylist(smart)) }
        // The range tests above deliberately include an unknown duration. A cross-device
        // backup needs known recording facts; keep the round-trip assertions on that contract.
        val backedUpTracks = tracks.map { if (it.durationMs == 0L) it.copy(durationMs = 60_000) else it }
        val text = DataBackup(source) { now }.export(backedUpTracks).text
        val restored = InMemoryKeyValueStore();val backup = DataBackup(restored) { now }
        backup.apply(backup.preview(text, backedUpTracks))
        assertEquals(rules, SmartPlaylists(restored) { now }.get(smart)?.rules)
        assertEquals(listOf(PlayContext.UserPlaylist(personal), PlayContext.SmartPlaylist(smart)), HomeCustomization(restored).state.value.shortcuts)
        assertEquals(listOf("a", "d"), UserPlaylists(restored) { now }.get(personal)?.trackIds)
    }
}
