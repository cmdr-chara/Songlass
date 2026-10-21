package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.collectAsState

import dev.cmdrchara.songlass.shared.resources.sleep_title

import dev.cmdrchara.songlass.shared.resources.playback_volume

import dev.cmdrchara.songlass.core.playback.SleepTimerState

import dev.cmdrchara.songlass.designsystem.VolumeSlider

import androidx.compose.foundation.combinedClickable
import dev.cmdrchara.songlass.designsystem.foundation.playerArtworkTarget
import dev.cmdrchara.songlass.designsystem.foundation.LocalArtworkPresentation
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkPresentation
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.SheetAction
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
    val trackArtwork = remember(track, state.library) { track.artwork(state.library) }
    val controller = state.controller
    val preferences by state.settings.state.collectAsState()
    val durationForControls = player.durationMs.takeIf { it > 0 } ?: track.durationMs
    val isLongTrack = durationForControls >= preferences.longTrackThresholdMs && durationForControls > 0L
    val secondary = LocalContentColor.current.copy(alpha = 0.72f)
    val isFavorite = track.id in favoriteIds
    val outputPicker = rememberAudioOutputPicker()
    val outputName = rememberAudioOutputName()
    val quality = audioQualityLabel(player.audioFormat)
    val artworkLabel = stringResource(Res.string.playback_artwork)
    val expandArtworkLabel = stringResource(Res.string.playback_expand_artwork)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        // On short windows scroll the composition instead of squeezing playback hit targets.
        val contentHeight = maxHeight.coerceAtLeast(540.dp)
        val needsScroll = maxHeight < contentHeight
        val seekGap = if (contentHeight > 600.dp) 8.dp else 4.dp
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize().then(if (needsScroll) Modifier.verticalScroll(scroll) else Modifier)) {
            Column(Modifier.fillMaxWidth().height(contentHeight)) {
                ScreenTransition(state.playerPage, Modifier.weight(1f).fillMaxWidth()) { page ->
                    Column(Modifier.fillMaxSize()) {
                        when (page) {
                            PlayerPage.Lyrics -> LyricsPane(track, player.positionMs, player.durationMs.takeIf { it > 0 } ?: track.durationMs, player.isPlaying, state.lyrics, controller::seekTo, Modifier.fillMaxSize())
                            PlayerPage.Queue -> {
                                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AlbumArt(trackArtwork, Modifier.size(48.dp), cornerRadius = 8.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        AppText(track.title, AppTheme.typography.headline, maxLines = 1)
                                        AppText(if (queue.current?.isAvailable == false) stringResource(Res.string.queue_file_unavailable)
                                            else if (queue.current?.isTemporary == true) stringResource(Res.string.queue_temporary_track)
                                            else artistName(track.artist), AppTheme.typography.subheadline, color = secondary, maxLines = 1)
                                    }
                                }
                                QueueHeading(queue, state)
                                if (queue.upNext.isEmpty()) {
                                    AppText(stringResource(Res.string.up_next_empty), AppTheme.typography.subheadline, color = secondary, modifier = Modifier.weight(1f))
                                } else ReorderableQueue(queue, state, Modifier.weight(1f))
                            }
                            PlayerPage.Artwork -> {
                                if (LocalArtworkPresentation.current == ArtworkPresentation.Square) {
                                    // A full-width rounded square, with the same resting size in pause/play.
                                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        // Fit both dimensions. Height-first aspectRatio could overflow
                                        // the width and clip the rounded sides when the flight ended.
                                        val artworkSide = minOf(maxWidth, maxHeight)
                                        AlbumArt(
                                            trackArtwork,
                                            Modifier.size(artworkSide)
                                                .playerArtworkTarget(cornerRadius = 12.dp)
                                                .combinedClickable(
                                                    role = Role.Button,
                                                    onClick = {},
                                                    onLongClickLabel = expandArtworkLabel,
                                                    onLongClick = { state.updateArtworkExpansion(true) },
                                                )
                                                .semantics { contentDescription = artworkLabel },
                                            cornerRadius = 12.dp,
                                        )
                                    }
                                } else if (needsScroll) Box(Modifier.weight(1f).fillMaxWidth().combinedClickable(
                                    role = Role.Button,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = onArtworkTap,
                                    onLongClickLabel = expandArtworkLabel,
                                    onLongClick = { state.updateArtworkExpansion(true) },
                                ).semantics { contentDescription = artworkLabel }) else Spacer(Modifier.weight(1f))
                                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        AppText(track.title, AppTheme.typography.title3.copy(fontWeight = FontWeight.Bold), maxLines = 2)
                                        AppText(if (queue.current?.isAvailable == false) stringResource(Res.string.queue_file_unavailable)
                                            else artistName(track.artist), AppTheme.typography.subheadline, color = secondary, maxLines = 2)
                                    }
                                    IconButton(
                                        icon = if (isFavorite) AppIcons.StarFilled else AppIcons.Star,
                                        contentDescription = stringResource(if (isFavorite) Res.string.action_unfavorite else Res.string.action_favorite),
                                        onClick = { state.favorites.toggle(track.id) }, selected = isFavorite, filled = true,
                                    )
                                    if (!track.uri.startsWith("demo:")) IconButton(AppIcons.InfoCircle, stringResource(Res.string.playback_song_info), onClick = state::showSongInfo, filled = true)
                                }
                            }
                        }
                    }
                }
                val compact = state.playerPage == PlayerPage.Lyrics
                val systemVolume = rememberSystemVolume()
                val sleepTimer by state.sleepTimer.state.collectAsState()
                // Controls take their natural height: the artwork and title get the rest.
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (AppTheme.accessibility.reduceMotion) Modifier else Modifier.animateContentSize()),
                ) {
                    val shuffle: @Composable () -> Unit = {
                        IconButton(AppIcons.Shuffle, stringResource(Res.string.playback_shuffle), onClick = { controller.setShuffle(!queue.isShuffled) }, selected = queue.isShuffled, filled = queue.isShuffled)
                    }
                    val repeat: @Composable () -> Unit = {
                        IconButton(
                            if (queue.repeatMode == RepeatMode.One) AppIcons.RepeatOne else AppIcons.Repeat,
                            stringResource(if (queue.repeatMode == RepeatMode.One) Res.string.playback_repeat_one else Res.string.playback_repeat),
                            onClick = controller::cycleRepeatMode, selected = queue.repeatMode != RepeatMode.Off, filled = queue.repeatMode != RepeatMode.Off,
                        )
                    }
                    if (compact) {
                        // Lyrics need the room: all playback controls on one line.
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            shuffle()
                            IconButton(AppIcons.Previous, labels.previous, controller::skipToPrevious)
                            IconButton(if (player.isPlaying) AppIcons.Pause else AppIcons.Play, if (player.isPlaying) labels.pause else labels.play, controller::togglePlayPause)
                            IconButton(AppIcons.Next, labels.next, controller::skipToNext)
                            repeat()
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                            TransportControls(player.isPlaying, labels, controller::skipToPrevious, controller::togglePlayPause, controller::skipToNext)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            shuffle()
                            repeat()
                        }
                        Spacer(Modifier.height(seekGap))
                    }
                    if (isLongTrack) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val rewindLabel = stringResource(Res.string.bookmark_seek_back_accessible)
                            val forwardLabel = stringResource(Res.string.bookmark_seek_forward_accessible)
                            ActionButton(stringResource(Res.string.bookmark_seek_back), { controller.skipBy(-10_000L) },
                                Modifier.weight(1f).semantics { contentDescription = rewindLabel })
                            ActionButton(stringResource(Res.string.bookmark_seek_forward), { controller.skipBy(10_000L) },
                                Modifier.weight(1f).semantics { contentDescription = forwardLabel })
                        }
                    }
                    SeekBar(player.positionMs, player.durationMs.takeIf { it > 0 } ?: track.durationMs, controller::seekTo, qualityLabel = quality, isPlaying = player.isPlaying)
                    if (!compact && systemVolume != null) {
                        VolumeSlider(systemVolume.value, systemVolume.set, stringResource(Res.string.playback_volume))
                    }
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (outputPicker != null) AudioOutputButton(outputName, onClick = outputPicker)
                        Spacer(Modifier.weight(1f))
                        val lyricsOn = state.playerPage == PlayerPage.Lyrics
                        val timerOn = sleepTimer != SleepTimerState.Off
                        IconButton(AppIcons.Timer, stringResource(Res.string.sleep_title), state::openSleepMenu, selected = timerOn, filled = timerOn)
                        IconButton(AppIcons.Lyrics, stringResource(Res.string.playback_lyrics), state::toggleLyrics, selected = lyricsOn, filled = lyricsOn)
                        IconButton(AppIcons.Queue, stringResource(Res.string.playback_queue), state::toggleQueue, selected = state.isQueueVisible, filled = state.isQueueVisible)
                        IconButton(AppIcons.More, stringResource(Res.string.action_more), onClick = { state.openMenu(track) })
                    }
                }
            }
        }
    }
}

