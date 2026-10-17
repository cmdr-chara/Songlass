package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkPresentation
import dev.cmdrchara.songlass.designsystem.foundation.LocalArtworkPresentation
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkTint
import dev.cmdrchara.songlass.designsystem.theme.ArtworkTint
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Compact source-quality row over a thin progress rail, with a full 44 dp seek target. */
@Composable
internal fun LiquidGlassSeekBar(
    positionMs: Long,
    durationMs: Long,
    qualityLabel: String?,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val content = LocalContentColor.current
    val currentOnSeek by rememberUpdatedState(onSeek)
    val duration = durationMs.coerceAtLeast(0L)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(duration) { dragFraction = null }
    val playedFraction = if (duration > 0) (positionMs.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f
    val fraction = dragFraction ?: playedFraction
    val shownPosition = (fraction.toDouble() * duration).toLong().coerceIn(0L, duration)
    fun seek(value: Float): Boolean {
        if (duration == 0L || !value.isFinite()) return false
        currentOnSeek((value.coerceIn(0f, 1f).toDouble() * duration).toLong().coerceIn(0L, duration))
        return true
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val timeStyle = AppTheme.typography.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            val secondary = content.copy(alpha = 0.72f)
            AppText(formatDuration(shownPosition), timeStyle, Modifier.widthIn(min = 48.dp), color = secondary)
            AppText(
                qualityLabel.orEmpty(), timeStyle, Modifier.weight(1f).padding(horizontal = 8.dp),
                color = secondary, textAlign = TextAlign.Center, maxLines = 1,
            )
            AppText(
                "-" + formatDuration(duration - shownPosition), timeStyle, Modifier.widthIn(min = 48.dp),
                color = secondary, textAlign = TextAlign.End,
            )
        }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(44.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    if (duration == 0L) disabled()
                    else setProgress { value -> seek(value) }
                }
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (size.width > 0) seek(offset.x / size.width)
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (duration > 0L && size.width > 0) dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = { dragFraction?.let { seek(it) }; dragFraction = null },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        if (duration > 0L && size.width > 0) {
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        }
                    }
                },
        ) {
            val railHeight = (if (dragFraction != null) 8.dp else 6.dp).toPx()
            val y = size.height / 2f
            val radius = CornerRadius(railHeight / 2f)
            drawRoundRect(content.copy(alpha = 0.25f), Offset(0f, y - railHeight / 2f), Size(size.width, railHeight), radius)
            drawRoundRect(content.copy(alpha = 0.85f), Offset(0f, y - railHeight / 2f), Size(size.width * fraction, railHeight), radius)
            val thumbWidth = if (dragFraction != null) 8.dp.toPx() else 6.dp.toPx()
            val thumbHeight = if (dragFraction != null) 24.dp.toPx() else 18.dp.toPx()
            val x = (size.width * fraction).coerceIn(thumbWidth / 2f, maxOf(thumbWidth / 2f, size.width - thumbWidth / 2f))
            drawRoundRect(content, Offset(x - thumbWidth / 2f, y - thumbHeight / 2f), Size(thumbWidth, thumbHeight), CornerRadius(thumbWidth / 2f))
        }
    }
}

@Composable
internal fun LiquidGlassTransportControls(
    isPlaying: Boolean,
    labels: PlaybackLabels,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidGlassIconButton(AppIcons.Previous, labels.previous, onPrevious, false, Modifier, iconSize = 42.dp, hitSize = 72.dp)
        LiquidGlassIconButton(
            icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (isPlaying) labels.pause else labels.play,
            onClick = onPlayPause,
            selected = false,
            modifier = Modifier,
            iconSize = 52.dp,
            hitSize = 88.dp,
        )
        LiquidGlassIconButton(AppIcons.Next, labels.next, onNext, false, Modifier, iconSize = 42.dp, hitSize = 72.dp)
    }
}

/**
 * Full-screen Now Playing sheet tinted by the artwork. The current appearance determines
 * light or dark tonal values and the matching foreground, without changing the cover image.
 */
