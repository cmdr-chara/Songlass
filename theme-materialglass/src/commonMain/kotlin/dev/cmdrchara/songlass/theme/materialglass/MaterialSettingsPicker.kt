package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalAppColors
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.designsystem.theme.LocalSheetTint

/** A compact selected value opens a bounded dialog, independent of the lazy settings item's height. */
@Composable
internal fun MaterialSettingsChoiceOptions(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    closeLabel: String,
    modifier: Modifier,
    inlineTitle: Boolean = false,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    emphasized: Boolean = false,
    contentInset: Dp? = null,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    val palette = currentPalette()
    val value = labels.getOrNull(selectedIndex).orEmpty()
    val rowShape = RoundedCornerShape(if (emphasized) MaterialShapes.ExtraLarge else MaterialShapes.Large)
    Row(modifier.fillMaxWidth().clip(rowShape)
        .then(when {
            emphasized -> Modifier.background(palette.primaryContainer)
            inlineTitle -> Modifier
            else -> Modifier.background(palette.container)
        })
        .semantics(mergeDescendants = true) { contentDescription = title; stateDescription = value }
        .clickable(remember { MutableInteractionSource() }, MaterialRipple,
            enabled = labels.isNotEmpty(), role = Role.Button, onClick = { expanded = true })
        .heightIn(min = if (emphasized) 80.dp else 64.dp)
        .padding(horizontal = contentInset ?: if (inlineTitle) 14.dp else 16.dp,
            vertical = if (inlineTitle) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (inlineTitle) 14.dp else 16.dp)) {
        leadingIcon?.let { icon ->
            Box(Modifier.size(if (emphasized) 48.dp else 40.dp)
                .clip(if (emphasized) ExpressiveShapes.Cookie else RoundedCornerShape(MaterialShapes.Medium))
                .background(if (emphasized) palette.primary else palette.primaryContainer),
                contentAlignment = Alignment.Center) {
                AppIcon(icon, null, size = 24.dp,
                    tint = if (emphasized) palette.onPrimary else palette.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (inlineTitle) AppText(title,
                AppTheme.typography.body.copy(fontSize = if (emphasized) 12.sp else 16.sp,
                    lineHeight = if (emphasized) 16.sp else 22.sp, fontWeight = FontWeight.Medium),
                color = if (emphasized) palette.onPrimaryContainer else palette.label)
            AppText(value, when {
                emphasized -> AppTheme.typography.title2.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                inlineTitle -> AppTheme.typography.subheadline
                else -> AppTheme.typography.body.copy(fontWeight = FontWeight.Medium)
            }, color = when {
                emphasized -> palette.onPrimaryContainer
                inlineTitle -> palette.primary
                else -> palette.label
            })
        }
        AppIcon(AppIcons.ChevronDown, null, size = 20.dp,
            tint = if (emphasized) palette.onPrimaryContainer else palette.secondaryLabel)
    }
    if (!expanded) return
    Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(
            LocalAppColors provides palette.appColors(),
            LocalContentColor provides palette.label,
            LocalSheetTint provides null,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)).padding(16.dp),
                contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null,
                    onClickLabel = closeLabel, onClick = { expanded = false })
                    .semantics { contentDescription = closeLabel })
                val maximumHeight = maxHeight
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maximumHeight)
                    .clip(RoundedCornerShape(MaterialShapes.ExtraLarge)).background(palette.container)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title }) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        leadingIcon?.let { icon ->
                            AppIcon(icon, null, size = 24.dp, tint = palette.primary)
                        }
                        AppText(title, AppTheme.typography.title2.copy(fontSize = 24.sp, lineHeight = 32.sp),
                            color = palette.label, modifier = Modifier.weight(1f).semantics { heading() })
                        IconButton(AppIcons.Close, closeLabel, { expanded = false }, Modifier.size(48.dp))
                    }
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).selectableGroup().padding(bottom = 12.dp)) {
                        description?.let { text ->
                            AppText(text, AppTheme.typography.subheadline, color = palette.secondaryLabel,
                                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp))
                        }
                        labels.forEachIndexed { index, label ->
                            MaterialSettingsChoiceRow(label, index == selectedIndex, {
                                onSelect(index)
                                expanded = false
                            }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
