package dev.cmdrchara.songlass.playback

import android.net.Uri
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import dev.cmdrchara.songlass.core.player.ReplayGainTags
import dev.cmdrchara.songlass.core.player.ReplayGain
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.cmdrchara.songlass.core.equalizer.EqChain
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.AudioFormatInfo
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.Crossfade
import dev.cmdrchara.songlass.core.player.CrossfadeKind
import dev.cmdrchara.songlass.core.player.CrossfadeSkipReason
import dev.cmdrchara.songlass.core.player.CrossfadeStatus
import dev.cmdrchara.songlass.core.player.CrossfadeInterruption
import dev.cmdrchara.songlass.core.player.CrossfadePhase
import dev.cmdrchara.songlass.core.player.CrossfadePlan
import dev.cmdrchara.songlass.core.player.CrossfadeResolution
import dev.cmdrchara.songlass.core.player.PlayerEvent
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.PlayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [AudioPlayer] backed by Media3's ExoPlayer. The queue lives in core's PlaybackController; the
 * player holds at most the current track and the next one ([setNext]), so ExoPlayer can join
 * them without a gap. Must be used from the main thread, like ExoPlayer itself.
 *
 * **Crossfade** ([setCrossfade], off by default) overlaps the decoded audio of two tracks. It needs
 * a second ExoPlayer, which [standbyFactory] builds on demand and which is released again when
 * crossfade is turned off. With crossfade on, the next track is prepared, paused and silent, on the
 * second player; shortly before the current track ends it starts, and the gains of both follow
 * [Crossfade.gains], driven by the playback position of the track that fades out. The incoming
 * track becomes the current one the moment it starts (one [PlayerEvent.AdvancedToNext], the queue
 * moves, [activePlayer] changes so the media session follows). Afterwards the two players swap roles.
 * Whenever an overlap is not possible (unknown or short durations, a standby that fails or is late,
 * repeat one) the next track is joined gaplessly in the active player, exactly as with crossfade off.
 *
 * [primaryPlayer] must handle audio focus; players from [standbyFactory] must not (focus moves to the
 * active player when an overlap ends, see [PlayerVoice]).
 */
