package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Inset content form. Opaque surfaces keep the glass exclusively in the app's navigation. */
@Composable
fun GlassPlaylistFormGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val surface = if (colors.isDark) colors.secondaryBackground else colors.background
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface)
        // The final cell's separator ends at the group's edge, never under its rounded corners.
        .drawWithContent {
            drawContent()
            val edge = 1.dp.toPx()
            drawRect(surface, Offset(0f, size.height - edge), Size(size.width, edge))
        }, content = { content() })
}

@Composable
fun GlassPlaylistFormToolbar(backLabel: String, onBack: () -> Unit, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LocalGlassLook.current.BackButton(backLabel, onBack, Modifier.weight(1f))
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val colors = AppTheme.colors
        Box(Modifier.heightIn(min = 44.dp).clip(CircleShape)
            .background(colors.accent.copy(alpha = if (pressed) .65f else 1f))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onAction)
            .padding(horizontal = 20.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            AppText(action, AppTheme.typography.headline, color = if (colors.isDark) Color.Black else Color.White)
        }
    }
}

@Composable
fun GlassPlaylistFormTitle(text: String) {
    AppText(text, AppTheme.typography.title1.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 22.dp).semantics { heading() })
}

@Composable
fun GlassPlaylistFormSection(text: String) {
    AppText(text, AppTheme.typography.footnote, color = AppTheme.colors.label.copy(alpha = .62f),
        modifier = Modifier.padding(start = 36.dp, end = 24.dp, top = 24.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
private fun Modifier.formSeparator(): Modifier {
    val color = AppTheme.colors.label.copy(alpha = if (AppTheme.colors.isDark) .13f else .12f)
    return drawBehind {
        val stroke = .5.dp.toPx()
        drawLine(color, Offset(16.dp.toPx(), size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
    }
}

/** Long paths and larger text reflow below the label instead of colliding with it. */
@Composable
fun GlassPlaylistFormRow(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reflow = LocalDensity.current.fontScale > 1.3f || value.length > 24
    Row(modifier.fillMaxWidth().heightIn(min = 50.dp)
        .background(if (pressed) colors.fill else Color.Transparent).formSeparator()
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (reflow) Column(Modifier.weight(1f)) {
            AppText(title, AppTheme.typography.body)
            AppText(value, AppTheme.typography.callout, color = colors.label.copy(alpha = .62f), modifier = Modifier.padding(top = 3.dp))
        } else {
            AppText(title, AppTheme.typography.body, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            AppText(value, AppTheme.typography.callout, color = colors.label.copy(alpha = .62f),
                modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
        }
        AppIcon(AppIcons.ChevronRight, null, tint = colors.tertiaryLabel, size = 14.dp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun GlassPlaylistFormField(value: String, onValueChange: (String) -> Unit, label: String, numeric: Boolean = false) {
    val colors = AppTheme.colors
    val reflow = LocalDensity.current.fontScale > 1.3f || label.length > 20
    val input: @Composable (Modifier) -> Unit = { modifier ->
        BasicTextField(value, onValueChange, modifier.semantics { contentDescription = label }, singleLine = true,
            textStyle = AppTheme.typography.body.copy(color = colors.label, textAlign = if (reflow) TextAlign.Start else TextAlign.End),
            cursorBrush = SolidColor(colors.accent), keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text))
    }
    if (reflow) Column(Modifier.fillMaxWidth().formSeparator().padding(16.dp)) {
        AppText(label, AppTheme.typography.footnote, color = colors.label.copy(alpha = .62f), modifier = Modifier.padding(bottom = 6.dp))
        input(Modifier.fillMaxWidth())
    } else Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).formSeparator().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppText(label, AppTheme.typography.body, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        input(Modifier.weight(1.25f))
    }
}

@Composable
fun GlassPlaylistFormAdd(text: String, onClick: () -> Unit) = FormAction(text, false, onClick)

@Composable
fun GlassPlaylistFormRemove(text: String, onClick: () -> Unit) = FormAction(text, true, onClick)

@Composable
private fun FormAction(text: String, destructive: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color = if (destructive) (if (colors.isDark) Color(0xFFFF453A) else Color(0xFFD6241B)) else colors.accent
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp)
        .background(if (pressed) colors.fill else Color.Transparent)
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!destructive) {
            AppIcon(AppIcons.Plus, null, tint = color, size = 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        AppText(text, AppTheme.typography.body, color = color)
    }
}
