package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerEvent
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The single entry point the UI uses to control playback. Owns the [PlaybackQueue] and
 * tells the [AudioPlayer] which track to play.
 */
class PlaybackController(
    private val player: AudioPlayer,
    scope: CoroutineScope,
    private val random: Random = Random,
) {
    private val _queue = MutableStateFlow(PlaybackQueue())
    val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()
    val playerState: StateFlow<PlayerState> = player.state

    private val _stopAfterCurrent = MutableStateFlow(false)

    /** Playback pauses when the current track ends (sleep timer "end of track"). */
    val stopAfterCurrent: StateFlow<Boolean> = _stopAfterCurrent.asStateFlow()

    init {
        scope.launch {
            player.events.collect { event ->
                when (event) {
                    PlayerEvent.TrackEnded -> onTrackEnded()
                    PlayerEvent.AdvancedToNext -> onAdvancedToNext()
                    // Remembered by the app graph for resuming on reconnect.
                    PlayerEvent.PausedByDisconnect -> Unit
                }
            }
        }
        // Keep the track that follows ready in the player, for gapless playback.
        scope.launch {
            combine(_queue, _stopAfterCurrent) { queue, stop -> if (stop) null else queue.upcomingTrack() }
                .distinctUntilChanged()
                .collect { player.setNext(it) }
        }
    }

    /** What plays after the current entry when it ends by itself, honouring repeat. */
    private fun PlaybackQueue.upcomingTrack(): Track? = nextIndex(userInitiated = false)?.let { entries[it].track }

    fun setStopAfterCurrent(enabled: Boolean) {
        _stopAfterCurrent.value = enabled
    }

    fun pause() = player.pause()

    /** Plays the current entry if it is paused; does nothing while playing or with an empty queue. */
    fun resume() {
        if (_queue.value.current == null || player.state.value.playWhenReady) return
        togglePlayPause()
    }

    /** Where the current queue was started from (album, playlist…), for the listening history. */
    var context: PlayContext? = null
        private set

    /** Replaces the queue with [tracks] and starts playing at [startIndex]. */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, context: PlayContext? = null) {
        this.context = context
        replaceQueue(PlaybackQueue.of(tracks, startIndex, shuffle = false, _queue.value.repeatMode, random))
    }

    /** Replaces the queue with [tracks] in random order (the "Shuffle" button on an album or playlist). */
    fun shuffleTracks(tracks: List<Track>, context: PlayContext? = null) {
        if (tracks.isEmpty()) return clearQueue()
        this.context = context
        val start = random.nextInt(tracks.size)
        replaceQueue(PlaybackQueue.of(tracks, start, shuffle = true, _queue.value.repeatMode, random))
    }

    fun togglePlayPause() {
        val state = player.state.value
        when {
            state.status == PlayerStatus.Ended -> {
                player.seekTo(0)
                player.play()
            }
            state.status == PlayerStatus.Idle || state.status == PlayerStatus.Error -> loadCurrent(playWhenReady = true)
            state.playWhenReady -> player.pause()
            else -> player.play()
        }
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    /** Goes to the next entry, keeping the play/pause state. Does nothing at the end of a non-repeating queue. */
    fun skipToNext() {
        val index = _queue.value.nextIndex(userInitiated = true) ?: return
        moveTo(index, playWhenReady = player.state.value.playWhenReady)
    }

    /**
     * Restarts the current track if it has played for more than [RESTART_THRESHOLD_MS],
     * otherwise goes to the previous entry (or restarts if there is none).
     */
    fun skipToPrevious() {
        if (player.state.value.positionMs > RESTART_THRESHOLD_MS) return player.seekTo(0)
        val index = _queue.value.previousIndex() ?: return player.seekTo(0)
        moveTo(index, playWhenReady = player.state.value.playWhenReady)
    }

    /**
     * Always goes to the previous entry, however long the current one has played (restarts it if
     * there is none). For gestures that show the track changing, such as swiping the mini player.
     */
    fun skipToPreviousTrack() {
        val index = _queue.value.previousIndex() ?: return player.seekTo(0)
        moveTo(index, playWhenReady = player.state.value.playWhenReady)
    }

    /** Plays the given queue entry (e.g. tapped in the Up Next list). */
    fun skipTo(entryId: Long) {
        val index = _queue.value.indexOf(entryId)
        if (index >= 0) moveTo(index, playWhenReady = true)
    }

    fun setShuffle(enabled: Boolean) = _queue.update { it.withShuffle(enabled, random) }

    fun cycleRepeatMode() = _queue.update { it.withRepeatMode(it.repeatMode.next()) }

    fun playNext(tracks: List<Track>) = addTracks { it.playNext(tracks) }

    fun addToQueue(tracks: List<Track>) = addTracks { it.addToEnd(tracks) }

    fun removeFromQueue(entryId: Long) {
        val before = _queue.value.current
        _queue.update { it.remove(entryId) }
        val after = _queue.value.current
        when {
            after == null -> player.stop()
            after.id != before?.id -> loadCurrent(playWhenReady = player.state.value.playWhenReady)
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) = _queue.update { it.move(fromIndex, toIndex) }

    /** Saved file tags must reach every duplicate slot and the hidden unshuffled order. */
    fun refreshTracks(tracks: List<Track>) {
        val byId = tracks.associateBy { it.id }
        _queue.update { it.withUpdatedTracks(byId) }
        _queue.value.current?.track?.let(player::updateMetadata)
    }

    fun clearQueue() {
        _queue.update { PlaybackQueue(repeatMode = it.repeatMode) }
        player.stop()
    }

    private fun addTracks(change: (PlaybackQueue) -> PlaybackQueue) {
        val wasEmpty = _queue.value.current == null
        _queue.update(change)
        if (wasEmpty) loadCurrent(playWhenReady = true)
    }

    private fun replaceQueue(queue: PlaybackQueue) {
        _queue.value = queue
        loadCurrent(playWhenReady = true)
    }

    private fun moveTo(index: Int, playWhenReady: Boolean) {
        _queue.update { it.withCurrentIndex(index) }
        loadCurrent(playWhenReady)
    }

    private fun loadCurrent(playWhenReady: Boolean) {
        val entry = _queue.value.current ?: return player.stop()
        player.load(entry.track, playWhenReady)
    }

    private fun onTrackEnded() {
        val queue = _queue.value
        val index = queue.nextIndex(userInitiated = false) ?: return
        if (_stopAfterCurrent.value) {
            // Ready on the next track, paused, as when a sleep timer ends a song.
            _stopAfterCurrent.value = false
            return moveTo(index, playWhenReady = false)
        }
        if (index == queue.currentIndex) {
            player.seekTo(0)
            player.play()
        } else {
            moveTo(index, playWhenReady = true)
        }
    }

    /** The player already started the next track: only the queue position moves. */
    private fun onAdvancedToNext() {
        val index = _queue.value.nextIndex(userInitiated = false) ?: return
        _queue.update { it.withCurrentIndex(index) }
    }

    companion object {
        /** Same idea as Apple Music: "previous" restarts the track after the first few seconds. */
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
