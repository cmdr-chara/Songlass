package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The queue must survive a restart, escape real tags and never grow without a limit. */
class ScrobbleQueueTest {
    private fun record(
        artist: String = "Synthetic Artist",
        title: String = "Synthetic Song",
        album: String? = "Synthetic Album",
        timestampSeconds: Long = 1_700_000_000,
        durationSeconds: Long = 180,
    ) = ScrobbleRecord(ScrobbleTrack(artist, title, album, durationSeconds * 1000), timestampSeconds, durationSeconds)

    @Test
    fun queuedListensSurviveANewInstance() {
        val store = InMemoryKeyValueStore()
        val queue = ScrobbleQueue(store)
        assertTrue(queue.enqueue(record()))
        assertTrue(queue.enqueue(record(title = "Second", timestampSeconds = 1_700_000_100)))

        val reloaded = ScrobbleQueue(store)
        assertEquals(2, reloaded.size)
        val restored = reloaded.snapshot().first()
        assertEquals("Synthetic Artist", restored.record.track.artist)
        assertEquals("Synthetic Song", restored.record.track.title)
        assertEquals("Synthetic Album", restored.record.track.album)
        assertEquals(1_700_000_000, restored.record.timestampSeconds)
        assertEquals(180, restored.record.durationSeconds)
        assertEquals(0, restored.attempts)
        assertEquals(0, restored.nextAttemptMs)
    }

    @Test
    fun tagsWithSeparatorsAndPercentRoundTrip() {
        val store = InMemoryKeyValueStore()
        val queue = ScrobbleQueue(store)
        val tricky = "100% \u001E\u001F real %25"
        assertTrue(queue.enqueue(record(artist = tricky, title = tricky)))
        val reloaded = ScrobbleQueue(store)
        assertEquals(1, reloaded.size)
        assertEquals(tricky, reloaded.snapshot().single().record.track.artist)
        assertEquals(tricky, reloaded.snapshot().single().record.track.title)
    }

    @Test
    fun duplicateOccurrenceIsDropped() {
        val store = InMemoryKeyValueStore()
        val queue = ScrobbleQueue(store)
        assertTrue(queue.enqueue(record()))
        assertFalse(queue.enqueue(record()))
        assertEquals(1, queue.size)
    }

    @Test
    fun sameTrackAtAnotherTimeIsAnotherListen() {
        val queue = ScrobbleQueue(InMemoryKeyValueStore())
        assertTrue(queue.enqueue(record(timestampSeconds = 1_700_000_000)))
        assertTrue(queue.enqueue(record(timestampSeconds = 1_700_000_200)))
        assertEquals(2, queue.size)
    }

    @Test
    fun backlogIsCappedByDroppingTheOldest() {
        val queue = ScrobbleQueue(InMemoryKeyValueStore())
        repeat(ScrobbleQueue.MAX_ENTRIES + 5) { index ->
            queue.enqueue(record(title = "Title $index", timestampSeconds = 1_700_000_000L + index))
        }
        assertEquals(ScrobbleQueue.MAX_ENTRIES, queue.size)
        assertEquals("Title 5", queue.snapshot().first().record.track.title)
    }

    @Test
    fun retryBookkeepingIsPersisted() {
        val store = InMemoryKeyValueStore()
        val queue = ScrobbleQueue(store)
        queue.enqueue(record())
        val entry = queue.snapshot().single()
        queue.requeue(listOf(entry.copy(attempts = 2, nextAttemptMs = 1_700_000_600_000)))

        val reloaded = ScrobbleQueue(store)
        assertEquals(2, reloaded.snapshot().single().attempts)
        assertEquals(1_700_000_600_000, reloaded.snapshot().single().nextAttemptMs)
        assertTrue(reloaded.due(1_700_000_599_999).isEmpty())
        assertEquals(1, reloaded.due(1_700_000_600_000).size)
    }

    @Test
    fun backoffGrowsAndIsCapped() {
        assertEquals(30_000L, ScrobbleBackoff.delayMs(1))
        assertEquals(120_000L, ScrobbleBackoff.delayMs(2))
        assertEquals(21_600_000L, ScrobbleBackoff.delayMs(6))
        assertEquals(21_600_000L, ScrobbleBackoff.delayMs(50))
        assertTrue(ScrobbleBackoff.MAX_ATTEMPTS >= ScrobbleBackoff.delaysMs.size)
    }

    @Test
    fun clearBackoffMakesEverythingDueAgain() {
        val queue = ScrobbleQueue(InMemoryKeyValueStore())
        queue.enqueue(record())
        queue.requeue(listOf(queue.snapshot().single().copy(attempts = 3, nextAttemptMs = Long.MAX_VALUE / 2)))
        queue.clearBackoff()
        val entry = queue.snapshot().single()
        assertEquals(0, entry.attempts)
        assertEquals(0, entry.nextAttemptMs)
        assertEquals(1, queue.due(0).size)
    }

    @Test
    fun clearForgetsEverythingIncludingStorage() {
        val store = InMemoryKeyValueStore()
        val queue = ScrobbleQueue(store)
        queue.enqueue(record())
        queue.clear()
        assertEquals(0, queue.size)
        assertEquals(0, ScrobbleQueue(store).size)
    }

    @Test
    fun corruptedStorageIsIgnoredInsteadOfCrashing() {
        val store = InMemoryKeyValueStore()
        store.putString(ScrobbleQueue.QUEUE_KEY, "not\u001Fa\u001Fvalid entry\u001Ebroken")
        val queue = ScrobbleQueue(store)
        assertEquals(0, queue.size)
    }
}
