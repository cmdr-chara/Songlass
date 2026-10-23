package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cmdrchara.songlass.designsystem.SettingsVisualChoice
import dev.cmdrchara.songlass.designsystem.SettingsVisualChoiceKind
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Four compact settings rows share one continuous group; visual previews remain uncomposed. */
@Composable
internal fun LiquidAppearanceChoices(
    kind: SettingsVisualChoiceKind,
    title: String,
    choices: List<SettingsVisualChoice>,
    closeLabel: String,
    description: String?,
    lastInGroup: Boolean,
    modifier: Modifier,
) {
    val icon = when (kind) {
        SettingsVisualChoiceKind.InterfaceStyle -> AppIcons.Palette
        SettingsVisualChoiceKind.Artwork -> AppIcons.Album
        SettingsVisualChoiceKind.AppIcon -> AppIcons.AppTile
    }
    val iconColor = when (kind) {
        SettingsVisualChoiceKind.InterfaceStyle -> AppTheme.colors.accent
        SettingsVisualChoiceKind.Artwork -> Color(0xFFB56E2E)
        SettingsVisualChoiceKind.AppIcon -> Color(0xFF7B65AD)
    }
    key(kind) {
        LiquidAppearanceOptionRow(title, choices.map { it.title }, choices.indexOfFirst { it.selected },
            { index -> choices[index].onClick() }, closeLabel, description, icon, iconColor,
            firstInGroup = kind == SettingsVisualChoiceKind.InterfaceStyle, lastInGroup = lastInGroup,
            accentValue = kind == SettingsVisualChoiceKind.InterfaceStyle, modifier = modifier)
    }
}

@Composable
internal fun LiquidAppearanceModeOptions(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    closeLabel: String,
    modifier: Modifier,
) {
    LiquidAppearanceOptionRow(title, labels, selectedIndex, onSelect, closeLabel, null, AppIcons.Contrast,
        Color(0xFF727883), firstInGroup = false, lastInGroup = false, accentValue = false, modifier = modifier)
}

@Composable
private fun LiquidAppearanceOptionRow(
    title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    closeLabel: String, description: String?, icon: ImageVector, iconColor: Color,
    firstInGroup: Boolean, lastInGroup: Boolean, accentValue: Boolean, modifier: Modifier,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    val colors = AppTheme.colors
    val value = labels.getOrNull(selectedIndex).orEmpty()
    val interaction = remember { MutableInteractionSource() }
    val pressed = liquidAppearancePressedColor(interaction)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(
        topStart = if (firstInGroup) 14.dp else 0.dp, topEnd = if (firstInGroup) 14.dp else 0.dp,
        bottomStart = if (lastInGroup) 14.dp else 0.dp, bottomEnd = if (lastInGroup) 14.dp else 0.dp,
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val reflow = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
        Row(Modifier.fillMaxWidth().clip(shape).background(colors.secondaryBackground).background(pressed)
            .drawBehind {
                if (!lastInGroup) {
                    val start = if (rtl) 16.dp.toPx() else 56.dp.toPx()
                    val end = size.width - if (rtl) 56.dp.toPx() else 16.dp.toPx()
                    val stroke = 0.5.dp.toPx()
                    drawLine(colors.label.copy(alpha = if (colors.isDark) 0.14f else 0.10f),
                        Offset(start, size.height - stroke / 2), Offset(end, size.height - stroke / 2), stroke)
                }
            }
            .semantics(mergeDescendants = true) { contentDescription = title; stateDescription = value }
            .clickable(interaction, indication = null, enabled = labels.isNotEmpty(), role = Role.Button) { expanded = true }
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(iconColor), contentAlignment = Alignment.Center) {
                AppIcon(icon, null, size = 18.dp, tint = Color.White)
            }
            if (reflow) Column(Modifier.weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AppText(title, AppTheme.typography.body, color = colors.label)
                AppText(value, AppTheme.typography.subheadline, color = if (accentValue) colors.accent else colors.secondaryLabel)
            } else Row(Modifier.weight(1f).clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(title, AppTheme.typography.body, color = colors.label, modifier = Modifier.weight(1f))
                AppText(value, AppTheme.typography.subheadline, color = if (accentValue) colors.accent else colors.secondaryLabel,
                    modifier = Modifier.widthIn(max = 200.dp), textAlign = TextAlign.End)
            }
            AppIcon(AppIcons.ChevronRight, null, size = 12.dp, tint = colors.tertiaryLabel,
                modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f })
        }
    }
    if (expanded) LiquidAppearancePicker(title, labels, selectedIndex, closeLabel, description,
        onDismiss = { expanded = false }, onSelect = { index -> onSelect(index); expanded = false })
}

/** Feedback stays within the row's clipped neutral surface and respects Reduce Motion. */
@Composable
private fun liquidAppearancePressedColor(interaction: MutableInteractionSource): Color {
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(
        if (pressed) AppTheme.colors.fill else Color.Transparent,
        if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "appearanceRowPress",
    )
    return color
}

/** A bounded textual picker: selected options use a trailing checkmark, not a miniature preview. */
@Composable
private fun LiquidAppearancePicker(
    title: String, labels: List<String>, selectedIndex: Int, closeLabel: String, description: String?,
    onDismiss: () -> Unit, onSelect: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)).padding(20.dp),
            contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null,
                onClickLabel = closeLabel, onClick = onDismiss).semantics { contentDescription = closeLabel })
            val availableHeight = maxHeight
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = availableHeight)
                .clip(RoundedCornerShape(20.dp)).background(colors.secondaryBackground)
                .pointerInput(Unit) { detectTapGestures { } }.semantics { paneTitle = title }) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppText(title, AppTheme.typography.title3, color = colors.label,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    val closeInteraction = remember { MutableInteractionSource() }
                    val closePressed = liquidAppearancePressedColor(closeInteraction)
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(closePressed)
                        .clickable(closeInteraction, indication = null, role = Role.Button, onClick = onDismiss)
                        .semantics { contentDescription = closeLabel }, contentAlignment = Alignment.Center) {
                        AppIcon(AppIcons.Close, null, size = 18.dp, tint = colors.secondaryLabel)
                    }
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                    .selectableGroup().padding(bottom = 8.dp)) {
                    description?.let { AppText(it, AppTheme.typography.footnote, color = colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) }
                    labels.forEachIndexed { index, label ->
                        val interaction = remember { MutableInteractionSource() }
                        val pressed = liquidAppearancePressedColor(interaction)
                        Row(Modifier.fillMaxWidth().background(pressed)
                            .selectable(index == selectedIndex, interaction, indication = null, role = Role.RadioButton,
                                onClick = { onSelect(index) })
                            .heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppText(label, AppTheme.typography.body, color = colors.label, modifier = Modifier.weight(1f))
                            Box(Modifier.size(20.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                                if (index == selectedIndex) AppIcon(AppIcons.Checkmark, null, size = 20.dp, tint = colors.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
