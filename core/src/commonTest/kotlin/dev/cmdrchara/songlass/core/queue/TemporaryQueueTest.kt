package dev.cmdrchara.songlass.core.queue

import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemporaryQueueTest {
    private fun PlaybackQueue.ids() = entries.map { it.track.id }
    private fun PlaybackQueue.advance(manual: Boolean = false): PlaybackQueue =
        nextIndex(manual)?.let(::withCurrentIndex) ?: returnToSource()

    @Test
    fun additionsPlayOnceThenContinueTheOriginalCollection() {
        var queue = PlaybackQueue.of(tracks(3)).enqueueTemporary(listOf(track("x")))
            .enqueueTemporary(listOf(track("y")))
        assertEquals(listOf("1", "x", "y", "2", "3"), queue.ids())
        assertEquals(2, queue.pendingTemporaryCount)
        queue = queue.advance()
        assertEquals("x", queue.current?.track?.id)
        queue = queue.advance()
        assertEquals(listOf("1", "y", "2", "3"), queue.ids())
        queue = queue.advance()
        assertEquals("2", queue.current?.track?.id)
        assertEquals(listOf("1", "2", "3"), queue.ids())
        assertEquals(0, queue.pendingTemporaryCount)
    }

    @Test
    fun playNextPrecedesPendingAdditionsWithoutInterruptingCurrent() {
        val queue = PlaybackQueue.of(tracks(3)).enqueueTemporary(listOf(track("x"), track("y")))
            .advance().enqueueTemporary(listOf(track("z")), playNext = true)
        assertEquals("x", queue.current?.track?.id)
        assertEquals(listOf("1", "x", "z", "y", "2", "3"), queue.ids())
    }

    @Test
    fun appendWhileTemporaryTrackIsPlayingPreservesPendingOrder() {
        val queue = PlaybackQueue.of(tracks(2)).enqueueTemporary(listOf(track("x"), track("y")))
            .advance().enqueueTemporary(listOf(track("z")))
        assertEquals(listOf("1", "x", "y", "z", "2"), queue.ids())
    }

    @Test
    fun repeatAllDoesNotReinsertConsumedAdditions() {
        var queue = PlaybackQueue.of(tracks(2), repeatMode = RepeatMode.All)
            .enqueueTemporary(listOf(track("x")))
        val played = mutableListOf<String>()
        repeat(7) {
            played += queue.current!!.track.id
            queue = queue.advance()
        }
        assertEquals(listOf("1", "x", "2", "1", "2", "1", "2"), played)
    }

    @Test
    fun repeatOneKeepsActiveAdditionAndManualSkipConsumesIt() {
        var queue = PlaybackQueue.of(tracks(2)).enqueueTemporary(listOf(track("x")))
            .advance().withRepeatMode(RepeatMode.One)
        assertEquals(queue.currentIndex, queue.nextIndex(userInitiated = false))
        queue = queue.advance(manual = true)
        assertEquals("2", queue.current?.track?.id)
        assertTrue(queue.temporaryEntries.isEmpty())
    }

    @Test
    fun standaloneTemporaryQueueStopsEvenWithRepeatAll() {
        val queue = PlaybackQueue(repeatMode = RepeatMode.All)
            .enqueueTemporary(listOf(track("x"), track("y"))).advance()
        assertEquals("y", queue.current?.track?.id)
        assertNull(queue.nextIndex(userInitiated = false))
        assertTrue(queue.returnToSource().entries.isEmpty())
    }

    @Test
    fun shuffleAndUnshufflePreserveActiveAdditionAndOriginalOrder() {
        val queue = PlaybackQueue.of(tracks(7), startIndex = 2)
            .enqueueTemporary(listOf(track("x"), track("y"))).advance()
        val shuffled = queue.withShuffle(true, Random(42))
        assertEquals(queue.current, shuffled.current)
        assertEquals(listOf("x", "y"), shuffled.temporaryEntries.map { it.track.id })
        val restored = shuffled.withShuffle(false)
        assertEquals(listOf("1", "2", "3", "x", "y", "4", "5", "6", "7"), restored.ids())
        assertEquals(queue.current, restored.current)
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7"), restored.returnToSource().ids())
        assertEquals("4", restored.returnToSource().current?.track?.id)
    }

    @Test
    fun additionsDoNotEnterTheHiddenUnshuffledCollection() {
        var queue = PlaybackQueue.of(tracks(5), startIndex = 1).withShuffle(true, Random(5))
        val expected = queue.upNext.first().track.id
        queue = queue.enqueueTemporary(listOf(track("x"))).advance().advance()
        assertEquals(expected, queue.current?.track?.id)
        assertEquals(listOf("1", "2", "3", "4", "5"), queue.withShuffle(false).ids())
    }

    @Test
    fun selectingLaterAdditionConsumesSkippedAdditions() {
        val queue = PlaybackQueue.of(tracks(2)).enqueueTemporary(listOf(track("x"), track("y"), track("z")))
        val selected = queue.withCurrentIndex(queue.entries.indexOfFirst { it.track.id == "z" })
        assertEquals(listOf("1", "z", "2"), selected.ids())
        assertEquals("z", selected.current?.track?.id)
        assertEquals("2", selected.advance().current?.track?.id)
    }

    @Test
    fun selectingCollectionTrackKeepsPendingAdditionsReachable() {
        val queue = PlaybackQueue.of(tracks(4)).enqueueTemporary(listOf(track("x"), track("y"))).advance()
        val selected = queue.withCurrentIndex(queue.entries.indexOfFirst { it.track.id == "3" })
        assertEquals(listOf("1", "2", "3", "y", "4"), selected.ids())
        assertEquals("3", selected.current?.track?.id)
    }

    @Test
    fun previousReturnsToSourceAnchorAndConsumesCurrentAddition() {
        val queue = PlaybackQueue.of(tracks(3), startIndex = 1)
            .enqueueTemporary(listOf(track("x"), track("y"))).advance()
        val previous = queue.withCurrentIndex(queue.previousIndex()!!)
        assertEquals("2", previous.current?.track?.id)
        assertEquals(listOf("1", "2", "y", "3"), previous.ids())
    }

    @Test
    fun clearingPendingAdditionsDoesNotInterruptCurrentTemporaryTrack() {
        val queue = PlaybackQueue.of(tracks(3)).enqueueTemporary(listOf(track("x"), track("y"))).advance()
        val cleared = queue.clearTemporary()
        assertEquals(queue.current, cleared.current)
        assertEquals(listOf("1", "x", "2", "3"), cleared.ids())
        assertEquals("2", cleared.advance().current?.track?.id)
    }

    @Test
    fun returningAtEndRetainsCollectionButHasNoCurrentTrack() {
        val queue = PlaybackQueue.of(tracks(2), startIndex = 1)
            .enqueueTemporary(listOf(track("x"))).advance().returnToSource()
        assertEquals(listOf("1", "2"), queue.ids())
        assertNull(queue.current)
        assertEquals(0, queue.nextIndex(userInitiated = true))
    }

    @Test
    fun removingSourceAnchorPreservesItsContinuation() {
        val queue = PlaybackQueue.of(tracks(3), startIndex = 1)
            .enqueueTemporary(listOf(track("x"))).advance()
        val removed = queue.remove(queue.entries.first { it.track.id == "2" }.id)
        assertEquals(listOf("1", "x", "3"), removed.ids())
        assertEquals("x", removed.current?.track?.id)
        assertEquals("3", removed.advance().current?.track?.id)
    }

    @Test
    fun removingLastTemporaryTrackDoesNotReplayCompletedSource() {
        val queue = PlaybackQueue.of(tracks(1)).enqueueTemporary(listOf(track("x"))).advance()
        val removed = queue.remove(queue.current!!.id)
        assertNull(removed.current)
        assertEquals(listOf("1"), removed.ids())
    }

    @Test
    fun duplicateTracksHaveIndependentSlotsAndOnlyAdditionIsConsumed() {
        val queue = PlaybackQueue.of(tracks(2)).enqueueTemporary(listOf(track("1"), track("1")))
        assertEquals(queue.entries.size, queue.entries.map { it.id }.toSet().size)
        val done = queue.advance().advance().advance()
        assertEquals(listOf("1", "2"), done.ids())
        assertFalse(done.entries.any { it.isTemporary })
    }

    @Test
    fun pendingReorderCannotMoveIntoCollectionOrAheadOfActiveAddition() {
        val queue = PlaybackQueue.of(tracks(3)).enqueueTemporary(listOf(track("x"), track("y"), track("z"))).advance()
        assertEquals(queue, queue.move(2, 0))
        assertEquals(queue, queue.move(2, 1))
        val moved = queue.move(2, 3)
        assertEquals(listOf("1", "x", "z", "y", "2", "3"), moved.ids())
        assertEquals(queue.current, moved.current)
    }

    @Test
    fun sourceReorderKeepsTemporarySegmentAnchored() {
        val queue = PlaybackQueue.of(tracks(4)).enqueueTemporary(listOf(track("x")))
        val moved = queue.move(queue.entries.lastIndex, 0)
        assertEquals(listOf("4", "1", "x", "2", "3"), moved.ids())
        assertEquals(queue.current, moved.current)
    }
}
