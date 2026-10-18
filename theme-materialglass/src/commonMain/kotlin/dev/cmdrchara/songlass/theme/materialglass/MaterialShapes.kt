package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Shapes in the spirit of the Material 3 Expressive shape set (cookie, clover, sunny, arch),
 * drawn from our own formulas: a circle whose radius waves [lobes] times around it. [depth] is
 * how far the dips go in (0 = circle); [sharpness] above 1 narrows the lobes.
 */
internal class PolarShape(
    private val lobes: Int,
    private val depth: Float,
    private val sharpness: Float = 1f,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val steps = 240
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (i in 0..steps) {
            val theta = i / steps.toFloat() * 2f * PI.toFloat() + rotation
            // 1 at the tip of a lobe, 0 at the bottom of a dip.
            val wave = ((cos(lobes * (theta - rotation)) + 1f) / 2f).let { abs(it).pow(1f / sharpness) }
            val r = 1f - depth * (1f - wave)
            val x = cx + cx * r * cos(theta)
            val y = cy + cy * r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

internal object ExpressiveShapes {
    /** Nine soft scallops. */
    val Cookie: Shape = PolarShape(lobes = 9, depth = 0.1f)

    /** Four round leaves. */
    val Clover: Shape = PolarShape(lobes = 4, depth = 0.24f, sharpness = 0.7f, rotation = (PI / 4).toFloat())

    /** Eight gentle rays. */
    val Sunny: Shape = PolarShape(lobes = 8, depth = 0.14f, sharpness = 1.6f)

    /** Round top, softly squared bottom. */
    val Arch: Shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 14, bottomEndPercent = 14)

    /** Six broad scallops. */
    val Flower: Shape = PolarShape(lobes = 6, depth = 0.16f)

    /** A rotating set, so neighbouring covers never share a shape. */
    val sequence: List<Shape> = listOf(Cookie, Arch, Clover, Sunny, Flower)
}
