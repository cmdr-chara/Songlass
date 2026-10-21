package dev.cmdrchara.songlass.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.playback.SleepTimerState
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.AudioFormatInfo
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.PlayerUtilityAction
import dev.cmdrchara.songlass.designsystem.PlayerOutputAction
import dev.cmdrchara.songlass.designsystem.PlayerUtilityBar
import dev.cmdrchara.songlass.designsystem.ScreenTransition
import dev.cmdrchara.songlass.designsystem.SeekBar
import dev.cmdrchara.songlass.designsystem.TransportControls
import dev.cmdrchara.songlass.designsystem.VolumeSlider
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkPresentation
import dev.cmdrchara.songlass.designsystem.foundation.LocalArtworkPresentation
import dev.cmdrchara.songlass.designsystem.foundation.playerArtworkTarget
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
    val outputName = player.outputRoute?.let { audioOutputName(it) } ?: rememberAudioOutputName()
    val quality = audioQualityLabel(player.audioFormat)
    val artworkLabel = stringResource(Res.string.playback_artwork)
    val expandArtworkLabel = stringResource(Res.string.playback_expand_artwork)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        val viewportHeight = maxHeight
        val minimumPageHeight = 240.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val seekGap = if (maxHeight > 600.dp) 8.dp else 4.dp
        val scroll = rememberScrollState()
        val needsScroll = scroll.maxValue > 0
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            PlayerContentLayout(viewportHeight, minimumPageHeight) {
                ScreenTransition(state.playerPage, Modifier.fillMaxWidth()) { page ->
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
                                QueuePane(state, queue, onSaveUpcoming = { snapshot ->
                                    state.askPlaylistName(PlaylistPrompt.CreateFromQueue(snapshot))
                                }, Modifier.weight(1f))
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
                                                    onClickLabel = expandArtworkLabel,
                                                    onClick = { state.updateArtworkExpansion(true) },
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
                    val minutesLeft = sleepMinutesLeft(sleepTimer)
                    val timerLabel = when {
                        minutesLeft != null -> stringResource(Res.string.player_timer_minutes, minutesLeft)
                        sleepTimer == SleepTimerState.EndOfTrack -> stringResource(Res.string.player_timer_track_end)
                        else -> stringResource(Res.string.player_timer_short)
                    }
                    PlayerUtilityBar(
                        output = outputPicker?.let { PlayerOutputAction(
                            outputName ?: stringResource(Res.string.playback_audio_output),
                            listOfNotNull(stringResource(Res.string.playback_audio_output), outputName).joinToString(", "), it,
                        ) },
                        audioLabel = stringResource(Res.string.audio_tools_title),
                        onAudio = state::showSongInfo,
                        actions = listOf(
                            PlayerUtilityAction(timerLabel, AppIcons.Timer, sleepTimer != SleepTimerState.Off, state::openSleepMenu,
                                accessibilityLabel = if (sleepTimer == SleepTimerState.Off) stringResource(Res.string.sleep_title)
                                    else "${stringResource(Res.string.sleep_title)}, $timerLabel"),
                            PlayerUtilityAction(stringResource(Res.string.playback_lyrics), AppIcons.Lyrics, state.playerPage == PlayerPage.Lyrics, state::toggleLyrics),
                            PlayerUtilityAction(stringResource(Res.string.player_queue_short), AppIcons.Queue, state.isQueueVisible, state::toggleQueue),
                            PlayerUtilityAction(stringResource(Res.string.action_more), AppIcons.More, false, onClick = { state.openMenu(track) }),
                        ),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

/** Show known input properties only: this does not claim the output device has the same format. */
internal fun audioQualityLabel(format: AudioFormatInfo?): String? = format?.let {
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
    AudioInformationSheet(state, track, player)
}


/**
 * Measure controls before allocating the page, in the same pass. Large text and short windows
 * grow the scrollable composition instead of clipping controls or moving the artwork target
 * after an onSizeChanged/recomposition round trip. Lazy queue/lyrics always receive a finite height.
 */
@Composable
private fun PlayerContentLayout(viewportHeight: Dp, minimumPageHeight: Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { children, constraints ->
        val controls = children[1].measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val height = maxOf(viewportHeight.roundToPx(), 540.dp.roundToPx(), controls.height + minimumPageHeight.roundToPx())
        val page = children[0].measure(Constraints.fixed(constraints.maxWidth, height - controls.height))
        layout(constraints.maxWidth, height) {
            page.placeRelative(0, 0)
            controls.placeRelative(0, page.height)
        }
    }
}
