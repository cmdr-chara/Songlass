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
) {
    val isPlaying: Boolean
        get() = playWhenReady && (status == PlayerStatus.Ready || status == PlayerStatus.Buffering)
}

sealed interface PlayerEvent {
    /** The loaded track played to the end by itself. */
    data object TrackEnded : PlayerEvent
}

/**
 * Plays one track at a time. Queue logic lives in [dev.cmdrchara.songlass.core.playback.PlaybackController];
 * implementations only wrap the platform player (Media3 on Android, AVFoundation on iOS).
 */
interface AudioPlayer {
    val state: StateFlow<PlayerState>
    val events: Flow<PlayerEvent>

    fun load(track: Track, playWhenReady: Boolean)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /** Unloads the current track and returns to [PlayerStatus.Idle]. */
    fun stop()
    fun release()
}
