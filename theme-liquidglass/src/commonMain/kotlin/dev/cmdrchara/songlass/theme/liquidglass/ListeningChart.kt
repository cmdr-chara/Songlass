package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.ListeningChartPoint
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import androidx.compose.ui.unit.sp

/** The shared accessible chart interaction. GlassLook chooses a line/area or tonal bars.
 * Linear segments do not suggest unobserved intermediate peaks; zero remains at the baseline.
 */
@Composable
fun ListeningHistoryPlot(points: List<ListeningChartPoint>, summary: String, bars: Boolean, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    val transparent = !AppTheme.accessibility.reduceTransparency
    val measurer = rememberTextMeasurer()
    val peak = points.maxOf { it.value }.coerceAtLeast(1)
    val ceiling = ((peak + 1) / 2 * 2).coerceAtLeast(2)
    val selectedPoint = points[selected]
    Column(modifier.clip(RoundedCornerShape(LocalGlassLook.current.corner(16.dp)))
        .background(colors.secondaryBackground).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText(selectedPoint.label, typography.subheadline, color = colors.secondaryLabel, modifier = Modifier.weight(1f))
            AppText(if (selectedPoint.recorded) selectedPoint.value.toString() else "—", typography.title2, color = colors.accent)
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
                listOf(0, ceiling / 2, ceiling).forEach { tick ->
                    val y = bottom - height * tick / ceiling
                    drawLine(lineColor, Offset(left, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                    val label = measurer.measure(tick.toString(), labelStyle)
                    drawText(label, topLeft = Offset(0f, (y - label.size.height / 2f).coerceAtLeast(0f)))
                }
                fun position(index: Int): Offset = Offset(left + step * (index + 0.5f), bottom - height * points[index].value / ceiling)
                if (bars) {
                    points.forEachIndexed { index, point ->
                        if (point.recorded && point.value > 0) {
                            val p = position(index)
                            val barWidth = (step * 0.68f).coerceAtMost(28.dp.toPx())
                            drawRoundRect(colors.accent.copy(alpha = if (index == selected) 1f else 0.65f),
                                topLeft = Offset(p.x - barWidth / 2, p.y), size = Size(barWidth, bottom - p.y),
                                cornerRadius = CornerRadius((barWidth / 2).coerceAtMost(6.dp.toPx())))
                        }
                    }
                } else {
                    // No line through dates before tracking began: unknown is not zero listens.
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
                        if (run.size == 1) drawCircle(colors.accent, 3.dp.toPx(), position(run.first()))
                    }
                }
                val current = position(selected)
                drawLine(colors.secondaryLabel.copy(alpha = 0.5f), Offset(current.x, top), Offset(current.x, bottom), 1.dp.toPx())
                if (selectedPoint.recorded) {
                    drawCircle(colors.secondaryBackground, 5.dp.toPx(), current)
                    drawCircle(colors.accent, 3.dp.toPx(), current)
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