/** Output icon plus the current device name ("This phone", headphones, Bluetooth speaker…). */
@Composable
private fun AudioOutputButton(name: String?, onClick: () -> Unit) {
    val label = stringResource(Res.string.playback_audio_output)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(label, name).joinToString(", ") }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            AppIcon(AppIcons.AudioOutput, contentDescription = null)
        }
        if (name != null) {
            AppText(name, AppTheme.typography.footnote, color = LocalContentColor.current.copy(alpha = 0.72f), maxLines = 1)
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

/** Shared controls keep each style's own sheet, typography, surfaces and touch targets. */
@Composable
private fun QueueHeading(queue: PlaybackQueue, state: AppState) {
    var options by remember { mutableStateOf(false) }
    val temporaryCount = remember(queue) { queue.temporaryEntries.size }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AppText(stringResource(Res.string.up_next), AppTheme.typography.headline)
            if (temporaryCount > 0) AppText(
                stringResource(if (queue.hasSource) Res.string.queue_return_hint else Res.string.queue_standalone_hint, temporaryCount),
                AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
            )
        }
        if (temporaryCount > 0) IconButton(AppIcons.More, stringResource(Res.string.queue_options), { options = true })
    }
    ActionSheet(
        visible = options && temporaryCount > 0,
        title = stringResource(Res.string.queue_options),
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = { options = false },
        actions = listOfNotNull(
            if (queue.pendingTemporaryCount > 0) SheetAction(stringResource(Res.string.queue_clear_pending), AppIcons.Close) {
                state.controller.clearTemporaryQueue()
            } else null,
            if (queue.current?.isTemporary == true) SheetAction(
                stringResource(if (queue.hasSource) Res.string.queue_return_source else Res.string.queue_finish_temporary), AppIcons.Library,
            ) { state.controller.returnToSource() } else null,
        ),
    )
}

