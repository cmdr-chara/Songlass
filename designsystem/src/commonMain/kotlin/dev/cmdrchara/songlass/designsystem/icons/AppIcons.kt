package dev.cmdrchara.songlass.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn icon set on a 24×24 grid, owned by the project (no third-party glyphs).
 * Placeholder quality: replace with a proper icon set once the visual identity is defined.
 * Icons are drawn in black and tinted at use site.
 */
object AppIcons {
    val Play: ImageVector by lazy {
        icon("Play") { fill { moveTo(7f, 4.5f); lineTo(20f, 12f); lineTo(7f, 19.5f); close() } }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            fill { roundRect(5.5f, 4f, 10f, 20f, 1.5f) }
            fill { roundRect(14f, 4f, 18.5f, 20f, 1.5f) }
        }
    }

    val Next: ImageVector by lazy {
        icon("Next") {
            fill { moveTo(2.5f, 5.5f); lineTo(12f, 12f); lineTo(2.5f, 18.5f); close() }
            fill { moveTo(12f, 5.5f); lineTo(21.5f, 12f); lineTo(12f, 18.5f); close() }
        }
    }

    val Previous: ImageVector by lazy {
        icon("Previous") {
            fill { moveTo(21.5f, 5.5f); lineTo(12f, 12f); lineTo(21.5f, 18.5f); close() }
            fill { moveTo(12f, 5.5f); lineTo(2.5f, 12f); lineTo(12f, 18.5f); close() }
        }
    }

    val Shuffle: ImageVector by lazy {
        icon("Shuffle") {
            stroke {
                moveTo(3f, 7f); lineTo(7f, 7f); lineTo(17f, 17f); lineTo(21f, 17f)
                moveTo(3f, 17f); lineTo(7f, 17f); lineTo(17f, 7f); lineTo(21f, 7f)
                moveTo(18f, 4f); lineTo(21f, 7f); lineTo(18f, 10f)
                moveTo(18f, 14f); lineTo(21f, 17f); lineTo(18f, 20f)
            }
        }
    }

    val Repeat: ImageVector by lazy { icon("Repeat") { stroke { repeatArrows() } } }

    val RepeatOne: ImageVector by lazy {
        icon("RepeatOne") {
            stroke {
                repeatArrows()
                moveTo(11f, 10.5f); lineTo(12.5f, 9.5f); lineTo(12.5f, 14.5f)
            }
        }
    }

    val Library: ImageVector by lazy {
        icon("Library") {
            fill { roundRect(4f, 8f, 20f, 20f, 2.5f) }
            stroke { moveTo(6.5f, 5f); lineTo(17.5f, 5f) }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            stroke {
                moveTo(4f, 10.5f)
                arcToRelative(6.5f, 6.5f, 0f, true, true, 13f, 0f)
                arcToRelative(6.5f, 6.5f, 0f, true, true, -13f, 0f)
                moveTo(15.5f, 15.5f); lineTo(20.5f, 20.5f)
            }
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") { stroke { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) } }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") { stroke { moveTo(5f, 9f); lineTo(12f, 15f); lineTo(19f, 9f) } }
    }

    val More: ImageVector by lazy {
        icon("More") {
            for (x in listOf(5f, 12f, 19f)) fill { circle(x, 12f, 1.75f) }
        }
    }

    val Queue: ImageVector by lazy {
        icon("Queue") {
            stroke {
                for (y in listOf(6f, 12f, 18f)) { moveTo(9f, y); lineTo(20f, y) }
            }
            for (y in listOf(6f, 12f, 18f)) fill { circle(4.5f, y, 1.5f) }
        }
    }

    val MusicNote: ImageVector by lazy {
        icon("MusicNote") {
            stroke { moveTo(11f, 17f); lineTo(11f, 4f); lineTo(18f, 6f) }
            fill { circle(8f, 17f, 3f) }
        }
    }

    val Star: ImageVector by lazy { icon("Star") { stroke { star() } } }

    val StarFilled: ImageVector by lazy { icon("StarFilled") { fill { star() } } }

    /** Drag handle for reordering rows. */
    val DragHandle: ImageVector by lazy {
        icon("DragHandle") { stroke { for (y in listOf(8f, 12f, 16f)) { moveTo(5f, y); lineTo(19f, y) } } }
    }

    val PlayNext: ImageVector by lazy {
        icon("PlayNext") {
            stroke { moveTo(3f, 6f); lineTo(13f, 6f); moveTo(3f, 12f); lineTo(10f, 12f); moveTo(3f, 18f); lineTo(10f, 18f) }
            fill { moveTo(14f, 10f); lineTo(21f, 15f); lineTo(14f, 20f); close() }
        }
    }

    val AddToQueue: ImageVector by lazy {
        icon("AddToQueue") {
            stroke {
                moveTo(3f, 6f); lineTo(14f, 6f); moveTo(3f, 12f); lineTo(14f, 12f); moveTo(3f, 18f); lineTo(10f, 18f)
                moveTo(17f, 14f); lineTo(17f, 22f); moveTo(13f, 18f); lineTo(21f, 18f)
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") { stroke { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    }
}

private class IconScope(val builder: ImageVector.Builder) {
    fun fill(block: PathBuilder.() -> Unit) {
        builder.path(fill = SolidColor(Color.Black), pathBuilder = block)
    }

    fun stroke(block: PathBuilder.() -> Unit) {
        builder.path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }
}

private fun icon(name: String, block: IconScope.() -> Unit): ImageVector {
    val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    IconScope(builder).block()
    return builder.build()
}

private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
    moveTo(left + r, top)
    lineTo(right - r, top)
    quadTo(right, top, right, top + r)
    lineTo(right, bottom - r)
    quadTo(right, bottom, right - r, bottom)
    lineTo(left + r, bottom)
    quadTo(left, bottom, left, bottom - r)
    lineTo(left, top + r)
    quadTo(left, top, left + r, top)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}

private fun PathBuilder.star() {
    // Five-pointed star, outer radius 9.5, inner 4, centred at (12, 12.5).
    val points = (0 until 10).map { i ->
        val r = if (i % 2 == 0) 9.5f else 4f
        val a = kotlin.math.PI / 5 * i - kotlin.math.PI / 2
        (12f + r * kotlin.math.cos(a).toFloat()) to (12.5f + r * kotlin.math.sin(a).toFloat())
    }
    moveTo(points[0].first, points[0].second)
    points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    close()
}

private fun PathBuilder.repeatArrows() {
    moveTo(4f, 12f); lineTo(4f, 10f); quadTo(4f, 7f, 7f, 7f); lineTo(20f, 7f)
    moveTo(17f, 4f); lineTo(20f, 7f); lineTo(17f, 10f)
    moveTo(20f, 12f); lineTo(20f, 14f); quadTo(20f, 17f, 17f, 17f); lineTo(4f, 17f)
    moveTo(7f, 20f); lineTo(4f, 17f); lineTo(7f, 14f)
}