@Composable
internal fun LiquidGlassNowPlayingSheet(
    visible: Boolean,
    artwork: Artwork?,
    artworkVisible: Boolean,
    artworkExpanded: Boolean,
    artworkLabel: String,
    expandArtworkLabel: String,
    closeArtworkLabel: String,
    onArtworkExpandedChange: (Boolean) -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    content: @Composable (onArtworkTap: () -> Unit) -> Unit,
) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    var artworkTapRequest by remember(artwork, visible, artworkVisible, artworkExpanded, reduceMotion) { mutableStateOf(0) }
    val onArtworkTap: () -> Unit = {
        if (visible && artworkVisible && !artworkExpanded && !reduceMotion) artworkTapRequest += 1
    }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val dismissThreshold = with(LocalDensity.current) { 140.dp.toPx() }
    val dismissVelocity = with(LocalDensity.current) { 1000.dp.toPx() }

    fun settleToOrigin(initialVelocity: Float = 0f) {
        settleJob?.cancel()
        if (reduceMotion || dragOffset == 0f) {
            dragOffset = 0f
            settling = false
        } else {
            settling = true
            settleJob = scope.launch {
                try {
                    animate(
                        initialValue = dragOffset,
                        targetValue = 0f,
                        initialVelocity = initialVelocity,
                        animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
                    ) { value, _ -> dragOffset = value.coerceAtLeast(0f) }
                } finally {
                    if (settleJob == coroutineContext[Job]) settling = false
                }
            }
        }
    }

    // Keep the release position throughout dismissal. A rapid reopen reverses the sheet
    // transition and settles any remaining drag; only a fully hidden sheet resets outright.
    LaunchedEffect(visible, reduceMotion) {
        if (visible) settleToOrigin() else settleJob?.cancel()
    }
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && !visibility.currentState) dragOffset = 0f
    }

    AnimatedVisibility(
        visibleState = visibility,
        enter = if (reduceMotion) {
            fadeIn(tween(LiquidGlassMotion.ReducedMillis))
        } else {
            slideInVertically(tween(LiquidGlassMotion.SheetMillis, easing = LiquidGlassMotion.Emphasized)) { it }
        },
        exit = if (reduceMotion) {
            fadeOut(tween(LiquidGlassMotion.ReducedMillis))
        } else {
            slideOutVertically(tween(LiquidGlassMotion.NavigationMillis, easing = LiquidGlassMotion.Emphasized)) { it }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, if (reduceMotion) 0 else dragOffset.roundToInt()) },
        ) {
            // The whole sheet is drawn in one hue from the cover (no blur is animated). With an
            // edge-to-edge cover, light or dark follows the cover so the fade is seamless.
            val fullBleed = LocalArtworkPresentation.current == ArtworkPresentation.FullBleed
            val hero = artworkVisible && fullBleed
            val tint = animateArtworkTint(rememberArtworkTint(artwork, AppTheme.colors.isDark, followArtwork = fullBleed))
            ArtworkBackdrop(
                tint,
                Modifier.fillMaxSize().pointerInput(Unit) {
                    // This sibling is behind the controls: it catches taps on artwork and
                    // empty space without competing with the header, queue or seek gestures.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
                flat = hero,
            )
            if (hero && artwork != null) ProvideArtworkTint(tint) {
                PlayerArtworkHero(
                    artwork = artwork,
                    surface = tint.background,
                    label = artworkLabel,
                    expandLabel = expandArtworkLabel,
                    tapRequest = artworkTapRequest,
                    onTap = onArtworkTap,
                    onExpand = { onArtworkExpandedChange(true) },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.64f)
                        .then(if (artworkExpanded) Modifier.clearAndSetSemantics {} else Modifier),
                )
            }
            ProvideArtworkTint(tint) {
                Column(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        .then(if (artworkExpanded) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    // Only the header drags the sheet, leaving queue scrolling, reordering
                    // and seeking free to handle their own gestures.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .draggable(
                                orientation = Orientation.Vertical,
                                enabled = visible && !artworkExpanded,
                                startDragImmediately = settling,
                                state = rememberDraggableState { delta ->
                                    dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                                },
                                onDragStarted = {
                                    settleJob?.cancel()
                                    settling = false
                                },
                                onDragStopped = { velocity ->
                                    if (dragOffset > dismissThreshold || velocity > dismissVelocity) {
                                        currentOnDismiss()
                                    } else {
                                        settleToOrigin(velocity)
                                    }
                                },
                            ),
                        contentAlignment = if (hero) Alignment.CenterStart else Alignment.Center,
                    ) {
                        if (hero) {
                            FloatingCircleButton(AppIcons.ChevronDown, dismissLabel, currentOnDismiss, Modifier.padding(start = 12.dp))
                        } else Box(
                            Modifier
                                .size(width = 64.dp, height = 44.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = visible,
                                    onClick = currentOnDismiss,
                                )
                                .semantics { onClick(label = dismissLabel) { currentOnDismiss(); true } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape).background(LocalContentColor.current.copy(alpha = 0.5f)))
                        }
                    }
                    content(onArtworkTap)
                }
                if (artwork != null) {
                    PlayerArtworkExpansion(
                        artwork = artwork,
                        expanded = visible && artworkVisible && artworkExpanded,
                        artworkLabel = artworkLabel,
                        closeLabel = closeArtworkLabel,
                        onClose = { onArtworkExpandedChange(false) },
                    )
                }
            }
        }
    }
}

/**
 * Background in the artwork's tint. Lyrics and queue get a soft glow of the same hue for depth;
 * the artwork page stays [flat] so the hero image can dissolve into it cleanly.
 * Radial gradients are soft by construction, so no full-screen blur is needed.
 */
@Composable
private fun ArtworkBackdrop(tint: ArtworkTint, modifier: Modifier, flat: Boolean) {
    val glow = lerp(tint.background, tint.content, if (tint.isDark) 0.1f else 0.06f)
    Canvas(modifier.background(tint.background)) {
        if (flat) return@Canvas
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.1f),
                radius = size.maxDimension * 0.7f,
            ),
        )
    }
}
