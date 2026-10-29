@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.*

/** AVFoundation playback. Queue transitions stay in core; Android's DSP/crossfades are not advertised here. */
internal class IosAudioPlayer(private val scope: CoroutineScope) : AudioPlayer {
    private var native = AVPlayer()
    private val session = AVAudioSession.sharedInstance()
    private val values = MutableStateFlow(PlayerState())
    private val notifications = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val state = values.asStateFlow()
    override val events = notifications.asSharedFlow()
    override var eventGeneration = 0L
        private set
    private var track: Track? = null
    private var item: AVPlayerItem? = null
    private var wanted = false
    private var ended = false
    private var resumeAfterInterruption = false
    private var pauseOnDisconnect = true
    private var handleInterruptions = true
    private var released = false
    private var failure: String? = null
    private var outputVolume = 1f
    private var endObserver: Any? = null
    private var seeking: Long? = null
    private val center = NSNotificationCenter.defaultCenter
    private val observers = mutableListOf<Any>()

    init {
        observers += center.addObserverForName(AVAudioSessionInterruptionNotification, session, NSOperationQueue.mainQueue) { note ->
            val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongLongValue
            if (type == AVAudioSessionInterruptionTypeBegan) {
                resumeAfterInterruption = wanted && handleInterruptions
                pauseNative()
            } else if (type == AVAudioSessionInterruptionTypeEnded) {
                val options = (note.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.unsignedLongLongValue ?: 0uL
                val resume = resumeAfterInterruption && options and AVAudioSessionInterruptionOptionShouldResume != 0uL
                resumeAfterInterruption = false
                if (resume) play()
            }
        }
        observers += center.addObserverForName(AVAudioSessionRouteChangeNotification, session, NSOperationQueue.mainQueue) { note ->
            val reason = (note?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongLongValue
            if (pauseOnDisconnect && reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable && wanted) {
                pause()
                notifications.tryEmit(PlayerEvent.PausedByDisconnect)
            }
        }
        observers += center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, NSOperationQueue.mainQueue) {
            // An invalidated decoder must never leave a playing island or a queued end event behind.
            eventGeneration++
            endObserver?.let(center::removeObserver)
            endObserver = null
            seeking = null
            resumeAfterInterruption = false
            native.pause()
            native = AVPlayer().apply { volume = outputVolume }
            fail(iosPlaybackText("audio_services_reset", "The system audio service restarted"))
        }
    }

    private val polling = scope.launch {
        while (!released) {
            refresh()
            delay(if (item == null) 1000 else 250)
        }
    }

    override fun load(track: Track, playWhenReady: Boolean) = loadAt(track, playWhenReady, 0)

    override fun loadAt(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        if (released) return
        eventGeneration++
        resumeAfterInterruption = false
        failure = null
        seeking = null
        native.pause()
        if (native.status == AVPlayerStatusFailed) native = AVPlayer().apply { volume = outputVolume }
        this.track = track
        wanted = playWhenReady
        ended = false
        val url = NSURL.URLWithString(track.uri)
        if (url == null) {
            item = null
            native.replaceCurrentItemWithPlayerItem(null)
            fail(iosPlaybackText("audio_invalid_url", "Invalid audio URL"))
            return
        }
        val next = AVPlayerItem(uRL = url)
        item = next
        native.replaceCurrentItemWithPlayerItem(next)
        values.value = PlayerState(PlayerStatus.Buffering, playWhenReady, startPositionMs.coerceAtLeast(0), track.durationMs)
        observeEnd(next)
        if (startPositionMs > 0) seekNative(startPositionMs) else if (wanted) startNative()
        refresh()
    }

    // AVPlayer reports completion to PlaybackController, which owns repeat, shuffle, sleep and next.
    override fun setNext(track: Track?) = Unit
    override fun updateMetadata(track: Track) { if (this.track?.id == track.id) this.track = track }
    override fun setVolume(volume: Float) {
        outputVolume = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        native.volume = outputVolume
    }
    override fun setInterruptionPolicy(handleAudioFocus: Boolean, pauseOnDisconnect: Boolean) {
        handleInterruptions = handleAudioFocus
        this.pauseOnDisconnect = pauseOnDisconnect
    }

    override fun play() {
        if (released || item == null) return
        resumeAfterInterruption = false
        wanted = true
        ended = false
        if (seeking == null) startNative()
        refresh()
    }

    private fun startNative() {
        if (!session.setCategory(AVAudioSessionCategoryPlayback, error = null) || !session.setActive(true, error = null)) {
            fail(iosPlaybackText("audio_session_unavailable", "The audio session could not be activated"))
            return
        }
        native.play()
    }

    override fun pause() {
        resumeAfterInterruption = false
        pauseNative()
    }

    private fun pauseNative() {
        wanted = false
        native.pause()
        refresh()
    }

    override fun seekTo(positionMs: Long) {
        if (item == null || released) return
        eventGeneration++
        resumeAfterInterruption = false
        ended = false
        val duration = state.value.durationMs
        val position = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        item?.let(::observeEnd)
        seekNative(position)
    }

    private fun observeEnd(current: AVPlayerItem) {
        endObserver?.let(center::removeObserver)
        val generation = eventGeneration
        endObserver = center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, current, NSOperationQueue.mainQueue) {
            if (item === current && eventGeneration == generation && seeking == null && failure == null && !ended) {
                ended = true
                refresh()
                notifications.tryEmit(PlayerEvent.TrackEnded(generation))
            }
        }
    }

