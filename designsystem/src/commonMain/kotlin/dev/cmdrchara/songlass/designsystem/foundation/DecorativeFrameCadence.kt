package dev.cmdrchara.songlass.designsystem.foundation

/**
 * A 30 Hz draw cadence on top of the display's own frame clock. Integer nanosecond
 * timestamps can truncate 60/120 Hz intervals just below a 30 Hz boundary; allow
 * one microsecond of rounding, not an extra display frame. No catch-up updates.
 * Construct a new instance when an animation resumes to exclude paused time.
 */
class DecorativeFrameCadence(startTimeNanos: Long) {
    private var previous = startTimeNanos

    /** Elapsed animation seconds for an eligible draw, or null for a skipped frame. */
    fun elapsedSeconds(frameTimeNanos: Long): Float? {
        if (frameTimeNanos < previous) {
            previous = frameTimeNanos
            return null
        }
        val elapsedNanos = frameTimeNanos - previous
        if (elapsedNanos < FRAME_INTERVAL_NANOS - ROUNDING_TOLERANCE_NANOS) return null
        previous = frameTimeNanos
        return (elapsedNanos / 1_000_000_000f).coerceAtMost(0.1f)
    }

    private companion object {
        const val FRAME_INTERVAL_NANOS = 33_333_333L
        const val ROUNDING_TOLERANCE_NANOS = 1_000L
    }
}
