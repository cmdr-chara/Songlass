package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.theme.AppColors
import dev.cmdrchara.songlass.designsystem.theme.AppTypography
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
 * Flex with full roundness. Android follows the phone's native dynamic color roles; other
 * platforms use the original violet palette. The glass keeps its own restrained optical treatment.
 */
@Composable
fun MaterialGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accessibility: UiAccessibility = UiAccessibility(),
    content: @Composable () -> Unit,
) {
    LiquidGlassTheme(darkTheme = darkTheme, accessibility = accessibility) {
        MaterialGlassLayer(enabled = true, darkTheme = darkTheme, content = content)
    }
}

/**
 * Applies Material Glass over a [LiquidGlassTheme] when [enabled], and passes the Liquid Glass
 * values through otherwise. The composition tree is identical either way, so switching style only
 * changes values: screens keep their state, images and scroll positions, with no blank frame.
 */
@Composable
fun MaterialGlassLayer(enabled: Boolean, darkTheme: Boolean, content: @Composable () -> Unit) {
    val system = if (enabled) platformMaterialColors(darkTheme) else null
    val palette = remember(system, darkTheme) { SeaPalette(system?.let { Hsl.of(it.primary).h } ?: DEFAULT_HUE, darkTheme, system) }
    // Loaded even while Liquid Glass is shown, so the font is ready the moment the style changes.
    val typography = rememberTypography()
    val colors = if (enabled) palette.appColors() else LocalAppColors.current
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides if (enabled) typography else LocalAppTypography.current,
        LocalContentColor provides if (enabled) colors.label else LocalContentColor.current,
        LocalGlassLook provides if (enabled) MaterialLook(palette) else LocalGlassLook.current,
        content = content,
    )
}

/** The violet of the file's tab bar (#9500FF). */
private const val DEFAULT_HUE = 275f

/**
 * Native Material roles on Android, with a tonal fallback on other platforms: a near-neutral background, cards a step below, a
 * saturated primary for the main action, a soft primary container for secondary actions and
 * strong text. The glass of the navigation layer keeps the Figma file's vivid colour (`C`) and
 * pale glow (`C'`). Only distinct tones keep the layers apart; one pastel everywhere looked washed out.
 */
internal class SeaPalette(val hue: Float, val dark: Boolean, private val system: SystemMaterialColors? = null) {
    val usesSystemColors: Boolean get() = system != null
    private fun tone(s: Float, l: Float) = Hsl(hue, s, l).toColor()

    val vivid: Color = system?.primary ?: tone(1f, 0.5f)
    val glow: Color = system?.primaryContainer ?: tone(1f, if (dark) 0.62f else 0.89f)

    val background: Color = system?.background ?: if (dark) tone(0.18f, 0.07f) else tone(0.35f, 0.975f)
    val container: Color = system?.container ?: if (dark) tone(0.2f, 0.13f) else tone(0.42f, 0.925f)
    val containerHigh: Color = system?.containerHigh ?: if (dark) tone(0.2f, 0.18f) else tone(0.45f, 0.885f)
    val primary: Color = system?.primary ?: if (dark) tone(0.85f, 0.78f) else tone(0.62f, 0.4f)
    val onPrimary: Color = system?.onPrimary ?: if (dark) tone(0.7f, 0.16f) else Color.White
    val primaryContainer: Color = system?.primaryContainer ?: if (dark) tone(0.45f, 0.28f) else tone(0.85f, 0.86f)
    val onPrimaryContainer: Color = system?.onPrimaryContainer ?: if (dark) tone(0.85f, 0.9f) else tone(0.65f, 0.17f)
    val secondary: Color = system?.secondary ?: if (dark) tone(0.24f, 0.80f) else tone(0.24f, 0.40f)
    val secondaryContainer: Color = system?.secondaryContainer ?: if (dark) tone(0.20f, 0.28f) else tone(0.28f, 0.90f)
    val onSecondaryContainer: Color = system?.onSecondaryContainer ?: if (dark) tone(0.24f, 0.92f) else tone(0.24f, 0.16f)
    val label: Color = system?.label ?: if (dark) tone(0.25f, 0.94f) else tone(0.3f, 0.1f)
    val secondaryLabel: Color = system?.secondaryLabel ?: if (dark) tone(0.15f, 0.74f) else tone(0.15f, 0.36f)

    fun appColors(): AppColors = AppColors(
        isDark = dark,
        background = background,
        secondaryBackground = container,
        label = label,
        secondaryLabel = secondaryLabel,
        tertiaryLabel = secondaryLabel.copy(alpha = 0.55f),
        separator = secondaryLabel.copy(alpha = 0.18f),
        fill = containerHigh,
        accent = primary,
    )
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
