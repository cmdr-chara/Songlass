package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.*

class SmartPlaylistsTest {
    private val now = 100L * 86_400_000
    private val tracks = listOf(
        track("a", "Alpha", artist = "One", folder = "Music/Chill").copy(dateAddedMs = now - 86_400_000),
        track("b", "Beta", artist = "Two", folder = "Music/Chill/Live").copy(dateAddedMs = now - 3 * 86_400_000),
        track("c", "Gamma", artist = "One", folder = "Music/Chillout").copy(dateAddedMs = now - 20 * 86_400_000),
        track("d", "Delta", artist = "One", folder = "Music/Chill"),
        track("e", "Future", artist = "One", folder = "Music/Chill").copy(dateAddedMs = now + 86_400_000),
    )
    private val history = HistorySnapshot(playCounts = mapOf("a" to 2, "b" to 1))

    @Test fun optionalEmptyFieldsAndUserTextSurviveProcessRecreation() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 42 }
        val empty = playlists.save(null, "All songs", SmartRules())
        val partial = playlists.save(null, "Unplayed", SmartRules(playStatus = SmartPlayStatus.Never, artist = "Yugo Kanno", limit = 25))
        val text = SmartRules(artist = "A:Guest\u001FSecond", folder = "Music/Chill")
        val unusual = playlists.save(null, "Text", text)
        val restored = SmartPlaylists(store) { 43 }
        assertEquals(SmartRules(), restored.get(empty)?.rules)
        assertEquals(playlists.get(partial), restored.get(partial))
        assertEquals(text, restored.get(unusual)?.rules)
        store.putString("smart.$empty.rules", "v1:5:false3:Any9999999999:")
        assertNull(SmartPlaylists(store) { 44 }.get(empty))
    }

    @Test fun andRulesRespectUnknownDatesMultipleArtistsAndFolderBoundaries() {
        val library = MusicLibrary(tracks)
        val rules = SmartRules(favoritesOnly = true, addedWithinDays = 7, folder = "Music/Chill", sort = SmartSort.MostPlayed)
        assertEquals(listOf("a", "b"), rules.resolve(library, history, setOf("a", "b", "c", "d", "e"), now).map { it.id })
        assertEquals(listOf("b"), rules.copy(artist = "two").resolve(library, history, setOf("a", "b"), now).map { it.id })
        assertEquals(emptyList(), rules.copy(playStatus = SmartPlayStatus.Never).resolve(library, history, setOf("a", "b"), now))
        val duet = tracks[0].copy(artists = listOf("One", "Guest"))
        assertEquals(listOf(duet), SmartRules(artist = "guest").resolve(MusicLibrary(listOf(duet)), history, emptySet(), now))
    }

    @Test fun membershipChangesWithLibraryFavoritesAndListeningHistory() {
        val rules = SmartRules(favoritesOnly = true, playStatus = SmartPlayStatus.Never)
        val library = MusicLibrary(tracks)
        assertEquals(listOf("d"), rules.resolve(library, history, setOf("a", "d"), now).map { it.id })
        assertEquals(emptyList(), rules.resolve(library, history.copy(playCounts = history.playCounts + ("d" to 1)), setOf("d"), now))
        assertEquals(emptyList(), rules.resolve(MusicLibrary(tracks.filterNot { it.id == "d" }), history, setOf("d"), now))
        assertEquals(listOf("b"), SmartRules(sort = SmartSort.Newest, addedWithinDays = 7, limit = 1, playStatus = SmartPlayStatus.Never)
            .resolve(library, HistorySnapshot(playCounts = mapOf("a" to 1)), emptySet(), now).map { it.id })
    }

    @Test fun rulesPersistAndDeletingDoesNotAffectOtherPlaylists() {
        val store = InMemoryKeyValueStore()
        val playlists = SmartPlaylists(store) { 7 }
        val rules = SmartRules(true, SmartPlayStatus.Played, 30, "One", "Music/Chill", SmartSort.MostPlayed, 25)
        val a = playlists.save(null, " Mine ", rules)
        val b = playlists.save(null, "Other", SmartRules())
        val restored = SmartPlaylists(store) { 7 }
        assertEquals(rules, restored.get(a)?.rules)
        assertEquals("Mine", restored.get(a)?.name)
        restored.save(a, "Renamed", rules.copy(limit = 10))
        restored.delete(b)
        assertEquals(listOf("Renamed"), SmartPlaylists(store) { 8 }.playlists.value.map { it.name })
        assertFailsWith<IllegalArgumentException> { restored.save(null, "", rules) }
        assertFailsWith<IllegalArgumentException> { restored.save(null, "Bad", rules.copy(limit = -1)) }
    }
}
