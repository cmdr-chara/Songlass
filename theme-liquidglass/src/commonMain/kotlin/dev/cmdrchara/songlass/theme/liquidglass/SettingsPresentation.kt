package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalAppColors

/** Grouped content: grey canvas and white cells in light mode, black and raised grey in dark mode. */
@Composable
internal fun LiquidSettingsPage(content: @Composable () -> Unit) {
    val base = AppTheme.colors
    val colors = remember(base) { base.copy(
        background = if (base.isDark) base.background else base.secondaryBackground,
        secondaryBackground = if (base.isDark) base.secondaryBackground else base.background,
    ) }
    CompositionLocalProvider(LocalAppColors provides colors) {
        Box(Modifier.fillMaxSize().background(colors.background)) { content() }
    }
}

@Composable
internal fun LiquidSettingsPageTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        AppText(title, AppTheme.typography.headline, modifier = Modifier.padding(horizontal = 52.dp).semantics { heading() },
            color = colors.label, maxLines = 2, textAlign = TextAlign.Center)
        Box(Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape)
            .background(colors.secondaryBackground).border(.5.dp, colors.separator.copy(alpha = .35f), CircleShape)
            .clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
            AppIcon(AppIcons.ChevronLeft, backLabel, tint = colors.label, size = 22.dp)
        }
    }
}

@Composable
internal fun LiquidSettingsCategoryIcon(icon: ImageVector, modifier: Modifier) {
    val tint = when (icon) {
        AppIcons.Home -> Color(0xFFE98225)
        AppIcons.Equalizer -> Color(0xFFD74677)
        AppIcons.Library -> Color(0xFFE35759)
        AppIcons.Lyrics -> Color(0xFF7464CE)
        AppIcons.Person -> Color(0xFF357CCC)
        AppIcons.ImportFile -> Color(0xFF26998E)
        AppIcons.InfoCircle -> Color(0xFF777783)
        else -> AppTheme.colors.accent
    }
    Box(modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(tint), contentAlignment = Alignment.Center) {
        AppIcon(icon, null, size = 19.dp, tint = Color.White)
    }
}

/** Mask the final separator at the rounded group edge, with no extra optical layer. */
@Composable
internal fun LiquidSettingsPageGroup(modifier: Modifier, content: @Composable () -> Unit) {
    val surface = AppTheme.colors.secondaryBackground
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(surface).drawWithContent {
        drawContent()
        val edge = 1.dp.toPx()
        drawRect(surface, Offset(0f, size.height - edge), Size(size.width, edge))
    }) { content() }
}

/** Quiet single-line destinations; a current preference is a trailing value, not a second description. */
@Composable
internal fun LiquidSettingsDestination(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .background(if (pressed) colors.fill else Color.Transparent)
        .liquidFocusIndicator(interaction)
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
        .padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidSettingsCategoryIcon(icon, Modifier)
        Spacer(Modifier.width(12.dp))
        BoxWithConstraints(Modifier.weight(1f).heightIn(min = 52.dp).drawBehind {
            val stroke = .5.dp.toPx()
            drawLine(colors.separator.copy(alpha = .6f), Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
        }.padding(end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            val inlineWidth = if (summaryIsValue) {
                measurer.measure(AnnotatedString(title), AppTheme.typography.body, softWrap = false).size.width +
                    measurer.measure(AnnotatedString(summary), AppTheme.typography.subheadline, softWrap = false).size.width +
                    with(density) { (13.dp + 16.dp).roundToPx() }
            } else 0
            val stackValue = summaryIsValue && (density.fontScale > 1.3f || inlineWidth > constraints.maxWidth)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stackValue) Column(Modifier.weight(1f)) {
                    AppText(title, AppTheme.typography.body, color = colors.label)
                    AppText(summary, AppTheme.typography.subheadline, color = colors.secondaryLabel)
                } else {
                    AppText(title, AppTheme.typography.body, modifier = Modifier.weight(1f), color = colors.label)
                    // The measured pair fits; preserve the trailing value without squeezing the title.
                    if (summaryIsValue) AppText(summary, AppTheme.typography.subheadline,
                        modifier = Modifier.widthIn(max = 200.dp), color = colors.secondaryLabel, maxLines = 1, textAlign = TextAlign.End)
                }
                AppIcon(AppIcons.ChevronRight, null, size = 13.dp, tint = colors.tertiaryLabel)
            }
        }
    }
}

@Composable
internal fun LiquidSettingsSearchResult(title: String, path: String, value: String?, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LiquidSettingsPageGroup(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .background(if (pressed) colors.fill else Color.Transparent)
            .liquidFocusIndicator(interaction)
            .semantics(mergeDescendants = true) { }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(title, AppTheme.typography.body, color = colors.label)
                AppText(path, AppTheme.typography.footnote, color = colors.secondaryLabel)
                value?.let { AppText(it, AppTheme.typography.subheadline, color = colors.accent) }
            }
            AppIcon(AppIcons.ChevronRight, null, size = 13.dp, tint = colors.tertiaryLabel)
        }
    }
}

/** The other looks keep their existing subtitle rows, shapes, padding and indication. */
@Composable
fun StandardSettingsDestination(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    val look = LocalGlassLook.current
    look.ListRow(title, summary, { look.SettingsCategoryIcon(icon, Modifier) },
        { AppIcon(AppIcons.ChevronRight, null, size = 16.dp, tint = AppTheme.colors.tertiaryLabel) }, false, onClick, Modifier)
}

/** One grouped preview surface with a radio beneath each illustration, rather than outlined tiles. */
@Composable
internal fun LiquidSettingsPreviewChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.semantics(mergeDescendants = true) { }
        .liquidFocusIndicator(interaction, RoundedCornerShape(12.dp))
        .selectable(selected, interaction, LocalGlassLook.current.pressIndication(), role = Role.RadioButton, onClick = onClick)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(54.dp), contentAlignment = Alignment.Center) { preview() }
        Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
            AppText(title, AppTheme.typography.footnote, color = colors.label, maxLines = 2, textAlign = TextAlign.Center)
        }
        Box(Modifier.size(20.dp).clip(CircleShape)
            .background(if (selected) colors.accent else Color.Transparent)
            .border(if (selected) 0.dp else 1.dp, if (selected) Color.Transparent else colors.separator, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) AppIcon(AppIcons.Checkmark, null, size = 13.dp, tint = if (colors.isDark) Color.Black else Color.White)
        }
    }
}
