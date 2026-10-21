package dev.cmdrchara.songlass.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Localized live values; the screen owns observations and actions, the style owns composition. */
@Immutable
data class PlaybackToolRow(
    val title: String,
    val value: String,
    val icon: ImageVector? = null,
    val onClick: (() -> Unit)? = null,
)

@Immutable
data class PlaybackToolGroup(val title: String?, val rows: List<PlaybackToolRow>, val ordered: Boolean = false)

@Immutable
data class PlayerUtilityAction(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit, val accessibilityLabel: String = label)

@Immutable
data class PlayerOutputAction(val label: String, val accessibilityLabel: String, val onClick: () -> Unit)

/** A single sheet owns both overview and detail pages; changing the page does not stack overlays. */
@Composable
fun PlaybackToolsSheet(
    visible: Boolean,
    title: String,
    subtitle: String?,
    groups: List<PlaybackToolGroup>,
    closeLabel: String,
    backLabel: String?,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
) = LocalUiStyle.current.PlaybackToolsSheet(visible, title, subtitle, groups, closeLabel, backLabel, onBack, onDismiss)

/** Output and audio settings are separate from labelled listening actions. */
@Composable
fun PlayerUtilityBar(
    output: PlayerOutputAction?,
    audioLabel: String,
    onAudio: () -> Unit,
    actions: List<PlayerUtilityAction>,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.PlayerUtilityBar(output, audioLabel, onAudio, actions, modifier)

/** Shared responsive allocation only; each look supplies its own control composition and shape. */
@Composable
fun PlayerUtilityRows(actions: List<PlayerUtilityAction>, spacing: Dp, content: @Composable RowScope.(PlayerUtilityAction, Int, Int) -> Unit) {
    if (actions.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minimumWidth = 64.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columns = when {
            maxWidth >= minimumWidth * actions.size + spacing * (actions.size - 1) -> actions.size
            maxWidth >= minimumWidth * 2 + spacing -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            actions.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEachIndexed { index, action -> content(action, index, row.size) }
                }
            }
        }
    }
}
