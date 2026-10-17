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
    val source = LocalGlassSource.current
        ?: return clip(shape).background(AppTheme.colors.secondaryBackground)
    val background = AppTheme.colors.background
    val style = remember(shape, background) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(background)
            // Haze's regular style keeps compact controls partly sharp (depth ~0.65 at 64 dp);
            // bars sit over text, so fully diffuse the backdrop for legibility.
            optics(depth = 1f, blurRadius = 20.dp, refractionDisplacement = 32.dp)
            pressed { scale(0.97f, 0.97f) }
        }
    }
    return hazeGlass(input = HazeInput.Sources(source), style = style, interactionSource = interactionSource)
}
