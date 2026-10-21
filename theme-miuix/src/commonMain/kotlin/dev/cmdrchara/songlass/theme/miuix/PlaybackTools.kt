package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.cmdrchara.songlass.designsystem.PlayerOutputAction
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
    OverlayBottomSheet(show = visible, title = title, onDismissRequest = dismiss,
        startAction = if (onBack != null && backLabel != null) ({
            MiuixIconAction(AppIcons.ArrowBack, backLabel, { if (visible) onBack() }, false, false, Modifier)
        }) else null,
        endAction = { MiuixIconAction(AppIcons.Close, closeLabel, dismiss, false, false, Modifier) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel) }
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.title?.let { AppText(it, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(start = 18.dp).semantics { heading() }) }
                    MiuixGroup({
                        group.rows.forEach { row ->
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

/** One compact native row for device/audio, then independent native utility buttons. */
@Composable
internal fun MiuixPlayerUtilities(output: PlayerOutputAction?, audioLabel: String, onAudio: () -> Unit, actions: List<PlayerUtilityAction>, modifier: Modifier) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MiuixGroup({
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (output != null) {
                    BasicComponent(Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = output.accessibilityLabel },
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp), onClick = output.onClick, role = Role.Button,
                        startAction = { Box(Modifier.padding(end = 8.dp)) { AppIcon(AppIcons.AudioOutput, null, size = 22.dp) } },
                    ) { AppText(output.label, AppTheme.typography.footnote, color = colors.secondaryLabel, modifier = Modifier.clearAndSetSemantics {}) }
                } else Spacer(Modifier.weight(1f))
                Button(onClick = onAudio, modifier = Modifier.padding(4.dp).heightIn(min = 48.dp), cornerRadius = 16.dp,
                    colors = ButtonDefaults.buttonColors(color = colors.fill, contentColor = colors.label)) {
                    AppIcon(AppIcons.Equalizer, null, size = 20.dp)
                    Spacer(Modifier.width(6.dp))
                    AppText(audioLabel, AppTheme.typography.subheadline)
                }
            }
        }, Modifier)
        PlayerUtilityRows(actions, spacing = 8.dp) { action, _, _ ->
                Button(onClick = action.onClick, modifier = Modifier.weight(1f).heightIn(min = 58.dp).semantics { selected = action.selected; contentDescription = action.accessibilityLabel },
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    cornerRadius = 18.dp, colors = ButtonDefaults.buttonColors(
                        color = if (action.selected) colors.accent.copy(alpha = 0.16f) else colors.fill,
                        contentColor = if (action.selected) colors.accent else colors.label,
                    )) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        AppIcon(action.icon, null, size = 22.dp, tint = if (action.selected) colors.accent else colors.label)
                        AppText(action.label, AppTheme.typography.caption1.copy(textAlign = TextAlign.Center),
                            color = if (action.selected) colors.accent else colors.secondaryLabel, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
        }
    }
}
