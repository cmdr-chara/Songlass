package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.AudioFormatInfo
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.designsystem.InfoSheet
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.ScreenTransition
import dev.cmdrchara.songlass.designsystem.SeekBar
import dev.cmdrchara.songlass.designsystem.TransportControls
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** Artwork, lyrics and queue share one stable set of playback controls. */
@Composable
fun NowPlayingContent(state: AppState, queue: PlaybackQueue, player: PlayerState, favoriteIds: List<String>, labels: PlaybackLabels, onArtworkTap: () -> Unit) {
    val track = queue.current?.track ?: return
    val controller = state.controller
    val secondary = LocalContentColor.current.copy(alpha = 0.72f)
    val isFavorite = track.id in favoriteIds
    val outputPicker = rememberAudioOutputPicker()
    val quality = audioQualityLabel(player.audioFormat)
    val artworkLabel = stringResource(Res.string.playback_artwork)
    val expandArtworkLabel = stringResource(Res.string.playback_expand_artwork)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // On short windows scroll the composition instead of squeezing playback hit targets.
        val contentHeight = maxHeight.coerceAtLeast(540.dp)
        val needsScroll = maxHeight < contentHeight
        val seekGap = if (contentHeight > 600.dp) 16.dp else 4.dp
        val controlsHeight = (contentHeight * 0.45f).coerceAtLeast(270.dp)
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize().then(if (needsScroll) Modifier.verticalScroll(scroll) else Modifier)) {
            Column(Modifier.fillMaxWidth().height(contentHeight)) {
                ScreenTransition(state.playerPage, Modifier.weight(1f).fillMaxWidth()) { page ->
                    Column(Modifier.fillMaxSize()) {
                        when (page) {
                            PlayerPage.Lyrics -> LyricsPane(track, player.positionMs, player.durationMs.takeIf { it > 0 } ?: track.durationMs, player.isPlaying, state.lyrics, controller::seekTo, Modifier.fillMaxSize())
                            PlayerPage.Queue -> {
                                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AlbumArt(track.artwork(), Modifier.size(48.dp), cornerRadius = 8.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        AppText(track.title, AppTheme.typography.headline, maxLines = 1)
                                        AppText(artistName(track.artist), AppTheme.typography.subheadline, color = secondary, maxLines = 1)
                                    }
                                }
                                AppText(stringResource(Res.string.up_next), AppTheme.typography.headline, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                                if (queue.upNext.isEmpty()) {
                                    AppText(stringResource(Res.string.up_next_empty), AppTheme.typography.subheadline, color = secondary, modifier = Modifier.weight(1f))
                                } else ReorderableQueue(queue, state, Modifier.weight(1f))
                            }
                            PlayerPage.Artwork -> {
                                if (needsScroll) Box(Modifier.weight(1f).fillMaxWidth().combinedClickable(
                                    role = Role.Button,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = onArtworkTap,
                                    onLongClickLabel = expandArtworkLabel,
                                    onLongClick = { state.updateArtworkExpansion(true) },
                                ).semantics { contentDescription = artworkLabel }) else Spacer(Modifier.weight(1f))
                                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        AppText(track.title, AppTheme.typography.title3.copy(fontWeight = FontWeight.Bold), maxLines = 2)
                                        AppText(artistName(track.artist), AppTheme.typography.subheadline, color = secondary, maxLines = 1)
                                    }
                                    IconButton(
                                        icon = if (isFavorite) AppIcons.StarFilled else AppIcons.Star,
                                        contentDescription = stringResource(if (isFavorite) Res.string.action_unfavorite else Res.string.action_favorite),
                                        onClick = { state.favorites.toggle(track.id) }, selected = isFavorite, filled = true,
                                    )
                                    if (!track.uri.startsWith("demo:")) IconButton(AppIcons.Checkmark, stringResource(Res.string.playback_on_device), onClick = state::showSongInfo, filled = true)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().height(controlsHeight)) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TransportControls(player.isPlaying, labels, controller::skipToPrevious, controller::togglePlayPause, controller::skipToNext)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(AppIcons.Shuffle, stringResource(Res.string.playback_shuffle), onClick = { controller.setShuffle(!queue.isShuffled) }, selected = queue.isShuffled)
                        IconButton(
                            if (queue.repeatMode == RepeatMode.One) AppIcons.RepeatOne else AppIcons.Repeat,
                            stringResource(if (queue.repeatMode == RepeatMode.One) Res.string.playback_repeat_one else Res.string.playback_repeat),
                            onClick = controller::cycleRepeatMode, selected = queue.repeatMode != RepeatMode.Off,
                        )
                    }
                    Spacer(Modifier.height(seekGap))
                    SeekBar(player.positionMs, player.durationMs.takeIf { it > 0 } ?: track.durationMs, controller::seekTo, qualityLabel = quality)
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (outputPicker != null) IconButton(AppIcons.AudioOutput, stringResource(Res.string.playback_audio_output), outputPicker)
                        Spacer(Modifier.weight(1f))
                        IconButton(AppIcons.Lyrics, stringResource(Res.string.playback_lyrics), state::toggleLyrics, selected = state.playerPage == PlayerPage.Lyrics)
                        IconButton(AppIcons.Queue, stringResource(Res.string.playback_queue), state::toggleQueue, selected = state.isQueueVisible)
                        IconButton(AppIcons.More, stringResource(Res.string.action_more), onClick = { state.openMenu(track) })
                    }
                }
            }
        }
    }
}

