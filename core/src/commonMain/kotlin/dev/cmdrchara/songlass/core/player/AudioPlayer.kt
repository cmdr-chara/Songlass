package dev.cmdrchara.songlass.core.player

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
) {
    val isPlaying: Boolean
        get() = playWhenReady && (status == PlayerStatus.Ready || status == PlayerStatus.Buffering)
}

sealed interface PlayerEvent {
    /** The loaded track played to the end by itself and nothing was queued after it. */
    data object TrackEnded : PlayerEvent

    /** The track set with [AudioPlayer.setNext] started right after the previous one, without a gap. */
    data object AdvancedToNext : PlayerEvent
}

/**
 * Plays the current track and, for gapless playback, keeps the next one ready. Queue logic lives
 * in [dev.cmdrchara.songlass.core.playback.PlaybackController]; implementations only wrap the
 * platform player (Media3 on Android, AVFoundation on iOS).
 */
interface AudioPlayer {
    val state: StateFlow<PlayerState>
    val events: Flow<PlayerEvent>

    fun load(track: Track, playWhenReady: Boolean)

    /**
     * The track to play right after the current one, prepared ahead so there is no gap; null to
     * stop at the end of the current one. Kept across [load] until replaced. When it starts, the
     * player emits [PlayerEvent.AdvancedToNext] and it becomes the current track.
     */
    fun setNext(track: Track?)

    /** Player output volume, 0..1, on top of the device volume (used for ReplayGain). */
    fun setVolume(volume: Float)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /** Unloads the current track and returns to [PlayerStatus.Idle]. */
    fun stop()
    fun release()
}
