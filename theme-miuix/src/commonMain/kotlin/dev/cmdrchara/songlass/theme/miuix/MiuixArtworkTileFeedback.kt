package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Static contour feedback remains visible with reduced motion without fading or clipping text. */
@Composable
internal fun miuixArtworkTileFeedback(modifier: Modifier, interactionSource: InteractionSource, shape: Shape): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    return when {
        focused && pressed -> modifier.border(3.dp, AppTheme.colors.accent, shape)
        focused -> modifier.border(2.dp, AppTheme.colors.accent, shape)
        pressed -> modifier.border(1.dp, AppTheme.colors.accent, shape)
        hovered -> modifier.border(1.dp, AppTheme.colors.secondaryLabel, shape)
        else -> modifier
    }
}
