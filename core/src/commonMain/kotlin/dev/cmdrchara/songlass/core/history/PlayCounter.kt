package dev.cmdrchara.songlass.core.history

/** Applies only to future playback occurrences; changing the rule never rewrites history. */
enum class PlayCountRule(val storageKey: String) {
    ThirtySecondsOrHalf("thirty_seconds_or_half"),
    Half("half"),
    NearlyAll("nearly_all");

    /** Unknown durations use 30 seconds. A zero-length threshold must never count a seek. */
    fun thresholdMs(durationMs: Long): Long = if (durationMs <= 0) 30_000L else when (this) {
        ThirtySecondsOrHalf -> minOf(30_000L, durationMs / 2)
        Half -> durationMs / 2
        NearlyAll -> (durationMs / 100) * 90 + (durationMs % 100) * 90 / 100
    }.coerceAtLeast(1L)

    companion object {
        fun fromStorage(value: String?): PlayCountRule = entries.firstOrNull { it.storageKey == value }
            ?: ThirtySecondsOrHalf
    }
}

/**
 * Counts actual observed progress, once per playback occurrence. Absolute position is never
 * listening time: resuming late in a track, seeking, pausing and buffering earn no credit.
 * The transport supplies independent occurrence and discontinuity tokens; an ordinary seek
 * invalidates one sample without allowing another count of an already counted occurrence.
 */
class PlayCounter {
    private var entryId: Long? = null
    private var occurrenceId = Long.MIN_VALUE
    private var discontinuityId = Long.MIN_VALUE
    private var thresholdMs = 30_000L
    private var heardMs = 0L
    private var counted = false
    private var hasPrevious = false
    private var previousPositionMs = 0L
    private var previousElapsedMs = 0L
    private var previousPlaying = false

    /**
     * [elapsedRealtimeMs] must come from a monotonic clock. [playing] must exclude buffering,
     * and be false while history writes are suspended (for example during backup restore).
     * Threshold and duration are snapshotted on entry/occurrence change, including an unknown
     * duration's fallback. No persistence or allocation proportional to playback time occurs.
     */
    fun update(
        entryId: Long?, occurrenceId: Long, discontinuityId: Long,
        positionMs: Long, durationMs: Long, playing: Boolean, elapsedRealtimeMs: Long,
        rule: PlayCountRule = PlayCountRule.ThirtySecondsOrHalf,
    ): Boolean {
        val newOccurrence = entryId != this.entryId || occurrenceId != this.occurrenceId
        if (newOccurrence) {
            this.entryId = entryId
            this.occurrenceId = occurrenceId
            thresholdMs = rule.thresholdMs(durationMs)
            heardMs = 0L
            counted = false
        }
        val validInterval = hasPrevious && !newOccurrence && discontinuityId == this.discontinuityId && playing && previousPlaying
        val position = positionMs.coerceAtLeast(0)
        val elapsed = elapsedRealtimeMs - previousElapsedMs
        val progress = position - previousPositionMs
        this.discontinuityId = discontinuityId
        hasPrevious = entryId != null
        previousPositionMs = position
        previousElapsedMs = elapsedRealtimeMs
        previousPlaying = playing
        if (entryId == null || counted || !validInterval) return false
        // Reject backwards/implausible movement. Small normal timestamp jitter may exceed
        // elapsed time, but credited time is always capped by the monotonic interval.
        if (elapsed <= 0 || progress <= 0 || progress - elapsed > POSITION_JITTER_MS) return false
        heardMs += minOf(progress, elapsed, thresholdMs - heardMs)
        if (heardMs < thresholdMs) return false
        counted = true
        return true
    }

    private companion object {
        const val POSITION_JITTER_MS = 1_000L
    }
}
