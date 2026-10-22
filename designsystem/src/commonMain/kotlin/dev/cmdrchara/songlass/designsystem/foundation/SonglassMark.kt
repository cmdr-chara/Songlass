package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import dev.cmdrchara.songlass.designsystem.rememberWelcomeBeat
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val BarLengths = floatArrayOf(19f, 12f, 15f, 9f, 13f, 18f, 13f, 9f, 15f, 12f)
private const val BarWidth = 10.2f
private const val BarInner = 21f

/**
 * The Songlass symbol: ten bars radiating from a heart. It is the launcher icon without its background, so a
 * screen can put it on any surface. The bars breathe like a level meter and the heart beats with them;
 * with reduced motion it holds still. Colours come from the caller, the drawing is the same everywhere.
 */
@Composable
fun SonglassMark(size: Dp, bars: Color, heart: Color, modifier: Modifier = Modifier, glyphScale: Float = 0.95f) {
    val beat = rememberWelcomeBeat()
    val animated = !AppTheme.accessibility.reduceMotion
    Canvas(modifier.size(size)) {
        val u = this.size.width / 108f * glyphScale
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val phase = beat.value
        for (i in BarLengths.indices) {
            val wave = if (animated) 0.78f + 0.22f * sin(2.0 * PI * (phase + i * 0.1)).toFloat() else 1f
            val t = (-90.0 + i * 36.0) * PI / 180.0
            val dir = Offset(cos(t).toFloat(), sin(t).toFloat())
            val end = BarInner + BarLengths[i] * wave
            drawLine(bars, c + dir * ((BarInner + BarWidth / 2) * u), c + dir * ((end - BarWidth / 2) * u),
                strokeWidth = BarWidth * u, cap = StrokeCap.Round)
        }
        val pulse = if (animated) 1f + 0.05f * sin(2.0 * PI * phase).toFloat() else 1f
        val h = 16.5f * u * pulse
        val origin = Offset(c.x, c.y + 1.5f * u)
        fun p(x: Float, y: Float) = Offset(origin.x + x * h, origin.y + y * h)
        val path = Path().apply {
            moveTo(p(0f, 0.92f).x, p(0f, 0.92f).y)
            cubicTo(p(-1.15f, 0.12f), p(-1.32f, -0.58f), p(-0.68f, -0.78f))
            cubicTo(p(-0.32f, -0.88f), p(0f, -0.66f), p(0f, -0.42f))
            cubicTo(p(0f, -0.66f), p(0.32f, -0.88f), p(0.68f, -0.78f))
            cubicTo(p(1.32f, -0.58f), p(1.15f, 0.12f), p(0f, 0.92f))
            close()
        }
        drawPath(path, heart)
    }
}

private fun Path.cubicTo(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
