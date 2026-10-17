package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/**
 * The content layer that glass elements refract. Set by the scaffold; `null` outside it,
 * in which case glass falls back to an opaque surface.
 */
internal val LocalGlassSource = staticCompositionLocalOf<HazeState?> { null }

/**
 * Regular Liquid Glass background for navigation-layer elements (tab bar, mini player).
 * Per the HIG, never use it for content-layer elements.
 *
 * This is the only place that touches Haze's experimental glass API, so swapping the
 * implementation (or library) only changes this function.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    interactionSource: InteractionSource? = null,
): Modifier {
    val colors = AppTheme.colors
    val accessibility = AppTheme.accessibility
    val source = LocalGlassSource.current
    if (accessibility.reduceTransparency || source == null) {
        return clip(shape).background(colors.secondaryBackground)
    }
    val style = remember(shape, colors, accessibility.reduceMotion) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(colors.background)
            // Haze's regular style keeps compact controls partly sharp (depth ~0.65 at 64 dp);
            // bars sit over text, so fully diffuse the backdrop for legibility.
            optics(depth = 1f, blurRadius = 16.dp, refractionDisplacement = 32.dp)
            // Apple's glass lets colors through vividly: less milky white, more chroma than
            // Haze's defaults (whitePoint 0.55 light / -0.22 dark, chroma 1.5).
            whitePoint(if (colors.isDark) -0.3f else 0.3f)
            chromaMultiplier(1.8f)
            pressed {
                val pressedScale = if (accessibility.reduceMotion) 1f else 0.97f
                scale(pressedScale, pressedScale)
            }
        }
    }
    return hazeGlass(input = HazeInput.Sources(source), style = style, interactionSource = interactionSource)
}
