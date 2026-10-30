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
import androidx.compose.ui.draw.shadow
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
import dev.cmdrchara.songlass.designsystem.theme.LocalBlurStrength

/**
 * The content layer that glass elements refract. Set by the scaffold; `null` outside it,
 * in which case glass falls back to an opaque surface.
 */
val LocalGlassSource = staticCompositionLocalOf<HazeState?> { null }

/**
 * Regular Liquid Glass background for navigation-layer elements (tab bar, mini player).
 * Per the HIG, never use it for content-layer elements.
 *
 * This is the only place that touches Haze's experimental glass API, so swapping the
 * implementation (or library) only changes this function.
 */
@Composable
internal fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    interactionSource: InteractionSource? = null,
    edges: Boolean = true,
): Modifier = LocalGlassLook.current.navigationSurface(this, shape, interactionSource, edges)

/** The tab bar can tune its lens without changing other navigation-layer controls. */
@Composable
internal fun Modifier.navigationBarGlass(
    shape: RoundedCornerShape,
    interactionSource: InteractionSource? = null,
    edges: Boolean = true,
): Modifier = LocalGlassLook.current.navigationBarSurface(this, shape, interactionSource, edges)

/** Songlass's Liquid Glass approximation, the default [GlassLook.navigationSurface]. */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.appleGlass(
    shape: RoundedCornerShape,
    interactionSource: InteractionSource?,
    edges: Boolean,
    navigationBar: Boolean = false,
    forceGlass: Boolean = false,
): Modifier {
    val colors = AppTheme.colors
    val accessibility = AppTheme.accessibility
    val source = LocalGlassSource.current
    // Off removes the blur layer instead of keeping a zero-radius lens: the opaque page surface is
    // the readable baseline, exactly like Reduce Transparency.
    val blur = LocalBlurStrength.current
    if (!forceGlass && LocalLiquidFinish.current == LiquidFinish.Metal) {
        return liquidMetal(shape, interactionSource, edges, navigationBar)
    }
    if (accessibility.reduceTransparency || !blur.blurred || source == null) {
        return clip(shape).background(colors.secondaryBackground)
    }
    val style = remember(shape, colors, accessibility.reduceMotion, edges, navigationBar, blur) {
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
                    blurRadius = OpticalSizeValue.Fixed(blur.scale(20.dp)),
                    refractionDisplacement = if (navigationBar) 10.dp else 9.dp,
                    refractionStrength = if (navigationBar) 0.38f else 0.36f,
                ),
            )
            // Apple's glass lets colors through: less milky white than Haze's defaults
            // (whitePoint 0.55 light / -0.22 dark). Dark glass is smoked, not black, so the
            // shape still reads over a black page. Chroma stays near neutral, otherwise glass
            // over a tinted page turns into a darker, more saturated slab than the page itself.
            whitePoint(if (navigationBar) { if (colors.isDark) -0.02f else 0.20f } else { if (colors.isDark) -0.06f else 0.16f })
            chromaMultiplier(1.04f)
            // Legibility over busy, colourful content (covers scrolling under the bars): pull the
            // material toward the page colour, enough that labels stay readable even over a dark
            // cover in light mode; colour still shows through.
            tint(colors.background.copy(alpha = if (navigationBar) { if (colors.isDark) 0.52f else 0.66f } else { if (colors.isDark) 0.44f else 0.60f }))
            // Light the glass from its own shape only. Haze derives part of the normals from the
            // content underneath by default, which scatters highlights over text and covers.
            contentNormalBlend(0f)
            // A crisp rim highlight and a gentle Fresnel lift at the edges, not a broad glare.
            specularIntensity(if (navigationBar) 0.24f else 0.30f)
            specularExponent(32f)
            ambientResponse(if (colors.isDark) 0.16f else 0.15f)
            fresnelExponent(2.2f)
            chromaticAberrationStrength(0.018f)
            // Press motion comes from liquidPress (whole element); the material only brightens a little.
            pressed {
                lightingIntensity(0.4f)
                refractionMultiplier(if (accessibility.reduceMotion) 1f else 1.15f)
            }
            if (!edges) {
                // Just the body of the material: no lens, rim light or edge shadow, so a piece
                // laid over the edges of two shapes reads as part of them (the liquid bridge).
                optics(
                    GlassDefaults.optics.copy(
                        depth = OpticalSizeValue.Fixed(1f),
                        blurRadius = OpticalSizeValue.Fixed(blur.scale(20.dp)),
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
    // One lighting profile for the capsule and connecting waist, with a narrow rim
    // and a restrained body reflection. Keep the mini player's separate recipe.
    val rimBrush = remember(colors.isDark, navigationBar) {
        if (navigationBar) liquidNavigationRim(colors.isDark)
        else Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (colors.isDark) 0.23f else 0.38f),
            Color.White.copy(alpha = if (colors.isDark) 0.04f else 0.08f),
        ))
    }
    val sheen = remember(colors.isDark, navigationBar) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (navigationBar) {
                if (colors.isDark) 0.05f else 0.10f
            } else if (colors.isDark) 0.035f else 0.07f),
            0.55f to Color.Transparent,
        )
    }
    val glass = hazeGlass(input = HazeInput.Sources(source), style = style, interactionSource = interactionSource)
        .background(sheen, shape)
    return if (edges) glass.border(if (navigationBar) 0.65.dp else 0.5.dp, rimBrush, shape) else glass
}

