package dev.cmdrchara.songlass.designsystem

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * Theme feedback for an artwork tile without another click target or a content clip.
 * Apply to the same layout node as clickable and share its interaction source so press
 * coordinates, focus, and feedback all describe the whole tile.
 */
@Composable
fun Modifier.artworkTileFeedback(interactionSource: InteractionSource, shape: Shape): Modifier =
    LocalUiStyle.current.artworkTileFeedback(this, interactionSource, shape)
