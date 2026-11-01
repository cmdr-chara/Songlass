package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.player.presentationPositionAt
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.library.Ratings
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
import dev.cmdrchara.songlass.designsystem.PlayerUtilityBar
import dev.cmdrchara.songlass.designsystem.LocalWindowLayout
import dev.cmdrchara.songlass.designsystem.WindowPanePlan
import dev.cmdrchara.songlass.designsystem.WindowLayout
import dev.cmdrchara.songlass.designsystem.WindowPoint
import dev.cmdrchara.songlass.designsystem.WindowRect
import dev.cmdrchara.songlass.designsystem.ScreenTransition
import dev.cmdrchara.songlass.designsystem.SeekBar
import dev.cmdrchara.songlass.designsystem.TransportControls
import dev.cmdrchara.songlass.designsystem.VolumeSlider
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
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
    val trackArtwork = rememberTrackCoverView(track, state.library).artwork
    val needsCover = rememberTrackNeedsCover(track)
    val controller = state.controller
    val preferences by state.settings.state.collectAsState()
    val durationForControls = player.durationMs.takeIf { it > 0 } ?: track.durationMs
    val isLongTrack = durationForControls >= preferences.longTrackThresholdMs && durationForControls > 0L
    val secondary = LocalContentColor.current.copy(alpha = 0.72f)
    val isFavorite = track.id in favoriteIds
    val storedRatings by state.ratings.values.collectAsState()
    val rating = storedRatings[track.id] ?: 0
    val quality = remember(player.audioFormat) { audioQualityLabel(player.audioFormat) }
    val artworkLabel = stringResource(Res.string.playback_artwork)
    val expandArtworkLabel = stringResource(Res.string.playback_expand_artwork)
    val windowLayout = LocalWindowLayout.current
    val density = LocalDensity.current
    val adaptiveWindow = windowLayout.requiresAdaptivePlayer
    var localWindowLayout by remember { mutableStateOf<WindowLayout?>(null) }
    val coordinatesHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val geometrySource = remember { arrayOfNulls<WindowLayout>(1) }
    fun updateWindowCoordinates(coordinates: LayoutCoordinates) {
        localWindowLayout = windowLayout.inLocalCoordinates(coordinates.size.width / density.density,
            coordinates.size.height / density.density) { x, y ->
            val local = coordinates.windowToLocal(Offset(x * density.density, y * density.density))
            WindowPoint(local.x / density.density, local.y / density.density)
        }
    }
    if (adaptiveWindow) SideEffect {
        // Camera/trait changes can occur without a placement change.
        if (geometrySource[0] != windowLayout) {
            geometrySource[0] = windowLayout
            coordinatesHolder[0]?.takeIf { it.isAttached }?.let(::updateWindowCoordinates)
        }
    }
    val windowCoordinates = if (!adaptiveWindow) Modifier else Modifier.onGloballyPositioned { coordinates ->
        coordinatesHolder[0] = coordinates
        updateWindowCoordinates(coordinates)
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp).then(windowCoordinates),
        contentAlignment = if (adaptiveWindow) AbsoluteAlignment.TopLeft else Alignment.TopStart) {
        val viewportHeight = maxHeight
        val minimumPageHeight = 240.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val scroll = rememberScrollState()
        val controlsScroll = rememberScrollState()
        val geometry = if (adaptiveWindow) localWindowLayout else null
        val nativePlan = geometry?.panes(WindowRect(0f, 0f, maxWidth.value, maxHeight.value), paired = true, allowWidePair = true)
        if (adaptiveWindow && (geometry == null || nativePlan == null)) return@BoxWithConstraints
        val paired = nativePlan?.takeIf { it.paired && it.first.height.dp >= minimumPageHeight && it.second!!.height >= 96f }
        val needsScroll = paired == null && scroll.maxValue > 0
        val singlePane = if (paired == null) geometry?.panes(WindowRect(0f, 0f, maxWidth.value, maxHeight.value), paired = false)?.first else null
        val frame = singlePane ?: WindowRect(0f, 0f, maxWidth.value, maxHeight.value)
        val controlsHeight = paired?.second?.height?.dp ?: frame.height.dp
        val relaxedControlSpacing = controlsHeight > 600.dp && density.fontScale <= 1.3f
        val controlGap = if (relaxedControlSpacing) 24.dp else 16.dp
        val utilityGap = if (relaxedControlSpacing) 20.dp else 12.dp
        val frameModifier = if (!adaptiveWindow) Modifier.fillMaxSize() else
            Modifier.absoluteOffset(frame.left.dp, frame.top.dp).width(frame.width.dp).height(frame.height.dp)
        Box(frameModifier.then(if (paired == null) Modifier.verticalScroll(scroll) else Modifier)) {
            PlayerContentLayout(if (adaptiveWindow && paired == null) frame.height.dp else viewportHeight, minimumPageHeight, paired) {
                ScreenTransition(state.playerPage, Modifier.fillMaxWidth()) { page ->
                    Column(Modifier.fillMaxSize()) {
                        when (page) {
                            PlayerPage.Lyrics -> LyricsPane(track, player.positionMs, player.durationMs.takeIf { it > 0 } ?: track.durationMs, player.isPlaying, state.lyrics, controller::seekTo, Modifier.fillMaxSize(), settings = state.settings,
                                visible = state.isNowPlayingOpen && state.playerPage == PlayerPage.Lyrics && !state.hasOverlay,
                                playbackPosition = { player.presentationPositionAt(controller.monotonicTimeMs) })
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
                                Column(
                                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppText(
                                            track.title,
                                            AppTheme.typography.title1.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.weight(1f).semantics { heading() },
                                            maxLines = 2,
                                        )
                                        IconButton(
                                            icon = if (isFavorite) AppIcons.StarFilled else AppIcons.Star,
                                            contentDescription = stringResource(if (isFavorite) Res.string.action_unfavorite else Res.string.action_favorite),
                                            onClick = { state.favorites.toggle(track.id) },
                                            selected = isFavorite,
                                            filled = isFavorite,
                                        )
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val artist = artistName(track.artist)
                                        val album = track.album?.takeIf(String::isNotBlank)?.takeUnless {
                                            it.equals(track.title, ignoreCase = true) || it.equals(artist, ignoreCase = true)
                                        }
                                        AppText(
                                            if (queue.current?.isAvailable == false) stringResource(Res.string.queue_file_unavailable)
                                            else listOfNotNull(artist, album).joinToString(" · "),
                                            AppTheme.typography.subheadline,
                                            modifier = Modifier.weight(1f),
                                            color = secondary,
                                            maxLines = 2,
                                        )
                                        if (preferences.showPlayerRating) {
                                            RatingIndicator(rating = rating, onClick = { state.openMenu(track, page = TrackMenuPage.Rating) })
                                        }
                                    }
                                    if (needsCover && LocalOnlineArtwork.current != null) {
                                        ActionButton(stringResource(Res.string.feedback_find_cover),
                                            { state.openArtworkSearch(track) }, Modifier.fillMaxWidth(), icon = AppIcons.Search)
                                    }
                                }
                            }
                        }
                    }
                }
                val compact = state.playerPage == PlayerPage.Lyrics
                val sleepTimer by state.sleepTimer.state.collectAsState()
                // Controls take their natural height: the artwork and title get the rest.
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (paired != null) Modifier.verticalScroll(controlsScroll) else Modifier)
                        .padding(top = if (relaxedControlSpacing) 8.dp else 4.dp)
                        .then(if (AppTheme.accessibility.reduceMotion) Modifier else Modifier.animateContentSize()),
                ) {
                    val shuffleState = stringResource(if (queue.isShuffled) Res.string.playback_shuffle_state_on else Res.string.playback_shuffle_state_off)
                    val repeatState = stringResource(when (queue.repeatMode) {
                        RepeatMode.Off -> Res.string.playback_repeat_state_off
                        RepeatMode.All -> Res.string.playback_repeat_state_all
                        RepeatMode.One -> Res.string.playback_repeat_state_one
                    })
                    val shuffle: @Composable () -> Unit = {
                        IconButton(AppIcons.Shuffle, stringResource(Res.string.playback_shuffle),
                            onClick = { controller.setShuffle(!queue.isShuffled) },
                            modifier = Modifier.semantics { stateDescription = shuffleState },
                            selected = queue.isShuffled, filled = queue.isShuffled)
                    }
                    val repeat: @Composable () -> Unit = {
                        IconButton(
                            if (queue.repeatMode == RepeatMode.One) AppIcons.RepeatOne else AppIcons.Repeat,
                            stringResource(Res.string.playback_repeat),
                            onClick = controller::cycleRepeatMode,
                            modifier = Modifier.semantics { stateDescription = repeatState },
                            selected = queue.repeatMode != RepeatMode.Off, filled = queue.repeatMode != RepeatMode.Off,
                        )
                    }
                    // Keep the timeline beside the track context, before the transport controls.
                    SeekBar(player.positionMs, durationForControls, controller::seekTo, qualityLabel = quality, isPlaying = player.isPlaying)
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
                    Spacer(Modifier.height(controlGap))
                    if (compact) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            shuffle()
                            IconButton(AppIcons.Previous, labels.previous, controller::skipToPrevious)
                            IconButton(if (player.isPlaying) AppIcons.Pause else AppIcons.Play, if (player.isPlaying) labels.pause else labels.play, controller::togglePlayPause)
                            IconButton(AppIcons.Next, labels.next, controller::skipToNext)
                            repeat()
                        }
                    } else {
                        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val inlineTransport = maxWidth >= 312.dp && LocalDensity.current.fontScale <= 1.3f
                            if (inlineTransport) {
                                Row(
                                    Modifier.widthIn(max = 416.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    shuffle()
                                    TransportControls(player.isPlaying, labels, controller::skipToPrevious,
                                        controller::togglePlayPause, controller::skipToNext, Modifier.weight(1f))
                                    repeat()
                                }
                            } else {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TransportControls(player.isPlaying, labels, controller::skipToPrevious,
                                        controller::togglePlayPause, controller::skipToNext, Modifier.fillMaxWidth())
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        shuffle()
                                        repeat()
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(utilityGap))
                    val minutesLeft = sleepMinutesLeft(sleepTimer)
                    val timerLabel = when {
                        minutesLeft != null -> stringResource(Res.string.player_timer_minutes, minutesLeft)
                        sleepTimer == SleepTimerState.EndOfTrack -> stringResource(Res.string.player_timer_track_end)
                        else -> stringResource(Res.string.player_timer_short)
                    }
                    PlayerUtilityBar(
                        actions = listOf(
                            PlayerUtilityAction(stringResource(Res.string.player_timer_short), AppIcons.Timer, sleepTimer != SleepTimerState.Off, state::openSleepMenu,
                                accessibilityLabel = if (sleepTimer == SleepTimerState.Off) stringResource(Res.string.sleep_title)
                                    else "${stringResource(Res.string.sleep_title)}, $timerLabel",
                                badge = timerLabel.takeIf { sleepTimer != SleepTimerState.Off }),
                            PlayerUtilityAction(stringResource(Res.string.playback_lyrics), AppIcons.Lyrics, state.playerPage == PlayerPage.Lyrics, state::toggleLyrics),
                            PlayerUtilityAction(stringResource(Res.string.player_queue_short), AppIcons.Queue, state.isQueueVisible, state::toggleQueue),
                            PlayerUtilityAction(stringResource(Res.string.audio_tools_title), AppIcons.Equalizer, false, state::showSongInfo),
                            PlayerUtilityAction(stringResource(Res.string.action_more), AppIcons.More, false, onClick = { state.openMenu(track) }),
                        ),
                        modifier = Modifier.padding(bottom = if (relaxedControlSpacing) 16.dp else 12.dp),
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
private fun PlayerContentLayout(viewportHeight: Dp, minimumPageHeight: Dp, panePlan: WindowPanePlan? = null, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { children, constraints ->
        val other = panePlan?.second
        if (panePlan != null && other != null) {
            val first = panePlan.first
            val page = children[0].measure(Constraints.fixed(first.width.dp.roundToPx().coerceAtLeast(1), first.height.dp.roundToPx().coerceAtLeast(1)))
            val controls = children[1].measure(Constraints.fixed(other.width.dp.roundToPx().coerceAtLeast(1), other.height.dp.roundToPx().coerceAtLeast(1)))
            return@Layout layout(constraints.maxWidth, viewportHeight.roundToPx()) {
                page.place(first.left.dp.roundToPx(), first.top.dp.roundToPx())
                controls.place(other.left.dp.roundToPx(), other.top.dp.roundToPx())
            }
        }
        val controls = children[1].measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        // Grow beyond the safe viewport only when the page and measured controls need it.
        val height = maxOf(viewportHeight.roundToPx(), controls.height + minimumPageHeight.roundToPx())
        val page = children[0].measure(Constraints.fixed(constraints.maxWidth, height - controls.height))
        layout(constraints.maxWidth, height) {
            page.placeRelative(0, 0)
            controls.placeRelative(0, page.height)
        }
    }
}

/** A compact score opens the existing rating page, independently of the Favourite action. */
@Composable
private fun RatingIndicator(rating: Int, onClick: () -> Unit) {
    val label = stringResource(Res.string.rating_title)
    val value = if (rating > 0) stringResource(Res.string.rating_of_five, rating.toString())
        else stringResource(Res.string.rating_none)
    val actionLabel = stringResource(Res.string.rating_set)
    Row(
        Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = value
            }
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            icon = if (rating > 0) AppIcons.StarFilled else AppIcons.Star,
            contentDescription = null,
            size = 14.dp,
            tint = if (rating > 0) AppTheme.colors.accent else AppTheme.colors.secondaryLabel,
        )
        AppText(
            "${if (rating > 0) rating.toString() else "–"}/${Ratings.MAX_STARS}",
            AppTheme.typography.footnote.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium),
            modifier = Modifier.clearAndSetSemantics {},
            maxLines = 1,
        )
    }
}
