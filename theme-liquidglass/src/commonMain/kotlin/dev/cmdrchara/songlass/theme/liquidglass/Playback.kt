package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Playback progress: thin track that thickens while dragging, elapsed and remaining time below. */
@Composable
internal fun LiquidGlassSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier) {
    val content = LocalContentColor.current
    val currentOnSeek by rememberUpdatedState(onSeek)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val playedFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction = dragFraction ?: playedFraction
    val active = dragFraction != null
    val trackHeight by animateDpAsState(if (active) 12.dp else 7.dp)
    val shownPosition = (fraction * durationMs).toLong()

    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { value -> currentOnSeek((value * durationMs).toLong()); true }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        currentOnSeek(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { currentOnSeek((it * durationMs).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(content.copy(alpha = if (active) 1f else 0.8f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val timeStyle = AppTheme.typography.caption1
            AppText(formatDuration(shownPosition), timeStyle, color = content.copy(alpha = 0.6f))
            Spacer(Modifier.weight(1f))
            AppText("-" + formatDuration(durationMs - shownPosition), timeStyle, color = content.copy(alpha = 0.6f))
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
        LiquidGlassIconButton(AppIcons.Previous, labels.previous, onPrevious, false, Modifier, iconSize = 34.dp, hitSize = 64.dp)
        LiquidGlassIconButton(
            icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (isPlaying) labels.pause else labels.play,
            onClick = onPlayPause,
            selected = false,
            modifier = Modifier,
            iconSize = 46.dp,
            hitSize = 80.dp,
        )
        LiquidGlassIconButton(AppIcons.Next, labels.next, onNext, false, Modifier, iconSize = 34.dp, hitSize = 64.dp)
    }
}

/**
 * Full-screen Now Playing sheet on a backdrop derived from the artwork colors. Content is
 * white; the backdrop is darkened so white text keeps enough contrast.
 */
@Composable
internal fun LiquidGlassNowPlayingSheet(
    visible: Boolean,
    artwork: Artwork?,
    dismissLabel: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val scope = rememberCoroutineScope()
        val offset = remember { Animatable(0f) }
        val dismissThreshold = with(LocalDensity.current) { 140.dp.toPx() }
        LaunchedEffect(Unit) { offset.snapTo(0f) }

        val base = artwork?.colors?.ifEmpty { null } ?: listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E))
        val backdrop = Brush.verticalGradient(base.map { lerp(it, Color.Black, 0.45f) } + lerp(base.last(), Color.Black, 0.7f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .background(backdrop)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
                    },
                    onDragStopped = { velocity ->
                        if (offset.value > dismissThreshold || velocity > 2000f) onDismiss() else offset.animateTo(0f)
                    },
                ),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    // Grabber: tap or swipe down to dismiss (HIG: include a grabber, support swipe to dismiss).
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 64.dp, height = 28.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                            .semantics { onClick(label = dismissLabel) { onDismiss(); true } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
                    }
                    content()
                }
            }
        }
    }
}
