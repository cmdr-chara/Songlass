package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
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
    edges: Boolean = true,
): Modifier {
    val colors = AppTheme.colors
    val accessibility = AppTheme.accessibility
    val source = LocalGlassSource.current
    if (accessibility.reduceTransparency || source == null) {
        return clip(shape).background(colors.secondaryBackground)
    }
    val style = remember(shape, colors, accessibility.reduceMotion, edges) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(colors.background)
            // Haze's regular style keeps compact controls partly sharp (depth ~0.65 at 64 dp);
            // bars sit over text, so fully diffuse the backdrop for legibility. Start from the
            // regular optics: the optics(...) builder defaults add a sharp, unblurred detail
            // pass along the edges that shows text underneath as streaks.
            optics(
                GlassDefaults.optics.copy(
                    depth = OpticalSizeValue.Fixed(1f),
                    blurRadius = OpticalSizeValue.Fixed(16.dp),
                    refractionDisplacement = 24.dp,
                ),
            )
            // Apple's glass lets colors through: less milky white than Haze's defaults
            // (whitePoint 0.55 light / -0.22 dark). Dark glass is smoked, not black, so the
            // shape still reads over a black page. Chroma stays near neutral, otherwise glass
            // over a tinted page turns into a darker, more saturated slab than the page itself.
            whitePoint(if (colors.isDark) -0.12f else 0.25f)
            chromaMultiplier(1.2f)
            // Legibility over busy, colourful content (covers scrolling under the bars): pull the
            // material toward the page colour, enough that labels stay readable even over a dark
            // cover in light mode; colour still shows through.
            tint(colors.background.copy(alpha = if (colors.isDark) 0.4f else 0.72f))
            // Light the glass from its own shape only. Haze derives part of the normals from the
            // content underneath by default, which scatters highlights over text and covers.
            contentNormalBlend(0f)
            // A crisp rim highlight and a gentle Fresnel lift at the edges, not a broad glare.
            specularIntensity(if (colors.isDark) 0.55f else 0.5f)
            specularExponent(24f)
            ambientResponse(if (colors.isDark) 0.16f else 0.15f)
            fresnelExponent(2.2f)
            chromaticAberrationStrength(0.15f)
            // Press motion comes from liquidPress (whole element); the material only brightens a little.
            pressed {
                lightingIntensity(0.4f)
                refractionMultiplier(1.15f)
            }
            if (!edges) {
                // Just the body of the material: no lens, rim light or edge shadow, so a piece
                // laid over the edges of two shapes reads as part of them (the liquid bridge).
                optics(
                    GlassDefaults.optics.copy(
                        depth = OpticalSizeValue.Fixed(1f),
                        blurRadius = OpticalSizeValue.Fixed(16.dp),
                        refractionStrength = 0f,
                        refractionDisplacement = 0.dp,
                    ),
                )
                specularIntensity(0f)
                ambientResponse(0f)
                edgeShadow(Color.Transparent)
                chromaticAberrationStrength(0f)
            }
        }
    }
    // Hairline inner rim, lit from above, that defines the edge like on iOS.
    val rimBrush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (colors.isDark) 0.28f else 0.6f), Color.White.copy(alpha = if (colors.isDark) 0.06f else 0.15f)),
    )
    // Soft light from above inside the body gives the curved, raised look of iOS glass.
    val sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (colors.isDark) 0.09f else 0.22f),
        0.55f to Color.White.copy(alpha = 0f),
    )
    val glass = hazeGlass(input = HazeInput.Sources(source), style = style, interactionSource = interactionSource)
        .background(sheen, shape)
    return if (edges) glass.border(0.75.dp, rimBrush, shape) else glass
}
