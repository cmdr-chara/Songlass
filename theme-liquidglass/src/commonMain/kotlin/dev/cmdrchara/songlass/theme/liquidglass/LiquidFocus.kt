package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Draw focus inside the existing control bounds without adding another focus target. */
@Composable
internal fun Modifier.liquidFocusIndicator(
    interactionSource: InteractionSource,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return if (enabled && focused) border(2.dp, AppTheme.colors.accent, shape) else this
}