@androidx.annotation.OptIn(UnstableApi::class)
class Media3AudioPlayer(
    primaryPlayer: ExoPlayer,
    private val scope: CoroutineScope,
    equalizer: EqualizerProcessor? = null,
    private val standbyFactory: (() -> PlayerVoice)? = null,
) : AudioPlayer {
    override var eventGeneration: Long = 0
        private set
    override val supportsCrossfade: Boolean get() = standbyFactory != null
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    private val primary = PlayerVoice(primaryPlayer, equalizer, focusOwner = true)
    private var secondary: PlayerVoice? = null

    /** The voice that plays the current track; everything the app sees comes from it. */
    private var active: PlayerVoice = primary

    /** The ExoPlayer of the current track. It changes when crossfade swaps the players. */
    val exoPlayer: ExoPlayer get() = active.player

    private val _activePlayer = MutableStateFlow(primaryPlayer)

    /** Emits the ExoPlayer that plays the current track, so the media session can follow it. */
    val activePlayer: StateFlow<ExoPlayer> = _activePlayer.asStateFlow()

    /** The session exposes the requested playback intent while this decoder prepares in pause. */
    val isPreparingManualTransition: Boolean get() = manualPreparing != null

    private val handler = Handler(primaryPlayer.applicationLooper)

    private enum class NextPlacement {
        /** Nothing follows, or the next track is not held anywhere yet. */
        None,

        /** The next track is the active player's second item (gapless). */
        Active,

        /** The next track is prepared on the other player, waiting for the overlap. */
        Standby,
    }

    private var positionJob: Job? = null
    private var nextTrack: Track? = null
    private var nextEntryId: Long? = null
    private var nextPlacement = NextPlacement.None
    private var crossfadeMs = 0L
    private var manualCrossfadeMs = 0L
    /** Non-zero only for the synchronous load/transition being prepared. */
    private var initialPositionMs = 0L
    private var outputVolume = 1f
    private var replayGainMode = ReplayGainMode.Off
    override val handlesReplayGain: Boolean get() = true
    private var chain: EqChain = EqChain.Flat

    /** Next track that must join gaplessly although crossfade is on (it failed or cannot overlap). */
    private var noOverlapFor: String? = null
    private var noOverlapReason: CrossfadeSkipReason? = null
    private var nextSkipReason: CrossfadeSkipReason? = null
    private var fading: PlayerVoice? = null
    private var fadePlan: CrossfadePlan? = null
    private var manualPreparing: Long? = null
    private var lastCrossfadeStatus: CrossfadeStatus? = null
    private var editingTrackId: String? = null
    private var resumeAfterEdit = false
    private var positionAfterEdit = 0L
    private var reportedDisconnect = false

    private var overlapsStarted = 0
    private var overlapsCompleted = 0
    private var handOvers = 0
    private var fallbacks = 0
    private var lastFallback: String? = null

    private val loadedTrack: Track? get() = active.track

    init {
        attach(primary)
    }

    private fun attach(voice: PlayerVoice) {
        voice.player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (voice === active) publishState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (voice === active) onGaplessTransition(mediaItem, reason)
            }

            override fun onTracksChanged(tracks: Tracks) {
                voice.replayGain = readReplayGain(tracks)
                updateBaseVolume(voice)
                if (voice === active) publishState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady && voice === active) reportedDisconnect = false
                // Either receiver can observe the unplug first. Report before mirroring the pause,
                // which otherwise turns the active voice's later callback into a no-op.
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY &&
                    (voice === active || voice === fading) && !reportedDisconnect) {
                    reportedDisconnect = true
                    _events.tryEmit(PlayerEvent.PausedByDisconnect)
                }
                // Only one player owns audio focus and each one watches its own noisy-output
                // broadcast. During an overlap a system pause of one must stop the other as well.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                ) {
                    val partner = when {
                        voice === active -> fading
                        voice === fading -> active
                        else -> null
                    }
                    // The selected incoming item stays paused until READY and the envelope starts.
                    // A focus regain on the outgoing voice must not silently consume its intro.
                    val awaitingManualStart = manualPreparing != null && partner === active && playWhenReady
                    if (partner != null && !awaitingManualStart && partner.player.playWhenReady != playWhenReady) {
                        partner.player.playWhenReady = playWhenReady
                    }
                }
                if (manualPreparing != null) scheduleManualStart()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when {
                    voice === active -> when (playbackState) {
                        Player.STATE_ENDED -> if (voice.player.playbackState == Player.STATE_ENDED) onActiveEnded()
                        Player.STATE_READY -> if (manualPreparing != null) scheduleManualStart() else scheduleTrigger()
                    }
                    voice === fading -> if (playbackState == Player.STATE_ENDED) {
                        // An estimated duration was too long: the outgoing track is done early.
                        interrupt(CrossfadeInterruption.OutgoingEnded)
                    }
                    // The prepared next track became ready.
                    playbackState == Player.STATE_READY -> scheduleTrigger()
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                // Transient focus loss can keep playWhenReady=true while suppressing the decoder.
                // A pending manual hand-over must not request focus again on the incoming player.
                if (manualPreparing != null && (voice === active || voice === fading) &&
                    playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                    cancelManualPreparationInPause()
                }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                // A seek that did not come through seekTo (media session controllers).
                if (voice === active && reason == Player.DISCONTINUITY_REASON_SEEK) interrupt(CrossfadeInterruption.Seek)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (voice === active) updatePositionPolling()
                scheduleTrigger()
            }

            override fun onPlayerError(error: PlaybackException) {
                // The active player's error reaches the app through the state; a fading player is
                // dropped by the fade itself. Only a prepared next track needs handling here.
                if (voice === active && fading != null) {
                    finishFade(if (manualPreparing != null) CrossfadeSkipReason.PrepareFailed else CrossfadeSkipReason.Interrupted)
                    publishState()
                    return
                }
                if (voice === active || voice === fading || nextPlacement != NextPlacement.Standby) return
                noOverlapFor = voice.track?.id
                noOverlapReason = CrossfadeSkipReason.PrepareFailed
                fallbacks++
                lastFallback = "standby-failed"
                interrupt(CrossfadeInterruption.StandbyFailed)
                reconcileNext()
            }
        })
    }

    override fun loadAt(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        initialPositionMs = startPositionMs.coerceAtLeast(0)
        try { load(track, playWhenReady) } finally { initialPositionMs = 0L }
    }

    override fun transitionToAt(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        initialPositionMs = startPositionMs.coerceAtLeast(0)
        try { transitionTo(track, playWhenReady) } finally { initialPositionMs = 0L }
    }

    override fun load(track: Track, playWhenReady: Boolean) {
        eventGeneration++
        interrupt(CrossfadeInterruption.Load)
        returnToPrimaryIfDisabled()
        val voice = active
        voice.track = track
        if (track.id == editingTrackId) {
            resumeAfterEdit = playWhenReady
            voice.player.stop()
            return
        }
        voice.replayGain = null
        _state.value = _state.value.copy(audioFormat = null, replayGain = null)
        voice.player.setMediaItem(track.toMediaItem(), initialPositionMs)
        // setMediaItem dropped whatever followed the old track.
        nextPlacement = NextPlacement.None
        reconcileNext()
        voice.player.prepare()
        voice.player.playWhenReady = playWhenReady
        publishState()
    }

    override fun transitionTo(track: Track, playWhenReady: Boolean) {
        val outgoing = active
        val decision = Crossfade.decide(
            CrossfadeKind.Manual, manualCrossfadeMs, durationOf(outgoing), track.durationMs,
            outgoing.player.currentPosition, sameTrack = outgoing.track?.id == track.id,
            supported = supportsCrossfade,
            playing = playWhenReady && outgoing.player.isPlaying,
        )
        // Repeated commands take control immediately; never create a third voice or let a stale
        // pending request replace the newly selected track. A file write must also remain exclusive.
        val reason = when {
            fading != null || editingTrackId != null -> CrossfadeSkipReason.Interrupted
            else -> decision.skipReason
        }
        if (reason != null) {
            load(track, playWhenReady)
            lastCrossfadeStatus = CrossfadeStatus(CrossfadeKind.Manual, manualCrossfadeMs, skipReason = reason)
            publishState()
            return
        }
        val incoming = spareVoice(create = true)
        if (incoming == null) {
            load(track, playWhenReady)
            lastCrossfadeStatus = CrossfadeStatus(CrossfadeKind.Manual, manualCrossfadeMs, skipReason = CrossfadeSkipReason.Unsupported)
            publishState()
            return
        }
        eventGeneration++
        handler.removeCallbacks(trigger)
        handler.removeCallbacks(tick)
        removeNextFromActive()
        val reusable = incoming.track?.id == track.id && incoming.player.mediaItemCount > 0 && incoming.player.playerError == null
        if (!reusable) incoming.reset()
        incoming.track = track
        if (!reusable) incoming.replayGain = null
        incoming.envelope = 0f
        updateBaseVolume(incoming)
        // The requested track becomes visible immediately in buffering state. Its predecessor is
        // only an audible tail, never the source of position, tags or future queue-advance events.
        active = incoming
        fading = outgoing
        manualPreparing = manualCrossfadeMs
        fadePlan = null
        nextPlacement = NextPlacement.None
        nextSkipReason = null
        if (!reusable) incoming.player.setMediaItem(track.toMediaItem(), initialPositionMs)
        else if (initialPositionMs > 0L) incoming.player.seekTo(initialPositionMs)
        // Decode while paused: the target's intro must not advance silently while READY waits
        // for the application looper. The pending intent is exposed by publishState instead.
        incoming.player.playWhenReady = false
        if (!reusable) incoming.player.prepare()
        _activePlayer.value = incoming.player
        updatePositionPolling()
        publishState()
        handler.removeCallbacks(manualTimeout)
        handler.postDelayed(manualTimeout, MANUAL_PREPARE_TIMEOUT_MS)
        scheduleManualStart()
    }

    override fun setNext(track: Track?) = setNext(track, null)

    override fun setNext(track: Track?, entryId: Long?) {
        nextTrack = track
        nextEntryId = entryId
        if (track?.id != noOverlapFor) {
            noOverlapFor = null
            noOverlapReason = null
        }
        interrupt(CrossfadeInterruption.NextChanged)
        reconcileNext(force = true)
    }

    override fun updateMetadata(track: Track) {
        val voice = active
        if (voice.track?.id != track.id) return
        voice.track = track
        if (track.id == editingTrackId) return
        val player = voice.player
        if (player.currentMediaItem != null) player.replaceMediaItem(player.currentMediaItemIndex, track.toMediaItem())
    }

    /**
     * Close readers before rewriting a file whose audio offsets may move with its tags. With an
     * overlap running or the next track prepared, every decoder that holds the file is released.
     */
    suspend fun withFileWrite(track: Track, write: suspend () -> Unit) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        val affected = loadedTrack?.id == track.id
        editingTrackId = track.id
        // An overlap holds two files open: end it if either one is the edited file.
        if (fading != null && (affected || fading?.track?.id == track.id)) finishFade(CrossfadeSkipReason.Interrupted)
        resumeAfterEdit = affected && active.player.playWhenReady
        positionAfterEdit = if (affected) active.player.currentPosition else 0
        if (affected) active.player.stop()
        if (nextTrack?.id == track.id) {
            // Both the second item of the active player and the prepared standby may hold the file.
            clearSpare()
            removeNextFromActive()
            nextPlacement = NextPlacement.None
            handler.removeCallbacks(trigger)
        }
        try { write() }
        finally {
            editingTrackId = null
            val latest = loadedTrack
            if (latest?.id == track.id) {
                load(latest, resumeAfterEdit)
                seekTo(positionAfterEdit)
            } else if (nextTrack?.id == track.id) reconcileNext(force = true)
        }
    }

    override fun setVolume(volume: Float) {
        outputVolume = volume.coerceIn(0f, 1f)
        updateBaseVolume(primary)
        secondary?.let(::updateBaseVolume)
    }

    override fun setReplayGainMode(mode: ReplayGainMode) {
        replayGainMode = mode
        updateBaseVolume(primary)
        secondary?.let(::updateBaseVolume)
    }

    private fun updateBaseVolume(voice: PlayerVoice) {
        voice.baseVolume = outputVolume * ReplayGain.volumeFor(voice.replayGain, replayGainMode)
        voice.applyVolume()
    }

    override fun setEqualizer(chain: EqChain) {
        this.chain = chain
        primary.equalizer?.setChain(chain)
        secondary?.equalizer?.setChain(chain)
    }

    override fun setCrossfade(durationMs: Long) {
        val value = Crossfade.normalize(durationMs).takeIf { it >= Crossfade.MIN_EFFECTIVE_MS } ?: 0L
        if (value == crossfadeMs) return
        crossfadeMs = value
        // Moves the next track between the active player's playlist and the second player.
        reconcileNext()
    }

    override fun setManualCrossfade(durationMs: Long) {
        manualCrossfadeMs = Crossfade.normalize(durationMs).takeIf { it >= Crossfade.MIN_EFFECTIVE_MS } ?: 0L
        // No eager decoder allocation. An in-flight manual transition keeps its requested duration.
        if (manualCrossfadeMs == 0L && crossfadeMs == 0L && fading == null) clearSpare()
    }

    override fun play() {
        if (loadedTrack?.id == editingTrackId && editingTrackId != null) { resumeAfterEdit = true; return }
        if (manualPreparing != null) {
            fading?.player?.play()
            scheduleManualStart()
            return
        }
        active.player.play()
        fading?.player?.play()
    }

    override fun pause() {
        if (loadedTrack?.id == editingTrackId && editingTrackId != null) { resumeAfterEdit = false; return }
        active.player.pause()
        fading?.player?.pause()
        if (manualPreparing != null) {
            finishFade(CrossfadeSkipReason.Interrupted)
            publishState()
        }
    }

    override fun seekTo(positionMs: Long) {
        if (loadedTrack?.id == editingTrackId && editingTrackId != null) { positionAfterEdit = positionMs.coerceAtLeast(0); return }
        // Seeking during an overlap means the user takes over the incoming track.
        interrupt(CrossfadeInterruption.Seek)
        active.player.seekTo(positionMs.coerceAtLeast(0))
        scheduleTrigger()
        publishState()
    }

    override fun stop() {
        eventGeneration++
        interrupt(CrossfadeInterruption.Stop)
        active.track = null
        active.player.pause()
        active.player.stop()
        active.player.clearMediaItems()
        nextPlacement = NextPlacement.None
        clearSpare()
        returnToPrimaryIfDisabled()
        publishState()
    }

    /** Only on an explicit replacement/stop: never interrupt an active stream to free a player. */
    private fun returnToPrimaryIfDisabled() {
        val second = secondary ?: return
        if (crossfadeMs != 0L || manualCrossfadeMs != 0L || fading != null || active !== second) return
        second.reset()
        active = primary
        primary.setFocusOwner(true)
        second.setFocusOwner(false)
        _activePlayer.value = primary.player
        second.player.release()
        secondary = null
        nextPlacement = NextPlacement.None
    }

    override fun release() {
        eventGeneration++
        handler.removeCallbacksAndMessages(null)
        positionJob?.cancel()
        fading = null
        fadePlan = null
        manualPreparing = null
        nextPlacement = NextPlacement.None
        active.track = null
        primary.player.release()
        secondary?.player?.release()
        secondary = null
        _state.value = PlayerState()
    }

    /** What the crossfade is doing, for runtime checks. Contains no user data. */
    fun diagnostics(): CrossfadeDiagnostics {
        val outgoing = fading
        val incoming = spareVoice(create = false)
        return CrossfadeDiagnostics(
            configuredMs = crossfadeMs,
            stage = when {
                manualPreparing != null -> CrossfadeStage.PreparingManual
                outgoing != null -> CrossfadeStage.Fading
                nextPlacement == NextPlacement.Standby -> CrossfadeStage.Armed
                crossfadeMs == 0L && manualCrossfadeMs == 0L -> CrossfadeStage.Off
                else -> CrossfadeStage.Idle
            },
            secondPlayerCreated = secondary != null,
            standbyReady = nextPlacement == NextPlacement.Standby && incoming?.player?.playbackState == Player.STATE_READY,
            overlapsStarted = overlapsStarted,
            overlapsCompleted = overlapsCompleted,
            handOversWithoutOverlap = handOvers,
            gaplessFallbacks = fallbacks,
            lastFallback = lastFallback,
            progress = outgoing?.let { fadePlan?.progressAt(it.player.currentPosition) } ?: 0f,
            outgoingVolume = outgoing?.player?.volume ?: 0f,
            incomingVolume = if (outgoing != null) active.player.volume else 0f,
            manualConfiguredMs = manualCrossfadeMs,
        )
    }

    // ---- Crossfade -------------------------------------------------------------------------

    private val trigger = Runnable { onTrigger() }

    private val manualStart = Runnable { startManualFadeWhenReady() }
    private val manualTimeout = Runnable {
        if (manualPreparing != null) {
            finishFade(if (active.player.playerError != null) CrossfadeSkipReason.PrepareFailed else CrossfadeSkipReason.NotReady)
            publishState()
        }
    }

    private fun scheduleManualStart() {
        handler.removeCallbacks(manualStart)
        if (manualPreparing != null) handler.post(manualStart)
    }

    private fun isManualPreparationSuppressed(): Boolean = manualPreparing != null &&
        (active.player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE ||
            fading?.player?.playbackSuppressionReason?.let { it != Player.PLAYBACK_SUPPRESSION_REASON_NONE } == true)

    private fun cancelManualPreparationInPause() {
        if (manualPreparing == null) return
        active.player.pause()
        fading?.player?.pause()
        finishFade(CrossfadeSkipReason.Interrupted, continuePrepared = false)
        publishState()
    }

    private fun startManualFadeWhenReady() {
        val requested = manualPreparing ?: return
        val outgoing = fading ?: return
        val incoming = active
        if (isManualPreparationSuppressed()) {
            cancelManualPreparationInPause()
            return
        }
        if (incoming.player.playerError != null) {
            finishFade(CrossfadeSkipReason.PrepareFailed)
            publishState()
            return
        }
        if (!outgoing.player.playWhenReady) {
            // A system focus/noisy-output pause during preparation must never resume the new item.
            cancelManualPreparationInPause()
            return
        }
        if (incoming.player.playbackState != Player.STATE_READY) return
        val decision = Crossfade.decide(
            CrossfadeKind.Manual, requested, durationOf(outgoing), durationOf(incoming),
            outgoing.player.currentPosition, sameTrack = outgoing.track?.id == incoming.track?.id,
            playing = outgoing.player.isPlaying,
        )
        val plan = decision.plan
        if (plan == null) {
            finishFade(decision.skipReason ?: CrossfadeSkipReason.NotReady)
            publishState()
            return
        }
        manualPreparing = null
        handler.removeCallbacks(manualTimeout)
        fadePlan = plan
        overlapsStarted++
        lastCrossfadeStatus = CrossfadeStatus(CrossfadeKind.Manual, requested, plan.durationMs)
        incoming.player.playWhenReady = true
        // No AdvancedToNext: the controller selected this queue entry before transitionTo.
        publishState()
        tick.run()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!applyFade()) return
            handler.postDelayed(this, if (fading?.player?.isPlaying == true) Crossfade.TICK_MS else IDLE_TICK_MS)
        }
    }

    private fun phase(): CrossfadePhase? = when {
        fading != null -> CrossfadePhase.Fading
        nextPlacement == NextPlacement.Standby -> CrossfadePhase.Armed
        else -> null
    }

    /** Applies the core policy to something that happened while an overlap was armed or running. */
    private fun interrupt(interruption: CrossfadeInterruption) {
        val phase = phase() ?: return
        when (Crossfade.resolve(phase, interruption)) {
            CrossfadeResolution.Keep -> Unit
            CrossfadeResolution.Replan -> scheduleTrigger()
            CrossfadeResolution.Disarm -> {
                nextPlacement = NextPlacement.None
                handler.removeCallbacks(trigger)
                clearSpare()
            }
            CrossfadeResolution.Finish -> if (fading != null) {
                val reason = when (interruption) {
                    CrossfadeInterruption.Load, CrossfadeInterruption.Seek, CrossfadeInterruption.Stop -> CrossfadeSkipReason.Interrupted
                    CrossfadeInterruption.OutgoingEnded -> if (manualPreparing != null) CrossfadeSkipReason.NotReady else null
                    else -> null
                }
                finishFade(reason, continuePrepared = interruption != CrossfadeInterruption.Load && interruption != CrossfadeInterruption.Stop)
            } else startTransition(null)
        }
    }

    /** Decides where the next track waits: nowhere, in the active player, or prepared on the other one. */
    private fun reconcileNext(force: Boolean = false) {
        if (fading != null) {
            // The other player is still busy fading out; this runs again when the overlap ends.
            nextPlacement = NextPlacement.None
            return
        }
        val current = active.track
        val next = nextTrack
        val desired = when {
            current == null || next == null || next.id == editingTrackId -> NextPlacement.None
            wantsOverlap(current, next) -> NextPlacement.Standby
            else -> NextPlacement.Active
        }
        nextSkipReason = if (desired == NextPlacement.Active && current != null && next != null) {
            if (noOverlapFor == next.id) noOverlapReason ?: CrossfadeSkipReason.NotReady
            else Crossfade.decide(CrossfadeKind.Automatic, crossfadeMs, durationOf(active), next.durationMs,
                sameTrack = current.id == next.id, supported = supportsCrossfade).skipReason
        } else null
        when (desired) {
            NextPlacement.Standby -> {
                removeNextFromActive()
                prepareStandby(next!!, force)
            }
            NextPlacement.Active -> {
                clearSpare()
                ensureNextInActive(next!!, force)
            }
            NextPlacement.None -> {
                clearSpare()
                removeNextFromActive()
            }
        }
        nextPlacement = desired
        scheduleTrigger()
    }

    private fun wantsOverlap(current: Track, next: Track): Boolean =
        crossfadeMs >= Crossfade.MIN_EFFECTIVE_MS && standbyFactory != null && noOverlapFor != next.id &&
            Crossfade.plan(crossfadeMs, durationOf(active), next.durationMs, 0, sameTrack = next.id == current.id) != null

    private fun durationOf(voice: PlayerVoice): Long =
        voice.player.duration.takeIf { it > 0 } ?: voice.track?.durationMs ?: 0L

    /** The voice that is not playing the current track; null before the second player exists. */
    private fun spareVoice(create: Boolean): PlayerVoice? =
        if (active === primary) {
            secondary ?: if (create) createSecondary() else null
        } else {
            primary
        }

    private fun createSecondary(): PlayerVoice? {
        val voice = standbyFactory?.invoke() ?: return null
        // The equalizer must be right before the first buffer, not after a moment of flat sound.
        voice.equalizer?.setChain(chain)
        voice.envelope = 0f
        voice.applyVolume()
        attach(voice)
        secondary = voice
        return voice
    }

    private fun prepareStandby(next: Track, force: Boolean) {
        val voice = spareVoice(create = true) ?: return
        val player = voice.player
        if (!force && voice.track?.id == next.id && player.mediaItemCount > 0 && player.playerError == null) return
        voice.track = next
        voice.replayGain = null
        // Silent before anything is decoded, so the first buffers never play at full volume.
        voice.envelope = 0f
        voice.applyVolume()
        player.playWhenReady = false
        player.setMediaItem(next.toMediaItem())
        player.prepare()
    }

    /** Frees the other player. Never touches one that is still fading out. */
    private fun clearSpare() {
        if (fading != null) return
        val voice = spareVoice(create = false) ?: return
        if (voice.track != null || voice.player.mediaItemCount > 0) voice.reset()
        if (crossfadeMs == 0L && manualCrossfadeMs == 0L && voice === secondary) {
            voice.player.release()
            secondary = null
        }
    }

    private fun removeNextFromActive() {
        if (loadedTrack == null) return
        val player = active.player
        val keep = player.currentMediaItemIndex + 1
        if (player.mediaItemCount > keep) player.removeMediaItems(keep, player.mediaItemCount)
    }

    private fun ensureNextInActive(next: Track, force: Boolean) {
        if (loadedTrack == null) return
        val player = active.player
        if (player.mediaItemCount == 0) return
        val index = player.currentMediaItemIndex + 1
        if (!force && player.mediaItemCount == index + 1 && player.getMediaItemAt(index).mediaId == next.id) return
        if (player.mediaItemCount > index) player.removeMediaItems(index, player.mediaItemCount)
        player.addMediaItem(next.toMediaItem())
    }

    private fun overlapFor(outgoing: PlayerVoice, next: Track, positionMs: Long): CrossfadePlan? {
        val standby = spareVoice(create = false)
        val incomingDuration = standby?.takeIf { it.track?.id == next.id }?.let(::durationOf) ?: next.durationMs
        return Crossfade.plan(
            crossfadeMs, durationOf(outgoing), incomingDuration, positionMs,
            sameTrack = next.id == outgoing.track?.id,
        )
    }

    private fun scheduleTrigger() {
        handler.removeCallbacks(trigger)
        if (manualPreparing != null) return scheduleManualStart()
        val next = nextTrack ?: return
        if (nextPlacement != NextPlacement.Standby || fading != null) return
        val outgoing = active
        if (!outgoing.player.isPlaying) return
        val wait = overlapFor(outgoing, next, outgoing.player.currentPosition)?.msUntilStart(outgoing.player.currentPosition) ?: 0L
        handler.postDelayed(trigger, wait.coerceIn(0L, MAX_WAIT_MS))
    }

    private fun onTrigger() {
        val next = nextTrack ?: return
        if (nextPlacement != NextPlacement.Standby || fading != null) return
        val outgoing = active
        if (!outgoing.player.isPlaying) return
        val position = outgoing.player.currentPosition
        val overlap = overlapFor(outgoing, next, position)
        if (overlap == null) return declineOverlap(next, "plan")
        val wait = overlap.msUntilStart(position)
        if (wait > TRIGGER_SLACK_MS) {
            // Look again later: the playhead may be moved by a seek in the meantime.
            handler.postDelayed(trigger, minOf(wait, MAX_WAIT_MS))
            return
        }
        val standby = spareVoice(create = false)
        if (standby == null || standby.track?.id != next.id || standby.player.playbackState != Player.STATE_READY) {
            // Still opening the file. The overlap shrinks as time passes and gives way to the
            // gapless join once there is too little left.
            handler.postDelayed(trigger, STANDBY_POLL_MS)
            return
        }
        if (!startTransition(overlap)) declineOverlap(next, "standby-late")
    }

    /** Joins [next] gaplessly in the active player instead of overlapping it. */
    private fun declineOverlap(next: Track, reason: String) {
        noOverlapFor = next.id
        noOverlapReason = if (reason == "plan") CrossfadeSkipReason.TooShort else CrossfadeSkipReason.NotReady
        fallbacks++
        lastFallback = reason
        nextPlacement = NextPlacement.None
        handler.removeCallbacks(trigger)
        clearSpare()
        reconcileNext()
    }

    /**
     * The prepared next track takes over as the current one. With an [overlap] both play and fade;
     * without one (the outgoing track already ended) the next one simply starts. False when the
     * prepared track is not ready, nothing has changed then.
     */
    private fun startTransition(overlap: CrossfadePlan?): Boolean {
        val incoming = spareVoice(create = false) ?: return false
        val next = nextTrack ?: return false
        val consumedId = nextEntryId
        val consumedGeneration = eventGeneration
        if (nextPlacement != NextPlacement.Standby || incoming.track?.id != next.id ||
            incoming.player.playbackState != Player.STATE_READY || incoming.player.playerError != null
        ) return false
        val outgoing = active
        handler.removeCallbacks(trigger)
        // The standby decoder already knows this file's own loudness tags.
        incoming.baseVolume = outputVolume * ReplayGain.volumeFor(incoming.replayGain, replayGainMode)
        incoming.envelope = if (overlap != null) 0f else 1f
        incoming.applyVolume()
        // From here on the incoming track is the current one for every listener.
        active = incoming
        nextTrack = null
        nextEntryId = null
        nextPlacement = NextPlacement.None
        if (overlap != null) {
            fading = outgoing
            fadePlan = overlap
            overlapsStarted++
        } else {
            handOvers++
            incoming.setFocusOwner(true)
            outgoing.setFocusOwner(false)
            outgoing.reset()
        }
        lastCrossfadeStatus = CrossfadeStatus(CrossfadeKind.Automatic, crossfadeMs,
            effectiveDurationMs = overlap?.durationMs ?: 0L,
            skipReason = if (overlap == null) CrossfadeSkipReason.TooShort else null)
        incoming.player.playWhenReady = true
        _activePlayer.value = incoming.player
        updatePositionPolling()
        publishState()
        _events.tryEmit(PlayerEvent.AdvancedToNext(consumedGeneration, consumedId, next))
        if (overlap != null) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        } else {
            reconcileNext()
        }
        return true
    }

    /** Sets both gains from the outgoing track's position. False when the overlap is over. */
    private fun applyFade(): Boolean {
        val outgoing = fading ?: return false
        val incoming = active
        val overlap = fadePlan
        val state = outgoing.player.playbackState
        if (overlap == null || state == Player.STATE_ENDED || state == Player.STATE_IDLE || outgoing.player.playerError != null) {
            finishFade()
            return false
        }
        val progress = overlap.progressAt(outgoing.player.currentPosition)
        if (progress >= 1f) {
            finishFade()
            return false
        }
        val gains = Crossfade.gains(progress)
        outgoing.envelope = gains.outgoing
        incoming.envelope = gains.incoming
        outgoing.applyVolume()
        incoming.applyVolume()
        return true
    }

    /** Drops the outgoing track, leaves the incoming one alone at its own volume and arms the next one. */
    private fun finishFade(reason: CrossfadeSkipReason? = null, continuePrepared: Boolean = true) {
        val outgoing = fading ?: return
        val wasStarted = fadePlan != null
        val preparingDuration = manualPreparing
        // This also guards timeout/error/seek paths if the suppression callback is still queued.
        val preparationSuppressed = isManualPreparationSuppressed()
        val resolvedReason = if (preparationSuppressed) CrossfadeSkipReason.Interrupted else reason
        val resumePrepared = preparingDuration != null && !preparationSuppressed && continuePrepared && outgoing.player.playWhenReady &&
            active.player.playerError == null
        if (resolvedReason != null) {
            lastCrossfadeStatus = if (preparingDuration != null) {
                CrossfadeStatus(CrossfadeKind.Manual, preparingDuration, skipReason = resolvedReason)
            } else lastCrossfadeStatus?.copy(skipReason = resolvedReason)
        }
        // First, so pauses caused by the focus hand-over below are not copied to the incoming track.
        fading = null
        fadePlan = null
        manualPreparing = null
        handler.removeCallbacks(tick)
        handler.removeCallbacks(manualStart)
        handler.removeCallbacks(manualTimeout)
        val incoming = active
        if (preparationSuppressed) {
            incoming.player.pause()
            outgoing.player.pause()
        }
        incoming.envelope = 1f
        incoming.applyVolume()
        if (resumePrepared) incoming.player.playWhenReady = true
        if (outgoing.ownsFocus) {
            incoming.setFocusOwner(true)
            outgoing.setFocusOwner(false)
        }
        outgoing.reset()
        if (wasStarted) overlapsCompleted++
        reconcileNext()
    }

    // ---- State -----------------------------------------------------------------------------

    private fun onGaplessTransition(mediaItem: MediaItem?, reason: Int) {
        val next = nextTrack ?: return
        val consumedId = nextEntryId
        val consumedGeneration = eventGeneration
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || mediaItem?.mediaId != next.id) return
        lastCrossfadeStatus = CrossfadeStatus(CrossfadeKind.Automatic, crossfadeMs,
            skipReason = nextSkipReason ?: CrossfadeSkipReason.NotReady)
        // The prepared next track took over: it becomes the current one.
        val voice = active
        voice.track = next
        nextTrack = null
        nextEntryId = null
        nextPlacement = NextPlacement.None
        voice.replayGain = null
        val index = voice.player.currentMediaItemIndex
        if (index > 0) voice.player.removeMediaItems(0, index)
        publishState()
        _events.tryEmit(PlayerEvent.AdvancedToNext(consumedGeneration, consumedId, next))
    }

    private fun onActiveEnded() {
        val endedGeneration = eventGeneration
        // The track ran out before an overlap could start (a seek to the end, a duration that was
        // too long): hand over to the prepared track right away instead of loading it from scratch.
        if (nextPlacement == NextPlacement.Standby && startTransition(null)) return
        if (nextTrack != null) {
            lastCrossfadeStatus = CrossfadeStatus(CrossfadeKind.Automatic, crossfadeMs,
                skipReason = nextSkipReason ?: CrossfadeSkipReason.NotReady)
            publishState()
        }
        _events.tryEmit(PlayerEvent.TrackEnded(endedGeneration))
    }

    private fun updatePositionPolling() {
        // ExoPlayer has no position callback: poll while playing.
        positionJob?.cancel()
        positionJob = null
        if (!active.player.isPlaying) return
        positionJob = scope.launch {
            while (isActive) {
                publishState()
                delay(POSITION_INTERVAL_MS)
            }
        }
    }

    private fun publishState() {
        val player = active.player
        val error: PlaybackException? = player.playerError
        _state.value = PlayerState(
            status = when {
                error != null -> PlayerStatus.Error
                manualPreparing != null -> PlayerStatus.Buffering
                player.playbackState == Player.STATE_BUFFERING -> PlayerStatus.Buffering
                player.playbackState == Player.STATE_READY -> PlayerStatus.Ready
                player.playbackState == Player.STATE_ENDED -> PlayerStatus.Ended
                else -> PlayerStatus.Idle
            },
            playWhenReady = manualPreparing != null || player.playWhenReady,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            errorMessage = error?.message,
            audioFormat = currentSourceFormat(),
            replayGain = active.replayGain,
            crossfadeStatus = lastCrossfadeStatus,
        )
    }

    private fun currentSourceFormat(): AudioFormatInfo? {
        val track = loadedTrack ?: return null
        val player = active.player
        val item = player.currentMediaItem ?: return null
        if (item.mediaId != track.id || item.localConfiguration?.uri?.toString() != track.uri) return null
        // A newly loaded item may briefly retain old track groups until preparation completes.
        if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_ENDED) return null
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (index in 0 until group.length) {
                if (group.isTrackSelected(index)) return group.getTrackFormat(index).sourceAudioFormat()
            }
        }
        return null
    }

    /** REPLAYGAIN_* tags of the current file: Vorbis comments, ID3 TXXX frames or MP4 freeform atoms. */
    private fun readReplayGain(tracks: Tracks): ReplayGainTags? {
        val entries = mutableMapOf<String, String>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (index in 0 until group.length) {
                val metadata = group.getTrackFormat(index).metadata ?: continue
                for (entryIndex in 0 until metadata.length()) {
                    when (val entry = metadata[entryIndex]) {
                        is VorbisComment -> entries[entry.key] = entry.value
                        is TextInformationFrame -> if (entry.id == "TXXX" && entry.description != null) {
                            entry.values.firstOrNull()?.let { entries[entry.description!!] = it }
                        }
                        is InternalFrame -> entries[entry.description] = entry.text
                    }
                }
            }
        }
        return ReplayGainTags.parse(entries)
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 250L

        /** Longest wait before the playhead is looked at again, so seeks and pauses are noticed. */
        const val MAX_WAIT_MS = 1_000L

        /** Close enough to the planned start to begin the overlap. */
        const val TRIGGER_SLACK_MS = 10L

        const val STANDBY_POLL_MS = 100L

        /** A local decoder that is still loading falls back to the selected track without a tail. */
        const val MANUAL_PREPARE_TIMEOUT_MS = 2_000L

        /** Gain updates while the overlap is paused or buffering need no 25 ms rhythm. */
        const val IDLE_TICK_MS = 100L
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist)
            .setTrackNumber(trackNumber)
            .setDiscNumber(discNumber)
            // The cover is embedded in the file itself; EmbeddedArtworkBitmapLoader reads it.
            .setArtworkUri(Uri.parse(artworkKey))
            .build(),
    )
    .build()
