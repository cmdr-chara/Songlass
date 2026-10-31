package dev.cmdrchara.songlass.theme.liquidglass

import kotlin.math.abs
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import dev.cmdrchara.songlass.designsystem.foundation.playerArtworkFrame
import dev.cmdrchara.songlass.designsystem.foundation.squarePlayerArtworkFrame
import dev.cmdrchara.songlass.designsystem.foundation.PlayerArtworkTransition
import dev.cmdrchara.songlass.designsystem.foundation.LocalPlayerArtworkTransition
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.util.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.LocalWindowLayout
import dev.cmdrchara.songlass.designsystem.WindowLayout
import dev.cmdrchara.songlass.designsystem.WindowPoint
import dev.cmdrchara.songlass.designsystem.WindowRect
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

/** Thin progress rail above balanced timestamps and source quality, with a full 44 dp seek target. */
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

    val timeStyle = AppTheme.typography.footnote.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    val secondary = content.copy(alpha = 0.72f)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val quality = qualityLabel?.takeIf { it.isNotBlank() }
    val elapsedText = formatDuration(shownPosition)
    val remainingText = "-" + formatDuration(duration - shownPosition)
    val textWidths = remember(elapsedText, remainingText, quality, timeStyle, textMeasurer, density) {
        // Equal timestamp columns keep the quality centered as the preview changes.
        val time = maxOf(textMeasurer.measure(elapsedText, timeStyle).size.width,
            textMeasurer.measure(remainingText, timeStyle).size.width)
        val qualityWidth = textMeasurer.measure(quality.orEmpty(), timeStyle).size.width
        with(density) { maxOf(48.dp, time.toDp()) to qualityWidth.toDp() }
    }
    Column(modifier.fillMaxWidth()) {
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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val timeWidth = textWidths.first.coerceAtMost(maxWidth / 2)
            val stacked = quality != null &&
                (density.fontScale > 1.3f || timeWidth * 2 + textWidths.second + 16.dp > maxWidth)
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText(elapsedText, timeStyle, Modifier.width(timeWidth), color = secondary, maxLines = 1)
                    if (quality == null || stacked) Spacer(Modifier.weight(1f))
                    else AppText(quality, timeStyle, Modifier.weight(1f).padding(horizontal = 8.dp),
                        color = secondary, textAlign = TextAlign.Center, maxLines = 1)
                    AppText(remainingText, timeStyle, Modifier.width(timeWidth),
                        color = secondary, textAlign = TextAlign.End, maxLines = 1)
                }
                if (stacked) AppText(quality.orEmpty(), timeStyle, Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = secondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Volume as in Apple Music: speakers at both ends of a thin rail that thickens while dragged.
 * Tapping anywhere jumps there; accessibility can set it directly.
 */
/**
 * iOS 26 volume slider: a thin rail between a quiet and a loud speaker that thickens under the
 * finger. Dragging is relative (touching never makes the volume jump); pulled past either end the
 * rail stretches like rubber and the speaker on that side swells, then both spring back on
 * release. Reduce Motion keeps the rail still.
 */
@Composable
internal fun LiquidGlassVolumeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier, showSpeakers: Boolean = true) {
    val content = LocalContentColor.current
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    // Rubber band past the ends in px: negative before the start, positive after the end.
    val stretch = remember { Animatable(0f) }
    val density = LocalDensity.current
    val maxStretch = with(density) { 16.dp.toPx() }
    val railHeight by animateFloatAsState(
        if (dragging && !reduceMotion) 12f else 6f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "volumeRail",
    )
    fun swell(side: Float) = 1f + 0.22f * (side / maxStretch).coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showSpeakers) AppIcon(
            AppIcons.SpeakerLow, contentDescription = null, size = 18.dp, tint = content.copy(alpha = 0.72f),
            modifier = Modifier.graphicsLayer {
                val s = swell(-stretch.value)
                scaleX = s; scaleY = s
                translationX = minOf(stretch.value, 0f) * 0.3f
            },
        )
        Canvas(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                    setProgress { v -> currentOnChange(v.coerceIn(0f, 1f)); true }
                }
                .pointerInput(reduceMotion) {
                    var startValue = 0f
                    var travelled = 0f
                    fun release() {
                        dragging = false
                        scope.launch { stretch.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            startValue = currentValue
                            travelled = 0f
                        },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, amount ->
                        change.consume()
                        if (size.width <= 0) return@detectHorizontalDragGestures
                        travelled += amount
                        val raw = startValue + travelled / size.width
                        currentOnChange(raw.coerceIn(0f, 1f))
                        if (!reduceMotion) {
                            val over = when {
                                raw < 0f -> raw * size.width
                                raw > 1f -> (raw - 1f) * size.width
                                else -> 0f
                            }
                            scope.launch { stretch.snapTo(rubberBand(over, maxStretch)) }
                        }
                    }
                },
        ) {
            val s = stretch.value
            // Stretched, the rail also thins a little, like something elastic being pulled.
            val h = railHeight.dp.toPx() * (1f - 0.3f * (abs(s) / maxStretch).coerceIn(0f, 1f))
            val left = minOf(s, 0f)
            val width = size.width + abs(s)
            val y = size.height / 2f - h / 2f
            drawRoundRect(content.copy(alpha = 0.25f), Offset(left, y), Size(width, h), CornerRadius(h / 2f))
            drawRoundRect(content.copy(alpha = 0.85f), Offset(left, y), Size(width * value.coerceIn(0f, 1f), h), CornerRadius(h / 2f))
        }
        if (showSpeakers) AppIcon(
            AppIcons.AudioOutput, contentDescription = null, size = 18.dp, tint = content.copy(alpha = 0.72f),
            modifier = Modifier.graphicsLayer {
                val s = swell(stretch.value)
                scaleX = s; scaleY = s
                translationX = maxOf(stretch.value, 0f) * 0.3f
            },
        )
    }
}

