package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One hue taken from an artwork, expanded into a full tonal palette: every surface and
 * foreground of a tinted screen is a shade of the same color, so the page "belongs" to the
 * album. Light tints pair a pale background with deep content; dark tints the opposite.
 */
@Immutable
data class ArtworkTint(
    val isDark: Boolean,
    val background: Color,
    /** Text and icons; at least [MIN_CONTENT_CONTRAST]:1 against [background]. */
    val content: Color,
    val secondaryContent: Color,
    /** Translucent tonal fill for resting circular buttons and chips. */
    val fill: Color,
    /** Solid fill for active states (selected toggles, primary play button). */
    val activeFill: Color,
    /** Icons and text drawn on [activeFill]. */
    val onActiveFill: Color,
) {
    /** The app's semantic colors re-expressed in this tint, so standard components follow it. */
    fun applyTo(colors: AppColors): AppColors = colors.copy(
        isDark = isDark,
        background = background,
        secondaryBackground = fill.compositeOver(background),
        label = content,
        secondaryLabel = secondaryContent,
        tertiaryLabel = content.copy(alpha = 0.4f),
        separator = content.copy(alpha = 0.18f),
        fill = fill,
        accent = content,
    )

    companion object {
        const val MIN_CONTENT_CONTRAST = 7f

        /**
         * Builds the palette for [seed]; near-gray seeds give a neutral (unsaturated) tint.
         *
         * With [edge] (the cover's bottom edge, where an edge-to-edge cover fades into the page)
         * the page follows the artwork instead of the app appearance: a dark edge gives a dark
         * tint even in light mode and vice versa, with a background close to the edge's
         * lightness, so the fade is seamless. Mid-tone edges keep the [dark] preference.
         */
        fun from(seed: Color, dark: Boolean, edge: Color? = null): ArtworkTint {
            val hsl = Hsl.of(seed)
            val saturation = if (hsl.s < NEUTRAL_SATURATION) 0f else hsl.s
            val edgeLuminance = edge?.luminance()
            val useDark = when {
                edgeLuminance == null -> dark
                edgeLuminance < DARK_EDGE -> true
                edgeLuminance > LIGHT_EDGE -> false
                else -> dark
            }
            val edgeLightness = edge?.let { Hsl.of(it).l }
            val background: Color
            var content: Color
            if (useDark) {
                val backgroundLightness = edgeLightness?.coerceIn(0.05f, 0.2f) ?: 0.15f
                // Near-black edges get a near-neutral background: a saturated dark red under a
                // black cover reads as a seam where the fade ends.
                val edgeSaturationScale = edgeLightness?.let { (it / 0.2f).coerceIn(0.35f, 1f) } ?: 1f
                background = Hsl(hsl.h, min(saturation, 0.55f) * 0.75f * edgeSaturationScale, backgroundLightness).toColor()
                var lightness = 0.8f
                content = Hsl(hsl.h, min(saturation * 0.9f + 0.15f, 0.75f).takeIf { saturation > 0f } ?: 0f, lightness).toColor()
                while (contrast(content, background) < MIN_CONTENT_CONTRAST && lightness < 0.97f) {
                    lightness += 0.03f
                    content = Hsl(hsl.h, Hsl.of(content).s, lightness).toColor()
                }
            } else {
                val backgroundLightness = edgeLightness?.coerceIn(0.82f, 0.95f) ?: 0.9f
                background = Hsl(hsl.h, min(saturation, 0.6f) * 0.65f, backgroundLightness).toColor()
                var lightness = 0.24f
                content = Hsl(hsl.h, min(saturation * 0.9f + 0.2f, 0.8f).takeIf { saturation > 0f } ?: 0f, lightness).toColor()
                while (contrast(content, background) < MIN_CONTENT_CONTRAST && lightness > 0.04f) {
                    lightness -= 0.03f
                    content = Hsl(hsl.h, Hsl.of(content).s, lightness).toColor()
                }
            }
            return ArtworkTint(
                isDark = useDark,
                background = background,
                content = content,
                secondaryContent = content.copy(alpha = 0.68f),
                fill = content.copy(alpha = 0.14f),
                activeFill = content,
                onActiveFill = background,
            )
        }

        /**
         * The most characterful color among [samples]: hues are bucketed and weighted by
         * saturation and mid-lightness, so a small vivid area beats a large dull one, and
         * mostly gray images keep their average gray.
         */
        fun seedOf(samples: List<Color>): Color {
            if (samples.isEmpty()) return Color.Gray
            val buckets = DoubleArray(HUE_BUCKETS)
            val members = Array(HUE_BUCKETS) { mutableListOf<Color>() }
            for (color in samples) {
                val hsl = Hsl.of(color)
                if (hsl.s < NEUTRAL_SATURATION || hsl.l < 0.08f || hsl.l > 0.94f) continue
                val weight = hsl.s * max(0.05f, 1f - abs(hsl.l - 0.5f) * 1.6f)
                val bucket = ((hsl.h / 360f) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
                buckets[bucket] += weight.toDouble()
                members[bucket] += color
            }
            val best = buckets.indices.maxBy { buckets[it] }
            val chosen = members[best].ifEmpty { samples }
            return Color(
                red = chosen.map { it.red }.average().toFloat(),
                green = chosen.map { it.green }.average().toFloat(),
                blue = chosen.map { it.blue }.average().toFloat(),
            )
        }

        /** Average color of [samples] (e.g. a cover's bottom edge). */
        fun averageOf(samples: List<Color>): Color = if (samples.isEmpty()) Color.Gray else Color(
            red = samples.map { it.red }.average().toFloat(),
            green = samples.map { it.green }.average().toFloat(),
            blue = samples.map { it.blue }.average().toFloat(),
        )

        /** WCAG contrast ratio between two opaque colors (1..21). */
        fun contrast(a: Color, b: Color): Float {
            val la = a.luminance() + 0.05f
            val lb = b.luminance() + 0.05f
            return max(la, lb) / min(la, lb)
        }

        /** Relative luminance below which a cover edge counts as dark, and above which as light. */
        private const val DARK_EDGE = 0.12f
        private const val LIGHT_EDGE = 0.45f
        private const val NEUTRAL_SATURATION = 0.12f
        private const val HUE_BUCKETS = 24
    }
}

/**
 * Tint of the page under the floating navigation layer (tab bar, mini player), or null for
 * untinted pages. Lets the glass adapt to what it floats over, as on iOS: dark glass with light
 * content over dark pages and vice versa.
 */
val LocalNavigationTint = staticCompositionLocalOf<ArtworkTint?> { null }

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
    )
}

/** Hue (0..360), saturation and lightness (0..1). */
internal data class Hsl(val h: Float, val s: Float, val l: Float) {
    fun toColor(): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h % 360f + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r, g, b) = when {
            hp < 1 -> Triple(c, x, 0f)
            hp < 2 -> Triple(x, c, 0f)
            hp < 3 -> Triple(0f, c, x)
            hp < 4 -> Triple(0f, x, c)
            hp < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
    }

    companion object {
        fun of(color: Color): Hsl {
            val r = color.red; val g = color.green; val b = color.blue
            val maxC = max(r, max(g, b)); val minC = min(r, min(g, b))
            val l = (maxC + minC) / 2f
            val d = maxC - minC
            if (d == 0f) return Hsl(0f, 0f, l)
            val s = d / (1f - abs(2f * l - 1f))
            val h = when (maxC) {
                r -> 60f * (((g - b) / d) % 6f)
                g -> 60f * ((b - r) / d + 2f)
                else -> 60f * ((r - g) / d + 4f)
            }
            return Hsl((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
        }
    }
}
