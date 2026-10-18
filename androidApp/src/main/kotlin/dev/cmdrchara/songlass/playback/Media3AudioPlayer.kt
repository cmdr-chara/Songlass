package dev.cmdrchara.songlass.playback

import android.net.Uri
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.AudioFormatInfo
import dev.cmdrchara.songlass.core.player.AudioPlayer
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
 */
@androidx.annotation.OptIn(UnstableApi::class)
class Media3AudioPlayer(
    val exoPlayer: ExoPlayer,
    private val scope: CoroutineScope,
) : AudioPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    private var positionJob: Job? = null
    private var loadedTrack: Track? = null
    private var nextTrack: Track? = null
    private var replayGain: ReplayGainTags? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publishState()

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val next = nextTrack ?: return
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || mediaItem?.mediaId != next.id) return
                // The prepared next track took over: it becomes the current one.
                loadedTrack = next
                nextTrack = null
                replayGain = null
                val index = exoPlayer.currentMediaItemIndex
                if (index > 0) exoPlayer.removeMediaItems(0, index)
                publishState()
                _events.tryEmit(PlayerEvent.AdvancedToNext)
            }

            override fun onTracksChanged(tracks: Tracks) {
                replayGain = readReplayGain(tracks)
                publishState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) _events.tryEmit(PlayerEvent.TrackEnded)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // ExoPlayer has no position callback: poll while playing.
                positionJob?.cancel()
                if (isPlaying) {
                    positionJob = scope.launch {
                        while (isActive) {
                            publishState()
                            delay(POSITION_INTERVAL_MS)
                        }
                    }
                }
            }
        })
    }

    override fun load(track: Track, playWhenReady: Boolean) {
        loadedTrack = track
        replayGain = null
        _state.value = _state.value.copy(audioFormat = null, replayGain = null)
        exoPlayer.setMediaItem(track.toMediaItem())
        nextTrack?.let { exoPlayer.addMediaItem(it.toMediaItem()) }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
        publishState()
    }

    override fun setNext(track: Track?) {
        nextTrack = track
        if (loadedTrack == null) return
        val keep = exoPlayer.currentMediaItemIndex + 1
        if (exoPlayer.mediaItemCount > keep) exoPlayer.removeMediaItems(keep, exoPlayer.mediaItemCount)
        if (track != null) exoPlayer.addMediaItem(track.toMediaItem())
    }

    override fun setVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0))
        publishState()
    }

    override fun stop() {
        loadedTrack = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        publishState()
    }

    override fun release() {
        loadedTrack = null
        positionJob?.cancel()
        exoPlayer.release()
        _state.value = PlayerState()
    }

    private fun publishState() {
        val error: PlaybackException? = exoPlayer.playerError
        _state.value = PlayerState(
            status = when {
                error != null -> PlayerStatus.Error
                exoPlayer.playbackState == Player.STATE_BUFFERING -> PlayerStatus.Buffering
                exoPlayer.playbackState == Player.STATE_READY -> PlayerStatus.Ready
                exoPlayer.playbackState == Player.STATE_ENDED -> PlayerStatus.Ended
                else -> PlayerStatus.Idle
            },
            playWhenReady = exoPlayer.playWhenReady,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0,
            errorMessage = error?.message,
            audioFormat = currentSourceFormat(),
            replayGain = replayGain,
        )
    }

    private fun currentSourceFormat(): AudioFormatInfo? {
        val track = loadedTrack ?: return null
        val item = exoPlayer.currentMediaItem ?: return null
        if (item.mediaId != track.id || item.localConfiguration?.uri?.toString() != track.uri) return null
        // A newly loaded item may briefly retain old track groups until preparation completes.
        if (exoPlayer.playbackState != Player.STATE_READY && exoPlayer.playbackState != Player.STATE_ENDED) return null
        for (group in exoPlayer.currentTracks.groups) {
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
            .setArtworkUri(Uri.parse(uri))
            .build(),
    )
    .build()
