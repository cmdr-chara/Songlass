package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.SettingsVisualChoice
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Retains Liquid and Miuix arrangement while each look still owns the individual choices. */
@Composable
internal fun GlassSettingsVisualChoices(choices: List<SettingsVisualChoice>, modifier: Modifier) {
    val look = LocalGlassLook.current
    BoxWithConstraints(modifier.fillMaxWidth()
        .then(if (look.usesInsetSettingsForm) Modifier.clip(RoundedCornerShape(26.dp)).background(AppTheme.colors.secondaryBackground) else Modifier)
        .selectableGroup()) {
        if (LocalDensity.current.fontScale > 1.3f || maxWidth < 280.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                choices.forEach { choice -> look.SettingsPreviewChoice(choice.title, choice.selected, choice.onClick, Modifier.fillMaxWidth(), choice.preview) }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.forEach { choice -> look.SettingsPreviewChoice(choice.title, choice.selected, choice.onClick, Modifier.weight(1f), choice.preview) }
        }
    }
}
