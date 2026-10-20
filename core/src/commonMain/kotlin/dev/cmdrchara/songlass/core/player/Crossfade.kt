package dev.cmdrchara.songlass.core.player

import kotlin.math.PI
import kotlin.math.sin

/**
 * When the overlap between two tracks starts and how long it lasts. All positions are playback
 * positions of the track that is fading out.
 */
data class CrossfadePlan(val startMs: Long, val durationMs: Long) {
    val endMs: Long get() = startMs + durationMs

    /** 0 before the overlap, 1 once it is over. */
    fun progressAt(positionMs: Long): Float =
        if (durationMs <= 0) 1f else ((positionMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)

    fun hasStarted(positionMs: Long): Boolean = positionMs >= startMs

    fun isComplete(positionMs: Long): Boolean = positionMs >= endMs

    fun msUntilStart(positionMs: Long): Long = (startMs - positionMs).coerceAtLeast(0)
}

/** Linear gains (0..1) of the two tracks at one moment of the overlap. */
data class CrossfadeGains(val outgoing: Float, val incoming: Float)

enum class CrossfadePhase {
    /** The next track is prepared on a second player but has not started. */
    Armed,

    /** Both tracks are audible. */
    Fading,
}

/** Something that happened while a crossfade was armed or running. */
enum class CrossfadeInterruption {
    /** The user moved the playhead of the current track. */
    Seek,

    /** A track was loaded explicitly (skip, tapped in the queue). */
    Load,

    /** Playback was stopped, the queue cleared or the player released. */
    Stop,

    /** The queue now plays something else (or nothing) after the current track. */
    NextChanged,

    /** The prepared next track could not be opened. */
    StandbyFailed,

    /** The fading-out track ran out before the planned end (an estimated duration was too long). */
    OutgoingEnded,

    Pause,
    Resume,
}

enum class CrossfadeResolution {
    /** Nothing changes. */
    Keep,

    /** Compute a new plan from the new playhead position. */
    Replan,

    /** Drop the plan and the prepared track; the next track plays gaplessly if it is still queued. */
    Disarm,

    /** End the overlap now: the track fading out is dropped, the incoming one plays on at full gain. */
    Finish,
}

/**
 * Crossfade policy, free of any player. The Android player applies it to two ExoPlayers; the
 * decoded audio of both tracks really overlaps (it is not a fade out followed by a fade in).
 */
object Crossfade {
    const val MAX_DURATION_MS = 12_000L

    /** Shorter overlaps are not worth a second decoder: the tracks are joined gaplessly instead. */
    const val MIN_EFFECTIVE_MS = 250L

    /** How often the gains are updated while both tracks play. */
    const val TICK_MS = 25L

    /** A stored or requested duration clamped to 0 (off) .. [MAX_DURATION_MS]. */
    fun normalize(durationMs: Long): Long = durationMs.coerceIn(0, MAX_DURATION_MS)

    /**
     * The overlap from [outgoingDurationMs] into a track of [incomingDurationMs], for a playhead at
     * [positionMs] of the outgoing track; null when the tracks should be joined without overlap.
     *
     * - Off, or shorter than [MIN_EFFECTIVE_MS]: null.
     * - Unknown durations: null, since the start of the overlap cannot be computed.
     * - A track looping into itself ([sameTrack], repeat one) stays gapless, as in other players.
     * - Short tracks: the overlap never exceeds half of the shorter track, so the incoming track
     *   cannot end during it and the outgoing one is heard on its own first.
     * - A playhead already inside the window (seek near the end): the overlap shrinks to what is left.
     */
    fun plan(
        configuredMs: Long,
        outgoingDurationMs: Long,
        incomingDurationMs: Long,
        positionMs: Long = 0,
        sameTrack: Boolean = false,
    ): CrossfadePlan? {
        val configured = normalize(configuredMs)
        if (configured < MIN_EFFECTIVE_MS || sameTrack) return null
        if (outgoingDurationMs <= 0 || incomingDurationMs <= 0) return null
        val wanted = minOf(configured, minOf(outgoingDurationMs, incomingDurationMs) / 2)
        val start = maxOf(positionMs.coerceAtLeast(0), outgoingDurationMs - wanted)
        val duration = outgoingDurationMs - start
        return if (duration < MIN_EFFECTIVE_MS) null else CrossfadePlan(start, duration)
    }

    /**
     * Gains at [progress] (0..1). They always add up to 1: both tracks are already at or below
     * full scale (ReplayGain only attenuates, the equalizer keeps its headroom), so even the sum of
     * two identical, correlated signals cannot clip. The curve is smooth at both ends, which avoids
     * an audible corner where the overlap starts and stops; at the middle each track is at 0.5.
     */
    fun gains(progress: Float): CrossfadeGains {
        val s = sin(progress.coerceIn(0f, 1f) * (PI / 2)).toFloat()
        val incoming = s * s
        return CrossfadeGains(outgoing = 1f - incoming, incoming = incoming)
    }

    /** What to do with the plan after [interruption] happened in [phase]. */
    fun resolve(phase: CrossfadePhase, interruption: CrossfadeInterruption): CrossfadeResolution = when (phase) {
        CrossfadePhase.Armed -> when (interruption) {
            CrossfadeInterruption.Seek -> CrossfadeResolution.Replan
            CrossfadeInterruption.Load,
            CrossfadeInterruption.Stop,
            CrossfadeInterruption.NextChanged,
            CrossfadeInterruption.StandbyFailed,
            -> CrossfadeResolution.Disarm
            // The track ended before the overlap could start: hand over to the prepared one now.
            CrossfadeInterruption.OutgoingEnded -> CrossfadeResolution.Finish
            CrossfadeInterruption.Pause, CrossfadeInterruption.Resume -> CrossfadeResolution.Keep
        }
        CrossfadePhase.Fading -> when (interruption) {
            // The user takes control of the incoming track, or everything is replaced or stopped.
            CrossfadeInterruption.Seek,
            CrossfadeInterruption.Load,
            CrossfadeInterruption.Stop,
            CrossfadeInterruption.OutgoingEnded,
            -> CrossfadeResolution.Finish
            // These concern the track after the incoming one, not the overlap itself. The overlap
            // is driven by the outgoing track's position, so pausing freezes it by itself.
            CrossfadeInterruption.NextChanged,
            CrossfadeInterruption.StandbyFailed,
            CrossfadeInterruption.Pause,
            CrossfadeInterruption.Resume,
            -> CrossfadeResolution.Keep
        }
    }
}
