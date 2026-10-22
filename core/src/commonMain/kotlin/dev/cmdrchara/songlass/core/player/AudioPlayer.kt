package dev.cmdrchara.songlass.core.player

import dev.cmdrchara.songlass.core.equalizer.EqChain
import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.TimeSource

private val playerClockOrigin = TimeSource.Monotonic.markNow()
internal fun monotonicPlayerTimeMs(): Long = playerClockOrigin.elapsedNow().inWholeMilliseconds

/** An actual disconnect, stamped at the source in [AudioPlayer.monotonicTimeMs]'s clock. */
data class OutputDisconnect(val occurrenceId: Long, val occurredAtMs: Long)

/** Playback state, modelled on Media3/ExoPlayer so both platform players map onto it easily. */
enum class PlayerStatus { Idle, Buffering, Ready, Ended, Error }

data class PlayerState(
    val status: PlayerStatus = PlayerStatus.Idle,
    /** Whether playback should run as soon as it can (true = "playing" from the user's view). */
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0,
    /** 0 when unknown. */
    val durationMs: Long = 0,
    val errorMessage: String? = null,
    val audioFormat: AudioFormatInfo? = null,
    /** Loudness tags of the playing file (see [ReplayGainTags]); null until read or if absent. */
    val replayGain: ReplayGainTags? = null,
    /** Last attempted transition; durations describe its applied plan, not measured audio timing. */
    val crossfadeStatus: CrossfadeStatus? = null,
    /** PCM and EQ observed in the active decoder's processor, not the source or DAC format. */
    val processing: AudioProcessingInfo? = null,
    val replayGainApplication: ReplayGainApplication? = null,
    /** Null on platforms without route reporting; an empty route means reporting is inconclusive. */
    val outputRoute: AudioOutputRoute? = null,
) {
    val isPlaying: Boolean
        get() = playWhenReady && (status == PlayerStatus.Ready || status == PlayerStatus.Buffering)
}

sealed interface PlayerEvent {
    /** The loaded track played to the end by itself and nothing was queued after it. */
    data class TrackEnded(val generation: Long = 0) : PlayerEvent

    /** The track set with [AudioPlayer.setNext] started right after the previous one, without a gap. */
    data class AdvancedToNext(
        val generation: Long = 0,
        /** The consumed queue slot, captured before a later queue edit can replace "next". */
        val entryId: Long? = null,
        val track: Track? = null,
        /** Monotonic per player instance; zero is reserved for legacy event-only players. */
        val serial: Long = 0,
    ) : PlayerEvent

    /**
     * Playback paused because the audio output went away. Its source occurrence and time are
     * retained in [AudioPlayer.latestDisconnect] before this notification or state is published.
     */
    data object PausedByDisconnect : PlayerEvent
}

/**
 * Plays the current track and, for gapless playback, keeps the next one ready. Queue logic lives
 * in [dev.cmdrchara.songlass.core.playback.PlaybackController]; implementations only wrap the
 * platform player (Media3 on Android, AVFoundation on iOS).
 */
interface AudioPlayer {
    val state: StateFlow<PlayerState>
    val events: Flow<PlayerEvent>

    /** Explicit loads, seeks and stops invalidate queued end/advance events from earlier intent. */
    val eventGeneration: Long get() = 0

    /**
     * The most recent native join, retained before publishing its incoming [state]. Commands can
     * reconcile the decoder's actual occurrence even when [events] has not delivered it yet.
     * Clear on an explicit load, seek or stop; its serial must not reset during this instance.
     */
    val latestAdvance: PlayerEvent.AdvancedToNext? get() = null

    /** A monotonic clock shared by disconnect timestamps and reconnect eligibility checks. */
    val monotonicTimeMs: Long get() = monotonicPlayerTimeMs()

    /**
     * The latest eligible disconnect. Clear when playback starts again; its occurrence counter
     * must never reset. A delayed notification cannot resurrect an occurrence cleared at source.
     */
    val latestDisconnect: OutputDisconnect? get() = null

    fun load(track: Track, playWhenReady: Boolean)

    /** Start at a saved position as part of the load, not as a later seek during an overlap. */
    fun loadAt(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        load(track, playWhenReady)
        if (startPositionMs > 0) seekTo(startPositionMs)
    }

    /** A user-selected track; the queue already moved, so this must not emit AdvancedToNext. */
    fun transitionTo(track: Track, playWhenReady: Boolean) = load(track, playWhenReady)

    /** Native implementations with a manual overlap prepare the incoming voice at this position. */
    fun transitionToAt(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        // For platforms without manual-overlap support, load at the requested position.
        // Overlap-capable platforms override this to prepare the incoming decoder directly.
        loadAt(track, playWhenReady, startPositionMs)
    }

    /**
     * The track to play right after the current one, prepared ahead so there is no gap; null to
     * stop at the end of the current one. Kept across [load] until replaced. When it starts, the
     * player emits [PlayerEvent.AdvancedToNext] and it becomes the current track.
     */
    fun setNext(track: Track?)

    /** Slot-aware preparation; platform implementations echo this identity when it starts. */
    fun setNext(track: Track?, entryId: Long?) = setNext(track)

    /** Updates descriptive metadata without restarting playback. */
    fun updateMetadata(track: Track) {}

    /** Player output volume, 0..1, on top of the device volume (used for ReplayGain). */
    fun setVolume(volume: Float)

    /** Equalizer applied to the output; players without one ignore it. */
    fun setEqualizer(chain: EqChain) {}

    /**
     * Length of the overlap between the end of a track and the start of the next one, 0 (off) to
     * [Crossfade.MAX_DURATION_MS]. Players that cannot overlap decoded audio ignore it and keep
     * playing gaplessly. Takes effect from the next transition; a running overlap finishes.
     */
    fun setCrossfade(durationMs: Long) {}

    /**
     * Whether playback yields to calls and other apps ([handleAudioFocus]) and pauses when the output
     * goes away, such as unplugged headphones ([pauseOnDisconnect]). Both are on by default.
     * Players without such a notion ignore it.
     */
    fun setInterruptionPolicy(handleAudioFocus: Boolean, pauseOnDisconnect: Boolean) {}

    /** Independent overlap for [transitionTo], off by default. Never changes automatic joins. */
    fun setManualCrossfade(durationMs: Long) {}

    /** Independent user transport fades. System focus/noisy pauses remain immediate. */
    fun setTransportFades(settings: TransportFadeSettings) {}
    val supportsTransportFades: Boolean get() = false

    val supportsCrossfade: Boolean get() = false

    /** True when the platform applies loudness tags separately to each overlapping decoder. */
    val handlesReplayGain: Boolean get() = false

    fun setReplayGainMode(mode: ReplayGainMode) {}

    /** Updates the route observed by the platform's existing output monitor. */
    fun setOutputRoute(route: AudioOutputRoute) {}
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /** Unloads the current track and returns to [PlayerStatus.Idle]. */
    fun stop()
    fun release()
}
