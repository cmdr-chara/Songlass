package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.ListeningChartPoint
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import androidx.compose.ui.unit.sp

/**
 * How a look draws the listening chart. Colours left null follow the accent; a look with its
 * own tonal roles (Material) supplies them.
 */
@Immutable
data class ListeningPlotStyle(
    val bars: Boolean,
    /** Corner of each bar as a fraction of its width (0.5 = pill). */
    val barRoundness: Float = 0.5f,
    val dashedGrid: Boolean = true,
    /** The selected value above its date, as large figures in HyperOS summaries. */
    val valueFirst: Boolean = false,
    val bar: Color? = null,
    val selectedBar: Color? = null,
    val container: Color? = null,
    val corner: Dp = 16.dp,
) {
    companion object {
        val Liquid = ListeningPlotStyle(bars = false)
    }
}

/** The shared accessible chart interaction. [ListeningPlotStyle] chooses a line/area or bars.
 * Linear segments do not suggest unobserved intermediate peaks; zero remains at the baseline.
 * Dates before dated tracking began form one shaded, labelled band: unknown, not zero listens.
 */
@Composable
fun ListeningHistoryPlot(points: List<ListeningChartPoint>, summary: String, unrecordedLabel: String, style: ListeningPlotStyle, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    val transparent = !AppTheme.accessibility.reduceTransparency
    val measurer = rememberTextMeasurer()
    val peak = points.maxOf { it.value }.coerceAtLeast(1)
    val ceiling = ((peak + 1) / 2 * 2).coerceAtLeast(2)
    val selectedPoint = points[selected]
    // Unknown dates precede tracking, so they are one leading run.
    val unknown = points.indexOfFirst { it.recorded }.let { if (it < 0) points.size else it }
    val barColor = style.bar ?: colors.accent.copy(alpha = 0.45f)
    val selectedBarColor = style.selectedBar ?: colors.accent
    Column(modifier.clip(RoundedCornerShape(LocalGlassLook.current.corner(style.corner)))
        .background(style.container ?: colors.secondaryBackground).padding(16.dp)) {
        val value: @Composable () -> Unit = {
            if (selectedPoint.recorded) AppText(selectedPoint.valueLabel, typography.title2, color = colors.accent)
            else AppText(unrecordedLabel, typography.headline, color = colors.secondaryLabel)
        }
        if (style.valueFirst) {
            value()
            AppText(selectedPoint.label, typography.subheadline, color = colors.secondaryLabel)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AppText(selectedPoint.label, typography.subheadline, color = colors.secondaryLabel, modifier = Modifier.weight(1f))
                value()
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = summary; isTraversalGroup = true }) {
            Canvas(Modifier.fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { point ->
                        val plotWidth = (size.width - 32.dp.toPx()).coerceAtLeast(1f)
                        selected = (((point.x - 32.dp.toPx()) / plotWidth) * points.size).toInt().coerceIn(points.indices)
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        val plotWidth = (size.width - 32.dp.toPx()).coerceAtLeast(1f)
                        selected = (((change.position.x - 32.dp.toPx()) / plotWidth) * points.size).toInt().coerceIn(points.indices)
                        change.consume()
                    }
                }) {
                val left = 32.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - 28.dp.toPx()
                val width = (size.width - left).coerceAtLeast(1f)
                val height = (bottom - top).coerceAtLeast(1f)
                val step = width / points.size
                val lineColor = colors.separator
                val labelStyle = typography.footnote.copy(color = colors.secondaryLabel, fontSize = 11.sp)
                if (unknown > 0) {
                    val end = left + step * unknown
                    drawRoundRect(colors.label.copy(alpha = if (colors.isDark) 0.06f else 0.045f), Offset(left, top),
                        Size(end - left, bottom - top), CornerRadius(8.dp.toPx()))
                    // Sparse diagonal hatching: reads as "no data" without colour alone.
                    clipRect(left, top, end, bottom) {
                        val gap = 10.dp.toPx()
                        var x = left - (bottom - top)
                        while (x < end) {
                            drawLine(colors.label.copy(alpha = 0.06f), Offset(x, bottom), Offset(x + (bottom - top), top), 1.dp.toPx())
                            x += gap
                        }
                    }
                    val text = measurer.measure(unrecordedLabel, labelStyle.copy(fontSize = 12.sp))
                    if (text.size.width + 16.dp.toPx() < end - left) {
                        drawText(text, topLeft = Offset(left + (end - left - text.size.width) / 2, top + (bottom - top - text.size.height) / 2))
                    }
                }
                listOf(0, ceiling / 2, ceiling).forEach { tick ->
                    val y = bottom - height * tick / ceiling
                    drawLine(lineColor, Offset(left, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = if (style.dashedGrid) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())) else null)
                    val label = measurer.measure(tick.toString(), labelStyle)
                    drawText(label, topLeft = Offset(0f, (y - label.size.height / 2f).coerceAtLeast(0f)))
                }
                fun position(index: Int): Offset = Offset(left + step * (index + 0.5f), bottom - height * points[index].value / ceiling)
                if (style.bars) {
                    val barWidth = (step * 0.62f).coerceAtMost(24.dp.toPx())
                    val radius = barWidth * style.barRoundness
                    points.forEachIndexed { index, point ->
                        if (!point.recorded) return@forEachIndexed
                        val p = position(index)
                        val color = if (index == selected) selectedBarColor else barColor
                        // A recorded zero keeps a short stub on the baseline, distinct from the unknown band.
                        val barTop = if (point.value > 0) p.y else bottom - 3.dp.toPx()
                        drawRoundRect(if (point.value > 0) color else color.copy(alpha = 0.5f),
                            topLeft = Offset(p.x - barWidth / 2, barTop), size = Size(barWidth, bottom - barTop),
                            cornerRadius = CornerRadius(radius.coerceAtMost((bottom - barTop) / 2)))
                    }
                } else {
                    val runs = mutableListOf<MutableList<Int>>()
                    points.indices.forEach { index ->
                        if (points[index].recorded) {
                            if (index == 0 || !points[index - 1].recorded) runs += mutableListOf<Int>()
                            runs.last() += index
                        }
                    }
                    runs.forEach { run ->
                        val line = Path().apply {
                            run.forEachIndexed { n, index ->
                                val p = position(index)
                                if (n == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                        }
                        if (transparent && run.size > 1) {
                            val fill = Path().apply { addPath(line); lineTo(position(run.last()).x, bottom); lineTo(position(run.first()).x, bottom); close() }
                            drawPath(fill, Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.28f), colors.accent.copy(alpha = 0.02f)), top, bottom))
                        }
                        drawPath(line, colors.accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                        // Few points: mark each one so a short run reads as data, not a stray stroke.
                        if (run.size <= 7) run.forEach { drawCircle(colors.accent, 3.dp.toPx(), position(it)) }
                    }
                    val current = position(selected)
                    drawLine(colors.secondaryLabel.copy(alpha = 0.5f), Offset(current.x, top), Offset(current.x, bottom), 1.dp.toPx())
                    if (selectedPoint.recorded) {
                        drawCircle(style.container ?: colors.secondaryBackground, 6.dp.toPx(), current)
                        drawCircle(colors.accent, 4.dp.toPx(), current)
                    }
                }
                val indices = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
                indices.forEach { index ->
                    val label = measurer.measure(points[index].axisLabel, labelStyle)
                    val x = (position(index).x - label.size.width / 2).coerceIn(left, (size.width - label.size.width).coerceAtLeast(left))
                    drawText(label, topLeft = Offset(x, bottom + 10.dp.toPx()))
                }
            }
            // Each real bucket is discoverable without operating the drawing's touch gestures.
            Row(Modifier.fillMaxSize().padding(start = 32.dp, bottom = 28.dp)) {
                points.forEachIndexed { index, point ->
                    Box(Modifier.weight(1f).fillMaxHeight().semantics {
                        contentDescription = point.description
                        this.selected = index == selected
                        onClick { selected = index; true }
                    })
                }
            }
        }
    }
}
