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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.RadioButton

@Composable
internal fun MiuixSettingsSectionTitle(title: String) {
    val root = LocalMiuixSettingsRoot.current
    val typography = AppTheme.typography
    val colors = AppTheme.colors
    if (root) {
        Spacer(Modifier.fillMaxWidth().height(12.dp).semantics { heading(); contentDescription = title })
    } else AppText(title, typography.footnote, color = colors.secondaryLabel,
        modifier = Modifier.padding(start = 30.dp, end = 24.dp, top = 24.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
internal fun MiuixSettingsDestinationGroup(modifier: Modifier, content: @Composable () -> Unit) {
    val root = LocalMiuixSettingsRoot.current
    val colors = AppTheme.colors
    val surface = if (root) {
        if (colors.isDark) Color(0xFF242424) else Color(0xFFF2F2F2)
    } else colors.secondaryBackground
    Card(modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = surface)) { content() }
}

@Composable
internal fun MiuixSettingsDestination(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    val root = LocalMiuixSettingsRoot.current
    val largeText = LocalDensity.current.fontScale > 1.3f
    if (root) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val reflow = largeText || maxWidth < 340.dp
            BasicComponent(
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).semantics(mergeDescendants = true) { },
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                startAction = { Box(Modifier.padding(end = 16.dp)) { MiuixSettingsCategoryIcon(icon, Modifier) } },
                endActions = {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (summaryIsValue && !reflow) AppText(summary, typography.subheadline.copy(fontSize = 15.sp),
                            Modifier.widthIn(max = 128.dp), colors.secondaryLabel, maxLines = 2)
                        AppIcon(AppIcons.ChevronRight, null, size = 16.dp, tint = colors.tertiaryLabel)
                    }
                },
                role = Role.Button,
                onClick = onClick,
            ) {
                AppText(title, typography.body.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
                    color = colors.label)
                if (summaryIsValue && reflow) AppText(summary, typography.subheadline,
                    color = colors.secondaryLabel)
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val reflow = largeText || maxWidth < 340.dp
        MiuixRow(title, if (summaryIsValue && reflow) summary else null,
            leading = { MiuixSettingsCategoryIcon(icon, Modifier) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summaryIsValue && !reflow) AppText(summary, typography.subheadline,
                        modifier = Modifier.widthIn(max = 144.dp), color = colors.secondaryLabel, maxLines = 1)
                    AppIcon(AppIcons.ChevronRight, null, size = 16.dp, tint = colors.tertiaryLabel)
                }
            }, highlighted = false, onClick = onClick, modifier = Modifier)
    }
}

@Composable
internal fun MiuixSettingsSearchResult(title: String, path: String, value: String?, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    Card(modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = colors.secondaryBackground)) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics(mergeDescendants = true) { role = Role.Button },
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            endActions = { AppIcon(AppIcons.ChevronRight, null, modifier = Modifier.padding(start = 12.dp), size = 16.dp, tint = colors.tertiaryLabel) },
            onClick = onClick,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(title, AppTheme.typography.body, color = colors.label)
                AppText(path, AppTheme.typography.subheadline, color = colors.secondaryLabel)
                value?.let { AppText(it, AppTheme.typography.subheadline, color = colors.accent) }
            }
        }
    }
}

@Composable
internal fun MiuixSettingsCategoryIcon(icon: ImageVector, modifier: Modifier) {
    val root = LocalMiuixSettingsRoot.current
    val color = if (root) when (icon) {
        AppIcons.Settings -> Color(0xFF3487F8)
        AppIcons.Home -> Color(0xFF8C6EF2)
        AppIcons.SpeakerLow -> Color(0xFFFA5546)
        AppIcons.Equalizer -> Color(0xFF20C645)
        AppIcons.AudioOutput -> Color(0xFF3487F8)
        AppIcons.Queue -> Color(0xFFF5AA00)
        AppIcons.Library -> Color(0xFF20A4C5)
        AppIcons.InfoCircle -> Color(0xFF91A3B0)
        AppIcons.Lyrics -> Color(0xFF39A9BB)
        AppIcons.Person -> Color(0xFF4D9CF0)
        AppIcons.ImportFile -> Color(0xFF4AA788)
        else -> Color(0xFF83838D)
    } else when (icon) {
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
