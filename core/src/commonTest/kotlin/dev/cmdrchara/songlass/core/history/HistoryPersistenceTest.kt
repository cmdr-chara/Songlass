package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HistoryPersistenceTest {
    @Test fun acceptedBatchIsPublishedAfterAllKeysAndRetainsTheExistingBackupFormat() {
        val backing = InMemoryKeyValueStore()
        var batches = 0
        lateinit var history: ListeningHistory
        val store = object : KeyValueStore by backing {
            override fun putStrings(writing: Map<String, String>) {
                // Observers still see the previous accepted history while a store writes.
                assertEquals(batches, history.state.value.totalPlays)
                assertEquals(setOf("history.contexts", "history.counts", "history.recent", "history.dated_plays"), writing.keys)
                backing.putStrings(writing)
                batches++
            }
        }
        var now = 1_000L
        history = ListeningHistory(store) { now }
        history.recordPlay("a", PlayContext.Artist("Á; 🎵"))
        now = 2_000L
        history.recordPlay("b", null)
        assertEquals(2, batches)
        assertEquals("artist\u001DÁ; 🎵\u001E1000", backing.getString("history.contexts"))
        assertEquals("a\u001E1\u001E1000\u001Fb\u001E1\u001E2000", backing.getString("history.counts"))
        assertEquals("b\u001Fa", backing.getString("history.recent"))
        assertEquals("b\u001E2000\u001Fa\u001E1000", backing.getString("history.dated_plays"))
        assertEquals(history.state.value, ListeningHistory(backing) { now }.state.value)
    }

    @Test fun failedFallbackBatchRollsBackAndDoesNotPublishAPhantomPlay() {
        val backing = InMemoryKeyValueStore()
        var failOnce = false
        val store = object : KeyValueStore {
            override fun getString(key: String) = backing.getString(key)
            override fun snapshot() = backing.snapshot()
            override fun putString(key: String, value: String?) {
                if (failOnce && key == "history.recent") {
                    failOnce = false
                    error("Injected storage failure")
                }
                backing.putString(key, value)
            }
        }
        val history = ListeningHistory(store) { 1_000L }
        history.recordPlay("a", PlayContext.Favorites)
        val before = history.state.value
        val persisted = backing.snapshot()
        failOnce = true
        assertFailsWith<IllegalStateException> { history.recordPlay("b", null) }
        assertEquals(before, history.state.value)
        assertEquals(persisted, backing.snapshot())
        history.recordPlay("b", null)
        assertEquals(mapOf("a" to 1, "b" to 1), history.state.value.playCounts)
        assertEquals(history.state.value, ListeningHistory(backing) { 1_000L }.state.value)
    }

    @Test fun reloadReadsOneSnapshotIncludingAbsentKeysWithoutFallingBackToAnotherGeneration() {
        var reads = 0
        val values = mapOf("history.counts" to "a\u001E4\u001E900", "history.tracking_started_ms" to "100")
        val store = object : KeyValueStore {
            override fun getString(key: String): String? {
                reads++
                error("A separate read could observe a different generation")
            }
            override fun putString(key: String, value: String?) = Unit
            override fun snapshot() = values
        }
        val history = ListeningHistory(store) { 1_000L }
        history.reload()
        assertEquals(0, reads)
        assertEquals(mapOf("a" to 4), history.state.value.playCounts)
        assertEquals(emptyList(), history.state.value.datedPlays)
        assertEquals(emptyList(), history.state.value.recentPlays)
    }

    @Test fun fullRetentionKeepsNewestFiftyThousandAndLifetimeTotalsAcrossRecreation() {
        val backing = InMemoryKeyValueStore()
        val now = 100_000L
        backing.putString("history.counts", "old\u001E70000\u001E99999")
        backing.putString("history.dated_plays", (0 until 50_000).joinToString("\u001F") { "old\u001E${now - it - 1}" })
        val history = ListeningHistory(backing) { now }
        val before = history.state.value.datedPlays
        history.recordPlay("new", null)
        assertEquals(listOf(DatedPlay("new", now)) + before.take(49_999), history.state.value.datedPlays)
        assertEquals(mapOf("old" to 70_000, "new" to 1), history.state.value.playCounts)
        assertEquals(history.state.value, ListeningHistory(backing) { now }.state.value)
    }
}
