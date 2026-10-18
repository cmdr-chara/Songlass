package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkTint
import dev.cmdrchara.songlass.designsystem.theme.AppColors
import dev.cmdrchara.songlass.designsystem.theme.AppTypography
import dev.cmdrchara.songlass.designsystem.theme.LocalAmbientArtwork
import dev.cmdrchara.songlass.designsystem.theme.LocalAppColors
import dev.cmdrchara.songlass.designsystem.theme.LocalAppTypography
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility
import dev.cmdrchara.songlass.theme.liquidglass.LiquidGlassTheme
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import dev.cmdrchara.songlass.theme.materialglass.resources.Res
import dev.cmdrchara.songlass.theme.materialglass.resources.google_sans_flex
import org.jetbrains.compose.resources.Font
import kotlin.math.abs

/**
 * Material Glass, after the "Google Sea Glass" Figma file (tokens in docs/design/material-glass.md):
 * pastel coloured glass with a glow beneath, light from inside, grain and pill shapes, in Google Sans
 * Flex with full roundness. Every colour comes from one hue taken from the cover of what is playing,
 * so the whole interface follows the music; violet (the file's tab bar) when nothing plays.
 */
@Composable
fun MaterialGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accessibility: UiAccessibility = UiAccessibility(),
    content: @Composable () -> Unit,
) {
    val hue = animatedHue(LocalAmbientArtwork.current, darkTheme)
    val palette = SeaPalette(hue, darkTheme)
    val typography = rememberTypography()
    LiquidGlassTheme(darkTheme = darkTheme, accessibility = accessibility) {
        val colors = palette.appColors()
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalAppTypography provides typography,
            LocalContentColor provides colors.label,
            LocalGlassLook provides MaterialLook(palette),
            content = content,
        )
    }
}

/** The cover's hue, animated along the shorter way round the colour wheel. */
@Composable
private fun animatedHue(artwork: dev.cmdrchara.songlass.designsystem.Artwork?, dark: Boolean): Float {
    val tint = rememberArtworkTint(artwork, dark)
    val target = Hsl.of(tint.content).let { if (artwork == null || it.s < 0.08f) DEFAULT_HUE else it.h }
    val hue = remember { Animatable(target) }
    LaunchedEffect(target) {
        val current = hue.value
        var delta = target - current
        if (abs(delta) > 180f) delta -= 360f * kotlin.math.sign(delta)
        hue.animateTo(current + delta, tween(HUE_CHANGE_MILLIS))
        hue.snapTo(((hue.value % 360f) + 360f) % 360f)
    }
    return hue.value
}

private const val HUE_CHANGE_MILLIS = 360

/** The violet of the file's tab bar (#9500FF). */
private const val DEFAULT_HUE = 275f

/**
 * The file's palette rule, for any hue: a vivid surface colour (`C`), a pale glow (`C'`, ~89%
 * lightness) and a dark, desaturated colour for text on it (~48% saturation, ~35% lightness).
 */
internal class SeaPalette(val hue: Float, val dark: Boolean) {
    val vivid: Color = Hsl(hue, 1f, 0.5f).toColor()
    val glow: Color = Hsl(hue, 1f, if (dark) 0.62f else 0.89f).toColor()
    val ink: Color = if (dark) Hsl(hue, 0.9f, 0.86f).toColor() else Hsl(hue, 0.48f, 0.35f).toColor()

    fun appColors(): AppColors = if (dark) {
        AppColors(
            isDark = true,
            background = Hsl(hue, 0.32f, 0.07f).toColor(),
            secondaryBackground = Hsl(hue, 0.32f, 0.14f).toColor(),
            label = Hsl(hue, 0.3f, 0.96f).toColor(),
            secondaryLabel = Hsl(hue, 0.25f, 0.82f).toColor().copy(alpha = 0.7f),
            tertiaryLabel = Hsl(hue, 0.2f, 0.8f).toColor().copy(alpha = 0.38f),
            separator = Hsl(hue, 0.2f, 0.7f).toColor().copy(alpha = 0.2f),
            fill = vivid.copy(alpha = 0.22f),
            accent = ink,
        )
    } else {
        AppColors(
            isDark = false,
            // The file's backgrounds (#F3EDF7, #F8FAFD) are near-white tints of the surface hue.
            background = Hsl(hue, 0.4f, 0.965f).toColor(),
            secondaryBackground = Hsl(hue, 0.7f, 0.92f).toColor(),
            label = Hsl(hue, 0.3f, 0.12f).toColor(),
            secondaryLabel = Hsl(hue, 0.2f, 0.32f).toColor().copy(alpha = 0.85f),
            tertiaryLabel = Hsl(hue, 0.15f, 0.35f).toColor().copy(alpha = 0.45f),
            separator = Hsl(hue, 0.25f, 0.45f).toColor().copy(alpha = 0.2f),
            fill = vivid.copy(alpha = 0.14f),
            accent = ink,
        )
    }
}

/**
 * Google Sans Flex, subset to Latin and pinned to the file's axes (roundness 100, width 100,
 * optical size 18); the weight stays variable from 300 to 800. Licensed under the SIL OFL 1.1
 * (composeResources/files/google_sans_flex_OFL.txt).
 */
@Composable
private fun rememberTypography(): AppTypography {
    fun weight(w: Int) = FontVariation.Settings(FontVariation.weight(w))
    val family = FontFamily(
        Font(Res.font.google_sans_flex, FontWeight.Normal, variationSettings = weight(400)),
        Font(Res.font.google_sans_flex, FontWeight.Medium, variationSettings = weight(500)),
        Font(Res.font.google_sans_flex, FontWeight.SemiBold, variationSettings = weight(600)),
        Font(Res.font.google_sans_flex, FontWeight.Bold, variationSettings = weight(700)),
    )
    // Same sizes as Liquid Glass so layouts hold; the file sets text at weight 500.
    fun style(size: Int, lineHeight: Int, w: FontWeight = FontWeight.Medium) =
        TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = w)
    return AppTypography(
        largeTitle = style(34, 41, FontWeight.Bold),
        title1 = style(28, 34, FontWeight.SemiBold),
        title2 = style(22, 28, FontWeight.Bold),
        title3 = style(20, 25, FontWeight.SemiBold),
        headline = style(17, 22, FontWeight.SemiBold),
        body = style(17, 22, FontWeight.Normal),
        callout = style(16, 21, FontWeight.Normal),
        subheadline = style(15, 20, FontWeight.Normal),
        footnote = style(13, 18, FontWeight.Normal),
        caption1 = style(12, 16),
        caption2 = style(11, 13),
    )
}

/** Hue (degrees), saturation and lightness (0..1). */
internal data class Hsl(val h: Float, val s: Float, val l: Float) {
    fun toColor(): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r, g, b) = when (hp.toInt()) {
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

    companion object {
        fun of(color: Color): Hsl {
            val max = maxOf(color.red, color.green, color.blue)
            val min = minOf(color.red, color.green, color.blue)
            val l = (max + min) / 2f
            if (max == min) return Hsl(0f, 0f, l)
            val d = max - min
            val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
            val h = when (max) {
                color.red -> ((color.green - color.blue) / d + (if (color.green < color.blue) 6f else 0f))
                color.green -> (color.blue - color.red) / d + 2f
                else -> (color.red - color.green) / d + 4f
            } * 60f
            return Hsl(h, s, l)
        }
    }
}
