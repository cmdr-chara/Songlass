package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import dev.cmdrchara.songlass.designsystem.SettingsVisualChoice
import dev.cmdrchara.songlass.designsystem.SettingsVisualChoiceKind
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** One tonal settings surface; rows share its outline instead of becoming individual cards. */
@Composable
internal fun MaterialSettingsDestinationGroup(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MaterialShapes.LargeIncreased))
            .background(currentPalette().container)
            .padding(vertical = 4.dp),
    ) { content() }
}

@Composable
internal fun MaterialSettingsSectionTitle(title: String) {
    AppText(title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = currentPalette().primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp).semantics { heading() })
}

@Composable
internal fun MaterialSettingsDestination(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean, onClick: () -> Unit) {
    val palette = currentPalette()
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth()
        .clickable(interaction, MaterialRipple, role = Role.Button, onClick = onClick)
        .heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MaterialSettingsCategoryIcon(icon, Modifier)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(title, AppTheme.typography.body.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium), color = palette.label)
            AppText(summary, AppTheme.typography.subheadline, color = palette.secondaryLabel,
                maxLines = if (summaryIsValue) 2 else 3)
        }
        MaterialSettingsChevron()
    }
}

@Composable
internal fun MaterialSettingsSearchResult(title: String, path: String, value: String?, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val interaction = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(MaterialShapes.LargeIncreased)).background(palette.container)
        .semantics(mergeDescendants = true) { }
        .clickable(interaction, MaterialRipple, role = Role.Button, onClick = onClick)
        .heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(title, AppTheme.typography.body.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium), color = palette.label)
            AppText(path, AppTheme.typography.subheadline, color = palette.secondaryLabel, maxLines = 3)
            value?.let { AppText(it, AppTheme.typography.subheadline, color = palette.primary) }
        }
        MaterialSettingsChevron()
    }
}

@Composable
internal fun MaterialSettingsCategoryIcon(icon: ImageVector, modifier: Modifier) {
    val palette = currentPalette()
    Box(modifier.size(36.dp).clip(RoundedCornerShape(MaterialShapes.Medium)).background(palette.primaryContainer),
        contentAlignment = Alignment.Center) {
        AppIcon(icon, null, size = 22.dp, tint = palette.onPrimaryContainer)
    }
}

@Composable
internal fun MaterialSettingsPreviewChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) {
    val palette = currentPalette()
    val shape = RoundedCornerShape(MaterialShapes.ExtraLarge)
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.clip(shape).background(if (selected) palette.containerHigh else palette.container)
        .semantics(mergeDescendants = true) { }
        .selectable(selected, interaction, MaterialRipple, role = Role.RadioButton, onClick = onClick)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.clearAndSetSemantics { }) { preview() }
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText(title, AppTheme.typography.subheadline, modifier = Modifier.weight(1f),
                color = palette.label, maxLines = 3)
            Box(Modifier.size(20.dp).border(2.dp, if (selected) palette.primary else palette.secondaryLabel, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(10.dp).background(palette.primary, CircleShape))
            }
        }
    }
}

/** Appearance stays compact; launcher choices show their real icon beside the label. */
@Composable
internal fun MaterialSettingsVisualChoices(
    kind: SettingsVisualChoiceKind,
    title: String,
    choices: List<SettingsVisualChoice>,
    closeLabel: String,
    description: String?,
    modifier: Modifier,
) {
    key(kind) {
        MaterialSettingsChoiceOptions(
            title = title,
            labels = choices.map { it.title },
            selectedIndex = choices.indexOfFirst { it.selected },
            onSelect = { index -> choices[index].onClick() },
            closeLabel = closeLabel,
            modifier = modifier.then(if (kind == SettingsVisualChoiceKind.InterfaceStyle) Modifier.padding(bottom = 4.dp) else Modifier),
            inlineTitle = true,
            description = description,
            leadingIcon = when (kind) {
                SettingsVisualChoiceKind.InterfaceStyle -> AppIcons.Palette
                SettingsVisualChoiceKind.Artwork -> AppIcons.Album
                SettingsVisualChoiceKind.AppIcon -> AppIcons.AppTile
            },
            emphasized = kind == SettingsVisualChoiceKind.InterfaceStyle,
            optionPreview = if (kind == SettingsVisualChoiceKind.AppIcon) { index -> choices[index].preview() } else null,
        )
    }
}

@Composable
private fun MaterialSettingsChevron() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    AppIcon(AppIcons.ChevronRight, null, size = 18.dp, tint = currentPalette().secondaryLabel,
        modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f })
}
