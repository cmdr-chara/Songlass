package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.ArtworkTint
import dev.cmdrchara.songlass.designsystem.theme.LocalAppColors
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor

/** Cross-fades between tints when the artwork changes (instantly with Reduce Motion). */
@Composable
internal fun animateArtworkTint(target: ArtworkTint): ArtworkTint {
    val spec = if (AppTheme.accessibility.reduceMotion) snap() else tween<androidx.compose.ui.graphics.Color>(LiquidGlassMotion.SheetMillis)
    val background by animateColorAsState(target.background, spec, label = "tintBackground")
    val content by animateColorAsState(target.content, spec, label = "tintContent")
    return target.copy(
        background = background,
        content = content,
        secondaryContent = content.copy(alpha = target.secondaryContent.alpha),
        fill = content.copy(alpha = target.fill.alpha),
        activeFill = content,
        onActiveFill = background,
    )
}

/** Makes every standard component below draw in [tint]: colors, content color and lyrics. */
@Composable
internal fun ProvideArtworkTint(tint: ArtworkTint, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides tint.applyTo(AppTheme.colors),
        LocalContentColor provides tint.content,
        LocalLyricsTint provides tint.content,
        content = content,
    )
}
