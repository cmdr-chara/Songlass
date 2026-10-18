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
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
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

    // Components a look may redraw in its own shape. Kept here, not in a separate UiStyle, so a
    // change of look keeps the same composition tree (no rebuilt screens when switching style).

    @Composable
    fun SeekBar(positionMs: Long, durationMs: Long, qualityLabel: String?, isPlaying: Boolean, onSeek: (Long) -> Unit, modifier: Modifier) =
        LiquidGlassSeekBar(positionMs, durationMs, qualityLabel, onSeek, modifier)

    @Composable
    fun TransportControls(isPlaying: Boolean, labels: PlaybackLabels, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, modifier: Modifier) =
        LiquidGlassTransportControls(isPlaying, labels, onPrevious, onPlayPause, onNext, modifier)

    @Composable
    fun ListRow(
        title: String,
        subtitle: String?,
        leading: (@Composable () -> Unit)?,
        trailing: (@Composable () -> Unit)?,
        highlighted: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    ) = LiquidGlassListRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)

    @Composable
    fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier) = LiquidGlassSettingsGroup(content, modifier)
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
