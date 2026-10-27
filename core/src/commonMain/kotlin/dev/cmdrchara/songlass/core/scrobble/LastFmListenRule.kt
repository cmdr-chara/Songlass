package dev.cmdrchara.songlass.core.scrobble

/**
 * Last.fm's rule for what may be scrobbled:
 * "the track must be longer than 30 seconds, and it must have been played for at least half its
 * duration or for 4 minutes, whichever occurs earlier".
 *
 * This is evaluated on top of the app's own [dev.cmdrchara.songlass.core.history.PlayCounter]
 * occurrence semantics: only real, uninterrupted listening time counts, so seeking, resuming late,
 * pausing and buffering never produce a scrobble on their own.
 */
object LastFmListenRule {
    /** Tracks of 30 seconds or less are never scrobbled. */
    const val MINIMUM_TRACK_MS: Long = 30_000L

    /** The rule stops asking for more than four minutes, however long the track is. */
    const val MAXIMUM_THRESHOLD_MS: Long = 240_000L

    /**
     * Listening time needed before a scrobble, or null when the track is too short to be eligible.
     * An unknown duration is treated as ineligible rather than guessed.
     */
    fun thresholdMs(durationMs: Long): Long? = when {
        durationMs <= MINIMUM_TRACK_MS -> null
        else -> minOf(durationMs / 2, MAXIMUM_THRESHOLD_MS).coerceAtLeast(1L)
    }
}

/** What one playback sample produced. */
internal data class PlaybackObservation(
    /** A new occurrence of a playable track started with this sample. */
    val occurrenceStarted: Boolean = false,
    /** Enough real listening time has accumulated for Last.fm to accept the scrobble. */
    val scrobbleDue: Boolean = false,
)

/**
 * Decides when a listen becomes scrobble-able, without persistence or network access.
 *
 * The playback transport supplies independent occurrence and discontinuity tokens (the same ones
 * [dev.cmdrchara.songlass.core.history.PlayCounter] receives): a seek invalidates one sample without
 * allowing a second scrobble of an already scrobbled occurrence, and pausing or buffering earns no
 * credit. Credit is the smaller of observed progress and monotonic elapsed time, so a position jump
 * can never fake listening.
 */
internal class LastFmListenTracker {
    private var trackKey: String? = null
    private var occurrenceId = Long.MIN_VALUE
    private var discontinuityId = Long.MIN_VALUE
    private var thresholdMs = Long.MAX_VALUE
    private var heardMs = 0L
    private var scrobbled = false
    private var hasPrevious = false
    private var previousPositionMs = 0L
    private var previousElapsedMs = 0L
    private var previousPlaying = false

    /**
     * [elapsedRealtimeMs] must be monotonic. [playing] must exclude buffering and be false while
     * history writes are suspended (for example during a backup restore).
     */
    fun observe(
        trackKey: String?,
        occurrenceId: Long,
        discontinuityId: Long,
        positionMs: Long,
        durationMs: Long,
        playing: Boolean,
        elapsedRealtimeMs: Long,
    ): PlaybackObservation {
        val newOccurrence = trackKey != this.trackKey || occurrenceId != this.occurrenceId
        if (newOccurrence) {
            this.trackKey = trackKey
            this.occurrenceId = occurrenceId
            thresholdMs = LastFmListenRule.thresholdMs(durationMs) ?: Long.MAX_VALUE
            heardMs = 0L
            scrobbled = false
        }
        val validInterval = hasPrevious && !newOccurrence &&
            discontinuityId == this.discontinuityId && playing && previousPlaying
        val position = positionMs.coerceAtLeast(0)
        val elapsed = elapsedRealtimeMs - previousElapsedMs
        val progress = position - previousPositionMs
        this.discontinuityId = discontinuityId
        hasPrevious = trackKey != null
        previousPositionMs = position
        previousElapsedMs = elapsedRealtimeMs
        previousPlaying = playing
        val started = newOccurrence && trackKey != null
        if (trackKey == null || scrobbled || !validInterval) return PlaybackObservation(occurrenceStarted = started)
        // A jump forward faster than wall time, a backwards step or a non-advancing clock is a seek,
        // a resume from a saved position or a platform hiccup: no credit for that interval.
        if (elapsed <= 0 || progress <= 0 || progress - elapsed > POSITION_JITTER_MS) {
            return PlaybackObservation(occurrenceStarted = started)
        }
        heardMs += minOf(progress, elapsed, thresholdMs - heardMs)
        if (heardMs < thresholdMs) return PlaybackObservation(occurrenceStarted = started)
        scrobbled = true
        return PlaybackObservation(occurrenceStarted = started, scrobbleDue = true)
    }

    /** Forgets the current occurrence, for example after a logout. */
    fun reset() {
        trackKey = null
        occurrenceId = Long.MIN_VALUE
        discontinuityId = Long.MIN_VALUE
        thresholdMs = Long.MAX_VALUE
        heardMs = 0L
        scrobbled = false
        hasPrevious = false
        previousPositionMs = 0L
        previousElapsedMs = 0L
        previousPlaying = false
    }

    private companion object {
        /** Ordinary timestamp jitter between position and monotonic clock stays far below this. */
        const val POSITION_JITTER_MS = 1_000L
    }
}
