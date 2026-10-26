package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** A restrained press does not fade or clip the title; focus uses the existing Liquid outline. */
@Composable
internal fun liquidArtworkTileFeedback(modifier: Modifier, interactionSource: InteractionSource, shape: Shape): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.97f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "artworkTilePress",
    )
    return modifier
        .graphicsLayer {
            val boundedScale = scale.coerceIn(0.97f, 1f)
            scaleX = boundedScale
            scaleY = boundedScale
        }
        // A focused tile still shows the press when reduced motion keeps its scale fixed.
        .then(if (pressed) Modifier.border(if (reduceMotion && focused) 3.dp else 1.dp, AppTheme.colors.accent, shape) else Modifier)
        .liquidFocusIndicator(interactionSource, shape)
}
