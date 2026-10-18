package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/**
 * How the shared glass components look. Structure, gestures and motion are the same for every
 * glass style; a look decides the materials and shapes. [LiquidLook] is Apple's Liquid Glass;
 * other styles (e.g. Material Glass) provide their own through [LocalGlassLook].
 */
@Stable
interface GlassLook {
    /**
     * Glass of navigation-layer elements (tab bar, search, mini player), refracting the content
     * layer published in [LocalGlassSource]. [edges] is false for the bare material of the
     * bridge between two shapes (no rim light or lens).
     */
    @Composable
    fun navigationSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier

    /**
     * A filled control inside the content layer (buttons, chips, fields, groups) in [color].
     * Content controls never sample the backdrop: they are part of what the glass refracts.
     * [prominent] marks the page's primary action.
     */
    @Composable
    fun controlSurface(modifier: Modifier, shape: Shape, color: Color, prominent: Boolean): Modifier

    /** Fill of the bubble that marks the selected tab. */
    @Composable
    fun selection(): Color

    /** Corner radius of cards and grouped surfaces designed at [base] for Liquid Glass. */
    fun corner(base: Dp): Dp
}

/** Apple's iOS 26 Liquid Glass. */
object LiquidLook : GlassLook {
    @Composable
    override fun navigationSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier =
        modifier.appleGlass(shape, interactionSource, edges)

    @Composable
    override fun controlSurface(modifier: Modifier, shape: Shape, color: Color, prominent: Boolean): Modifier =
        modifier.clip(shape).background(color)

    @Composable
    override fun selection(): Color = AppTheme.colors.fill

    override fun corner(base: Dp): Dp = base
}

val LocalGlassLook = staticCompositionLocalOf<GlassLook> { LiquidLook }
