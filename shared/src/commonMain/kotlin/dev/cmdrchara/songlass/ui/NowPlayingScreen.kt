package dev.cmdrchara.songlass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.SeekBar
import dev.cmdrchara.songlass.designsystem.TransportControls
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.playback_queue
import dev.cmdrchara.songlass.shared.resources.playback_repeat
import dev.cmdrchara.songlass.shared.resources.playback_repeat_one
import dev.cmdrchara.songlass.shared.resources.playback_shuffle
import dev.cmdrchara.songlass.shared.resources.up_next
import dev.cmdrchara.songlass.shared.resources.up_next_empty
import org.jetbrains.compose.resources.stringResource

/** Contents of the Now Playing sheet: artwork (or the queue), track info and controls. */
@Composable
fun NowPlayingContent(state: AppState, queue: PlaybackQueue, player: PlayerState, labels: PlaybackLabels) {
    val track = queue.current?.track ?: return
    val controller = state.controller
    val secondary = LocalContentColor.current.copy(alpha = 0.6f)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (state.isQueueVisible) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(track.artwork(), Modifier.size(64.dp), cornerRadius = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    AppText(track.title, AppTheme.typography.headline, maxLines = 1)
                    AppText(artistName(track.artist), AppTheme.typography.subheadline, color = secondary, maxLines = 1)
                }
            }
            AppText(stringResource(Res.string.up_next), AppTheme.typography.headline, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            if (queue.upNext.isEmpty()) {
                AppText(stringResource(Res.string.up_next_empty), AppTheme.typography.subheadline, color = secondary, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(queue.upNext, key = { it.id }) { entry ->
                        ListRow(
                            title = entry.track.title,
                            subtitle = artistName(entry.track.artist),
                            leading = { AlbumArt(entry.track.artwork(), Modifier.size(40.dp), cornerRadius = 6.dp) },
                            onClick = { controller.skipTo(entry.id) },
                        )
                    }
                }
            }
        } else {
            // Apple Music shrinks the artwork while paused.
            val artScale by animateFloatAsState(if (player.isPlaying) 1f else 0.86f)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlbumArt(
                    track.artwork(),
                    Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = artScale; scaleY = artScale },
                    cornerRadius = 12.dp,
                )
            }
            AppText(track.title, AppTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            AppText(artistName(track.artist), AppTheme.typography.title3.copy(fontWeight = FontWeight.Normal), color = secondary, maxLines = 1)
        }

        Spacer(Modifier.height(16.dp))
        SeekBar(
            positionMs = player.positionMs,
            durationMs = if (player.durationMs > 0) player.durationMs else track.durationMs,
            onSeek = controller::seekTo,
        )
        Spacer(Modifier.height(8.dp))
        TransportControls(
            isPlaying = player.isPlaying,
            labels = labels,
            onPrevious = controller::skipToPrevious,
            onPlayPause = controller::togglePlayPause,
            onNext = controller::skipToNext,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(
                icon = AppIcons.Shuffle,
                contentDescription = stringResource(Res.string.playback_shuffle),
                onClick = { controller.setShuffle(!queue.isShuffled) },
                selected = queue.isShuffled,
            )
            IconButton(
                icon = AppIcons.Queue,
                contentDescription = stringResource(Res.string.playback_queue),
                onClick = state::toggleQueue,
                selected = state.isQueueVisible,
            )
            IconButton(
                icon = if (queue.repeatMode == RepeatMode.One) AppIcons.RepeatOne else AppIcons.Repeat,
                contentDescription = stringResource(if (queue.repeatMode == RepeatMode.One) Res.string.playback_repeat_one else Res.string.playback_repeat),
                onClick = controller::cycleRepeatMode,
                selected = queue.repeatMode != RepeatMode.Off,
            )
        }
    }
}