    private fun seekNative(positionMs: Long) {
        val generation = eventGeneration
        val current = item ?: return
        seeking = generation
        values.value = state.value.copy(positionMs = positionMs, status = PlayerStatus.Buffering)
        val zero = CMTimeMakeWithSeconds(0.0, 1000)
        native.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000), zero, zero) { finished ->
            scope.launch {
                if (!released && item === current && eventGeneration == generation) {
                    seeking = null
                    if (finished && wanted) startNative()
                    refresh()
                }
            }
        }
    }

    override fun stop() {
        eventGeneration++
        endObserver?.let(center::removeObserver)
        endObserver = null
        seeking = null
        failure = null
        resumeAfterInterruption = false
        wanted = false
        ended = false
        item = null
        track = null
        native.pause()
        native.replaceCurrentItemWithPlayerItem(null)
        session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
        values.value = PlayerState()
    }

    private fun refresh() {
        val current = item ?: return
        if (failure != null) return
        val error = current.error ?: native.error
        if (current.status == AVPlayerItemStatusFailed || error != null) {
            fail(error?.localizedDescription ?: iosPlaybackText("audio_unavailable", "This audio file cannot be played"))
            return
        }
        val seconds = CMTimeGetSeconds(native.currentTime())
        val duration = CMTimeGetSeconds(current.duration)
        val status = when {
            ended -> PlayerStatus.Ended
            seeking != null || current.status != AVPlayerItemStatusReadyToPlay ||
                (wanted && native.timeControlStatus != AVPlayerTimeControlStatusPlaying) -> PlayerStatus.Buffering
            else -> PlayerStatus.Ready
        }
        values.value = PlayerState(status, wanted,
            if (seeking == null && seconds.isFinite()) (seconds * 1000).toLong().coerceAtLeast(0) else state.value.positionMs,
            if (duration.isFinite() && duration > 0) (duration * 1000).toLong() else track?.durationMs ?: 0)
    }

    private fun fail(message: String) {
        wanted = false
        failure = message
        native.pause()
        values.value = state.value.copy(status = PlayerStatus.Error, playWhenReady = false, errorMessage = message)
    }

    override fun release() {
        if (released) return
        stop()
        released = true
        polling.cancel()
        observers.forEach(center::removeObserver)
        observers.clear()
    }
}
