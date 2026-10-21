package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListeningHistoryTest {
    private var now = 1_000L
    private val clock = { now }

    @Test
    fun countsPlaysAndRemembersLastTime() {
        val history = ListeningHistory(InMemoryKeyValueStore(), clock)
        history.recordPlay("a", null)
        now = 2_000
        history.recordPlay("a", null)
        history.recordPlay("b", null)
        val s = history.state.value
        assertEquals(mapOf("a" to 2, "b" to 1), s.playCounts)
        assertEquals(2_000L, s.lastPlayedMs["a"])
        assertEquals(listOf("b", "a", "a"), s.recentPlays)
        assertEquals(3, s.totalPlays)
    }

    @Test
    fun recentContextsAreNewestFirstAndDeduplicated() {
        val history = ListeningHistory(InMemoryKeyValueStore(), clock)
        val album = PlayContext.Album("X", "Y")
        history.recordPlay("1", album)
        now = 2_000
        history.recordPlay("2", PlayContext.Playlist("Music/Chill"))
        now = 3_000
        history.recordPlay("3", album)
        assertEquals(listOf(album.key, PlayContext.Playlist("Music/Chill").key), history.state.value.recentContexts.map { it.context.key })
        assertEquals(3_000L, history.state.value.recentContexts.first().atMs)
    }

    @Test
    fun historySurvivesRecreation() {
        val store = InMemoryKeyValueStore()
        ListeningHistory(store, clock).apply {
            recordPlay("a", PlayContext.Album("X", null))
            recordPlay("b", PlayContext.Favorites)
            recordPlay("a", PlayContext.Artist("Z"))
        }
        val restored = ListeningHistory(store, clock).state.value
        assertEquals(mapOf("a" to 2, "b" to 1), restored.playCounts)
        assertEquals(
            listOf(PlayContext.Artist("Z"), PlayContext.Favorites, PlayContext.Album("X", null)),
            restored.recentContexts.map { it.context },
        )
    }

    @Test
    fun malformedStoredValuesAreIgnored() {
        val store = InMemoryKeyValueStore().apply {
            putString("history.contexts", "garbage")
            putString("history.counts", "x\u001Enot-a-number")
        }
        val s = ListeningHistory(store, clock).state.value
        assertTrue(s.recentContexts.isEmpty())
        assertTrue(s.playCounts.isEmpty())
    }

    @Test
    fun contextKeysRoundTrip() {
        val contexts = listOf(PlayContext.Album("A", null), PlayContext.Album("A", "B"), PlayContext.Playlist("p"), PlayContext.Artist(null), PlayContext.Favorites)
        for (c in contexts) assertEquals(c, PlayContext.fromKey(c.key))
        assertNull(PlayContext.fromKey("hologram\u001Dx"))
    }

}
