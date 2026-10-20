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
    val Share: ImageVector by lazy {
        icon("Share") { stroke {
            moveTo(8f, 9f); lineTo(5f, 9f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 9f); lineTo(16f, 9f)
            moveTo(12f, 15f); lineTo(12f, 2.5f); moveTo(7.5f, 7f); lineTo(12f, 2.5f); lineTo(16.5f, 7f)
        } }
    }

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

    val Home: ImageVector by lazy {
        icon("Home") {
            // House with a rounded body and a door cut-out, filled like the other tab icons.
            fill {
                moveTo(12f, 3.2f); lineTo(21f, 10.4f); lineTo(19.2f, 10.4f); lineTo(19.2f, 19f)
                quadTo(19.2f, 20.8f, 17.4f, 20.8f); lineTo(14.4f, 20.8f); lineTo(14.4f, 15.2f)
                quadTo(14.4f, 14f, 13.2f, 14f); lineTo(10.8f, 14f); quadTo(9.6f, 14f, 9.6f, 15.2f)
                lineTo(9.6f, 20.8f); lineTo(6.6f, 20.8f); quadTo(4.8f, 20.8f, 4.8f, 19f)
                lineTo(4.8f, 10.4f); lineTo(3f, 10.4f); close()
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

    val Settings: ImageVector by lazy {
        icon("Settings") {
            stroke {
                // Eight original teeth with a rounded outline, on the shared 24 dp grid.
                for (i in 0 until 32) {
                    val radius = if (i % 4 == 1 || i % 4 == 2) 10f else 8f
                    val angle = kotlin.math.PI / 16 * i - kotlin.math.PI / 2
                    val x = 12f + radius * kotlin.math.cos(angle).toFloat()
                    val y = 12f + radius * kotlin.math.sin(angle).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
                circle(12f, 12f, 3.25f)
            }
        }
    }

    /** Track details and other informational actions, on the shared original icon grid. */
    val InfoCircle: ImageVector by lazy {
        icon("InfoCircle") {
            stroke(width = 1.9f) { circle(12f, 12f, 9f) }
            fill { circle(12f, 7.7f, 1.2f) }
            stroke(width = 2.2f) {
                moveTo(10.8f, 11f); lineTo(12f, 11f); lineTo(12f, 16.7f)
                moveTo(10.5f, 16.7f); lineTo(13.5f, 16.7f)
            }
        }
    }

    val Checkmark: ImageVector by lazy {
        icon("Checkmark") { stroke(width = 2.4f) { moveTo(5.5f, 12.6f); lineTo(9.8f, 16.8f); lineTo(18.5f, 7.6f) } }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") { stroke { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) } }
    }

    /** Arrow pointing left, the back control of Material layouts. */
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") { stroke { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) } }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") { stroke { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) } }
    }

    /** Pin: a pushpin leaning right. */
    val Pin: ImageVector by lazy {
        icon("Pin") {
            fill {
                moveTo(9f, 3.5f)
                lineTo(15f, 3.5f)
                quadTo(16f, 3.5f, 15.6f, 4.5f)
                lineTo(14.6f, 7f)
                lineTo(14.6f, 11f)
                lineTo(17.2f, 13.6f)
                quadTo(17.8f, 14.5f, 16.8f, 14.5f)
                lineTo(7.2f, 14.5f)
                quadTo(6.2f, 14.5f, 6.8f, 13.6f)
                lineTo(9.4f, 11f)
                lineTo(9.4f, 7f)
                lineTo(8.4f, 4.5f)
                quadTo(8f, 3.5f, 9f, 3.5f)
                close()
            }
            stroke { moveTo(12f, 14.5f); lineTo(12f, 20.5f) }
        }
    }

    /** Folder: a tab on the top left over a rounded body. */
    val Folder: ImageVector by lazy {
        icon("Folder") {
            fill {
                moveTo(3f, 7.5f)
                quadTo(3f, 5f, 5.5f, 5f)
                lineTo(9.2f, 5f)
                quadTo(10.2f, 5f, 10.9f, 5.8f)
                lineTo(12f, 7f)
                lineTo(18.5f, 7f)
                quadTo(21f, 7f, 21f, 9.5f)
                lineTo(21f, 16.5f)
                quadTo(21f, 19f, 18.5f, 19f)
                lineTo(5.5f, 19f)
                quadTo(3f, 19f, 3f, 16.5f)
                close()
            }
        }
    }

    /** Artist: head and shoulders. */
    val Person: ImageVector by lazy {
        icon("Person") {
            fill { circle(12f, 8.5f, 4f) }
            fill {
                moveTo(4.5f, 19.5f)
                quadTo(5.5f, 14f, 12f, 14f)
                quadTo(18.5f, 14f, 19.5f, 19.5f)
                quadTo(19.6f, 20.5f, 18.5f, 20.5f)
                lineTo(5.5f, 20.5f)
                quadTo(4.4f, 20.5f, 4.5f, 19.5f)
                close()
            }
        }
    }

    /** Album: a cover with a record label in the middle. */
    val Album: ImageVector by lazy {
        icon("Album") {
            stroke { roundRect(4f, 4f, 20f, 20f, 3.5f) }
            fill { circle(12f, 12f, 3.2f) }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") { stroke { moveTo(5f, 9f); lineTo(12f, 15f); lineTo(19f, 9f) } }
    }

    /** Audio output: a speaker with sound waves (our own glyph). */
    val AudioOutput: ImageVector by lazy {
        icon("AudioOutput") {
            fill { moveTo(3f, 9.5f); lineTo(7f, 9.5f); lineTo(12f, 5f); lineTo(12f, 19f); lineTo(7f, 14.5f); lineTo(3f, 14.5f); close() }
            stroke {
                moveTo(15.5f, 9f); quadTo(17.2f, 12f, 15.5f, 15f)
                moveTo(18.5f, 6.5f); quadTo(21.8f, 12f, 18.5f, 17.5f)
            }
        }
    }

    /** Speaker without waves, the quiet end of the volume slider. */
    val SpeakerLow: ImageVector by lazy {
        icon("SpeakerLow") {
            fill { moveTo(5f, 9.5f); lineTo(9f, 9.5f); lineTo(14f, 5f); lineTo(14f, 19f); lineTo(9f, 14.5f); lineTo(5f, 14.5f); close() }
        }
    }

    /** Duration: a clock face and two hands, drawn in the app's own icon language. */
    val Clock: ImageVector by lazy {
        icon("Clock") { stroke { circle(12f, 12f, 8.5f); moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f) } }
    }

    /** Sleep timer: a crescent moon. */
    val Timer: ImageVector by lazy {
        icon("Timer") {
            fill {
                moveTo(14.5f, 3.2f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 20.8f, y1 = 15.6f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14.5f, y1 = 3.2f)
                close()
            }
        }
    }

    /** Listening statistics, drawn here rather than borrowed from a platform asset. */
    val ChartBars: ImageVector by lazy {
        icon("ChartBars") {
            stroke(width = 3.5f) {
                moveTo(5f, 19f); lineTo(5f, 12f)
                moveTo(12f, 19f); lineTo(12f, 5f)
                moveTo(19f, 19f); lineTo(19f, 9f)
            }
        }
    }

    /** Three sliders at different heights. */
    val Equalizer: ImageVector by lazy {
        icon("Equalizer") {
            stroke {
                for ((x, knob) in listOf(6f to 15f, 12f to 8f, 18f to 13f)) {
                    moveTo(x, 4f); lineTo(x, knob - 2.2f)
                    moveTo(x, knob + 2.2f); lineTo(x, 20f)
                }
            }
            for ((x, knob) in listOf(6f to 15f, 12f to 8f, 18f to 13f)) fill { circle(x, knob, 2.4f) }
        }
    }

    val More: ImageVector by lazy {
        icon("More") {
            for (x in listOf(5f, 12f, 19f)) fill { circle(x, 12f, 1.75f) }
        }
    }

    val Lyrics: ImageVector by lazy {
        icon("Lyrics") {
            stroke {
                moveTo(5f, 4f); lineTo(19f, 4f); quadTo(21f, 4f, 21f, 6f)
                lineTo(21f, 16f); quadTo(21f, 18f, 19f, 18f)
                lineTo(10f, 18f); lineTo(5f, 21f); lineTo(5f, 18f)
                quadTo(3f, 18f, 3f, 16f); lineTo(3f, 6f); quadTo(3f, 4f, 5f, 4f)
                moveTo(7f, 9f); lineTo(17f, 9f)
                moveTo(7f, 13f); lineTo(14f, 13f)
            }
        }
    }

    val ImportFile: ImageVector by lazy {
        icon("ImportFile") { stroke {
            moveTo(14f, 3f); lineTo(5f, 3f); lineTo(5f, 21f); lineTo(19f, 21f)
            lineTo(19f, 8f); lineTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
            moveTo(12f, 11f); lineTo(12f, 18f)
            moveTo(9f, 15f); lineTo(12f, 18f); lineTo(15f, 15f)
        } }
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

    val Star: ImageVector by lazy { icon("Star") { stroke(width = 2.2f) { star() } } }

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

    val Plus: ImageVector by lazy { icon("Plus") { stroke { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } } }

    val Close: ImageVector by lazy {
        icon("Close") { stroke { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    }
}

private class IconScope(val builder: ImageVector.Builder) {
    fun fill(block: PathBuilder.() -> Unit) {
        builder.path(fill = SolidColor(Color.Black), pathBuilder = block)
    }

    fun stroke(width: Float = 2f, block: PathBuilder.() -> Unit) {
        builder.path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
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
    // Five-pointed star with classic proportions (inner/outer ≈ 0.48) and softly rounded
    // corners, centred at (12, 12.6) so it sits optically in the middle of the grid.
    val points = (0 until 10).map { i ->
        val r = if (i % 2 == 0) 9.6f else 4.6f
        val a = kotlin.math.PI / 5 * i - kotlin.math.PI / 2
        (12f + r * kotlin.math.cos(a).toFloat()) to (12.6f + r * kotlin.math.sin(a).toFloat())
    }
    roundedPolygon(points, cornerFraction = { i -> if (i % 2 == 0) 0.2f else 0.12f })
}

/**
 * Closed polygon whose corners are replaced by quadratic curves; [cornerFraction] is how much
 * of each adjacent edge the curve uses at vertex i.
 */
private fun PathBuilder.roundedPolygon(points: List<Pair<Float, Float>>, cornerFraction: (Int) -> Float) {
    fun along(from: Pair<Float, Float>, to: Pair<Float, Float>, t: Float) =
        (from.first + (to.first - from.first) * t) to (from.second + (to.second - from.second) * t)
    val n = points.size
    for (i in 0 until n) {
        val prev = points[(i - 1 + n) % n]
        val curr = points[i]
        val next = points[(i + 1) % n]
        val f = cornerFraction(i)
        val start = along(curr, prev, f)
        val end = along(curr, next, f)
        if (i == 0) moveTo(start.first, start.second) else lineTo(start.first, start.second)
        quadTo(curr.first, curr.second, end.first, end.second)
    }
    close()
}

private fun PathBuilder.repeatArrows() {
    moveTo(4f, 12f); lineTo(4f, 10f); quadTo(4f, 7f, 7f, 7f); lineTo(20f, 7f)
    moveTo(17f, 4f); lineTo(20f, 7f); lineTo(17f, 10f)
    moveTo(20f, 12f); lineTo(20f, 14f); quadTo(20f, 17f, 17f, 17f); lineTo(4f, 17f)
    moveTo(7f, 20f); lineTo(4f, 17f); lineTo(7f, 14f)
}
