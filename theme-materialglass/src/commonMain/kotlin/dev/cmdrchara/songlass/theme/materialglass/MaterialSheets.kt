package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook

/** Action hierarchy uses a single tonal group; nested Back lives in the header. */
@Composable
internal fun MaterialActionSheet(
    visible: Boolean,
    title: String,
    subtitle: String?,
    artwork: Artwork?,
    actions: List<SheetAction>,
    cancelLabel: String,
    onDismiss: () -> Unit,
    palette: SeaPalette,
) {
    val back = actions.firstOrNull { it.isBackAction }
    val entries = actions.filterNot { it.isBackAction }
    MaterialSheetHost(visible, title, subtitle, artwork, cancelLabel, onDismiss, palette,
        backLabel = back?.label, onBack = back?.onClick) {
        val look = LocalGlassLook.current
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            entries.forEachIndexed { index, action ->
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) MaterialShapes.Large else 4.dp,
                    topEnd = if (index == 0) MaterialShapes.Large else 4.dp,
                    bottomStart = if (index == entries.lastIndex) MaterialShapes.Large else 4.dp,
                    bottomEnd = if (index == entries.lastIndex) MaterialShapes.Large else 4.dp,
                )
                Row(Modifier.fillMaxWidth().clip(shape).background(palette.containerHigh)
                    .clickable(remember { MutableInteractionSource() }, look.pressIndication(),
                        enabled = visible, role = Role.Button, onClick = {
                            if (visible) {
                                if (action.dismissOnClick) onDismiss()
                                action.onClick()
                            }
                        })
                    .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    action.icon?.let { AppIcon(it, null, size = 24.dp, tint = palette.primary) }
                    AppText(action.label, AppTheme.typography.body, color = palette.label, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Labels and values form one reading column rather than a stack of separate cards. */
@Composable
internal fun MaterialInfoSheet(
    visible: Boolean,
    title: String,
    rows: List<InfoRow>,
    closeLabel: String,
    onDismiss: () -> Unit,
    palette: SeaPalette,
) {
    MaterialSheetHost(visible, title, null, closeLabel = closeLabel, onDismiss = onDismiss, palette = palette) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.secondaryLabel.copy(alpha = 0.16f)))
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(row.label, AppTheme.typography.subheadline, color = palette.secondaryLabel)
                    AppText(row.value, AppTheme.typography.body, color = palette.label)
                }
            }
        }
    }
}
