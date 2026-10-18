package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import kotlin.math.PI
import kotlin.math.sin

/**
 * Expressive seek bar: the played part is a wave that flows while music plays and flattens into
 * a line when paused or while seeking; a gap separates it from the remaining track and a tall
 * pill thumb marks the position. Reduce Motion keeps a still, flat line.
 */
@Composable
internal fun WavySeekBar(
    positionMs: Long,
    durationMs: Long,
    qualityLabel: String?,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val content = LocalContentColor.current
    val reduceMotion = AppTheme.accessibility.reduceMotion
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

    val waving = isPlaying && dragFraction == null && !reduceMotion
    val amplitude by animateFloatAsState(
        if (waving) 1f else 0f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "waveAmplitude",
    )
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val timeStyle = AppTheme.typography.footnote.copy(fontWeight = FontWeight.Medium)
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
                    if (duration == 0L) disabled() else setProgress { value -> seek(value) }
                }
                .pointerInput(duration) {
                    detectTapGestures { offset -> if (size.width > 0) seek(offset.x / size.width) }
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
            val stroke = 5.dp.toPx()
            val y = size.height / 2f
            val dragging = dragFraction != null
            val thumbWidth = (if (dragging) 7.dp else 5.dp).toPx()
            val thumbHeight = (if (dragging) 30.dp else 24.dp).toPx()
            val gap = 5.dp.toPx()
            val x = (size.width * fraction).coerceIn(thumbWidth / 2f, maxOf(thumbWidth / 2f, size.width - thumbWidth / 2f))

            // Remaining track: a flat line after the thumb.
            val restStart = x + thumbWidth / 2f + gap
            if (restStart < size.width - stroke / 2f) {
                drawLine(content.copy(alpha = 0.22f), Offset(restStart, y), Offset(size.width - stroke / 2f, y), stroke, StrokeCap.Round)
            }
            // Played part: a sine wave of fixed wavelength, flowing to the right.
            val playedEnd = x - thumbWidth / 2f - gap
            if (playedEnd > stroke / 2f) {
                val wave = Path()
                val wavelength = 30.dp.toPx()
                val height = 3.5.dp.toPx() * amplitude
                val step = 2f
                var px = stroke / 2f
                wave.moveTo(px, y + height * sin(px / wavelength * 2f * PI.toFloat() - phase))
                while (px < playedEnd) {
                    px = minOf(px + step, playedEnd)
                    wave.lineTo(px, y + height * sin(px / wavelength * 2f * PI.toFloat() - phase))
                }
                drawPath(wave, content.copy(alpha = 0.92f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            drawRoundRect(content, Offset(x - thumbWidth / 2f, y - thumbHeight / 2f), Size(thumbWidth, thumbHeight), CornerRadius(thumbWidth / 2f))
        }
    }
}

private const val WAVE_PERIOD_MS = 1600
