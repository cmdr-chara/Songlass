package dev.cmdrchara.songlass.core.queue

import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackQueueTest {

    private fun PlaybackQueue.ids() = entries.map { it.track.id }

    @Test
    fun emptyQueueHasNoCurrentEntry() {
        val queue = PlaybackQueue.of(emptyList())
        assertNull(queue.current)
        assertNull(queue.nextIndex(userInitiated = true))
        assertNull(queue.previousIndex())
    }

    @Test
    fun startsAtGivenIndex() {
        val queue = PlaybackQueue.of(tracks(5), startIndex = 2)
        assertEquals("3", queue.current?.track?.id)
        assertEquals(listOf("4", "5"), queue.upNext.map { it.track.id })
    }

    @Test
    fun rejectsOutOfBoundsStartIndex() {
        assertFailsWith<IllegalArgumentException> { PlaybackQueue.of(tracks(3), startIndex = 3) }
    }

    @Test
    fun nextStopsAtEndWhenRepeatIsOff() {
        val queue = PlaybackQueue.of(tracks(3), startIndex = 2)
        assertNull(queue.nextIndex(userInitiated = false))
        assertNull(queue.nextIndex(userInitiated = true))
    }

    @Test
    fun repeatAllWrapsInBothDirections() {
        val atEnd = PlaybackQueue.of(tracks(3), startIndex = 2, repeatMode = RepeatMode.All)
        assertEquals(0, atEnd.nextIndex(userInitiated = false))
        val atStart = atEnd.withCurrentIndex(0)
        assertEquals(2, atStart.previousIndex())
    }

    @Test
    fun previousDoesNotWrapWhenRepeatIsOff() {
        assertNull(PlaybackQueue.of(tracks(3)).previousIndex())
    }

    @Test
    fun repeatOneReplaysOnlyWhenTrackEndsByItself() {
        val queue = PlaybackQueue.of(tracks(3), startIndex = 1, repeatMode = RepeatMode.One)
        assertEquals(1, queue.nextIndex(userInitiated = false))
        assertEquals(2, queue.nextIndex(userInitiated = true))
    }

    @Test
    fun repeatModeCyclesOffAllOne() {
        assertEquals(RepeatMode.All, RepeatMode.Off.next())
        assertEquals(RepeatMode.One, RepeatMode.All.next())
        assertEquals(RepeatMode.Off, RepeatMode.One.next())
    }

    @Test
    fun shuffleKeepsCurrentFirstAndAllTracks() {
        val queue = PlaybackQueue.of(tracks(20), startIndex = 7).withShuffle(true, Random(42))
        assertTrue(queue.isShuffled)
        assertEquals(0, queue.currentIndex)
        assertEquals("8", queue.current?.track?.id)
        assertEquals((1..20).map { it.toString() }.toSet(), queue.ids().toSet())
        assertEquals(20, queue.entries.size)
        assertNotEquals((1..20).map { it.toString() }, queue.ids(), "order should change")
    }

    @Test
    fun unshuffleRestoresOriginalOrderAndKeepsCurrentTrack() {
        val original = PlaybackQueue.of(tracks(10), startIndex = 3)
        val shuffled = original.withShuffle(true, Random(1))
        val playing = shuffled.withCurrentIndex(5)
        val restored = playing.withShuffle(false)
        assertFalse(restored.isShuffled)
        assertEquals(original.ids(), restored.ids())
        assertEquals(playing.current, restored.current)
    }

    @Test
    fun shuffleIsDeterministicWithSeededRandom() {
        val a = PlaybackQueue.of(tracks(10)).withShuffle(true, Random(7))
        val b = PlaybackQueue.of(tracks(10)).withShuffle(true, Random(7))
        assertEquals(a.ids(), b.ids())
    }

    @Test
    fun settingSameShuffleStateIsNoOp() {
        val queue = PlaybackQueue.of(tracks(5))
        assertEquals(queue, queue.withShuffle(false))
    }

    @Test
    fun playNextInsertsAfterCurrent() {
        val queue = PlaybackQueue.of(tracks(3), startIndex = 0).playNext(listOf(track("x"), track("y")))
        assertEquals(listOf("1", "x", "y", "2", "3"), queue.ids())
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun addToEndAppends() {
        val queue = PlaybackQueue.of(tracks(2)).addToEnd(listOf(track("x")))
        assertEquals(listOf("1", "2", "x"), queue.ids())
    }

    @Test
    fun addingToEmptyQueueMakesFirstAddedCurrent() {
        val queue = PlaybackQueue().addToEnd(listOf(track("x"), track("y")))
        assertEquals("x", queue.current?.track?.id)
    }

    @Test
    fun sameTrackTwiceGetsDistinctEntryIds() {
        val t = track("x")
        val queue = PlaybackQueue.of(listOf(t)).addToEnd(listOf(t))
        assertEquals(2, queue.entries.map { it.id }.toSet().size)
        val removed = queue.remove(queue.entries[1].id)
        assertEquals(1, removed.entries.size)
        assertEquals(queue.entries[0], removed.entries[0])
    }

    @Test
    fun playNextWhileShuffledSurvivesUnshuffle() {
        val shuffled = PlaybackQueue.of(tracks(5), startIndex = 1).withShuffle(true, Random(3))
        val withNext = shuffled.playNext(listOf(track("x")))
        assertEquals("x", withNext.upNext.first().track.id)
        val restored = withNext.withShuffle(false)
        assertEquals(listOf("1", "2", "x", "3", "4", "5"), restored.ids())
    }

    @Test
    fun removeBeforeCurrentShiftsIndex() {
        val queue = PlaybackQueue.of(tracks(4), startIndex = 2)
        val result = queue.remove(queue.entries[0].id)
        assertEquals(1, result.currentIndex)
        assertEquals("3", result.current?.track?.id)
    }

    @Test
    fun removeCurrentMakesFollowingEntryCurrent() {
        val queue = PlaybackQueue.of(tracks(4), startIndex = 1)
        val result = queue.remove(queue.entries[1].id)
        assertEquals("3", result.current?.track?.id)
    }

    @Test
    fun removeCurrentLastEntryFallsBackToNewLast() {
        val queue = PlaybackQueue.of(tracks(3), startIndex = 2)
        val result = queue.remove(queue.entries[2].id)
        assertEquals("2", result.current?.track?.id)
    }

    @Test
    fun removeOnlyEntryEmptiesQueue() {
        val queue = PlaybackQueue.of(tracks(1))
        val result = queue.remove(queue.entries[0].id)
        assertNull(result.current)
        assertEquals(PlaybackQueue.NO_INDEX, result.currentIndex)
    }

    @Test
    fun removeUnknownEntryIsNoOp() {
        val queue = PlaybackQueue.of(tracks(3))
        assertEquals(queue, queue.remove(999))
    }

    @Test
    fun moveKeepsCurrentEntryCurrent() {
        val queue = PlaybackQueue.of(tracks(4), startIndex = 2)
        val current = queue.current

        val fromBefore = queue.move(0, 3)
        assertEquals(listOf("2", "3", "4", "1"), fromBefore.ids())
        assertEquals(current, fromBefore.current)

        val fromAfter = queue.move(3, 0)
        assertEquals(listOf("4", "1", "2", "3"), fromAfter.ids())
        assertEquals(current, fromAfter.current)

        val movingCurrent = queue.move(2, 0)
        assertEquals(0, movingCurrent.currentIndex)
        assertEquals(current, movingCurrent.current)
    }

    @Test
    fun moveRejectsInvalidIndices() {
        assertFailsWith<IllegalArgumentException> { PlaybackQueue.of(tracks(2)).move(0, 2) }
    }
}
