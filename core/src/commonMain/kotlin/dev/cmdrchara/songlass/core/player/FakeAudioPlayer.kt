package dev.cmdrchara.songlass.core.player

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Silent [AudioPlayer] that only tracks state. Used by tests and by the UI until the real
 * platform players are wired in. Time does not pass on its own: call [advanceBy] or [finishTrack].
 */
class FakeAudioPlayer : AudioPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    var loadedTrack: Track? = null
        private set

    override fun load(track: Track, playWhenReady: Boolean) {
        loadedTrack = track
        _state.value = PlayerState(
            status = PlayerStatus.Ready,
            playWhenReady = playWhenReady,
            durationMs = track.durationMs,
        )
    }

    override fun play() {
        if (loadedTrack != null) _state.update { it.copy(playWhenReady = true) }
    }

    override fun pause() {
        _state.update { it.copy(playWhenReady = false) }
    }

    override fun seekTo(positionMs: Long) {
        if (loadedTrack == null) return
        _state.update {
            val max = if (it.durationMs > 0) it.durationMs else Long.MAX_VALUE
            it.copy(
                positionMs = positionMs.coerceIn(0, max),
                status = if (it.status == PlayerStatus.Ended) PlayerStatus.Ready else it.status,
            )
        }
    }

    override fun stop() {
        loadedTrack = null
        _state.value = PlayerState()
    }

    override fun release() = stop()

    /** Moves the playhead forward; reaching the end finishes the track. */
    fun advanceBy(ms: Long) {
        val current = _state.value
        val target = current.positionMs + ms
        if (current.durationMs > 0 && target >= current.durationMs) {
            finishTrack()
        } else {
            _state.update { it.copy(positionMs = target) }
        }
    }

    /** Simulates the track playing to its end. */
    fun finishTrack() {
        _state.update { it.copy(status = PlayerStatus.Ended, positionMs = it.durationMs) }
        _events.tryEmit(PlayerEvent.TrackEnded)
    }

    fun fail(message: String) {
        _state.update { it.copy(status = PlayerStatus.Error, errorMessage = message) }
    }
}
