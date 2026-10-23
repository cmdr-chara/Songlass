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
import androidx.compose.ui.text.font.FontWeight
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
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook

/** The audio path stays ordered; informative details and actionable groups have distinct roles. */
@Composable
internal fun MaterialPlaybackTools(visible: Boolean, title: String, subtitle: String?, groups: List<PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, dismiss: () -> Unit, palette: SeaPalette) {
    val lineHeight = AppTheme.typography.subheadline.lineHeight
    val badgeSize = if (lineHeight.isSp) with(LocalDensity.current) { lineHeight.toDp() + 8.dp }.coerceAtLeast(28.dp) else 28.dp
    CompositionLocalProvider(LocalSheetTint provides null) {
        MaterialSheetHost(
            visible = visible,
            title = title,
            subtitle = subtitle,
            closeLabel = closeLabel,
            onDismiss = dismiss,
            palette = palette,
            backLabel = backLabel,
            onBack = onBack,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                groups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        group.title?.let { AppText(it, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.primary, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }) }
                        group.rows.forEachIndexed { index, row ->
                            val onClick = row.onClick
                            if (row.prominent && onClick != null) {
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp)
                                    .clip(RoundedCornerShape(28.dp)).background(palette.primary)
                                    .clickable(remember { MutableInteractionSource() }, MaterialRipple,
                                        enabled = visible, role = Role.Button, onClick = { if (visible) onClick() })
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    row.icon?.let { AppIcon(it, null, size = 24.dp, tint = palette.onPrimary) }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AppText(row.title, AppTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                                            color = palette.onPrimary, textAlign = TextAlign.Center)
                                        if (row.value.isNotEmpty()) AppText(row.value, AppTheme.typography.subheadline,
                                            color = palette.onPrimary, textAlign = TextAlign.Center)
                                    }
                                }
                                return@forEachIndexed
                            }
                            val actionable = onClick != null
                            // Only adjacent actions share a connected tonal surface. Details remain ordinary text.
                            val firstAction = index == 0 || group.rows[index - 1].onClick == null
                            val lastAction = index == group.rows.lastIndex || group.rows[index + 1].onClick == null
                            val shape = RoundedCornerShape(
                                topStart = if (firstAction) 20.dp else 4.dp, topEnd = if (firstAction) 20.dp else 4.dp,
                                bottomStart = if (lastAction) 20.dp else 4.dp, bottomEnd = if (lastAction) 20.dp else 4.dp,
                            )
                            val surface = if (actionable) Modifier.heightIn(min = 56.dp).clip(shape)
                                .background(palette.primaryContainer)
                                .clickable(remember { MutableInteractionSource() }, MaterialRipple, enabled = visible,
                                    role = Role.Button, onClick = { if (visible) onClick() })
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                            else Modifier.padding(vertical = 8.dp)
                            Row(Modifier.fillMaxWidth().then(surface), verticalAlignment = if (actionable) Alignment.CenterVertically else Alignment.Top) {
                                if (group.ordered) {
                                    Box(Modifier.padding(top = 2.dp).size(badgeSize).clip(CircleShape)
                                        .background(palette.primaryContainer), contentAlignment = Alignment.Center) {
                                        AppText((index + 1).toString(), AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                                            color = palette.onPrimaryContainer)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                } else row.icon?.let {
                                    AppIcon(it, null, size = 22.dp, tint = if (actionable) palette.onPrimaryContainer else palette.secondaryLabel,
                                        modifier = if (actionable) Modifier else Modifier.padding(top = 1.dp))
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    AppText(row.title, AppTheme.typography.body.copy(fontWeight = if (actionable) FontWeight.Medium else FontWeight.Normal),
                                        color = if (actionable) palette.onPrimaryContainer else palette.label)
                                    if (row.value.isNotEmpty()) AppText(row.value, AppTheme.typography.subheadline,
                                        color = if (actionable) palette.onPrimaryContainer else palette.secondaryLabel)
                                }
                                if (actionable) {
                                    Spacer(Modifier.width(8.dp))
                                    AppIcon(AppIcons.ChevronRight, null, size = 18.dp, tint = palette.onPrimaryContainer,
                                        modifier = Modifier)
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
