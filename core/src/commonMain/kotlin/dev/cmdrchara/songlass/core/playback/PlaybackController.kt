package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.ResumeOverrides
import dev.cmdrchara.songlass.core.library.ResumePolicy
import dev.cmdrchara.songlass.core.library.resumePolicy
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.TrackAvailability
import dev.cmdrchara.songlass.core.model.isSameFileAs
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerEvent
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import dev.cmdrchara.songlass.core.queue.QueueEntry
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.queue.TemporaryPlacement
import dev.cmdrchara.songlass.core.queue.shuffledBy
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.QueueAddOrder
import dev.cmdrchara.songlass.core.settings.QueueEnd
import dev.cmdrchara.songlass.core.settings.QueueStart
import dev.cmdrchara.songlass.core.settings.ShuffleBias
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Transport boundaries for observers; position ticks alone do not prove listening time. */
data class PlaybackTimeline(val occurrenceId: Long = 0, val discontinuityId: Long = 0)

/** Session-only points in the current track's own timeline (including CUE-relative time). */
data class AbLoop(val trackId: String, val startMs: Long, val endMs: Long? = null)

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
    /** Counted plays of a song, for a shuffle that favours the songs played least. */
    private val playCountOf: (Track) -> Int = { 0 },
    /**
     * Per-collection resumption overrides. The controller applies them when reading and writing
     * resume points; the folder/playlist pages edit this same store, so no screen duplicates the
     * resolution. In-memory by default, as for the other optional collaborators.
     */
    val resumeOverrides: ResumeOverrides = ResumeOverrides(),
    // Last, so existing callers can keep passing it as a trailing lambda.
    private val preferences: () -> AppPreferences = { AppPreferences() },
) {
    private val _queue = MutableStateFlow(PlaybackQueue())
    val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()
    val playerState: StateFlow<PlayerState> = player.state
    /** Same monotonic clock as the native position samples. */
    val monotonicTimeMs: Long get() = player.monotonicTimeMs
    private val _timeline = MutableStateFlow(PlaybackTimeline())
    val timeline: StateFlow<PlaybackTimeline> = _timeline.asStateFlow()
    /** Serial owner-thread token for rejecting delayed gestures after a newer manual intent. */
    var transportIntentRevision: Long = 0
        private set

    /**
     * Records a Play/selection that must await the library before it can open a file.
     * The serial owner thread must compare the returned revision again immediately before
     * applying the result. A newer pending request, Pause, Stop or selection supersedes it.
     * Reconcile native advancement and cancel reconnect/gesture tokens first; the requested
     * selection itself is not applied until the caller validates the returned revision.
     */
    fun beginAsyncPlaybackRequest(): Long {
        beginUserTransport()
        return transportIntentRevision
    }

    private val _abLoop = MutableStateFlow<AbLoop?>(null)
    val abLoop: StateFlow<AbLoop?> = _abLoop.asStateFlow()
    // Native seek fades can finish later. Do not restart the same pending seek on every tick.
    private var loopSeekPending = false

    private val _stopAfterCurrent = MutableStateFlow(false)
    /** The decoder identity, deliberately independent of refreshed queue metadata. */
    private var loadedTrack: Track? = null
    private var libraryAvailability: TrackAvailability? = null
    private var handledAdvanceSerial = 0L
    private var prefetchInitialized = false
    private var prefetchedEntry: QueueEntry? = null

    /** Playback pauses when the current track ends (sleep timer "end of track"). */
    val stopAfterCurrent: StateFlow<Boolean> = _stopAfterCurrent.asStateFlow()

    init {
        scope.launch {
            player.events.collect { event ->
                // State and UI commands can already have observed the incoming decoder while
                // this Flow event was queued. Reconcile its retained identity before any event.
                synchronizeNativeAdvance()
                when (event) {
                    is PlayerEvent.TrackEnded -> if (event.generation == player.eventGeneration) onTrackEnded()
                    is PlayerEvent.AdvancedToNext -> acceptAdvance(event)
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
            player.state.collect {
                synchronizeNativeAdvance()
                // Reconciliation may explicitly load a different continuation. Never pair the
                // old emitted position with the newly authoritative decoder identity.
                if (repeatAbLoop()) return@collect
                val status = player.state.value
                val track = loadedTrack
                val signature = track?.let(TrackSignature::of)
                if (track != null) {
                    if (status.status == PlayerStatus.Ended) safeMarkers { markers?.completed(track) }
                    else if (status.status == PlayerStatus.Ready && status.positionMs >= 0) {
                        val resume = effectiveResumePolicy()
                        if (resume.resumePerTrack && (status.playWhenReady || (wasPlaying && previousSignature == signature))) {
                            safeMarkers { markers?.checkpoint(track, status.positionMs,
                                resume.longTrackThresholdMs, force = !status.playWhenReady) }
                        }
                    }
                }
                previousSignature = signature
                wasPlaying = status.playWhenReady
            }
        }
        // Keep the track that follows ready in the player, for gapless playback.
        scope.launch {
            combine(_queue, _stopAfterCurrent) { _, _ -> Unit }
                .collect { reconcilePrefetch() }
        }
    }

    /** Re-read authoritative state: an older queued Flow emission must never re-arm a stale URI. */
    private fun reconcilePrefetch(force: Boolean = false) {
        val queue = _queue.value
        val nextIndex = queue.nextIndex(userInitiated = false)
        val next = if (_abLoop.value?.endMs != null || _stopAfterCurrent.value || stopsAtQueueEnd(queue, nextIndex)) null
            else nextIndex?.let { queue.entries[it] }
        if (!force && prefetchInitialized && prefetchedEntry == next) return
        // Publish the command identity before calling a player that can emit synchronously.
        prefetchInitialized = true
        prefetchedEntry = next
        player.setNext(next?.track, next?.id)
    }

    fun setStopAfterCurrent(enabled: Boolean) {
        synchronizeNativeAdvance()
        _stopAfterCurrent.value = enabled
        if (enabled) clearAbLoop()
        reconcilePrefetch()
    }

    /** Capture from authoritative native state, rejecting gestures from an outgoing track. */
    fun markLoopStart(expectedTrackId: String): Boolean {
        beginUserTransport()
        val track = loadedTrack ?: return false
        val status = player.state.value
        val duration = status.durationMs.takeIf { it > 0 } ?: track.durationMs
        if (track.id != expectedTrackId || _queue.value.current?.isAvailable != true ||
            status.status != PlayerStatus.Ready || duration < MIN_LOOP_MS ||
            status.positionMs !in 0..(duration - MIN_LOOP_MS)) return false
        loopSeekPending = false
        _abLoop.value = AbLoop(track.id, status.positionMs)
        reconcilePrefetch()
        return true
    }

    fun markLoopEnd(expectedTrackId: String): Boolean {
        beginUserTransport()
        val loop = _abLoop.value ?: return false
        val status = player.state.value
        val duration = status.durationMs.takeIf { it > 0 } ?: loadedTrack?.durationMs ?: 0L
        if (loop.trackId != expectedTrackId || loadedTrack?.id != expectedTrackId ||
            _queue.value.current?.isAvailable != true || status.status != PlayerStatus.Ready ||
            status.positionMs > duration || status.positionMs - loop.startMs < MIN_LOOP_MS) return false
        _abLoop.value = loop.copy(endMs = status.positionMs)
        _stopAfterCurrent.value = false
        reconcilePrefetch()
        loopSeekPending = true
        seekCurrent(loop.startMs)
        return true
    }

    fun clearAbLoop() = clearAbLoop(reconcile = true)

    private fun clearAbLoop(reconcile: Boolean) {
        if (_abLoop.value == null) return
        loopSeekPending = false
        _abLoop.value = null
        if (reconcile) reconcilePrefetch()
    }

    /** Uses the process-owned player ticks, never a UI timer; precision follows native samples. */
    private fun repeatAbLoop(ended: Boolean = false): Boolean {
        val loop = _abLoop.value ?: return false
        val end = loop.endMs ?: return false
        val status = player.state.value
        val duration = status.durationMs.takeIf { it > 0 } ?: loadedTrack?.durationMs ?: 0L
        if (loadedTrack?.id != loop.trackId || status.status == PlayerStatus.Idle ||
            status.status == PlayerStatus.Error || duration < end) {
            clearAbLoop()
            return false
        }
        if (loopSeekPending) {
            if (status.positionMs < end) loopSeekPending = false
            else return true
        }
        if (!status.playWhenReady || (!ended && status.positionMs < end) ||
            (status.status != PlayerStatus.Ready && status.status != PlayerStatus.Ended)) return false
        loopSeekPending = true
        seekCurrent(loop.startMs)
        if (ended || status.status == PlayerStatus.Ended) player.play()
        return true
    }

    /** Main-thread boundary before a native file writer temporarily releases its decoders. */
    fun prepareForFileWrite() {
        synchronizeNativeAdvance()
        checkpointCurrent(force = true)
    }

    fun pause() {
        beginUserTransport()
        pauseCurrent()
    }

    private fun pauseCurrent() {
        checkpointCurrent(force = true)
        player.pause()
    }

    /** External Stop releases the decoder but retains the queue and its exact eligible checkpoint. */
    fun stop() {
        beginUserTransport()
        checkpointCurrent(force = true)
        clearAbLoop(reconcile = false)
        loadedTrack = null
        player.stop()
    }

    /** Plays the current entry if it is paused; does nothing while playing or with an empty queue. */
    fun resume() {
        beginUserTransport()
        val state = player.state.value
        if (_queue.value.current == null || (state.playWhenReady &&
                (state.status == PlayerStatus.Ready || state.status == PlayerStatus.Buffering))) return
        toggleCurrentPlayback()
    }

    /** Where the current queue was started from (album, playlist…), for the listening history. */
    var context: PlayContext? = null
        private set

    /** A one-shot addition is not attributed to the album/playlist it temporarily interrupts. */
    val listeningContext: PlayContext? get() = context.takeUnless { _queue.value.current?.isTemporary == true }

    /** Global preferences with the override of the collection this queue was started from. */
    private fun effectiveResumePolicy(): ResumePolicy =
        preferences().resumePolicy(resumeOverrides.overrideFor(context))

    /** Replaces the queue with [tracks] and starts playing at [startIndex]. */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, context: PlayContext? = null, startPositionMs: Long? = null) {
        beginUserTransport()
        val availability = libraryAvailability
        if (tracks.isNotEmpty() && availability != null &&
            availability.resolve(tracks.getOrNull(startIndex) ?: return) == null) return
        this.context = context
        // Choosing a song normally ends shuffle; "keep shuffle" reshuffles the new list with that song first.
        val keepShuffle = preferences().keepShuffleOnSelection && _queue.value.isShuffled
        replaceQueue(PlaybackQueue.of(tracks, startIndex, shuffle = keepShuffle, _queue.value.repeatMode, random,
            firstEntryId = _queue.value.nextEntryId, weight = shuffleWeight()),
            positionOverrideMs = startPositionMs?.coerceAtLeast(0))
    }

    /** Replaces the queue with [tracks] in random order (the "Shuffle" button on an album or playlist). */
    fun shuffleTracks(tracks: List<Track>, context: PlayContext? = null, startPositionMs: Long? = null) {
        if (tracks.isEmpty()) return clearQueue()
        beginUserTransport()
        val availability = libraryAvailability
        val candidates = tracks.indices.filter { availability == null || availability.resolve(tracks[it]) != null }
        if (candidates.isEmpty()) return
        val weight = shuffleWeight()
        val start = if (weight == null) candidates[random.nextInt(candidates.size)]
            else candidates.shuffledBy(random) { weight(tracks[it]) }.first()
        this.context = context
        replaceQueue(PlaybackQueue.of(tracks, start, shuffle = true, _queue.value.repeatMode, random,
            firstEntryId = _queue.value.nextEntryId, weight = weight), positionOverrideMs = startPositionMs?.coerceAtLeast(0))
    }

    fun togglePlayPause() {
        beginUserTransport()
        toggleCurrentPlayback()
    }

    private fun toggleCurrentPlayback() {
        val state = player.state.value
        when {
            state.status == PlayerStatus.Ended && _queue.value.current?.isAvailable != true -> {
                loadedTrack = null
                player.stop()
            }
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
        beginUserTransport()
        seekCurrent(positionMs)
    }

    private fun seekCurrent(positionMs: Long, explicit: Boolean = true) {
        if (loadedTrack == null) return
        _abLoop.value?.let { loop ->
            if (positionMs < loop.startMs || (loop.endMs != null && positionMs >= loop.endMs)) clearAbLoop()
        }
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
        beginUserTransport()
        val target = if (libraryAvailability == null) track else libraryAvailability?.resolve(track) ?: return
        val at = positionMs.coerceIn(0L, target.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val status = player.state.value.status
        // A scan may safely remap a queue slot to a new URI, but the open decoder still
        // owns the old identity. Explicit playback must adopt the resolved URI instead
        // of pretending a seek refreshed the decoder and its metadata.
        val sameOpenedFile = loadedTrack?.isSameFileAs(target) == true
        if (sameOpenedFile && status != PlayerStatus.Idle && status != PlayerStatus.Error) {
            seekCurrent(at)
            if (!player.state.value.playWhenReady) player.play()
            return
        }
        val index = _queue.value.entries.indexOfFirst { it.track.isSameFileAs(target) }
        if (index >= 0 && _queue.value.entries[index].isAvailable) moveTo(index, playWhenReady = true, manual = true, positionOverrideMs = at)
        else if (index >= 0) return
        else {
            context = null
            replaceQueue(PlaybackQueue.of(listOf(target), repeatMode = _queue.value.repeatMode, random = random,
                firstEntryId = _queue.value.nextEntryId), at)
        }
    }

    /** Contextual ±10 s buttons use the same seek semantics as the progress slider. */
    fun skipBy(deltaMs: Long) {
        synchronizeNativeAdvance()
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
        beginUserTransport()
        val index = _queue.value.nextIndex(userInitiated = true) ?: run {
            if (_queue.value.current?.isTemporary == true) leaveTemporarySegment(manual = true)
            return
        }
        moveTo(index, playWhenReady = player.state.value.playWhenReady, manual = true)
    }

    /**
     * Restarts the current track if it has played for more than [RESTART_THRESHOLD_MS],
     * otherwise goes to the previous entry (or restarts if there is none).
     */
    fun skipToPrevious() {
        beginUserTransport()
        if (player.state.value.positionMs > RESTART_THRESHOLD_MS) return seekCurrent(0)
        val index = _queue.value.previousIndex() ?: return seekCurrent(0)
        moveTo(index, playWhenReady = player.state.value.playWhenReady, manual = true)
    }

    /**
     * Always goes to the previous entry, however long the current one has played (restarts it if
     * there is none). For gestures that show the track changing, such as swiping the mini player.
     */
    fun skipToPreviousTrack() {
        beginUserTransport()
        val index = _queue.value.previousIndex() ?: return seekCurrent(0)
        moveTo(index, playWhenReady = player.state.value.playWhenReady, manual = true)
    }

    /** Plays the given queue entry (e.g. tapped in the Up Next list). */
    fun skipTo(entryId: Long) {
        synchronizeNativeAdvance()
        val index = _queue.value.indexOf(entryId)
        if (index < 0 || !_queue.value.entries[index].isAvailable) return
        notifyUserTransport()
        moveTo(index, playWhenReady = true, manual = true)
    }

    fun setShuffle(enabled: Boolean) {
        synchronizeNativeAdvance()
        _queue.update { it.withShuffle(enabled, random, shuffleWeight()) }
        reconcilePrefetch()
    }

    fun cycleRepeatMode() {
        synchronizeNativeAdvance()
        setRepeatMode(_queue.value.repeatMode.next())
    }

    /** The app and external controls select the same core mode; decoders never own looping. */
    fun setRepeatMode(mode: RepeatMode) {
        synchronizeNativeAdvance()
        _queue.update { it.withRepeatMode(mode) }
        reconcilePrefetch()
    }

    fun playNext(tracks: List<Track>) = addTracks { it.enqueueTemporary(tracks, playNext = true) }

    /**
     * Adds [tracks] as one-shot additions, following the queue rules: replace the waiting additions,
     * where to place them, whether they start right away, and in what order they join the waiting ones.
     */
    fun addToQueue(tracks: List<Track>) {
        val rules = preferences()
        val before = _queue.value.nextEntryId
        addTracks { queue ->
            (if (rules.queueReplaces) queue.clearTemporary() else queue).enqueueTemporary(
                tracks,
                placement = if (rules.queueStart == QueueStart.AfterCollection) TemporaryPlacement.AfterCollection else TemporaryPlacement.AfterCurrent,
                shuffleAdded = rules.queueAddOrder == QueueAddOrder.Shuffled,
                random = random,
            )
        }
        val queue = _queue.value
        if (rules.queueStart == QueueStart.Immediately && queue.nextEntryId > before && queue.current?.isTemporary != true) {
            queue.upNext.firstOrNull { it.isTemporary && it.isAvailable }?.let { skipTo(it.id) }
        }
    }

    private fun shuffleWeight(): ((Track) -> Double)? =
        if (preferences().shuffleBias == ShuffleBias.LeastPlayed) { track -> 1.0 / (1 + playCountOf(track)) } else null

    /** With "stop at the end of the additions", the collection waits instead of carrying on by itself. */
    private fun stopsAtQueueEnd(queue: PlaybackQueue, nextIndex: Int?): Boolean =
        preferences().queueEnd == QueueEnd.Stop && queue.current?.isTemporary == true &&
            nextIndex != null && nextIndex != queue.currentIndex && !queue.entries[nextIndex].isTemporary

    fun clearTemporaryQueue() {
        synchronizeNativeAdvance()
        _queue.update { it.clearTemporary() }
    }

    fun returnToSource() {
        beginUserTransport()
        leaveTemporarySegment(manual = true)
    }

    private fun leaveTemporarySegment(manual: Boolean) {
        val before = _queue.value.current
        val playing = player.state.value.playWhenReady
        _queue.update { it.returnToSource() }
        if (_queue.value.current?.id != before?.id) loadCurrent(playing, manual = manual)
    }

    fun removeFromQueue(entryId: Long) {
        synchronizeNativeAdvance()
        if (_queue.value.indexOf(entryId) < 0) return
        val before = _queue.value.current
        if (before?.id == entryId) notifyUserTransport()
        _queue.update { it.remove(entryId) }
        val after = _queue.value.current
        when {
            after == null -> { checkpointCurrent(force = true); loadedTrack = null; player.stop() }
            after.id != before?.id -> loadCurrent(playWhenReady = player.state.value.playWhenReady)
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val before = _queue.value
        val movingId = before.entries.getOrNull(fromIndex)?.id
        val targetId = before.entries.getOrNull(toIndex)?.id
        synchronizeNativeAdvance()
        if (movingId == null || targetId == null) return
        _queue.update { queue ->
            // Consuming a temporary row can change both indices during synchronization.
            val from = queue.indexOf(movingId)
            val to = queue.indexOf(targetId)
            if (from < 0 || to < 0) queue else queue.move(from, to)
        }
    }

    /** Accessible/drag actions can outlive their composed row; resolve its slot at execution. */
    fun moveQueueEntry(entryId: Long, direction: Int): Boolean {
        synchronizeNativeAdvance()
        if (direction != -1 && direction != 1) return false
        var moved = false
        _queue.update { queue ->
            val from = queue.indexOf(entryId)
            val to = from + direction
            if (from <= queue.currentIndex || to <= queue.currentIndex || to !in queue.entries.indices ||
                queue.entries[from].isTemporary != queue.entries[to].isTemporary) queue
            else queue.move(from, to).also { moved = true }
        }
        return moved
    }

    /** Saved tags reach every slot; [stableIds] requires a registry-validated canonical scan. */
    fun refreshTracks(tracks: List<Track>, stableIds: Boolean = false) {
        synchronizeNativeAdvance()
        val availability = TrackAvailability(tracks, stableIds)
        libraryAvailability = availability
        _queue.update { it.withResolvedTracks(availability::resolve) }
        if (_queue.value.current?.let { it.isAvailable && loadedTrack?.isSameFileAs(it.track) == true } != true) clearAbLoop()
        // MainScope's collectors are dispatched. Do not leave an invalidated next file armed
        // until that later message, especially after an early tag-source rejection or revoke.
        reconcilePrefetch()
        _queue.value.current?.track?.takeIf { loadedTrack?.isSameFileAs(it) == true }?.let {
            loadedTrack = it
            player.updateMetadata(it)
        }
    }

    fun clearQueue() {
        beginUserTransport()
        clearAbLoop(reconcile = false)
        checkpointCurrent(force = true)
        loadedTrack = null
        _queue.update { PlaybackQueue(repeatMode = it.repeatMode, nextEntryId = it.nextEntryId) }
        player.stop()
    }

    private fun addTracks(change: (PlaybackQueue) -> PlaybackQueue) {
        synchronizeNativeAdvance()
        val wasEmpty = _queue.value.current == null
        _queue.update { before ->
            val changed = change(before)
            libraryAvailability?.let { changed.withResolvedTracks(it::resolve) } ?: changed
        }
        if (wasEmpty && _queue.value.current != null) {
            notifyUserTransport()
            loadCurrent(playWhenReady = true, explicitSelection = true)
        }
    }

    private fun replaceQueue(queue: PlaybackQueue, positionOverrideMs: Long? = null) {
        val replacing = _queue.value.current != null
        _queue.value = libraryAvailability?.let { queue.withResolvedTracks(it::resolve) } ?: queue
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
        clearAbLoop(reconcile = false)
        checkpointCurrent(force = true)
        val entry = _queue.value.current?.takeIf { it.isAvailable }
            ?: run { loadedTrack = null; return player.stop() }
        // Clear after checkpointing the outgoing decoder: reindexing can resolve both identities
        // to this same file, and an outgoing flush must not reintroduce a deliberate restart.
        if (positionOverrideMs == 0L) safeMarkers { markers?.restart(entry.track) }
        val resume = effectiveResumePolicy()
        val chosen = if (explicitSelection && resume.resumePerTrack)
            markers?.progressFor(entry.track, resume.longTrackThresholdMs) else null
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
        reconcilePrefetch()
    }

    private inline fun safeMarkers(write: () -> Unit) {
        // A storage failure must never block Play, Pause, Stop or queue advancement.
        try { write() } catch (_: Exception) { }
    }

    private fun beginUserTransport() {
        synchronizeNativeAdvance()
        notifyUserTransport()
    }

    private fun notifyUserTransport() {
        transportIntentRevision++
        onUserTransport()
    }

    private fun synchronizeNativeAdvance() {
        player.latestAdvance?.let(::acceptAdvance)
    }

    private fun acceptAdvance(event: PlayerEvent.AdvancedToNext) {
        if (event.generation != player.eventGeneration) return
        if (event.serial > 0L) {
            if (event.serial <= handledAdvanceSerial) return
            // Mark before queue/state changes can synchronously re-enter an observer.
            handledAdvanceSerial = event.serial
        }
        // The native decoder consumed the prepared item. Equality with a subsequent desired
        // slot no longer proves it is prepared: Repeat One selected before Flow delivery can
        // request exactly the item just consumed. Invalidate before any synchronous observer.
        prefetchInitialized = false
        prefetchedEntry = null
        onAdvancedToNext(event)
    }

    private fun checkpointCurrent(force: Boolean = false) {
        val track = loadedTrack ?: return
        val resume = effectiveResumePolicy()
        if (!resume.resumePerTrack) return
        val status = player.state.value
        if (status.status == PlayerStatus.Ended) safeMarkers { markers?.completed(track) }
        else safeMarkers { markers?.checkpoint(track, status.positionMs, resume.longTrackThresholdMs, force) }
    }

    private fun onTrackEnded() {
        if (repeatAbLoop(ended = true)) return
        loadedTrack?.let { track -> safeMarkers { markers?.completed(track) } }
        val queue = _queue.value
        val playing = player.state.value.playWhenReady
        val index = queue.nextIndex(userInitiated = false) ?: run {
            _stopAfterCurrent.value = false
            if (queue.current?.isTemporary == true) leaveTemporarySegment(manual = false)
            return
        }
        if (_stopAfterCurrent.value || stopsAtQueueEnd(queue, index)) {
            // Ready on the next track, paused, as when a sleep timer ends a song.
            _stopAfterCurrent.value = false
            return moveTo(index, playWhenReady = false)
        }
        if (index == queue.currentIndex) {
            seekCurrent(0, explicit = false)
            if (playing) player.play()
        } else {
            moveTo(index, playWhenReady = playing)
        }
    }

    /** The player already started the next track: only the queue position moves. */
    private fun onAdvancedToNext(event: PlayerEvent.AdvancedToNext) {
        clearAbLoop(reconcile = false)
        loadedTrack?.let { track -> safeMarkers { markers?.completed(track) } }
        val before = _queue.value
        val previousEntryId = before.current?.id
        val consumed = event.entryId?.let(before::indexOf)
        val exact = consumed?.takeIf { it >= 0 && before.entries[it].isAvailable &&
            (event.track == null || before.entries[it].track.isSameFileAs(event.track)) }
        if (event.entryId != null && exact == null) {
            // The decoder already advanced, but this slot was removed/remapped while its
            // event waited for the controller. Do not label a different track as that decoder.
            // Adopt the edited continuation with an explicit load (or stop at its end).
            loadedTrack = null
            val next = before.nextIndex(userInitiated = false)
            val playing = player.state.value.playWhenReady && !_stopAfterCurrent.value
            _stopAfterCurrent.value = false
            if (next != null) moveTo(next, playing)
            else {
                if (before.current?.isTemporary == true) _queue.update { it.returnToSource() }
                player.stop()
            }
            return
        }
        val index = exact ?: before.nextIndex(userInitiated = false) ?: return
        // Queue observers can synchronously prepare the following item and publish player
        // state. Attribute those callbacks to this incoming file before notifying observers.
        loadedTrack = before.entries[index].track
        _timeline.update { it.copy(occurrenceId = it.occurrenceId + 1) }
        _queue.update { it.withCurrentIndex(index) }
        if (_stopAfterCurrent.value) {
            // The end-of-track request raced with an already-started native join.
            _stopAfterCurrent.value = false
            player.pause()
        }
        // Repeat one (or a one-slot Repeat all) keeps an equal queue state. The native player
        // consumed its prepared item, so StateFlow/distinct cannot re-arm it for us.
        if (_queue.value.current?.id == previousEntryId) {
            reconcilePrefetch(force = true)
        }
    }

    companion object {
        /** Same idea as Apple Music: "previous" restarts the track after the first few seconds. */
        const val RESTART_THRESHOLD_MS = 3_000L
        const val MIN_LOOP_MS = 1_000L
    }
}