/** Up Next supports touch dragging and the same move/remove actions through a tap or TalkBack. */
@Composable
private fun ReorderableQueue(queue: PlaybackQueue, state: AppState, modifier: Modifier) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val currentQueue by rememberUpdatedState(queue)
    val upcoming = remember(queue) { queue.upNext }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var editId by remember { mutableStateOf<Long?>(null) }
    val editLabel = stringResource(Res.string.queue_edit_entry)
    val upLabel = stringResource(Res.string.queue_move_up)
    val downLabel = stringResource(Res.string.queue_move_down)
    val removeLabel = stringResource(Res.string.queue_remove_entry)

    fun canMove(id: Long, direction: Int): Boolean {
        val q = currentQueue
        val index = q.indexOf(id)
        val target = index + direction
        return index > q.currentIndex && target > q.currentIndex && target in q.entries.indices &&
            q.entries[index].isTemporary == q.entries[target].isTemporary
    }
    fun move(id: Long, direction: Int): Boolean {
        return state.controller.moveQueueEntry(id, direction)
    }

    LazyColumn(modifier) {
        itemsIndexed(upcoming, key = { _, entry -> entry.id }) { _, entry ->
            val dragging = entry.id == draggedId
            var rowHeightPx by remember(entry.id) { mutableIntStateOf(0) }
            Box(
                Modifier
                    .heightIn(min = 60.dp)
                    .onSizeChanged { rowHeightPx = it.height }
                    .semantics {
                        customActions = buildList {
                            if (canMove(entry.id, -1)) add(CustomAccessibilityAction(upLabel) { move(entry.id, -1) })
                            if (canMove(entry.id, 1)) add(CustomAccessibilityAction(downLabel) { move(entry.id, 1) })
                            add(CustomAccessibilityAction(removeLabel) { state.controller.removeFromQueue(entry.id); true })
                        }
                    }
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        val scale = if (dragging && !reduceMotion) 1.02f else 1f
                        scaleX = scale; scaleY = scale
                    },
            ) {
                ListRow(
                    title = entry.track.title,
                    subtitle = if (!entry.isAvailable) stringResource(Res.string.queue_file_unavailable)
                        else if (entry.isTemporary) stringResource(Res.string.queue_temporary_subtitle, artistName(entry.track.artist))
                        else artistName(entry.track.artist),
                    leading = { AlbumArt(rememberTrackArtwork(entry.track), Modifier.size(40.dp), cornerRadius = 6.dp) },
                    trailing = {
                        IconButton(
                            icon = AppIcons.DragHandle,
                            contentDescription = editLabel,
                            onClick = { editId = entry.id },
                            modifier = Modifier.pointerInput(entry.id, rowHeightPx) {
                                detectDragGestures(
                                    onDragStart = { draggedId = entry.id; dragOffset = 0f },
                                    onDragEnd = { draggedId = null; dragOffset = 0f },
                                    onDragCancel = { draggedId = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    if (rowHeightPx > 0) {
                                        if (dragOffset > rowHeightPx / 2 && move(entry.id, 1)) dragOffset -= rowHeightPx
                                        else if (dragOffset < -rowHeightPx / 2 && move(entry.id, -1)) dragOffset += rowHeightPx
                                        else dragOffset = dragOffset.coerceIn(-rowHeightPx.toFloat(), rowHeightPx.toFloat())
                                    }
                                }
                            },
                        )
                    },
                    onClick = if (entry.isAvailable) ({ state.controller.skipTo(entry.id) }) else null,
                )
            }
        }
    }
    val editing = editId?.let { id -> queue.entries.firstOrNull { it.id == id } }
    ActionSheet(
        visible = editing != null,
        title = editing?.track?.title.orEmpty(),
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = { editId = null },
        actions = listOfNotNull(
            editing?.takeIf { canMove(it.id, -1) }?.let { entry -> SheetAction(upLabel, null) { move(entry.id, -1) } },
            editing?.takeIf { canMove(it.id, 1) }?.let { entry -> SheetAction(downLabel, null) { move(entry.id, 1) } },
            editing?.let { entry -> SheetAction(removeLabel, AppIcons.Close) { state.controller.removeFromQueue(entry.id) } },
        ),
    )
}
