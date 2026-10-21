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
class FakeAudioPlayer(private val clock: () -> Long = ::monotonicPlayerTimeMs) : AudioPlayer {
    override var eventGeneration: Long = 0
        private set
    override var latestAdvance: PlayerEvent.AdvancedToNext? = null
        private set
    private var advanceSerial = 0L
    override val monotonicTimeMs: Long get() = clock()
    override var latestDisconnect: OutputDisconnect? = null
        private set
    private var disconnectOccurrence = 0L
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    var loadedTrack: Track? = null
        private set

    /** What [setNext] queued after the current track. */
    var nextTrack: Track? = null
        private set
    private var nextEntryId: Long? = null

    var volume: Float = 1f
        private set

    override fun setNext(track: Track?) = setNext(track, null)

    override fun setNext(track: Track?, entryId: Long?) {
        nextTrack = track
        nextEntryId = entryId
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
    }

    /** Simulates the platform reading the file's loudness tags. */
    fun reportReplayGain(tags: ReplayGainTags?) {
        _state.update { it.copy(replayGain = tags) }
    }

    override fun load(track: Track, playWhenReady: Boolean) = loadAt(track, playWhenReady, 0L)

    override fun loadAt(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        eventGeneration++
        latestAdvance = null
        if (playWhenReady) latestDisconnect = null
        loadedTrack = track
        _state.value = PlayerState(
            status = PlayerStatus.Ready,
            playWhenReady = playWhenReady,
            positionMs = startPositionMs.coerceIn(0L, track.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE),
            durationMs = track.durationMs,
        )
    }

    override fun transitionToAt(track: Track, playWhenReady: Boolean, startPositionMs: Long) =
        loadAt(track, playWhenReady, startPositionMs)

    override fun play() {
        if (loadedTrack != null) {
            latestDisconnect = null
            _state.update { it.copy(playWhenReady = true) }
        }
    }

    override fun pause() {
        _state.update { it.copy(playWhenReady = false) }
    }

    /** Mirrors native ordering: retain the occurrence before notification and paused state. */
    fun disconnectOutput() {
        if (!_state.value.playWhenReady || loadedTrack == null) return
        latestDisconnect = OutputDisconnect(++disconnectOccurrence, monotonicTimeMs)
        _events.tryEmit(PlayerEvent.PausedByDisconnect)
        _state.update { it.copy(playWhenReady = false) }
    }

    override fun seekTo(positionMs: Long) {
        if (loadedTrack == null) return
        eventGeneration++
        latestAdvance = null
        _state.update {
            val max = if (it.durationMs > 0) it.durationMs else Long.MAX_VALUE
            it.copy(
                positionMs = positionMs.coerceIn(0, max),
                status = if (it.status == PlayerStatus.Ended) PlayerStatus.Ready else it.status,
            )
        }
    }

    override fun stop() {
        eventGeneration++
        latestAdvance = null
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

    /** Simulates the track playing to its end: the next one starts gaplessly if one was set. */
    fun finishTrack() {
        val next = nextTrack
        val consumedGeneration = eventGeneration
        if (next != null) {
            val consumedId = nextEntryId
            loadedTrack = next
            nextTrack = null
            nextEntryId = null
            val advance = PlayerEvent.AdvancedToNext(consumedGeneration, consumedId, next, ++advanceSerial)
            latestAdvance = advance
            _state.update { it.copy(status = PlayerStatus.Ready, positionMs = 0, durationMs = next.durationMs, replayGain = null) }
            _events.tryEmit(advance)
            return
        }
        _state.update { it.copy(status = PlayerStatus.Ended, positionMs = it.durationMs) }
        _events.tryEmit(PlayerEvent.TrackEnded(consumedGeneration))
    }

    fun fail(message: String) {
        _state.update { it.copy(status = PlayerStatus.Error, errorMessage = message) }
    }
}
