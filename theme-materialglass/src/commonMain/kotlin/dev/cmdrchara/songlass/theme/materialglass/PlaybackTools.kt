package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.PlaybackToolGroup
import dev.cmdrchara.songlass.designsystem.PlayerUtilityRows
import dev.cmdrchara.songlass.designsystem.PlayerUtilityAction
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalSheetTint
import dev.cmdrchara.songlass.theme.liquidglass.GlassPlaybackToolsHost
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook

/** Numbered audio path and connected tonal action groups, using the phone's semantic colors. */
@Composable
internal fun MaterialPlaybackTools(visible: Boolean, title: String, subtitle: String?, groups: List<PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, dismiss: () -> Unit, palette: SeaPalette) {
    val lineHeight = AppTheme.typography.headline.lineHeight
    val badgeSize = if (lineHeight.isSp) with(LocalDensity.current) { lineHeight.toDp() + 12.dp }.coerceAtLeast(36.dp) else 36.dp
    // The phone palette also owns the host/header; artwork tint must not recolor this panel.
    CompositionLocalProvider(LocalSheetTint provides null) {
    GlassPlaybackToolsHost(visible, title, subtitle, closeLabel, dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null && backLabel != null) LocalGlassLook.current.BackButton(backLabel, { if (visible) onBack() }, Modifier)
                Spacer(Modifier.weight(1f))
                dev.cmdrchara.songlass.designsystem.IconButton(AppIcons.Close, closeLabel, { if (visible) dismiss() })
            }
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    group.title?.let { AppText(it, AppTheme.typography.headline, color = palette.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp).semantics { heading() }) }
                    group.rows.forEachIndexed { index, row ->
                        val onClick = row.onClick
                        val shape = RoundedCornerShape(
                            topStart = if (index == 0) 22.dp else 5.dp, topEnd = if (index == 0) 22.dp else 5.dp,
                            bottomStart = if (index == group.rows.lastIndex) 22.dp else 5.dp, bottomEnd = if (index == group.rows.lastIndex) 22.dp else 5.dp,
                        )
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(palette.containerHigh)
                            .then(if (onClick != null) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = LocalGlassLook.current.pressIndication(),
                                enabled = visible, role = Role.Button, onClick = onClick,
                            ) else Modifier)
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (group.ordered || row.icon != null) {
                                Box(Modifier.size(if (group.ordered) badgeSize else 36.dp).clip(if (group.ordered) CircleShape else RoundedCornerShape(12.dp))
                                    .background(palette.primaryContainer), contentAlignment = Alignment.Center) {
                                    if (group.ordered) AppText((index + 1).toString(), AppTheme.typography.headline, color = palette.onPrimaryContainer)
                                    else row.icon?.let { AppIcon(it, null, size = 21.dp, tint = palette.onPrimaryContainer) }
                                }
                                Spacer(Modifier.width(14.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppText(row.title, AppTheme.typography.headline, color = palette.label)
                                AppText(row.value, AppTheme.typography.subheadline, color = palette.secondaryLabel)
                            }
                            if (onClick != null) {
                                Spacer(Modifier.width(8.dp))
                                AppIcon(AppIcons.ChevronRight, null, size = 18.dp, tint = palette.secondaryLabel)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/** Connected tonal icon buttons; the selected one becomes a rounded primary-container pill. */
@Composable
internal fun MaterialPlayerUtilities(actions: List<PlayerUtilityAction>, modifier: Modifier, palette: SeaPalette) {
    val look = LocalGlassLook.current
    PlayerUtilityRows(actions, spacing = 3.dp, modifier = modifier) { action, index, rowSize ->
        val shape = if (action.selected) RoundedCornerShape(22.dp) else RoundedCornerShape(
            topStart = if (index == 0) 22.dp else 6.dp, bottomStart = if (index == 0) 22.dp else 6.dp,
            topEnd = if (index == rowSize - 1) 22.dp else 6.dp, bottomEnd = if (index == rowSize - 1) 22.dp else 6.dp,
        )
        val foreground = if (action.selected) palette.onPrimaryContainer else palette.secondaryLabel
        Row(Modifier.weight(if (action.badge != null) 1.8f else 1f).heightIn(min = 48.dp).clip(shape)
            .background(if (action.selected) palette.primaryContainer else palette.containerHigh)
            .clickable(remember { MutableInteractionSource() }, look.pressIndication(), role = Role.Button, onClick = action.onClick)
            .semantics { selected = action.selected; contentDescription = action.accessibilityLabel }.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AppIcon(action.icon, null, size = 22.dp, tint = foreground)
            action.badge?.let { AppText(it, AppTheme.typography.caption1, color = foreground, modifier = Modifier.padding(start = 4.dp).clearAndSetSemantics {}, maxLines = 1) }
        }
    }
}
