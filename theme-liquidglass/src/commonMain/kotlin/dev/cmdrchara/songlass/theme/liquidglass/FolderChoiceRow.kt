package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.FolderChoice
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Folder disclosure and exclusion are separate hit targets. Names never repeat ancestor paths. */
@Composable
fun GlassFolderChoiceRow(choice: FolderChoice, onExpand: () -> Unit, onExcludedChange: (Boolean) -> Unit, material: Boolean, modifier: Modifier) {
    val colors = AppTheme.colors
    val indication = LocalGlassLook.current.pressIndication()
    val disclosureInteraction = remember { MutableInteractionSource() }
    val exclusionInteraction = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).drawBehind {
        drawLine(colors.separator, Offset(44.dp.toPx(), size.height), Offset(size.width, size.height), 0.5.dp.toPx())
    }.padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val disclosure = if (choice.expanded != null) Modifier.clickable(disclosureInteraction, indication, role = Role.Button, onClick = onExpand)
            .semantics { contentDescription = choice.expandLabel; stateDescription = choice.subtitle } else Modifier
        Row(Modifier.weight(1f).then(disclosure).heightIn(min = 64.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Folder, contentDescription = null, size = 24.dp, tint = colors.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AppText(choice.title, AppTheme.typography.body, maxLines = 2)
                AppText(choice.subtitle, AppTheme.typography.caption1, color = colors.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
            }
            if (choice.expanded != null) AppIcon(if (choice.expanded == true) AppIcons.ChevronDown else AppIcons.ChevronRight,
                contentDescription = null, size = 14.dp, tint = colors.tertiaryLabel, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Box(Modifier.size(48.dp).toggleable(choice.excluded, exclusionInteraction, indication, enabled = choice.enabled,
            role = Role.Checkbox, onValueChange = onExcludedChange).semantics { contentDescription = choice.excludeLabel }, contentAlignment = Alignment.Center) {
            val shape = if (material) RoundedCornerShape(4.dp) else CircleShape
            val color = if (choice.excluded) colors.accent else colors.secondaryLabel
            Box(Modifier.size(24.dp).background(if (choice.excluded) colors.accent else androidx.compose.ui.graphics.Color.Transparent, shape)
                .border(if (choice.excluded) 0.dp else 1.5.dp, color.copy(alpha = if (choice.enabled) 1f else 0.45f), shape), contentAlignment = Alignment.Center) {
                if (choice.excluded) AppIcon(AppIcons.Checkmark, contentDescription = null, size = 16.dp,
                    tint = colors.background, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}
