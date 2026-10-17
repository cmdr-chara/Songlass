package dev.cmdrchara.songlass.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.cmdrchara.songlass.core.model.Track
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
 * [AudioPlayer] backed by Media3's ExoPlayer. It plays one track at a time: the queue lives in
 * core's PlaybackController. Must be used from the main thread, like ExoPlayer itself.
 */
class Media3AudioPlayer(
    val exoPlayer: ExoPlayer,
    private val scope: CoroutineScope,
) : AudioPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    private var positionJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publishState()

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
        exoPlayer.setMediaItem(track.toMediaItem())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
        publishState()
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
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        publishState()
    }

    override fun release() {
        positionJob?.cancel()
        exoPlayer.release()
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
        )
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
            .build(),
    )
    .build()
