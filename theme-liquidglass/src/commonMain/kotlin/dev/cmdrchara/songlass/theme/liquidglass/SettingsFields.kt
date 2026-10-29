package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.SettingsGroupPosition
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** One continuous group can retain a lazy item and search anchor for every preference. */
@Composable
internal fun LiquidSettingsGroupFragment(position: SettingsGroupPosition, modifier: Modifier, content: @Composable () -> Unit) {
    val first = position == SettingsGroupPosition.First || position == SettingsGroupPosition.Single
    val last = position == SettingsGroupPosition.Last || position == SettingsGroupPosition.Single
    val surface = AppTheme.colors.secondaryBackground
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(
        topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp,
    )).background(surface).drawWithContent {
        drawContent()
        if (last) drawRect(surface, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
    }) { content() }
}

/** A selected value belongs to the group; its textual alternatives live in a bounded picker. */
@Composable
internal fun LiquidSettingsValueChoice(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier) {
    var expanded by remember(title) { mutableStateOf(false) }
    val colors = AppTheme.colors
    val value = labels.getOrNull(selectedIndex).orEmpty()
    val interaction = remember { MutableInteractionSource() }
    val pressed = liquidSettingsPressedColor(interaction)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
        Row(Modifier.fillMaxWidth().background(pressed).drawBehind {
            val stroke = .5.dp.toPx()
            drawLine(colors.separator, Offset(if (rtl) 0f else 16.dp.toPx(), size.height - stroke / 2),
                Offset(size.width - if (rtl) 16.dp.toPx() else 0f, size.height - stroke / 2), stroke)
        }.semantics(mergeDescendants = true) { contentDescription = title; stateDescription = value }
            .clickable(interaction, indication = null, enabled = labels.isNotEmpty(), role = Role.Button) { expanded = true }
            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stacked) Column(Modifier.weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AppText(title, AppTheme.typography.body, color = colors.label)
                AppText(value, AppTheme.typography.subheadline, color = colors.secondaryLabel)
            } else {
                AppText(title, AppTheme.typography.body, color = colors.label,
                    modifier = Modifier.weight(1f).alignByBaseline().clearAndSetSemantics { })
                AppText(value, AppTheme.typography.body, color = colors.secondaryLabel,
                    modifier = Modifier.widthIn(max = 160.dp).alignByBaseline().clearAndSetSemantics { }, textAlign = TextAlign.End)
            }
            AppIcon(AppIcons.ChevronRight, null, size = 12.dp, tint = colors.tertiaryLabel,
                modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f })
        }
    }
    if (expanded) LiquidSettingsChoicePicker(title, labels, selectedIndex, closeLabel, null,
        onDismiss = { expanded = false }, onSelect = { index -> onSelect(index); expanded = false })
}