/** Show known input properties only: this does not claim the output device has the same format. */
private fun audioQualityLabel(format: AudioFormatInfo?): String? = format?.let {
    buildList {
        it.bitDepth?.takeIf { n -> n > 0 }?.let { n -> add("$n-bit") }
        it.sampleRateHz?.takeIf { n -> n > 0 }?.let { hz ->
            val fraction = (hz % 1000).toString().padStart(3, '0').trimEnd('0')
            add("${hz / 1000}${if (fraction.isEmpty()) "" else ".$fraction"} kHz")
        }
        it.codec?.takeIf(String::isNotBlank)?.let(::add)
    }.joinToString(" · ").takeIf(String::isNotBlank)
}

@Composable
fun SongInformation(state: AppState, track: Track, player: PlayerState) {
    val quality = audioQualityLabel(player.audioFormat)
    InfoSheet(
        visible = state.isSongInfoVisible,
        title = stringResource(Res.string.playback_song_info),
        rows = listOfNotNull(
            InfoRow(stringResource(Res.string.playback_info_title), track.title),
            InfoRow(stringResource(Res.string.playback_info_artist), artistName(track.artist)),
            track.album?.let { InfoRow(stringResource(Res.string.playback_info_album), it) },
            InfoRow(stringResource(Res.string.playback_info_duration), formatDuration(player.durationMs.takeIf { it > 0 } ?: track.durationMs)),
            quality?.let { InfoRow(stringResource(Res.string.playback_info_format), it) },
            player.audioFormat?.bitRate?.takeIf { it > 0 }?.let { InfoRow(stringResource(Res.string.playback_info_bitrate), "${it / 1000} kbps") },
            track.folder?.let { InfoRow(stringResource(Res.string.playback_info_folder), it) },
            if (!track.uri.startsWith("demo:")) InfoRow(stringResource(Res.string.playback_info_storage), stringResource(Res.string.playback_local_file)) else null,
        ),
        closeLabel = stringResource(Res.string.action_close),
        onDismiss = state::closeSongInfo,
    )
}

private val QueueRowHeight = 60.dp

/**
 * Up Next list: tap a row to play it, drag the handle to reorder. Rows have a fixed height,
 * so the drag offset maps directly to positions.
 */
@Composable
private fun ReorderableQueue(queue: PlaybackQueue, state: AppState, modifier: Modifier) {
    val rowHeightPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    val reduceMotion = AppTheme.accessibility.reduceMotion
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
                        val scale = if (dragging && !reduceMotion) 1.02f else 1f
                        scaleX = scale; scaleY = scale
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
