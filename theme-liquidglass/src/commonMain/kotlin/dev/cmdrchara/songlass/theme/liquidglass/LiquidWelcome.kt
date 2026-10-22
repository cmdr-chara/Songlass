package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.WelcomeModel
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

private val Pill = RoundedCornerShape(percent = 50)

/**
 * iOS 26 welcome: colour behind, light on top. Soft glows sit under a centred mark and a large
 * title; the choices and the main button are translucent capsules, like the controls of the app.
 */
@Composable
internal fun LiquidWelcome(model: WelcomeModel) {
    val colors = AppTheme.colors
    val glow = colors.accent
    Box(
        Modifier.fillMaxSize().background(colors.background).drawBehind {
            // Two overlapping glows, one high and one low: the "liquid" the glass would refract.
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = if (colors.isDark) 0.38f else 0.28f), Color.Transparent),
                center = Offset(size.width * 0.78f, size.height * 0.14f), radius = size.width * 0.95f))
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = if (colors.isDark) 0.22f else 0.16f), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.62f), radius = size.width * 0.9f))
        }.windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 24.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.7f))
            model.brand(Modifier.size(104.dp))
            Spacer(Modifier.height(28.dp))
            AppText(model.title, AppTheme.typography.largeTitle.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            AppText(model.body, AppTheme.typography.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            if (model.choices.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    model.choices.forEach { choice ->
                        val shape = Pill
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
                                .background(if (choice.selected) colors.label.copy(alpha = 0.16f) else colors.label.copy(alpha = 0.07f))
                                .border(BorderStroke(if (choice.selected) 1.2.dp else 0.6.dp, colors.label.copy(alpha = if (choice.selected) 0.55f else 0.2f)), shape)
                                .selectable(choice.selected, remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = choice.onSelect)
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(choice.label, AppTheme.typography.body.copy(fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Normal),
                                modifier = Modifier.weight(1f))
                            if (choice.selected) AppIcon(AppIcons.Checkmark, null, size = 20.dp, tint = colors.accent)
                        }
                    }
                }
            }
            model.status?.let {
                Spacer(Modifier.height(24.dp))
                AppText(it, AppTheme.typography.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            Dots(model)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Pill)
                    .background(Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.95f), colors.accent.copy(alpha = 0.78f))))
                    .border(BorderStroke(0.8.dp, Color.White.copy(alpha = 0.45f)), Pill)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = model.onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                AppText(model.primaryLabel, AppTheme.typography.headline, color = if (colors.isDark) Color.Black else Color.White)
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                val onSecondary = model.onSecondary
                if (model.secondaryLabel != null && onSecondary != null) {
                    AppText(model.secondaryLabel!!, AppTheme.typography.body, color = colors.accent,
                        modifier = Modifier.clip(Pill).clickable(role = Role.Button, onClick = onSecondary).padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun Dots(model: WelcomeModel) {
    val colors = AppTheme.colors
    Row(Modifier.semantics { contentDescription = model.stepDescription }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(model.stepCount) { i ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (i + 1 == model.step) colors.label else colors.label.copy(alpha = 0.25f)))
        }
    }
}
