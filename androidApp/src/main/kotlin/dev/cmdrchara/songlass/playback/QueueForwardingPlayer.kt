package dev.cmdrchara.songlass.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.queue.RepeatMode

/**
 * What the system (notification, lock screen, headphones, Bluetooth) sees. ExoPlayer only
 * holds the current track, so "next" and "previous" are advertised here and routed to core's
 * PlaybackController, which owns the queue.
 */
@UnstableApi
class QueueForwardingPlayer(
    player: Player,
    private val controller: PlaybackController,
    private val audio: dev.cmdrchara.songlass.core.player.AudioPlayer? = null,
) : ForwardingSimpleBasePlayer(player) {

    /** Keep one session player and its listeners while the active decoder changes. */
    fun switchPlayer(player: Player) { setPlayer(player) }

    /** Core intent can change while the underlying, still-preparing decoder remains paused. */
    fun refreshPlaybackIntent() { invalidateState() }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) controller.resume() else controller.pause()
        refreshPlaybackIntent()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        controller.stop()
        refreshPlaybackIntent()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        val mode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> RepeatMode.Off
            Player.REPEAT_MODE_ALL -> RepeatMode.All
            Player.REPEAT_MODE_ONE -> RepeatMode.One
            else -> return Futures.immediateFailedFuture<Any>(IllegalArgumentException("Unknown repeat mode"))
        }
        controller.setRepeatMode(mode)
        refreshPlaybackIntent()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        controller.setShuffle(shuffleModeEnabled)
        refreshPlaybackIntent()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        val target = audio ?: return super.handleSetVolume(volume, volumeOperationType)
        target.setVolume(volume)
        return Futures.immediateVoidFuture()
    }

    override fun getState(): State {
        val state = super.getState()
        val commands = state.availableCommands.buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE,
            )
            // The native timeline contains only active/prepared decoders, not queue slots.
            // Single-item selection remains available: MediaSession resolves it through
            // onSetMediaItems, including browse requests and playback resumption.
            .removeAll(
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_PLAYLIST_METADATA,
                Player.COMMAND_SET_SPEED_AND_PITCH,
            )
            .build()
        val queue = controller.queue.value
        val builder = state.buildUpon().setAvailableCommands(commands)
            .setRepeatMode(when (queue.repeatMode) {
                RepeatMode.Off -> Player.REPEAT_MODE_OFF
                RepeatMode.All -> Player.REPEAT_MODE_ALL
                RepeatMode.One -> Player.REPEAT_MODE_ONE
            })
            .setShuffleModeEnabled(queue.isShuffled)
        if ((audio as? Media3AudioPlayer)?.isPreparingManualTransition == true && state.playerError == null) {
            builder.setPlaybackState(Player.STATE_BUFFERING)
                .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }
        return builder.build()
    }

    // Items handed over by media browsers (Android Auto) were already resolved and queued in
    // PlaybackController by the library callback; the player keeps what core loaded.
    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> =
        Futures.immediateVoidFuture()

    // Defend the boundary as well as the advertised commands. A future Media3 path must
    // not edit one decoder's playlist while core still describes a different collection.
    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> = unsupportedMutation()

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> = unsupportedMutation()

    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> = unsupportedMutation()

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> = unsupportedMutation()

    override fun handleSetPlaylistMetadata(playlistMetadata: MediaMetadata): ListenableFuture<*> = unsupportedMutation()

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> = unsupportedMutation()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> controller.skipToNext()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> controller.skipToPrevious()
            Player.COMMAND_SEEK_TO_MEDIA_ITEM -> return unsupportedMutation()
            else -> controller.seekTo(positionMs.coerceAtLeast(0))
        }
        refreshPlaybackIntent()
        return Futures.immediateVoidFuture()
    }

    private fun unsupportedMutation(): ListenableFuture<*> = Futures.immediateFailedFuture<Any>(
        UnsupportedOperationException("This operation is not supported by the Songlass queue"),
    )
}
