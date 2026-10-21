package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.isSameFileAs
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerEvent
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import dev.cmdrchara.songlass.core.settings.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Transport boundaries for observers; position ticks alone do not prove listening time. */
data class PlaybackTimeline(val occurrenceId: Long = 0, val discontinuityId: Long = 0)

/**
 * The single entry point the UI uses to control playback. Owns the [PlaybackQueue] and
 * tells the [AudioPlayer] which track to play.
 */
class PlaybackController(
    private val player: AudioPlayer,
    scope: CoroutineScope,
    private val random: Random = Random,
    private val markers: TrackMarkers? = null,
    private val onUserTransport: () -> Unit = {},
    private val preferences: () -> AppPreferences = { AppPreferences() },
) {
    private val _queue = MutableStateFlow(PlaybackQueue())
    val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()
    val playerState: StateFlow<PlayerState> = player.state
    private val _timeline = MutableStateFlow(PlaybackTimeline())
    val timeline: StateFlow<PlaybackTimeline> = _timeline.asStateFlow()

    private val _stopAfterCurrent = MutableStateFlow(false)
    /** The decoder identity, deliberately independent of refreshed queue metadata. */
    private var loadedTrack: Track? = null

    /** Playback pauses when the current track ends (sleep timer "end of track"). */
    val stopAfterCurrent: StateFlow<Boolean> = _stopAfterCurrent.asStateFlow()

    init {
        scope.launch {
            player.events.collect { event ->
                when (event) {
                    is PlayerEvent.TrackEnded -> if (event.generation == player.eventGeneration) onTrackEnded()
                    is PlayerEvent.AdvancedToNext -> if (event.generation == player.eventGeneration) onAdvancedToNext()
                    // Remembered by the app graph for resuming on reconnect.
                    PlayerEvent.PausedByDisconnect -> Unit
                }
            }
        }
        // The player already publishes position ticks. Persist only at useful milestones and
        // on pauses; never write to storage for every position notification.
        scope.launch {
            var wasPlaying = false
            var previousSignature: TrackSignature? = null
            player.state.collect { status ->
                val track = loadedTrack
                val signature = track?.let(TrackSignature::of)
                if (track != null) {
                    if (status.status == PlayerStatus.Ended) safeMarkers { markers?.completed(track) }
                    else if (status.status == PlayerStatus.Ready && status.positionMs >= 0) {
                        val enabled = preferences().resumePerTrack
                        if (enabled && (status.playWhenReady || (wasPlaying && previousSignature == signature))) {
                            safeMarkers { markers?.checkpoint(track, status.positionMs,
                                preferences().longTrackThresholdMs, force = !status.playWhenReady) }
                        }
                    }
                }
                previousSignature = signature
                wasPlaying = status.playWhenReady
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

    fun pause() {
        onUserTransport()
        pauseCurrent()
    }

    private fun pauseCurrent() {
        checkpointCurrent(force = true)
        player.pause()
    }

    /** External Stop releases the decoder but retains the queue and its exact eligible checkpoint. */
    fun stop() {
        onUserTransport()
        checkpointCurrent(force = true)
        loadedTrack = null
        player.stop()
    }

    /** Plays the current entry if it is paused; does nothing while playing or with an empty queue. */
    fun resume() {
        onUserTransport()
        if (_queue.value.current == null || player.state.value.playWhenReady) return
        toggleCurrentPlayback()
    }

    /** Where the current queue was started from (album, playlist…), for the listening history. */
    var context: PlayContext? = null
        private set

    /** Replaces the queue with [tracks] and starts playing at [startIndex]. */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, context: PlayContext? = null) {
        onUserTransport()
        this.context = context
        replaceQueue(PlaybackQueue.of(tracks, startIndex, shuffle = false, _queue.value.repeatMode, random))
    }

    /** Replaces the queue with [tracks] in random order (the "Shuffle" button on an album or playlist). */
    fun shuffleTracks(tracks: List<Track>, context: PlayContext? = null) {
        if (tracks.isEmpty()) return clearQueue()
        onUserTransport()
        this.context = context
        val start = random.nextInt(tracks.size)
        replaceQueue(PlaybackQueue.of(tracks, start, shuffle = true, _queue.value.repeatMode, random))
    }

    fun togglePlayPause() {
        onUserTransport()
        toggleCurrentPlayback()
    }

    private fun toggleCurrentPlayback() {
        val state = player.state.value
        when {
            state.status == PlayerStatus.Ended -> {
                seekCurrent(0)
                player.play()
            }
            state.status == PlayerStatus.Idle || state.status == PlayerStatus.Error ->
                loadCurrent(playWhenReady = true, explicitSelection = state.status == PlayerStatus.Idle)
            state.playWhenReady -> pauseCurrent()
            else -> player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        onUserTransport()
        seekCurrent(positionMs)
    }

    private fun seekCurrent(positionMs: Long, explicit: Boolean = true) {
        if (loadedTrack == null) return
        _timeline.update { it.copy(
            occurrenceId = it.occurrenceId + if (positionMs <= 0L) 1 else 0,
            discontinuityId = it.discontinuityId + if (explicit) 1 else 0,
        ) }
        player.seekTo(positionMs)
        loadedTrack?.let { track ->
            if (positionMs <= 0L) safeMarkers { markers?.restart(track) }
            // A SeekBar drag can emit many intermediate seeks; only checkpoint on a
            // meaningful position change, with final flush on pause/change of track.
            else checkpointCurrent(force = false)
        }
    }

    /** For chapters and bookmarks. Does not change the queue when the track is already active. */
    fun playBookmark(track: Track, positionMs: Long) {
        onUserTransport()
        val at = positionMs.coerceIn(0L, track.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val status = player.state.value.status
        if (loadedTrack?.isSameFileAs(track) == true && status != PlayerStatus.Idle && status != PlayerStatus.Error) {
            seekCurrent(at)
            if (!player.state.value.playWhenReady) player.play()
            return
        }
        val index = _queue.value.entries.indexOfFirst { it.track.isSameFileAs(track) }
        if (index >= 0) moveTo(index, playWhenReady = true, manual = true, positionOverrideMs = at)
        else {
            context = null
            replaceQueue(PlaybackQueue.of(listOf(track), repeatMode = _queue.value.repeatMode, random = random), at)
        }
    }

    /** Contextual ±10 s buttons use the same seek semantics as the progress slider. */
    fun skipBy(deltaMs: Long) {
        val now = player.state.value.positionMs.coerceAtLeast(0)
        val maximum = player.state.value.durationMs.takeIf { it > 0 }
            ?: loadedTrack?.durationMs?.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = if (deltaMs > 0 && now > Long.MAX_VALUE - deltaMs) Long.MAX_VALUE else
            if (deltaMs < 0 && now < -deltaMs) 0L else now + deltaMs
        seekTo(target.coerceIn(0L, maximum))
    }

    /** A deliberate fresh start overrides and removes any saved per-track point. */
    fun playFromBeginning(track: Track) {
        playBookmark(track, 0L)
    }

    /** Goes to the next entry, keeping the play/pause state. Does nothing at the end of a non-repeating queue. */
    fun skipToNext() {
        onUserTransport()
        val index = _queue.value.nextIndex(userInitiated = true) ?: return
        moveTo(index, playWhenReady = player.state.value.playWhenReady, manual = true)
    }

    /**
     * Restarts the current track if it has played for more than [RESTART_THRESHOLD_MS],
     * otherwise goes to the previous entry (or restarts if there is none).
     */
    fun skipToPrevious() {
        onUserTransport()
        if (player.state.value.positionMs > RESTART_THRESHOLD_MS) return seekCurrent(0)
        val index = _queue.value.previousIndex() ?: return seekCurrent(0)
        moveTo(index, playWhenReady = player.state.value.playWhenReady, manual = true)
    }

    /**
     * Always goes to the previous entry, however long the current one has played (restarts it if
     * there is none). For gestures that show the track changing, such as swiping the mini player.
     */
    fun skipToPreviousTrack() {
        onUserTransport()
        val index = _queue.value.previousIndex() ?: return seekCurrent(0)
        moveTo(index, playWhenReady = player.state.value.playWhenReady, manual = true)
    }

    /** Plays the given queue entry (e.g. tapped in the Up Next list). */
    fun skipTo(entryId: Long) {
        onUserTransport()
        val index = _queue.value.indexOf(entryId)
        if (index >= 0) moveTo(index, playWhenReady = true, manual = true)
    }

    fun setShuffle(enabled: Boolean) = _queue.update { it.withShuffle(enabled, random) }

    fun cycleRepeatMode() = _queue.update { it.withRepeatMode(it.repeatMode.next()) }

    fun playNext(tracks: List<Track>) = addTracks { it.playNext(tracks) }

    fun addToQueue(tracks: List<Track>) = addTracks { it.addToEnd(tracks) }

    fun removeFromQueue(entryId: Long) {
        val before = _queue.value.current
        if (before?.id == entryId) onUserTransport()
        _queue.update { it.remove(entryId) }
        val after = _queue.value.current
        when {
            after == null -> { checkpointCurrent(force = true); loadedTrack = null; player.stop() }
            after.id != before?.id -> loadCurrent(playWhenReady = player.state.value.playWhenReady)
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) = _queue.update { it.move(fromIndex, toIndex) }

    /** Saved file tags must reach every duplicate slot and the hidden unshuffled order. */
    fun refreshTracks(tracks: List<Track>) {
        val byId = tracks.groupBy { it.id }.mapNotNull { (id, matches) ->
            matches.singleOrNull()?.let { id to it }
        }.toMap()
        _queue.update { it.withUpdatedTracks(byId) }
        _queue.value.current?.track?.takeIf { loadedTrack?.isSameFileAs(it) == true }?.let {
            loadedTrack = it
            player.updateMetadata(it)
        }
    }

    fun clearQueue() {
        onUserTransport()
        checkpointCurrent(force = true)
        loadedTrack = null
        _queue.update { PlaybackQueue(repeatMode = it.repeatMode) }
        player.stop()
    }

    private fun addTracks(change: (PlaybackQueue) -> PlaybackQueue) {
        val wasEmpty = _queue.value.current == null
        _queue.update(change)
        if (wasEmpty && _queue.value.current != null) {
            onUserTransport()
            loadCurrent(playWhenReady = true, explicitSelection = true)
        }
    }

    private fun replaceQueue(queue: PlaybackQueue, positionOverrideMs: Long? = null) {
        val replacing = _queue.value.current != null
        _queue.value = queue
        loadCurrent(playWhenReady = true, manual = replacing, explicitSelection = true, positionOverrideMs = positionOverrideMs)
    }

    private fun moveTo(index: Int, playWhenReady: Boolean, manual: Boolean = false, positionOverrideMs: Long? = null) {
        _queue.update { it.withCurrentIndex(index) }
        loadCurrent(playWhenReady, manual, explicitSelection = manual, positionOverrideMs = positionOverrideMs)
    }

    private fun loadCurrent(
        playWhenReady: Boolean,
        manual: Boolean = false,
        explicitSelection: Boolean = false,
        positionOverrideMs: Long? = null,
    ) {
        checkpointCurrent(force = true)
        val entry = _queue.value.current ?: run { loadedTrack = null; return player.stop() }
        // Clear after checkpointing the outgoing decoder: reindexing can resolve both identities
        // to this same file, and an outgoing flush must not reintroduce a deliberate restart.
        if (positionOverrideMs == 0L) safeMarkers { markers?.restart(entry.track) }
        val chosen = if (explicitSelection && preferences().resumePerTrack)
            markers?.progressFor(entry.track, preferences().longTrackThresholdMs) else null
        val start = positionOverrideMs ?: chosen ?: 0L
        _timeline.update { it.copy(
            occurrenceId = it.occurrenceId + 1,
            discontinuityId = it.discontinuityId + if (positionOverrideMs != null) 1 else 0,
        ) }
        // Assign before the player's synchronous state callback can run: it must not attribute
        // the new decoder's zero position to the previously loaded recording.
        loadedTrack = entry.track
        if (manual) player.transitionToAt(entry.track, playWhenReady, start)
        else player.loadAt(entry.track, playWhenReady, start)
    }

    private inline fun safeMarkers(write: () -> Unit) {
        // A storage failure must never block Play, Pause, Stop or queue advancement.
        try { write() } catch (_: Exception) { }
    }

    private fun checkpointCurrent(force: Boolean = false) {
        val track = loadedTrack ?: return
        if (!preferences().resumePerTrack) return
        val status = player.state.value
        if (status.status == PlayerStatus.Ended) safeMarkers { markers?.completed(track) }
        else safeMarkers { markers?.checkpoint(track, status.positionMs, preferences().longTrackThresholdMs, force) }
    }

    private fun onTrackEnded() {
        loadedTrack?.let { track -> safeMarkers { markers?.completed(track) } }
        val queue = _queue.value
        val index = queue.nextIndex(userInitiated = false) ?: return
        if (_stopAfterCurrent.value) {
            // Ready on the next track, paused, as when a sleep timer ends a song.
            _stopAfterCurrent.value = false
            return moveTo(index, playWhenReady = false)
        }
        if (index == queue.currentIndex) {
            seekCurrent(0, explicit = false)
            player.play()
        } else {
            moveTo(index, playWhenReady = true)
        }
    }

    /** The player already started the next track: only the queue position moves. */
    private fun onAdvancedToNext() {
        loadedTrack?.let { track -> safeMarkers { markers?.completed(track) } }
        val index = _queue.value.nextIndex(userInitiated = false) ?: return
        _timeline.update { it.copy(occurrenceId = it.occurrenceId + 1) }
        _queue.update { it.withCurrentIndex(index) }
        loadedTrack = _queue.value.current?.track
    }

    companion object {
        /** Same idea as Apple Music: "previous" restarts the track after the first few seconds. */
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
