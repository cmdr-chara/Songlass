package dev.cmdrchara.songlass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.SeekBar
import dev.cmdrchara.songlass.designsystem.TransportControls
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.action_favorite
import dev.cmdrchara.songlass.shared.resources.action_more
import dev.cmdrchara.songlass.shared.resources.action_reorder
import dev.cmdrchara.songlass.shared.resources.action_unfavorite
import dev.cmdrchara.songlass.shared.resources.playback_queue
import dev.cmdrchara.songlass.shared.resources.playback_repeat
import dev.cmdrchara.songlass.shared.resources.playback_repeat_one
import dev.cmdrchara.songlass.shared.resources.playback_shuffle
import dev.cmdrchara.songlass.shared.resources.up_next
import dev.cmdrchara.songlass.shared.resources.up_next_empty
import org.jetbrains.compose.resources.stringResource

/** Contents of the Now Playing sheet: artwork (or the queue), track info and controls. */
@Composable
fun NowPlayingContent(state: AppState, queue: PlaybackQueue, player: PlayerState, favoriteIds: List<String>, labels: PlaybackLabels) {
    val track = queue.current?.track ?: return
    val controller = state.controller
    val secondary = LocalContentColor.current.copy(alpha = 0.6f)
    val isFavorite = track.id in favoriteIds

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (state.isQueueVisible) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(track.artwork(), Modifier.size(64.dp), cornerRadius = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AppText(track.title, AppTheme.typography.headline, maxLines = 1)
                    AppText(artistName(track.artist), AppTheme.typography.subheadline, color = secondary, maxLines = 1)
                }
                TrackActions(track, isFavorite, state)
            }
            AppText(stringResource(Res.string.up_next), AppTheme.typography.headline, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            if (queue.upNext.isEmpty()) {
                AppText(stringResource(Res.string.up_next_empty), AppTheme.typography.subheadline, color = secondary, modifier = Modifier.weight(1f))
            } else {
                ReorderableQueue(queue, state, Modifier.weight(1f))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText(track.title, AppTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                    AppText(artistName(track.artist), AppTheme.typography.title3.copy(fontWeight = FontWeight.Normal), color = secondary, maxLines = 1)
                }
                TrackActions(track, isFavorite, state)
            }
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

/** Favorite star and "…" in translucent circles, as in Apple Music. */
@Composable
private fun TrackActions(track: Track, isFavorite: Boolean, state: AppState) {
    IconButton(
        icon = if (isFavorite) AppIcons.StarFilled else AppIcons.Star,
        contentDescription = stringResource(if (isFavorite) Res.string.action_unfavorite else Res.string.action_favorite),
        onClick = { state.favorites.toggle(track.id) },
        filled = true,
    )
    IconButton(AppIcons.More, stringResource(Res.string.action_more), onClick = { state.openMenu(track) }, filled = true)
}

private val QueueRowHeight = 60.dp

/**
 * Up Next list: tap a row to play it, drag the handle to reorder. Rows have a fixed height,
 * so the drag offset maps directly to positions.
 */
@Composable
private fun ReorderableQueue(queue: PlaybackQueue, state: AppState, modifier: Modifier) {
    val rowHeightPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    val currentQueue by rememberUpdatedState(queue)
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val reorderLabel = stringResource(Res.string.action_reorder)

    LazyColumn(modifier) {
        itemsIndexed(queue.upNext, key = { _, entry -> entry.id }) { _, entry ->
            val dragging = entry.id == draggedId
            Box(
                Modifier
                    .height(QueueRowHeight)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        if (dragging) { scaleX = 1.02f; scaleY = 1.02f }
                    },
            ) {
                ListRow(
                    title = entry.track.title,
                    subtitle = artistName(entry.track.artist),
                    leading = { AlbumArt(entry.track.artwork(), Modifier.size(40.dp), cornerRadius = 6.dp) },
                    trailing = {
                        AppIcon(
                            icon = AppIcons.DragHandle,
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = 0.6f),
                            modifier = Modifier
                                .semantics { contentDescription = reorderLabel }
                                .pointerInput(entry.id) {
                                    detectDragGestures(
                                        onDragStart = { draggedId = entry.id; dragOffset = 0f },
                                        onDragEnd = { draggedId = null; dragOffset = 0f },
                                        onDragCancel = { draggedId = null; dragOffset = 0f },
                                    ) { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val q = currentQueue
                                        val index = q.upNext.indexOfFirst { it.id == entry.id }
                                        val base = q.currentIndex + 1
                                        if (dragOffset > rowHeightPx / 2 && index < q.upNext.lastIndex) {
                                            state.controller.moveInQueue(base + index, base + index + 1)
                                            dragOffset -= rowHeightPx
                                        } else if (dragOffset < -rowHeightPx / 2 && index > 0) {
                                            state.controller.moveInQueue(base + index, base + index - 1)
                                            dragOffset += rowHeightPx
                                        }
                                    }
                                },
                        )
                    },
                    onClick = { state.controller.skipTo(entry.id) },
                )
            }
        }
    }
}
