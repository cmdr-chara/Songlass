package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.RadioButton

@Composable
internal fun MiuixSettingsSectionTitle(title: String) {
    AppText(title, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 30.dp, end = 24.dp, top = 24.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
internal fun MiuixSettingsDestinationGroup(modifier: Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground)) { content() }
}

@Composable
internal fun MiuixSettingsDestination(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val reflow = largeText || maxWidth < 340.dp
        MiuixRow(title, if (summaryIsValue && reflow) summary else null,
            leading = { MiuixSettingsCategoryIcon(icon, Modifier) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summaryIsValue && !reflow) AppText(summary, AppTheme.typography.subheadline,
                        modifier = Modifier.widthIn(max = 144.dp), color = colors.secondaryLabel, maxLines = 1)
                    AppIcon(AppIcons.ChevronRight, null, size = 16.dp, tint = colors.tertiaryLabel)
                }
            }, highlighted = false, onClick = onClick, modifier = Modifier)
    }
}

@Composable
internal fun MiuixSettingsCategoryIcon(icon: ImageVector, modifier: Modifier) {
    val color = when (icon) {
        AppIcons.Settings -> Color(0xFF4C85F5)
        AppIcons.Home -> Color(0xFF8C6EF2)
        AppIcons.Equalizer -> Color(0xFF55A473)
        AppIcons.Library -> Color(0xFFDF9B35)
        AppIcons.Lyrics -> Color(0xFF39A9BB)
        AppIcons.Person -> Color(0xFF4D9CF0)
        AppIcons.ImportFile -> Color(0xFF4AA788)
        else -> Color(0xFF83838D)
    }
    Box(modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(color), contentAlignment = Alignment.Center) {
        AppIcon(icon, null, size = 19.dp, tint = Color.White)
    }
}

@Composable
internal fun MiuixSettingsPreviewChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Card(modifier.clip(shape)
        .border(if (selected) 1.5.dp else 0.dp, if (selected) colors.accent else Color.Transparent, shape)
        .semantics(mergeDescendants = true) { }
        .selectable(selected, interaction, LocalIndication.current, role = Role.RadioButton, onClick = onClick),
        cornerRadius = 20.dp, insideMargin = PaddingValues(12.dp),
        colors = CardDefaults.defaultColors(color = if (selected) colors.accent.copy(alpha = .08f).compositeOver(colors.secondaryBackground) else colors.secondaryBackground)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            preview()
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(title, AppTheme.typography.footnote, modifier = Modifier.weight(1f), color = colors.label, maxLines = 2)
                Box(Modifier.clearAndSetSemantics { }) {
                    RadioButton(selected = selected, onClick = null)
                }
            }
        }
    }
}
