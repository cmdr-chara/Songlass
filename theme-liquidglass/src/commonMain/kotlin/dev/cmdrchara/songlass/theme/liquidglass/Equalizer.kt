package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.EqBand
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlin.math.abs
import kotlin.math.ln

/**
 * Graphic equalizer as one picture: the response curve over a decibel grid, a point on each band
 * that follows the finger up and down (relative to where the drag starts, so touching never makes
 * a band jump), and a label with the value while dragging. Every band is also an adjustable
 * element for screen readers.
 */
@Composable
internal fun LiquidGlassEqualizerEditor(
    bands: List<EqBand>,
    curve: List<Float>,
    maxGainDb: Float,
    enabled: Boolean,
    locked: Boolean,
    formatGain: (Float) -> String,
    onBandChange: (Int, Float) -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val look = LocalGlassLook.current
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val measurer = rememberTextMeasurer()
    var active by remember { mutableStateOf<Int?>(null) }
    val currentBands by rememberUpdatedState(bands)
    val currentOnChange by rememberUpdatedState(onBandChange)
    // Presets glide into place; a dragged band follows the finger exactly.
    val spec = if (reduceMotion || active != null) snap<Float>() else spring(dampingRatio = 0.8f, stiffness = 300f)
    val shownCurve = curve.map { animateFloatAsState(it, spec, label = "eqCurve").value }
    val shownGains = bands.map { animateFloatAsState(it.gainDb, spec, label = "eqBand").value }
    val range = maxGainDb + 3f
    val emphasis = if (enabled) 1f else 0.4f
    val accent = colors.accent
    val ink = colors.label
    val labelStyle = TextStyle(fontSize = 11.sp, color = colors.secondaryLabel)
    val bubbleStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.background)
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val labels = bands.map { measurer.measure(it.label, labelStyle) }
    val fractions = bands.map { (ln(it.frequencyHz / 20f) / ln(1000f)).coerceIn(0f, 1f) }
    val leftInset = with(density) {
        maxOf(GRAPH_SIDE.toPx(), (labels.firstOrNull()?.size?.width ?: 0) / 2f + 4.dp.toPx())
    }
    val rightInset = with(density) {
        maxOf((GRAPH_SIDE + 18.dp).toPx(), (labels.lastOrNull()?.size?.width ?: 0) / 2f + 4.dp.toPx())
    }
    val bottomInset = with(density) {
        maxOf(30.dp.toPx(), (labels.maxOfOrNull { it.size.height } ?: 0) + 16.dp.toPx())
    }
    val labelGap = with(density) { 8.dp.toPx() }
    val minimumPlotWidth = bands.indices.drop(1).maxOfOrNull { i ->
        val separation = fractions[i] - fractions[i - 1]
        if (separation > 0f) {
            ((labels[i - 1].size.width + labels[i].size.width) / 2f + labelGap) / separation
        } else 0f
    } ?: 0f

    BoxWithConstraints(
        look.controlSurface(modifier.fillMaxWidth().height(260.dp), RoundedCornerShape(look.corner(20.dp)), colors.secondaryBackground, prominent = false),
    ) {
        // Keep every measured frequency label readable at large text sizes. The
        // complete graph scrolls together, retaining its logarithmic positions.
        val graphWidth = maxOf(maxWidth, with(density) { (minimumPlotWidth + leftInset + rightInset).toDp() })
        Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
            Canvas(
                Modifier
                    .width(graphWidth).fillMaxHeight()
                    .then(
                        // Locked: the curve stays readable but stops intercepting touches, so a
                        // vertical scroll over the graph scrolls the page instead of moving a band.
                        if (locked) Modifier else Modifier.pointerInput(bands.size, leftInset, rightInset, bottomInset) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val graph = graphArea(size.width.toFloat(), size.height.toFloat(), leftInset, rightInset, bottomInset)
                            val band = currentBands.indices.minByOrNull { abs(graph.x(currentBands[it].frequencyHz) - down.position.x) } ?: return@awaitEachGesture
                            val startGain = currentBands[band].gainDb
                            val startY = down.position.y
                            val pxPerDb = graph.height / (2f * range)
                            // Only a real vertical drag moves a band: a tap or a wobble leaves it alone.
                            val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                            active = band
                            currentOnChange(band, (startGain - (slop.position.y - startY) / pxPerDb).coerceIn(-maxGainDb, maxGainDb))
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (change.positionChange() != Offset.Zero) {
                                    val gain = (startGain - (change.position.y - startY) / pxPerDb).coerceIn(-maxGainDb, maxGainDb)
                                    currentOnChange(band, gain)
                                    change.consume()
                                }
                            }
                            active = null
                        }
                        }
                    ),
            ) {
                val graph = graphArea(size.width, size.height, leftInset, rightInset, bottomInset)
                fun y(db: Float) = graph.top + graph.height / 2f - db / range * (graph.height / 2f)

                // Decibel grid: 0 stronger, ±6 and ±maxGain faint, with their values on the right.
                for (db in listOf(-maxGainDb, -6f, 6f, maxGainDb, 0f)) {
                    val strong = db == 0f
                    drawLine(ink.copy(alpha = if (strong) 0.28f else 0.1f), Offset(graph.left, y(db)), Offset(graph.right, y(db)), strokeWidth = (if (strong) 1.5f else 1f).dp.toPx())
                    val text = measurer.measure(if (db > 0) "+${db.toInt()}" else "${db.toInt()}", labelStyle)
                    drawText(text, topLeft = Offset(size.width - text.size.width - 6.dp.toPx(), y(db) - text.size.height - 1.dp.toPx()))
                }

                // Response curve with a soft fill towards 0 dB.
                if (shownCurve.size > 1) {
                    val step = graph.width / (shownCurve.size - 1)
                    val line = Path().apply {
                        shownCurve.forEachIndexed { i, db ->
                            val p = Offset(graph.left + i * step, y(db.coerceIn(-range, range)))
                            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                        }
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(graph.right, y(0f))
                        lineTo(graph.left, y(0f))
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f * emphasis), accent.copy(alpha = 0.04f * emphasis), accent.copy(alpha = 0.28f * emphasis)), graph.top, graph.bottom))
                    drawPath(line, accent.copy(alpha = emphasis), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // Band points and frequency labels.
                bands.forEachIndexed { i, band ->
                    val x = graph.x(band.frequencyHz)
                    val center = Offset(x, y(shownGains[i]))
                    val dragging = active == i
                    if (dragging) drawLine(accent.copy(alpha = 0.35f), Offset(x, graph.top), Offset(x, graph.bottom), strokeWidth = 1.dp.toPx())
                    drawCircle(colors.secondaryBackground, (if (dragging) 11 else 8).dp.toPx(), center)
                    drawCircle(accent.copy(alpha = emphasis), (if (dragging) 11 else 8).dp.toPx(), center, style = if (dragging) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.5.dp.toPx()))
                    val label = labels[i]
                    drawText(label, topLeft = Offset(x - label.size.width / 2f, graph.bottom + 8.dp.toPx()))
                }

                // Value bubble above the dragged band.
                active?.let { i -> drawValueBubble(measurer.measure("${formatGain(bands[i].gainDb)} · ${bands[i].label} Hz", bubbleStyle), graph.x(bands[i].frequencyHz), accent) }
            }

            // Screen readers: one adjustable element per band, laid over the picture.
            Row(Modifier.width(graphWidth).fillMaxHeight().padding(horizontal = GRAPH_SIDE)) {
                bands.forEachIndexed { i, band ->
                    Box(
                        Modifier.weight(1f).fillMaxSize().semantics {
                            contentDescription = "${band.label} Hz"
                            stateDescription = formatGain(band.gainDb)
                            if (locked) {
                                disabled()
                            } else {
                                progressBarRangeInfo = ProgressBarRangeInfo(band.gainDb, -maxGainDb..maxGainDb, steps = (maxGainDb * 4).toInt() - 1)
                                setProgress { value -> currentOnChange(i, value.coerceIn(-maxGainDb, maxGainDb)); true }
                            }
                        },
                    )
                }
            }
        }
    }
}

private val GRAPH_SIDE = 16.dp

private class GraphArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    /** Log-scale position of [frequencyHz] between 20 Hz and 20 kHz. */
    fun x(frequencyHz: Float): Float = left + width * (ln(frequencyHz / 20f) / ln(1000f)).coerceIn(0f, 1f)
}

private fun androidx.compose.ui.unit.Density.graphArea(
    width: Float,
    height: Float,
    leftInset: Float,
    rightInset: Float,
    bottomInset: Float,
) = GraphArea(
    left = leftInset,
    top = 40.dp.toPx(),
    right = width - rightInset,
    bottom = height - bottomInset,
)

private fun DrawScope.drawValueBubble(text: androidx.compose.ui.text.TextLayoutResult, x: Float, color: androidx.compose.ui.graphics.Color) {
    val padH = 10.dp.toPx()
    val padV = 5.dp.toPx()
    val w = text.size.width + padH * 2
    val h = text.size.height + padV * 2
    val left = (x - w / 2f).coerceIn(4.dp.toPx(), size.width - w - 4.dp.toPx())
    val top = 6.dp.toPx()
    drawRoundRect(color, Offset(left, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f))
    drawText(text, topLeft = Offset(left + padH, top + padV))
}
