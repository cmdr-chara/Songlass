package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.PlaybackToolGroup
import dev.cmdrchara.songlass.designsystem.PlayerUtilityRows
import dev.cmdrchara.songlass.designsystem.PlayerUtilityAction
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/** Native Miuix overlay, group and rows. No extra title or glass layer inside the sheet. */
@Composable
internal fun MiuixPlaybackTools(visible: Boolean, title: String, subtitle: String?, groups: List<PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, dismiss: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(title, visible) { scroll.scrollTo(0) }
    OverlayBottomSheet(show = visible, title = null, onDismissRequest = dismiss,
        // The native header overlays its slots. A single full-width slot reserves real space
        // for both native icon actions, so long titles wrap without colliding with Close.
        endAction = {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null && backLabel != null) {
                    MiuixIconAction(AppIcons.ArrowBack, backLabel, { if (visible) onBack() }, false, false, Modifier.size(48.dp))
                } else Spacer(Modifier.width(48.dp))
                AppText(title, AppTheme.typography.title3.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.colors.label, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() })
                MiuixIconAction(AppIcons.Close, closeLabel, { if (visible) dismiss() }, false, false, Modifier.size(48.dp))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 520.dp)
            .verticalScroll(scroll).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel) }
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.title?.let { AppText(it, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(start = 18.dp).semantics { heading() }) }
                    MiuixGroup({
                        group.rows.forEach rowLoop@ { row ->
                            val onClick = row.onClick
                            if (row.prominent && onClick != null) {
                                val colors = AppTheme.colors
                                val foreground = if (colors.accent.luminance() > 0.179f) Color.Black else Color.White
                                Button(onClick = { if (visible) onClick() }, enabled = visible,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp).heightIn(min = 48.dp),
                                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    cornerRadius = 18.dp,
                                    colors = ButtonDefaults.buttonColors(color = colors.accent, contentColor = foreground)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        row.icon?.let { AppIcon(it, null, size = 20.dp, tint = foreground); Spacer(Modifier.width(8.dp)) }
                                        AppText(row.title, AppTheme.typography.headline, color = foreground,
                                            modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
                                    }
                                }
                                return@rowLoop
                            }
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                                startAction = row.icon?.let { icon -> { Box(Modifier.padding(end = 14.dp)) { AppIcon(icon, null, size = 24.dp, tint = AppTheme.colors.accent) } } },
                                endActions = if (row.onClick != null) ({ AppIcon(AppIcons.ChevronRight, null, size = 18.dp, tint = AppTheme.colors.secondaryLabel) }) else null,
                                onClick = row.onClick?.let { callback -> { if (visible) callback() } },
                                role = if (row.onClick != null) Role.Button else null,
                            ) {
                                AppText(row.title, AppTheme.typography.body, color = AppTheme.colors.label)
                                AppText(row.value, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel)
                            }
                        }
                    }, Modifier)
                }
            }
        }
    }
}

/** Independent native buttons, one icon each; a live value sits beside its icon. */
@Composable
internal fun MiuixPlayerUtilities(actions: List<PlayerUtilityAction>, modifier: Modifier) {
    val colors = AppTheme.colors
    PlayerUtilityRows(actions, spacing = 8.dp, modifier = modifier) { action, _, _ ->
        Button(onClick = action.onClick, modifier = Modifier.weight(if (action.badge != null) 1.8f else 1f).heightIn(min = 48.dp).semantics { selected = action.selected; contentDescription = action.accessibilityLabel },
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            cornerRadius = 16.dp, colors = ButtonDefaults.buttonColors(
                color = if (action.selected) colors.accent.copy(alpha = 0.16f) else colors.fill,
                contentColor = if (action.selected) colors.accent else colors.label,
            )) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AppIcon(action.icon, null, size = 22.dp, tint = if (action.selected) colors.accent else colors.label)
                action.badge?.let { AppText(it, AppTheme.typography.caption1, color = colors.accent, modifier = Modifier.padding(start = 4.dp).clearAndSetSemantics {}, maxLines = 1) }
            }
        }
    }
}
