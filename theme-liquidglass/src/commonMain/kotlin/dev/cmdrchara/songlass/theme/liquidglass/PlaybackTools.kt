package dev.cmdrchara.songlass.theme.liquidglass

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
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

/** Shared overlay mechanics only. Material supplies its own content composition below this host. */
@Composable
fun GlassPlaybackToolsHost(
    visible: Boolean,
    title: String,
    subtitle: String?,
    closeLabel: String,
    onDismiss: () -> Unit,
    headerLeading: (@Composable () -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    LiquidGlassActionSheet(visible, title, subtitle, null, emptyList(), closeLabel, onDismiss,
        field = content, contentKey = title, headerLeading = headerLeading, headerTrailing = headerTrailing)
}

/** Inset grouped rows: content stays opaque while the player/navigation retain their glass. */
@Composable
internal fun LiquidPlaybackTools(visible: Boolean, title: String, subtitle: String?, groups: List<PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, dismiss: () -> Unit) {
    GlassPlaybackToolsHost(visible, title, subtitle, closeLabel, dismiss) {
        val colors = AppTheme.colors
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onBack != null && backLabel != null) {
                LocalGlassLook.current.BackButton(backLabel, { if (visible) onBack() }, Modifier)
            }
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.title?.let { AppText(it, AppTheme.typography.footnote, color = colors.secondaryLabel,
                        modifier = Modifier.padding(start = 12.dp).semantics { heading() }) }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.background)) {
                        group.rows.forEachIndexed { index, row ->
                            val onClick = row.onClick
                            if (row.prominent && onClick != null) {
                                val interaction = remember { MutableInteractionSource() }
                                val scale = pressScale(interaction)
                                val foreground = if (colors.accent.luminance() > 0.179f) Color.Black else Color.White
                                Row(Modifier.fillMaxWidth().padding(8.dp).heightIn(min = 48.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(RoundedCornerShape(14.dp)).background(colors.accent)
                                    .clickable(interaction, indication = null, enabled = visible, role = Role.Button,
                                        onClick = { if (visible) onClick() })
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    row.icon?.let { AppIcon(it, null, size = 20.dp, tint = foreground); Spacer(Modifier.width(8.dp)) }
                                    AppText(row.title, AppTheme.typography.headline, color = foreground,
                                        modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
                                }
                                return@forEachIndexed
                            }
                            if (index > 0) Box(Modifier.fillMaxWidth().padding(start = if (row.icon != null) 54.dp else 14.dp)
                                .height(0.5.dp).background(colors.separator))
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .then(if (onClick != null) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() }, indication = LocalGlassLook.current.pressIndication(),
                                    enabled = visible, role = Role.Button, onClick = onClick,
                                ) else Modifier)
                                .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                row.icon?.let {
                                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(colors.fill), contentAlignment = Alignment.Center) {
                                        AppIcon(it, null, size = 19.dp, tint = colors.accent)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    AppText(row.title, AppTheme.typography.body, color = colors.label)
                                    AppText(row.value, AppTheme.typography.footnote, color = colors.secondaryLabel)
                                }
                                if (onClick != null) {
                                    Spacer(Modifier.width(8.dp))
                                    AppIcon(AppIcons.ChevronRight, null, size = 16.dp, tint = colors.secondaryLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A single row of icons; a selected one gets a soft tonal backdrop, a live value sits beside its icon. */
@Composable
internal fun LiquidPlayerUtilities(actions: List<PlayerUtilityAction>, modifier: Modifier) {
    val colors = AppTheme.colors
    PlayerUtilityRows(actions, spacing = 4.dp, modifier = modifier) { action, _, _ ->
        Row(Modifier.weight(if (action.badge != null) 1.8f else 1f).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
            .background(if (action.selected) colors.fill else Color.Transparent)
            .clickable(role = Role.Button, onClick = action.onClick).semantics { selected = action.selected; contentDescription = action.accessibilityLabel }
            .padding(horizontal = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AppIcon(action.icon, null, size = 24.dp, tint = if (action.selected) colors.accent else colors.label)
            action.badge?.let { AppText(it, AppTheme.typography.caption1, color = colors.accent, modifier = Modifier.padding(start = 4.dp).clearAndSetSemantics {}, maxLines = 1) }
        }
    }
}