/** Original navigation lighting shared by the capsule and its connecting waist. */
internal fun liquidNavigationRim(isDark: Boolean): Brush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = if (isDark) 0.30f else 0.42f),
    0.36f to Color.White.copy(alpha = if (isDark) 0.06f else 0.10f),
    0.70f to Color.White.copy(alpha = if (isDark) 0.03f else 0.05f),
    1f to Color.White.copy(alpha = if (isDark) 0.17f else 0.23f),
)

/** Independent lens over the same page source, never a blur of the bar or its labels. */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.navigationLens(shape: RoundedCornerShape): Modifier {
    val colors = AppTheme.colors
    val source = LocalGlassSource.current
    val blur = LocalBlurStrength.current
    if (AppTheme.accessibility.reduceTransparency || !blur.blurred || source == null) {
        return clip(shape).background(
            androidx.compose.ui.graphics.lerp(colors.secondaryBackground, colors.label, 0.10f),
        ).border(0.75.dp, colors.label.copy(alpha = 0.14f), shape)
    }
    // A fixed optical recipe: movement transforms the lens, never animates its blur.
    val style = remember(shape, colors, blur) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(colors.background)
            optics(GlassDefaults.optics.copy(
                depth = OpticalSizeValue.Fixed(1f),
                blurRadius = OpticalSizeValue.Fixed(blur.scale(12.dp)),
                refractionDisplacement = 26.dp,
                refractionStrength = 0.64f,
            ))
            tint(colors.secondaryBackground.copy(alpha = if (colors.isDark) 0.50f else 0.58f))
            whitePoint(if (colors.isDark) -0.02f else 0.24f)
            chromaMultiplier(1.05f)
            contentNormalBlend(0f)
            specularIntensity(0.48f)
            specularExponent(20f)
            ambientResponse(0.18f)
            fresnelExponent(2.5f)
            chromaticAberrationStrength(0.05f)
        }
    }
    val rim = Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (colors.isDark) 0.40f else 0.60f),
        colors.label.copy(alpha = 0.10f),
        Color.White.copy(alpha = if (colors.isDark) 0.24f else 0.36f),
    ))
    val sheen = Brush.verticalGradient(listOf(
        Color.White.copy(alpha = if (colors.isDark) 0.06f else 0.11f),
        Color.Transparent,
        Color.Black.copy(alpha = if (colors.isDark) 0.10f else 0.035f),
    ))
    return shadow(6.dp, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = 0.10f),
        spotColor = Color.Black.copy(alpha = 0.16f))
        .hazeGlass(input = HazeInput.Sources(source), style = style)
        .background(sheen, shape)
        .border(0.75.dp, rim, shape)
}