/** Resistance past the end of a control: grows quickly at first, then never beyond [max]. */
private fun rubberBand(over: Float, max: Float): Float {
    if (over == 0f) return 0f
    val magnitude = max * (1f - 1f / (abs(over) / max * 0.55f + 1f))
    return if (over < 0f) -magnitude else magnitude
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
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = ((maxWidth - 172.dp) / 2).coerceIn(0.dp, 12.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidTransportButton(AppIcons.Previous, labels.previous, onPrevious, 30.dp, 52.dp)
            LiquidTransportButton(
                icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                description = if (isPlaying) labels.pause else labels.play,
                onClick = onPlayPause,
                iconSize = 40.dp,
                hitSize = 68.dp,
            )
            LiquidTransportButton(AppIcons.Next, labels.next, onNext, 30.dp, 52.dp)
        }
    }
}

/** Quiet ink feedback moves only the drawing, leaving each transport target stable. */
@Composable
private fun LiquidTransportButton(icon: ImageVector, description: String, onClick: () -> Unit, iconSize: Dp, hitSize: Dp) {
    val content = LocalContentColor.current
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.96f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = 700f),
        label = "playerTransportPress",
    )
    Box(
        Modifier.size(hitSize)
            .liquidFocusIndicator(interaction, CircleShape)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape).background(content.copy(alpha = if (pressed) 0.08f else 0f)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, null, size = iconSize, tint = content)
        }
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
    onStatusBarAppearanceChange: (Boolean?) -> Unit,
    content: @Composable (onArtworkTap: () -> Unit) -> Unit,
) {
    val morph = LocalPlayerMorph.current
    val reduceMotion = AppTheme.accessibility.reduceMotion
    var artworkTapRequest by remember(artwork, visible, artworkVisible, artworkExpanded, reduceMotion) { mutableStateOf(0) }
    val onArtworkTap: () -> Unit = {
        if (visible && artworkVisible && !artworkExpanded && !reduceMotion) artworkTapRequest += 1
    }
    // 0 = closed (the mini player), 1 = full screen. Springs keep a reopen during closing smooth.
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnStatusBarAppearanceChange by rememberUpdatedState(onStatusBarAppearanceChange)
    val look = LocalGlassLook.current
    val fullBleed = LocalArtworkPresentation.current == ArtworkPresentation.FullBleed
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowLayout = LocalWindowLayout.current
    val adaptiveWindow = windowLayout.requiresAdaptivePlayer
    var sourceAnchor by remember { mutableStateOf<Rect?>(null) }
    var thumbAnchor by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var headerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var headerPane by remember { mutableStateOf<WindowRect?>(null) }
    val headerGeometrySource = remember { arrayOfNulls<WindowLayout>(1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val dismissThreshold = with(LocalDensity.current) { 140.dp.toPx() }
    val dismissVelocity = with(LocalDensity.current) { 1000.dp.toPx() }

    fun updateHeaderPane(coordinates: LayoutCoordinates) {
        if (!adaptiveWindow) return
        val local = windowLayout.inLocalCoordinates(coordinates.size.width / density.density,
            coordinates.size.height / density.density) { x, y ->
            val point = coordinates.windowToLocal(Offset(x * density.density, y * density.density))
            WindowPoint(point.x / density.density, point.y / density.density)
        }
        headerPane = local.panes(local.bounds, paired = false)?.first
    }
    if (adaptiveWindow) SideEffect {
        if (headerGeometrySource[0] != windowLayout) {
            headerGeometrySource[0] = windowLayout
            headerCoordinates?.takeIf { it.isAttached }?.let(::updateHeaderPane)
        }
    }

    fun dragWindowScale(): Float {
        val coordinates = headerCoordinates?.takeIf { it.isAttached } ?: return 1f
        val scale = abs(coordinates.localToWindow(Offset(0f, 1f)).y - coordinates.localToWindow(Offset.Zero).y)
        return scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    }

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
    LaunchedEffect(visible, reduceMotion, fullBleed, artwork) {
        // Freeze the resting anchors before the page below starts receding. Measuring that
        // transformed page during the animation otherwise makes the destination move away.
        if (visible && progress.value == 0f) {
            sourceAnchor = morph.source
            thumbAnchor = morph.thumb
        }
        val target = if (visible) 1f else 0f
        when {
            reduceMotion -> progress.animateTo(target, tween(LiquidGlassMotion.ReducedMillis))
            visible -> progress.animateTo(target, look.playerOpenMotion)
            else -> progress.animateTo(target, look.playerCloseMotion)
        }
        if (!visible) {
            dragOffset = 0f
        }
    }
    val screenHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val statusBarHeight = WindowInsets.statusBars.getTop(density).toFloat()
    val darkAppearance = AppTheme.colors.isDark
    val fallbackTop = screenHeight - with(density) { 120.dp.toPx() }
    // One fraction drives the page, sheet, and artwork in both directions. During a drag the
    // top of the interpolated sheet follows the finger, then continues from that exact position.
    val fraction: () -> Float = {
        val travel = ((sourceAnchor?.top ?: fallbackTop) - origin.y).coerceAtLeast(1f)
        val value = progress.value.coerceIn(0f, 1f) * if (reduceMotion) 1f else (1f - dragOffset / travel).coerceIn(0f, 1f)
        // Once less than a pixel remains, hand off now instead of waiting for the spring tail.
        if (!reduceMotion && value * travel < 1f) 0f else value
    }
    val squareImageInFlight = remember(fullBleed, reduceMotion, artwork != null) {
        derivedStateOf { !fullBleed && !reduceMotion && artwork != null && fraction() > 0f && fraction() < 1f }
    }
    val squareTransition = remember(squareImageInFlight) { PlayerArtworkTransition(squareImageInFlight) }
    val artworkOwner = remember(fullBleed, reduceMotion, artwork != null, fallbackTop, squareTransition) {
        derivedStateOf {
            !reduceMotion && artwork != null && fraction() > 0f &&
                (fullBleed || squareTransition.target() != null)
        }
    }
    DisposableEffect(morph, artworkOwner) {
        morph.artworkOwner = artworkOwner
        onDispose { morph.artworkOwner = null }
    }
    fun localThumbnail(size: Size): Rect {
        thumbAnchor?.let { return it.translate(-origin.x, -origin.y) }
        val source = sourceAnchor?.translate(-origin.x, -origin.y)
        val inset = with(density) { 6.dp.toPx() }
        val side = ((source?.height ?: with(density) { 56.dp.toPx() }) - inset * 2f).coerceAtLeast(1f)
        val left = (source?.left ?: with(density) { 16.dp.toPx() }) + inset
        val top = (source?.top ?: (size.height - with(density) { 120.dp.toPx() })) + inset
        return Rect(left, top, left + side, top + side)
    }
    fun artworkFrame(size: Size, t: Float): dev.cmdrchara.songlass.designsystem.foundation.PlayerArtworkFrame {
        val target = if (fullBleed) null else squareTransition.target()
        return if (target != null) squarePlayerArtworkFrame(localThumbnail(size), size, target.bounds, t)
        else playerArtworkFrame(localThumbnail(size), size, t)
    }
    LaunchedEffect(reduceMotion, screenHeight, statusBarHeight, fullBleed, artworkVisible, artworkExpanded, darkAppearance) {
        snapshotFlow {
            val t = fraction()
            val top = ((sourceAnchor?.top ?: fallbackTop) - origin.y) * (1f - t)
            // The receding page leaves a black gap above it. Status icons must stay white
            // over that gap even after the sheet itself has moved below the status row.
            val pageTop = screenHeight * 0.04f * t
            val sheetCoversStatus = if (reduceMotion) t > 0.5f else t > 0f && top <= statusBarHeight * 0.25f
            val lightIcons = when {
                sheetCoversStatus -> if (fullBleed && artworkVisible && !artworkExpanded) true else darkAppearance
                !reduceMotion && pageTop > statusBarHeight * 0.5f -> true
                else -> null // The uncovered route owns its system chrome again.
            }
            t to lightIcons
        }.collect { (t, lightIcons) ->
            morph.recede = t
            currentOnStatusBarAppearanceChange(lightIcons)
        }
    }
    DisposableEffect(morph) { onDispose { morph.recede = 0f; currentOnStatusBarAppearanceChange(null) } }

    // Keep the original full-bleed cover and fade, with controls in the selected appearance.
    // Only header chrome adapts to the image behind it; it must not recolor the whole player.
    val hero = artworkVisible && fullBleed
    val tint = animateArtworkTint(look.playerTint(rememberArtworkTint(artwork, AppTheme.colors.isDark)))
    val headerTint = rememberArtworkTint(artwork, dark = true)
    // Animation frames belong to layout/drawing. Composition only needs to know when
    // the outgoing sheet has fully left, not every intermediate spring value.
    val sheetPresent by remember(visible) { derivedStateOf { visible || progress.value > 0f } }
    if (sheetPresent) {
        DisposableEffect(squareTransition) { onDispose { squareTransition.clearContainer() } }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInWindow() }
                .graphicsLayer {
                    val p = fraction()
                    // Both layers read the same derived ownership in the drawing phase:
                    // no coroutine completion or recomposition gap at the thumbnail hand-off.
                    // Lyrics/Queue can open before an Artwork page has supplied a Square
                    // target. Only that page waits for the single-cover hand-off.
                    alpha = if (artworkVisible && artwork != null && !reduceMotion && !morph.artworkInFlight) 0f else 1f
                    if (reduceMotion) {
                        alpha = p.coerceIn(0f, 1f)
                        return@graphicsLayer
                    }
                    val fallback = with(density) { Rect(16.dp.toPx(), size.height - 120.dp.toPx(), size.width - 16.dp.toPx(), size.height - 64.dp.toPx()) }
                    val source = sourceAnchor?.translate(-origin.x, -origin.y) ?: fallback
                    val t = p.coerceIn(0f, 1f)
                    val bounds = artworkFrame(size, t).surface
                    val thumb = localThumbnail(size)
                    val outline = look.playerThumbnailShape().createOutline(thumb.size, layoutDirection, density) as? Outline.Rounded
                    val rounded = outline?.roundRect
                    fun corner(c: CornerRadius) = CornerRadius(c.x * (1f - t), c.y * (1f - t))
                    val radius = lerp(source.height / 2f, with(density) { 28.dp.toPx() }, t) * (1f - smoothstep(0.85f, 1f, t))
                    clip = true
                    shape = GenericShape { _, _ ->
                        addRoundRect(if (rounded != null) RoundRect(
                            bounds.left, bounds.top, bounds.right, bounds.bottom,
                            corner(rounded.topLeftCornerRadius), corner(rounded.topRightCornerRadius),
                            corner(rounded.bottomRightCornerRadius), corner(rounded.bottomLeftCornerRadius),
                        ) else RoundRect(bounds, CornerRadius(radius)))
                    }
                },
        ) {
            ArtworkBackdrop(
                tint,
                Modifier.fillMaxSize()
                    // At the very start and end the real mini player shows through, so the hand-off
                    // between it and the growing player is seamless.
                    .graphicsLayer { if (!reduceMotion) alpha = smoothstep(0.02f, 0.25f, fraction()) }
                    .pointerInput(Unit) {
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
            if (fullBleed && artwork != null) ProvideArtworkTint(tint) {
                // Remeasure only the image, not the player controls. Crop then changes continuously
                // from the square thumbnail to the tall hero; scaling an already cropped hero
                // would land on a different part of the album cover at the miniature end.
                Layout(
                    modifier = Modifier.fillMaxSize(),
                    content = {
                        PlayerArtworkHero(
                            artwork = artwork,
                            surface = tint.background,
                            label = artworkLabel,
                            expandLabel = expandArtworkLabel,
                            tapRequest = artworkTapRequest,
                            onTap = onArtworkTap,
                            onExpand = { if (visible) onArtworkExpandedChange(true) },
                            transitionProgress = { if (reduceMotion) 1f else smoothstep(0.6f, 1f, fraction()) },
                            interactive = visible && artworkVisible && !artworkExpanded,
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    // The cover takes over as queue/lyrics controls leave the card.
                                    alpha = if (artworkVisible) 1f else if (reduceMotion) 0f else 1f - smoothstep(0.55f, 0.95f, fraction())
                                    if (reduceMotion) return@graphicsLayer
                                    val thumb = thumbAnchor ?: return@graphicsLayer
                                    val outline = look.playerThumbnailShape().createOutline(thumb.size, layoutDirection, density) as? Outline.Rounded
                                        ?: return@graphicsLayer
                                    val round = outline.roundRect
                                    val remaining = 1f - fraction()
                                    fun corner(c: CornerRadius) = CornerRadius(c.x * remaining, c.y * remaining)
                                    clip = true
                                    shape = GenericShape { _, _ ->
                                        addRoundRect(RoundRect(
                                            0f, 0f, size.width, size.height,
                                            corner(round.topLeftCornerRadius), corner(round.topRightCornerRadius),
                                            corner(round.bottomRightCornerRadius), corner(round.bottomLeftCornerRadius),
                                        ))
                                    }
                                }
                                .then(if (artworkExpanded || !artworkVisible) Modifier.clearAndSetSemantics {} else Modifier),
                        )
                    },
                ) { measurables, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val full = Rect(0f, 0f, width.toFloat(), height * 0.64f)
                    val bounds = if (reduceMotion) full else playerArtworkFrame(
                        localThumbnail(Size(width.toFloat(), height.toFloat())),
                        Size(width.toFloat(), height.toFloat()), fraction(),
                    ).cover
                    val cover = measurables.single().measure(Constraints.fixed(
                        bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1),
                    ))
                    layout(width, height) { cover.place(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                }
            }
            if (!fullBleed && artwork != null && !reduceMotion) {
                Layout(
                    modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                    content = {
                        PlayerSquareArtwork(artwork, Modifier.fillMaxSize().graphicsLayer {
                            alpha = if (squareImageInFlight.value && squareTransition.target() != null) {
                                if (artworkVisible) 1f else 1f - smoothstep(0.55f, 0.95f, fraction())
                            } else 0f
                            val t = fraction()
                            val thumb = localThumbnail(size)
                            val round = (look.playerThumbnailShape().createOutline(
                                thumb.size, layoutDirection, density,
                            ) as? Outline.Rounded)?.roundRect
                            val targetRadius = squareTransition.target()?.cornerRadius ?: 0f
                            fun corner(c: CornerRadius?) = CornerRadius(
                                lerp(c?.x ?: 0f, targetRadius, t),
                                lerp(c?.y ?: 0f, targetRadius, t),
                            )
                            clip = true
                            shape = GenericShape { _, _ -> addRoundRect(RoundRect(
                                0f, 0f, size.width, size.height,
                                corner(round?.topLeftCornerRadius), corner(round?.topRightCornerRadius),
                                corner(round?.bottomRightCornerRadius), corner(round?.bottomLeftCornerRadius),
                            )) }
                        })
                    },
                ) { measurables, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val bounds = artworkFrame(Size(width.toFloat(), height.toFloat()), fraction()).cover
                    val cover = measurables.single().measure(Constraints.fixed(
                        bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1),
                    ))
                    layout(width, height) { cover.place(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                }
            }
            look.PlayerContent(tint) {
                Column(
                    Modifier.fillMaxSize()
                        // Controls come in once the player has mostly grown, and leave first.
                        .graphicsLayer {
                            if (!reduceMotion) {
                                val t = fraction()
                                alpha = smoothstep(0.6f, 0.95f, t)
                                if (adaptiveWindow) {
                                    // Hardware regions are fixed in window space. Fade controls
                                    // at their final geometry instead of scaling their fold gap.
                                    scaleX = 1f
                                    scaleY = 1f
                                    translationX = 0f
                                    translationY = 0f
                                    return@graphicsLayer
                                }
                                val artworkFrame = artworkFrame(size, t)
                                val frame = artworkFrame.surface
                                // Let controls travel with the surface instead of being revealed
                                // by a moving crop. Their layout and hit targets remain unchanged.
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = frame.width / size.width.coerceAtLeast(1f)
                                // A zero scale has no inverse: pointer coordinates become NaN
                                // while the gesture still owns this invisible controls layer.
                                scaleY = if (fullBleed) (scaleX * artworkFrame.bodyReveal).coerceAtLeast(0.001f)
                                    else scaleX.coerceAtLeast(0.001f)
                                translationX = frame.left
                                val coverBottom = if (fullBleed) size.height * 0.64f
                                    else squareTransition.target()?.bounds?.bottom ?: size.height * 0.64f
                                // Anchor both layouts' controls beneath the flying cover. Compressing
                                // the whole Square page into the card exposed a tiny footer too early.
                                translationY = artworkFrame.cover.bottom - coverBottom * scaleY
                            }
                        }
                        .onGloballyPositioned(squareTransition::placeContainer)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .then(if (artworkExpanded) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    // Only the header drags the sheet, leaving queue scrolling, reordering
                    // and seeking free to handle their own gestures.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .onGloballyPositioned {
                                headerCoordinates = it
                                updateHeaderPane(it)
                            }
                            .draggable(
                                orientation = Orientation.Vertical,
                                enabled = visible && !artworkExpanded,
                                startDragImmediately = settling,
                                state = rememberDraggableState { delta ->
                                    // Input is local to the shrinking controls layer. Convert back
                                    // to window pixels so a drag never accelerates as it shrinks.
                                    val windowDelta = delta * dragWindowScale()
                                    if (windowDelta.isFinite()) dragOffset = (dragOffset + windowDelta).coerceAtLeast(0f)
                                },
                                onDragStarted = {
                                    settleJob?.cancel()
                                    settling = false
                                },
                                onDragStopped = { velocity ->
                                    val windowVelocity = (velocity * dragWindowScale()).takeIf { it.isFinite() } ?: 0f
                                    if (dragOffset > dismissThreshold || windowVelocity > dismissVelocity) {
                                        currentOnDismiss()
                                    } else {
                                        settleToOrigin(windowVelocity)
                                    }
                                },
                            ),
                        contentAlignment = if (hero) Alignment.CenterStart else Alignment.Center,
                    ) {
                        val safeHeader = if (adaptiveWindow) headerPane else null
                        if (!adaptiveWindow || safeHeader != null) Box(
                            modifier = if (safeHeader == null) Modifier else Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset { androidx.compose.ui.unit.IntOffset(safeHeader.left.dp.roundToPx(), safeHeader.top.dp.roundToPx()) }
                                .size(safeHeader.width.dp, safeHeader.height.dp),
                            contentAlignment = if (hero) Alignment.CenterStart else Alignment.Center,
                        ) {
                        if (hero) ProvideArtworkTint(headerTint) {
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
                    }
                    CompositionLocalProvider(LocalPlayerArtworkTransition provides squareTransition) {
                        content(onArtworkTap)
                    }
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

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
