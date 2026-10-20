package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight

internal val LocalMiuixBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Original Songlass recipe, using the official Miuix backdrop and highlight APIs. */
@Composable
internal fun miuixSurface(modifier: Modifier, shape: RoundedCornerShape, selected: Boolean = false): Modifier {
    val colors = AppTheme.colors
    val fill = if (selected) colors.fill else colors.secondaryBackground
    val backdrop = LocalMiuixBackdrop.current.takeUnless { AppTheme.accessibility.reduceTransparency }
    val base = modifier.shadow(if (selected) 2.dp else 10.dp, shape, clip = false).clip(shape)
    if (backdrop == null) {
        return base.background(fill)
            .border(0.75.dp, if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.75f), shape)
    }
    val fillAlpha = if (selected) 0.84f else if (colors.isDark) 0.80f else 0.72f
    val rim = when {
        selected && colors.isDark -> Highlight.GlassStrokeSmallDark
        selected -> Highlight.GlassStrokeSmallLight
        colors.isDark -> Highlight.GlassStrokeMiddleDark
        else -> Highlight.GlassStrokeMiddleLight
    }
    // Every surface samples only page content, so the selected bubble never blurs the bar again.
    return base.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur((if (selected) 12.dp else 24.dp).toPx())
            colorControls(saturation = if (colors.isDark) 1.08f else 1.12f)
        },
        // Tint belongs after filtering. Chaining it into the backdrop effect produced
        // a sharp horizontal band on the Android renderer instead of a continuous surface.
        onDrawSurface = { drawRect(fill.copy(alpha = fillAlpha)) },
        highlight = { rim },
    )
}
