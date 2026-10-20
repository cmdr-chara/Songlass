package dev.cmdrchara.songlass.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.cmdrchara.songlass.core.playback.PlaybackController

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
        val target = audio ?: return super.handleStop()
        target.stop()
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
            )
            .build()
        val builder = state.buildUpon().setAvailableCommands(commands)
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

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> controller.skipToNext()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> controller.skipToPrevious()
            else -> controller.seekTo(positionMs.coerceAtLeast(0))
        }
        refreshPlaybackIntent()
        return Futures.immediateVoidFuture()
    }
}
