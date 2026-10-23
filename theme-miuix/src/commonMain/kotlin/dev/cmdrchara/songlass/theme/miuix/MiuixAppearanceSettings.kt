package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.SettingsVisualChoice
import dev.cmdrchara.songlass.designsystem.SettingsVisualChoiceKind
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/** Preference rows share a single neutral outline; no miniature interface or artwork previews. */
@Composable
internal fun MiuixSettingsVisualChoices(
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
    AppearancePreference(
        title = title,
        labels = choices.map { it.title },
        selectedIndex = choices.indexOfFirst { it.selected },
        onSelect = { index -> choices[index].onClick() },
        closeLabel = closeLabel,
        icon = icon,
        firstInGroup = kind == SettingsVisualChoiceKind.InterfaceStyle,
        lastInGroup = lastInGroup,
        description = description,
        modifier = modifier,
    )
}

@Composable
internal fun MiuixSettingsAppearanceModeOptions(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    closeLabel: String,
    modifier: Modifier,
) = AppearancePreference(title, labels, selectedIndex, onSelect, closeLabel, AppIcons.Contrast,
    firstInGroup = false, lastInGroup = false, description = null, modifier = modifier)

@Composable
internal fun AppearancePreference(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    closeLabel: String,
    icon: ImageVector?,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    description: String?,
    modifier: Modifier,
    contentInset: Dp = 16.dp,
) {
    if (labels.isEmpty()) return
    var expanded by remember(title) { mutableStateOf(false) }
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = AppTheme.typography.body
    val valueStyle = AppTheme.typography.subheadline.copy(fontWeight = if (firstInGroup) FontWeight.Medium else FontWeight.Normal)
    val value = labels.getOrNull(selectedIndex).orEmpty()
    val textWidth = with(density) {
        (measurer.measure(title, titleStyle).size.width + measurer.measure(value, valueStyle).size.width).toDp()
    }
    val shape = RoundedCornerShape(
        topStart = if (firstInGroup) 22.dp else 0.dp,
        topEnd = if (firstInGroup) 22.dp else 0.dp,
        bottomStart = if (lastInGroup) 22.dp else 0.dp,
        bottomEnd = if (lastInGroup) 22.dp else 0.dp,
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val reflow = density.fontScale > 1.3f || maxWidth < 340.dp || textWidth + contentInset * 2 + (if (icon != null) 80.dp else 40.dp) > maxWidth
        Card(Modifier.fillMaxWidth().clip(shape), cornerRadius = 0.dp, insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(color = colors.secondaryBackground)) {
            BasicComponent(
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics(mergeDescendants = true) { role = Role.Button },
                insideMargin = PaddingValues(horizontal = contentInset, vertical = 12.dp),
                startAction = icon?.let { leading -> {
                    Box(Modifier.padding(end = 12.dp).size(28.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (firstInGroup) colors.accent.copy(alpha = 0.13f) else colors.fill),
                        contentAlignment = Alignment.Center) {
                        AppIcon(leading, null, size = 19.dp, tint = if (firstInGroup) colors.accent else colors.secondaryLabel)
                    }
                } },
                endActions = {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!reflow) AppText(value, valueStyle,
                            color = if (firstInGroup) colors.accent else colors.secondaryLabel)
                        AppIcon(AppIcons.ChevronRight, null, size = 16.dp, tint = colors.tertiaryLabel)
                    }
                },
                onClick = { expanded = true },
            ) {
                AppText(title, titleStyle, color = colors.label)
                if (reflow) AppText(value, valueStyle, color = if (firstInGroup) colors.accent else colors.secondaryLabel,
                    modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
    OverlayBottomSheet(
        show = expanded,
        title = title,
        onDismissRequest = { expanded = false },
        endAction = { MiuixIconAction(AppIcons.Close, closeLabel, { expanded = false }, false, false, Modifier) },
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(expanded) { if (expanded) scroll.scrollTo(0) }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 480.dp)
            .verticalScroll(scroll).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            description?.takeIf(String::isNotBlank)?.let {
                AppText(it, AppTheme.typography.subheadline, color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(color = colors.secondaryBackground)) {
                labels.forEachIndexed { index, label ->
                    val checked = index == selectedIndex
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                            .background(if (checked) colors.accent.copy(alpha = 0.07f) else colors.secondaryBackground)
                            .semantics(mergeDescendants = true) { role = Role.RadioButton; selected = checked },
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        endActions = {
                            Box(Modifier.padding(start = 16.dp).clearAndSetSemantics { }) {
                                RadioButton(selected = checked, onClick = null)
                            }
                        },
                        onClick = {
                            if (expanded) {
                                onSelect(index)
                                expanded = false
                            }
                        },
                    ) {
                        AppText(label, AppTheme.typography.body.copy(fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal),
                            color = if (checked) colors.accent else colors.label)
                    }
                }
            }
        }
    }
}
