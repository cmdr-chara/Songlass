package dev.cmdrchara.songlass.core.scrobble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Last.fm rule and the tracker that feeds it. Seeks, pauses and buffering must never create a
 * scrobble, and one playback occurrence must produce at most one.
 */
class LastFmListenRuleTest {
    @Test
    fun tracksOfThirtySecondsOrLessAreNeverScrobbled() {
        assertNull(LastFmListenRule.thresholdMs(0))
        assertNull(LastFmListenRule.thresholdMs(10_000))
        assertNull(LastFmListenRule.thresholdMs(30_000))
    }

    @Test
    fun thresholdIsHalfTheDurationOrFourMinutes() {
        assertEquals(15_000L, LastFmListenRule.thresholdMs(30_001))
        assertEquals(90_000L, LastFmListenRule.thresholdMs(180_000))
        assertEquals(150_000L, LastFmListenRule.thresholdMs(300_000))
        // Four minutes, whichever is earlier: a one-hour track still needs only 240 s.
        assertEquals(240_000L, LastFmListenRule.thresholdMs(3_600_000))
    }

    @Test
    fun continuousListeningReachesHalfTheTrack() {
        val tracker = LastFmListenTracker()
        val observations = listen(tracker, durationMs = 300_000, seconds = 150)
        assertFalse(observations.take(149).any { it.scrobbleDue })
        assertTrue(observations.last().scrobbleDue)
        // Exactly one scrobble per occurrence, even if playback continues.
        assertTrue(listen(tracker, durationMs = 300_000, seconds = 60, startAtMs = 150_000, startElapsedMs = 150_000).none { it.scrobbleDue })
    }

    @Test
    fun longTrackScrobblesAtFourMinutes() {
        val tracker = LastFmListenTracker()
        val observations = listen(tracker, durationMs = 3_600_000, seconds = 240)
        assertTrue(observations.last().scrobbleDue)
        assertFalse(observations.dropLast(1).any { it.scrobbleDue })
    }

    @Test
    fun shortTrackNeverReachesAScrobble() {
        val tracker = LastFmListenTracker()
        assertTrue(listen(tracker, durationMs = 20_000, seconds = 20).none { it.scrobbleDue })
    }

    @Test
    fun unknownDurationIsIneligible() {
        val tracker = LastFmListenTracker()
        assertTrue(listen(tracker, durationMs = 0, seconds = 300).none { it.scrobbleDue })
    }

    @Test
    fun seekingForwardEarnsNoCredit() {
        val tracker = LastFmListenTracker()
        // Listen 60 s, then jump two minutes ahead with one second of elapsed time.
        listen(tracker, durationMs = 300_000, seconds = 60)
        val jump = observe(tracker, positionMs = 180_000, elapsedMs = 61_000, durationMs = 300_000)
        assertFalse(jump.scrobbleDue)
        // The jump itself is not listening time: 150 s of credit are still needed from here.
        val after = listen(tracker, durationMs = 300_000, seconds = 89, startAtMs = 180_000, startElapsedMs = 61_000)
        assertTrue(after.none { it.scrobbleDue })
        val enough = listen(tracker, durationMs = 300_000, seconds = 1, startAtMs = 269_000, startElapsedMs = 150_000)
        assertTrue(enough.last().scrobbleDue)
    }

    @Test
    fun pausingAndBufferingEarnNoCredit() {
        val tracker = LastFmListenTracker()
        listen(tracker, durationMs = 300_000, seconds = 10)
        // Five minutes pass while paused; the position does not move.
        observe(tracker, positionMs = 10_000, elapsedMs = 310_000, durationMs = 300_000, playing = false)
        observe(tracker, positionMs = 10_000, elapsedMs = 320_000, durationMs = 300_000, playing = false)
        // Resuming continues from the real position, and only the next 140 s count.
        val resumed = listen(tracker, durationMs = 300_000, seconds = 139, startAtMs = 10_000, startElapsedMs = 320_000, firstSamplePlaying = false)
        assertTrue(resumed.none { it.scrobbleDue })
        // Two more seconds: 148 s credited so far, 150 s needed.
        val finalSeconds = listen(tracker, durationMs = 300_000, seconds = 2, startAtMs = 149_000, startElapsedMs = 459_000)
        assertTrue(finalSeconds.last().scrobbleDue)
    }

    @Test
    fun newOccurrenceStartsANewListenAndReportsTheStart() {
        val tracker = LastFmListenTracker()
        val first = observe(tracker, positionMs = 0, elapsedMs = 0, durationMs = 300_000, occurrenceId = 1)
        assertTrue(first.occurrenceStarted)
        val second = observe(tracker, positionMs = 1_000, elapsedMs = 1_000, durationMs = 300_000, occurrenceId = 2, discontinuityId = 2)
        assertTrue(second.occurrenceStarted)
        // A repeat of the same track is a different occurrence, not a continuation.
        assertFalse(second.scrobbleDue)
        val replay = listen(tracker, durationMs = 300_000, seconds = 150, occurrenceId = 2, discontinuityId = 2)
        assertTrue(replay.last().scrobbleDue)
    }

    @Test
    fun seekBackwardsAndStalledClockEarnNoCredit() {
        val tracker = LastFmListenTracker()
        listen(tracker, durationMs = 300_000, seconds = 30)
        val backwards = observe(tracker, positionMs = 5_000, elapsedMs = 31_000, durationMs = 300_000)
        assertFalse(backwards.scrobbleDue)
        val stalled = observe(tracker, positionMs = 6_000, elapsedMs = 31_000, durationMs = 300_000)
        assertFalse(stalled.scrobbleDue)
    }

    private fun observe(
        tracker: LastFmListenTracker,
        positionMs: Long,
        elapsedMs: Long,
        durationMs: Long,
        playing: Boolean = true,
        occurrenceId: Long = 1,
        discontinuityId: Long = 1,
        trackKey: String = "track-1",
    ): PlaybackObservation = tracker.observe(
        trackKey = trackKey,
        occurrenceId = occurrenceId,
        discontinuityId = discontinuityId,
        positionMs = positionMs,
        durationMs = durationMs,
        playing = playing,
        elapsedRealtimeMs = elapsedMs,
    )

    /**
     * Feeds one second of playback at a time starting from [startAtMs]/[startElapsedMs], skipping
     * the very first "baseline" sample that [LastFmListenTracker] needs before it can credit time.
     */
    private fun listen(
        tracker: LastFmListenTracker,
        durationMs: Long,
        seconds: Int,
        startAtMs: Long = 0,
        startElapsedMs: Long = 0,
        occurrenceId: Long = 1,
        discontinuityId: Long = 1,
        firstSamplePlaying: Boolean = true,
    ): List<PlaybackObservation> {
        val observations = mutableListOf<PlaybackObservation>()
        var position = startAtMs
        var elapsed = startElapsedMs
        observations += observe(tracker, position, elapsed, durationMs,
            playing = firstSamplePlaying, occurrenceId = occurrenceId, discontinuityId = discontinuityId)
        repeat(seconds) {
            position += 1_000
            elapsed += 1_000
            observations += observe(tracker, position, elapsed, durationMs,
                occurrenceId = occurrenceId, discontinuityId = discontinuityId)
        }
        return observations.drop(1)
    }
}
