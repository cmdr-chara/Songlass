package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.WelcomeModel
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/**
 * Material 3 Expressive welcome: big expressive shapes around the mark, a left-aligned headline,
 * a connected group of choices, a full-width pill button, and a progress row whose current step stretches.
 */
@Composable
internal fun MaterialWelcome(model: WelcomeModel, palette: SeaPalette) {
    Column(
        Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 24.dp),
    ) {
        // The shapes change from page to page, so each page feels like a new card of the same deck.
        Box(Modifier.fillMaxWidth().weight(0.9f), contentAlignment = Alignment.Center) {
            val shapes = ExpressiveShapes.sequence
            val big = shapes[(model.step - 1).coerceAtLeast(0) % shapes.size]
            val small = shapes[(model.step + 1) % shapes.size]
            val tiny = shapes[(model.step + 3) % shapes.size]
            Box(Modifier.size(220.dp).clip(big).background(palette.primaryContainer), contentAlignment = Alignment.Center) {
                model.brand(Modifier.size(112.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 12.dp).size(76.dp).clip(small).background(palette.vivid.copy(alpha = 0.85f)))
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp).size(52.dp).clip(tiny).background(palette.containerHigh))
        }
        AppText(model.title, AppTheme.typography.largeTitle.copy(fontWeight = FontWeight.Medium), color = palette.label)
        Spacer(Modifier.height(10.dp))
        AppText(model.body, AppTheme.typography.body, color = palette.secondaryLabel)
        if (model.choices.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            // The same connected group the player uses: square inner corners, rounded ends, a selected pill that rounds fully.
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                model.choices.forEachIndexed { index, choice ->
                    val last = model.choices.lastIndex
                    val shape = if (choice.selected) RoundedCornerShape(24.dp) else RoundedCornerShape(
                        topStart = if (index == 0) 24.dp else 6.dp, bottomStart = if (index == 0) 24.dp else 6.dp,
                        topEnd = if (index == last) 24.dp else 6.dp, bottomEnd = if (index == last) 24.dp else 6.dp,
                    )
                    Row(
                        Modifier.weight(1f).heightIn(min = 52.dp).clip(shape)
                            .background(if (choice.selected) palette.primaryContainer else palette.containerHigh)
                            .selectable(choice.selected, remember { MutableInteractionSource() }, MaterialRipple, role = Role.RadioButton, onClick = choice.onSelect)
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (choice.selected) AppIcon(AppIcons.Checkmark, null, size = 16.dp, tint = palette.onPrimaryContainer)
                        AppText(choice.label, AppTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
                            color = if (choice.selected) palette.onPrimaryContainer else palette.label,
                            modifier = Modifier.padding(start = if (choice.selected) 4.dp else 0.dp), maxLines = 2)
                    }
                }
            }
        }
        model.status?.let {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(palette.container).padding(horizontal = 14.dp, vertical = 10.dp)) {
                AppText(it, AppTheme.typography.subheadline, color = palette.label)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.semantics { contentDescription = model.stepDescription }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(model.stepCount) { i ->
                val current = i + 1 == model.step
                Box(Modifier.height(8.dp).width(if (current) 32.dp else 8.dp).clip(CircleShape)
                    .background(if (current) palette.primary else palette.containerHigh))
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(palette.primary)
                .clickable(remember { MutableInteractionSource() }, MaterialRipple, role = Role.Button, onClick = model.onPrimary),
            contentAlignment = Alignment.Center,
        ) { AppText(model.primaryLabel, AppTheme.typography.headline, color = palette.onPrimary) }
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
            val onSecondary = model.onSecondary
            if (model.secondaryLabel != null && onSecondary != null) {
                AppText(model.secondaryLabel!!, AppTheme.typography.body.copy(fontWeight = FontWeight.Medium), color = palette.primary,
                    modifier = Modifier.clip(CircleShape).clickable(remember { MutableInteractionSource() }, MaterialRipple, role = Role.Button, onClick = onSecondary)
                        .padding(horizontal = 24.dp, vertical = 14.dp))
            }
        }
    }
}
