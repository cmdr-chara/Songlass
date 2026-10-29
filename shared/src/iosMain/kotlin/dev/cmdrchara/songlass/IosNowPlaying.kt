@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass

import dev.cmdrchara.songlass.core.playback.SystemNowPlaying
import dev.cmdrchara.songlass.core.playback.PlaybackTimeline
import dev.cmdrchara.songlass.core.queue.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import platform.MediaPlayer.*

/** One process-lived owner for Dynamic Island, Lock Screen, Control Center and accessory transport. */
internal class IosNowPlaying(
    private val graph: AppGraph,
    private val source: IosMusicSource,
    private val scope: CoroutineScope,
) {
    private val info = MPNowPlayingInfoCenter.defaultCenter()
    private val commands = MPRemoteCommandCenter.sharedCommandCenter()
    private val targets = mutableListOf<Pair<MPRemoteCommand, Any>>()
    private var lastArtworkKey: String? = null
    private var lastArtworkRevision = -1L
    private var artwork: MPMediaItemArtwork? = null
    private val observation: Job

    init {
        bindTransport(commands.playCommand) { graph.controller.resume() }
        bindTransport(commands.pauseCommand) { graph.controller.pause() }
        bindTransport(commands.togglePlayPauseCommand) { graph.controller.togglePlayPause() }
        bindTransport(commands.stopCommand) { graph.controller.stop() }
        bindTransport(commands.nextTrackCommand) { graph.controller.skipToNext() }
        bindTransport(commands.previousTrackCommand) { graph.controller.skipToPrevious() }
        bind(commands.changePlaybackPositionCommand, accepts = { event ->
            (event as? MPChangePlaybackPositionCommandEvent)?.positionTime?.let { it.isFinite() && it >= 0 } == true
        }) { event ->
            graph.controller.seekTo(((event as MPChangePlaybackPositionCommandEvent).positionTime * 1000).toLong())
        }
        bind(commands.changeRepeatModeCommand, accepts = { it is MPChangeRepeatModeCommandEvent }) { event ->
            val mode = (event as MPChangeRepeatModeCommandEvent).repeatType
            graph.controller.setRepeatMode(when (mode) {
                MPRepeatType.MPRepeatTypeOne -> RepeatMode.One
                MPRepeatType.MPRepeatTypeAll -> RepeatMode.All
                else -> RepeatMode.Off
            })
        }
        bind(commands.changeShuffleModeCommand, accepts = { it is MPChangeShuffleModeCommandEvent }) { event ->
            val mode = (event as MPChangeShuffleModeCommandEvent).shuffleType
            graph.controller.setShuffle(mode != MPShuffleType.MPShuffleTypeOff)
        }
        // Do not advertise controls for DSP/tempo features this native player does not implement.
        commands.changePlaybackRateCommand.enabled = false
        commands.seekForwardCommand.enabled = false
        commands.seekBackwardCommand.enabled = false
        commands.skipForwardCommand.enabled = false
        commands.skipBackwardCommand.enabled = false
        observation = scope.launch {
            val presentations = combine(graph.controller.queue, graph.player.state, source.hasAccess, graph.artworkAvailability.revision) { queue, state, access, revision ->
                Publication(
                    if (!access) null else SystemNowPlaying.from(queue.current?.track, state, queue.currentIndex, queue.entries.size),
                    queue.repeatMode, queue.isShuffled, revision,
                )
            }
            combine(presentations, graph.controller.timeline) { value, timeline -> value.copy(timeline = timeline) }
                // iOS extrapolates elapsed time from rate. Publish on metadata, transport,
                // seek/discontinuity and authorization changes, not every 250 ms position tick.
                .distinctUntilChangedBy { it.copy(info = it.info?.copy(elapsedSeconds = 0.0)) }
                .collect(::publish)
        }
    }

    private fun bindTransport(command: MPRemoteCommand, action: () -> Unit) = bind(command) { action() }

    private fun bind(
        command: MPRemoteCommand,
        accepts: (MPRemoteCommandEvent?) -> Boolean = { true },
        action: (MPRemoteCommandEvent?) -> Unit,
    ) {
        val target = command.addTargetWithHandler { event ->
            if (!source.hasAccess.value || graph.controller.queue.value.current == null) MPRemoteCommandHandlerStatusNoSuchContent
            else if (!accepts(event)) MPRemoteCommandHandlerStatusCommandFailed
            else {
                // MediaPlayer may deliver on a worker queue; all graph mutations belong to Main.
                scope.launch { if (source.hasAccess.value && graph.controller.queue.value.current != null) action(event) }
                MPRemoteCommandHandlerStatusSuccess
            }
        }
        targets += command to target
    }

    private data class Publication(
        val info: SystemNowPlaying?,
        val repeatMode: RepeatMode,
        val shuffled: Boolean,
        val artworkRevision: Long,
        val timeline: PlaybackTimeline = PlaybackTimeline(),
    )

    private fun publish(publication: Publication) {
        val value = publication.info
        targets.forEach { (command, _) -> command.enabled = value != null }
        if (value == null) {
            info.nowPlayingInfo = null
            artwork = null
            lastArtworkKey = null
            return
        }
        val track = value.track
        if (lastArtworkKey != track.artworkKey || lastArtworkRevision != publication.artworkRevision) {
            // Clear before lookup: a missing new cover must not inherit the previous album.
            artwork = null
            lastArtworkKey = track.artworkKey
            lastArtworkRevision = publication.artworkRevision
            artwork = source.artwork(track)
        }
        info.nowPlayingInfo = buildMap<Any?, Any> {
            put(MPMediaItemPropertyTitle, track.title)
            track.artist?.let { put(MPMediaItemPropertyArtist, it) }
            track.album?.let { put(MPMediaItemPropertyAlbumTitle, it) }
            track.albumArtist?.let { put(MPMediaItemPropertyAlbumArtist, it) }
            put(MPMediaItemPropertyPlaybackDuration, value.durationSeconds)
            put(MPNowPlayingInfoPropertyElapsedPlaybackTime, value.elapsedSeconds)
            put(MPNowPlayingInfoPropertyPlaybackRate, value.playbackRate)
            put(MPNowPlayingInfoPropertyDefaultPlaybackRate, 1.0)
            put(MPNowPlayingInfoPropertyMediaType, MPNowPlayingInfoMediaTypeAudio)
            put(MPNowPlayingInfoPropertyIsLiveStream, false)
            put(MPNowPlayingInfoPropertyPlaybackQueueIndex, value.queueIndex)
            put(MPNowPlayingInfoPropertyPlaybackQueueCount, value.queueCount)
            put(MPNowPlayingInfoPropertyExternalContentIdentifier, track.id)
            artwork?.let { put(MPMediaItemPropertyArtwork, it) }
        }
        commands.changeRepeatModeCommand.currentRepeatType = when (publication.repeatMode) {
            RepeatMode.Off -> MPRepeatType.MPRepeatTypeOff
            RepeatMode.One -> MPRepeatType.MPRepeatTypeOne
            RepeatMode.All -> MPRepeatType.MPRepeatTypeAll
        }
        commands.changeShuffleModeCommand.currentShuffleType = if (publication.shuffled) MPShuffleType.MPShuffleTypeItems else MPShuffleType.MPShuffleTypeOff
    }

    fun release() {
        observation.cancel()
        targets.forEach { (command, target) -> command.removeTarget(target); command.enabled = false }
        targets.clear()
        info.nowPlayingInfo = null
        artwork = null
    }
}
