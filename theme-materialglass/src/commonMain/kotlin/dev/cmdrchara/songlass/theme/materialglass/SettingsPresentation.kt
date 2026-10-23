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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    AppText(title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium), color = currentPalette().primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
internal fun MaterialSettingsDestination(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean, onClick: () -> Unit) {
    val palette = currentPalette()
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth()
        .clickable(interaction, MaterialRipple, role = Role.Button, onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MaterialSettingsCategoryIcon(icon, Modifier)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(title, AppTheme.typography.body, color = palette.label)
            AppText(summary, AppTheme.typography.subheadline, color = palette.secondaryLabel,
                maxLines = if (summaryIsValue) 2 else 3)
        }
        AppIcon(AppIcons.ChevronRight, null, size = 18.dp, tint = palette.secondaryLabel)
    }
}

@Composable
internal fun MaterialSettingsSearchResult(title: String, path: String, value: String?, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val interaction = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(MaterialShapes.ExtraLarge)).background(palette.container)
        .semantics(mergeDescendants = true) { }
        .clickable(interaction, MaterialRipple, role = Role.Button, onClick = onClick)
        .heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(title, AppTheme.typography.body, color = palette.label)
            AppText(path, AppTheme.typography.subheadline, color = palette.secondaryLabel)
            value?.let { AppText(it, AppTheme.typography.subheadline, color = palette.primary) }
        }
        AppIcon(AppIcons.ChevronRight, null, size = 18.dp, tint = palette.secondaryLabel)
    }
}

@Composable
internal fun MaterialSettingsCategoryIcon(icon: ImageVector, modifier: Modifier) {
    val palette = currentPalette()
    AppIcon(icon, null, size = 24.dp, tint = palette.primary, modifier = modifier)
}

@Composable
internal fun MaterialSettingsPreviewChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) {
    val palette = currentPalette()
    val shape = RoundedCornerShape(MaterialShapes.ExtraLarge)
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.clip(shape).background(if (selected) palette.primaryContainer else palette.container)
        .border(if (selected) 2.dp else 1.dp, if (selected) palette.primary else palette.secondaryLabel.copy(alpha = .15f), shape)
        .semantics(mergeDescendants = true) { }
        .selectable(selected, interaction, MaterialRipple, role = Role.RadioButton, onClick = onClick)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        preview()
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText(title, AppTheme.typography.footnote, modifier = Modifier.weight(1f),
                color = if (selected) palette.onPrimaryContainer else palette.label, maxLines = 2)
            Box(Modifier.size(20.dp).border(2.dp, if (selected) palette.primary else palette.secondaryLabel, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(10.dp).background(palette.primary, CircleShape))
            }
        }
    }
}
