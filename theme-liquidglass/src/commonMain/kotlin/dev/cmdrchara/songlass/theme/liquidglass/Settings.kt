package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Settings belong to the content layer, so grouped surfaces remain opaque in every mode. */
@Composable
internal fun LiquidGlassSettingsGroup(content: @Composable () -> Unit, modifier: Modifier) {
    Column(
        LocalGlassLook.current.controlSurface(modifier.fillMaxWidth(), RoundedCornerShape(LocalGlassLook.current.corner(20.dp)), AppTheme.colors.secondaryBackground, prominent = false),
    ) {
        content()
    }
}

@Composable
internal fun LiquidGlassSettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedColor = settingsPressedColor(pressed)
    val checkmarkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "settingsCheckmark",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(pressedColor)
            .settingsSeparator()
            .liquidFocusIndicator(interaction)
            .selectable(selected, interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(title, AppTheme.typography.body, modifier = Modifier.weight(1f), color = colors.label)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            AppIcon(
                AppIcons.Checkmark,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.graphicsLayer { alpha = checkmarkAlpha },
            )
        }
    }
}

@Composable
internal fun LiquidGlassSettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val pressedColor = settingsPressedColor(pressed)
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = 600f),
        label = "settingsSwitchThumb",
    )
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.label.copy(alpha = 0.24f)
            checked -> colors.accent
            colors.isDark -> Color(0xFF636366)
            else -> Color(0xFF8E8E93)
        },
        animationSpec = if (reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "settingsSwitchTrack",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(pressedColor)
            .settingsSeparator()
            .liquidFocusIndicator(interaction, enabled = enabled)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, AppTheme.typography.body, color = if (enabled) colors.label else colors.secondaryLabel)
            if (subtitle != null) {
                AppText(
                    subtitle,
                    AppTheme.typography.footnote,
                    modifier = Modifier.padding(top = 4.dp),
                    color = if (enabled) colors.label.copy(alpha = 0.7f) else colors.secondaryLabel,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        // The entire row is one switch for touch, keyboard, and screen readers.
        // Thumb position conveys state without depending on the track color.
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 32.dp)
                .clearAndSetSemantics { }
                .clip(CircleShape)
                .background(trackColor)
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.offset(x = thumbOffset).size(26.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun Modifier.settingsSeparator(): Modifier {
    val separator = AppTheme.colors.separator
    return drawBehind {
        val strokeWidth = 0.5.dp.toPx()
        val y = size.height - strokeWidth / 2
        drawLine(separator, Offset(16.dp.toPx(), y), Offset(size.width, y), strokeWidth)
    }
}

@Composable
private fun settingsPressedColor(pressed: Boolean): Color {
    val color by animateColorAsState(
        targetValue = if (pressed) AppTheme.colors.fill else Color.Transparent,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "settingsRowPress",
    )
    return color
}
