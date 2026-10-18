package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cover for music without embedded art: an abstract composition drawn from [Artwork.seed], so
 * every album or song gets its own, always the same. Four compositions (sun and horizon, waves,
 * overlapping discs, concentric arcs) in three related colours, with the title's initials.
 */
@Composable
fun GenerativeCover(artwork: Artwork, modifier: Modifier = Modifier, showMonogram: Boolean = true) {
    val palette = coverPalette(artwork.seed)
    BoxWithConstraints(modifier) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(Modifier.fillMaxSize()) { drawComposition(artwork.seed, palette) }
        val monogram = artwork.monogram
        if (showMonogram && monogram != null && side.value >= 64f) {
            AppText(
                text = monogram,
                style = AppTheme.typography.largeTitle.copy(fontWeight = FontWeight.Black, fontSize = (side.value * 0.2f).sp, lineHeight = (side.value * 0.2f).sp),
                color = palette.ink,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = side * 0.08f, bottom = side * 0.06f),
            )
        }
    }
}

/**
 * Draws the composition for [seed] into this scope, without the monogram: for surfaces outside
 * Compose layouts, such as images for the car screen and home-screen widgets.
 */
fun DrawScope.drawGenerativeCover(seed: Int) = drawComposition(seed, coverPalette(seed))

/** The two colours used for tinting pages around a generated cover. */
fun generativeCoverColors(seed: Int): List<Color> = coverPalette(seed).let { listOf(it.background, it.shapeA) }

/** First letters of up to two words, uppercase ("Blue Monday" → "BM"). */
fun monogramOf(title: String?): String? = title
    ?.split(' ', '-', '_', '.', '(', ')', '[', ']')
    ?.filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    ?.take(2)
    ?.joinToString("") { it.first().uppercase() }
    ?.takeIf { it.isNotEmpty() }

private class CoverPalette(val background: Color, val backgroundEnd: Color, val shapeA: Color, val shapeB: Color, val accent: Color, val ink: Color)

private fun coverPalette(seed: Int): CoverPalette {
    val random = Random(seed)
    val hue = random.nextFloat() * 360f
    // Neighbouring hues for harmony, one complementary accent for a spark.
    val shift = if (random.nextBoolean()) 35f else -35f
    val darkBackground = random.nextFloat() < 0.35f
    return if (darkBackground) {
        CoverPalette(
            background = hsl(hue, 0.55f, 0.18f),
            backgroundEnd = hsl(hue + shift, 0.6f, 0.28f),
            shapeA = hsl(hue + shift, 0.85f, 0.6f),
            shapeB = hsl(hue - shift, 0.8f, 0.7f),
            accent = hsl(hue + 180f, 0.9f, 0.65f),
            ink = Color.White.copy(alpha = 0.92f),
        )
    } else {
        CoverPalette(
            background = hsl(hue, 0.75f, 0.62f),
            backgroundEnd = hsl(hue + shift, 0.8f, 0.52f),
            shapeA = hsl(hue + shift, 0.9f, 0.82f),
            shapeB = hsl(hue - shift, 0.7f, 0.35f),
            accent = hsl(hue + 180f, 0.85f, 0.6f),
            ink = Color.White.copy(alpha = 0.95f),
        )
    }
}

private fun DrawScope.drawComposition(seed: Int, p: CoverPalette) {
    val random = Random(seed * 31 + 7)
    val w = size.width
    val h = size.height
    drawRect(Brush.linearGradient(listOf(p.background, p.backgroundEnd), Offset.Zero, Offset(w, h)))
    clipRect {
        when (abs(seed) % 4) {
            0 -> { // Sun over a striped horizon.
                val cx = w * (0.3f + random.nextFloat() * 0.4f)
                val r = w * (0.22f + random.nextFloat() * 0.1f)
                val horizon = h * 0.58f
                drawCircle(p.shapeA, r, Offset(cx, horizon - r * 0.35f))
                val stripes = 5
                for (i in 0 until stripes) {
                    val y = horizon + i * (h - horizon) / stripes
                    drawRect(p.shapeB.copy(alpha = 0.9f - i * 0.12f), Offset(0f, y), Size(w, (h - horizon) / stripes * (0.55f + i * 0.07f)))
                }
                drawCircle(p.accent, w * 0.045f, Offset(w * 0.82f, h * 0.18f))
            }
            1 -> { // Stacked waves.
                val bands = 4
                val wavelength = w * (0.6f + random.nextFloat() * 0.5f)
                val phase = random.nextFloat() * 2f * PI.toFloat()
                for (i in 0 until bands) {
                    val base = h * (0.3f + i * 0.18f)
                    val amp = h * 0.05f
                    val path = Path().apply {
                        moveTo(0f, h)
                        var x = 0f
                        lineTo(0f, base)
                        while (x <= w) {
                            lineTo(x, base + amp * sin(x / wavelength * 2f * PI.toFloat() + phase + i))
                            x += w / 40f
                        }
                        lineTo(w, h)
                        close()
                    }
                    val color = when (i) { 0 -> p.shapeA; 1 -> p.accent; 2 -> p.shapeB; else -> p.background }
                    drawPath(path, color.copy(alpha = 0.92f))
                }
            }
            2 -> { // Overlapping discs.
                repeat(3) { i ->
                    val r = w * (0.22f + random.nextFloat() * 0.18f)
                    val c = Offset(w * (0.2f + random.nextFloat() * 0.6f), h * (0.2f + random.nextFloat() * 0.55f))
                    val color = when (i) { 0 -> p.shapeA; 1 -> p.shapeB; else -> p.accent }
                    drawCircle(color.copy(alpha = 0.85f), r, c)
                }
            }
            else -> { // Concentric arcs from a corner.
                val corner = if (random.nextBoolean()) Offset(0f, h) else Offset(w, h)
                val rings = 6
                for (i in rings downTo 1) {
                    val r = w * 0.2f * i
                    val color = if (i % 2 == 0) p.shapeA else p.shapeB
                    drawCircle(color.copy(alpha = 0.9f), r, corner)
                }
                rotate(-20f, Offset(w * 0.75f, h * 0.22f)) {
                    drawRect(p.accent, Offset(w * 0.62f, h * 0.2f), Size(w * 0.26f, h * 0.05f))
                }
            }
        }
        // Thin frame line for a printed-sleeve feel.
        drawRect(Color.White.copy(alpha = 0.12f), style = Stroke(w * 0.012f))
    }
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f / 60f
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(hue % 2f - 1f))
    val (r, g, b) = when (hue.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m)
}
