package dev.cmdrchara.songlass.core.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayCounterTest {
    private val counter = PlayCounter()
    private fun sample(position: Long, time: Long, playing: Boolean = true, occurrence: Long = 1,
        discontinuity: Long = 0, duration: Long = 200_000, rule: PlayCountRule = PlayCountRule.ThirtySecondsOrHalf,
        entry: Long? = 1,
    ) = counter.update(entry, occurrence, discontinuity, position, duration, playing, time, rule)

    @Test fun resumedPositionIsNotListeningTimeAndEachOccurrenceCountsOnlyOnce() {
        assertFalse(sample(100_000, 0))
        assertFalse(sample(129_999, 29_999))
        assertTrue(sample(130_000, 30_000))
        assertFalse(sample(160_000, 60_000))
        assertFalse(sample(0, 60_001, occurrence = 2))
        assertTrue(sample(30_000, 90_001, occurrence = 2))
    }

    @Test fun pausedSeekAndBufferingNeverCount() {
        assertFalse(sample(0, 0, playing = false))
        assertFalse(sample(150_000, 30_000, playing = false, discontinuity = 1))
        assertFalse(sample(150_000, 60_000, discontinuity = 1))
        assertFalse(sample(160_000, 70_000, discontinuity = 1))
        // Buffering is passed as not playing even when playWhenReady remains true.
        assertFalse(sample(180_000, 100_000, playing = false, discontinuity = 1))
        assertFalse(sample(180_000, 110_000, discontinuity = 1))
        assertTrue(sample(200_000, 130_000, discontinuity = 1))
    }

    @Test fun shortSeeksAreExcludedAndRewindingDoesNotCreateAnotherPlay() {
        assertFalse(sample(0, 0))
        assertFalse(sample(10_000, 10_000))
        // A small seek fits a plausible clock interval, but its revision still excludes it.
        assertFalse(sample(10_500, 10_500, discontinuity = 1))
        assertFalse(sample(30_000, 30_000, discontinuity = 1))
        assertTrue(sample(30_500, 30_500, discontinuity = 1))
        assertFalse(sample(0, 30_501, discontinuity = 2))
        assertFalse(sample(60_000, 90_501, discontinuity = 2))
    }

    @Test fun impossibleProgressAndNonMonotonicSamplesEarnNoCredit() {
        assertFalse(sample(0, 0))
        assertFalse(sample(100_000, 1_000))
        assertFalse(sample(99_000, 500))
        assertFalse(sample(110_000, 1_000))
        assertFalse(sample(139_999, 30_999))
        assertTrue(sample(140_000, 31_000))
    }

    @Test fun elapsedTimeCapsCreditEvenForSmallPositionJitter() {
        assertFalse(sample(0, 0))
        assertFalse(sample(30_000, 29_000))
        assertTrue(sample(31_000, 30_000))
    }

    @Test fun thresholdIsSnapshottedUntilANewOccurrenceStarts() {
        assertFalse(sample(0, 0, duration = 100_000, rule = PlayCountRule.Half))
        assertFalse(sample(30_000, 30_000, duration = 10_000))
        assertTrue(sample(50_000, 50_000, rule = PlayCountRule.NearlyAll))
        assertFalse(sample(0, 50_001, occurrence = 2, duration = 100_000, rule = PlayCountRule.NearlyAll))
        assertFalse(sample(89_999, 140_000, occurrence = 2))
        assertTrue(sample(90_000, 140_001, occurrence = 2))
    }

    @Test fun shortAndUnknownDurationsHaveNonzeroThresholds() {
        assertFalse(sample(0, 0, duration = 20_000))
        assertFalse(sample(9_999, 9_999, duration = 20_000))
        assertTrue(sample(10_000, 10_000, duration = 20_000))
        assertFalse(sample(0, 10_001, occurrence = 2, duration = 0, rule = PlayCountRule.Half))
        assertTrue(sample(30_000, 40_001, occurrence = 2, duration = 0))
        assertFalse(sample(0, 40_002, occurrence = 3, duration = 1))
        assertTrue(sample(1, 40_003, occurrence = 3, duration = 1))
    }

    @Test fun noEntryAndChangedEntriesCannotInheritProgress() {
        assertFalse(sample(0, 0))
        assertFalse(sample(29_000, 29_000))
        assertFalse(sample(30_000, 30_000, entry = null))
        assertFalse(sample(30_000, 31_000, entry = 2))
        assertTrue(sample(60_000, 61_000, entry = 2))
    }
}
