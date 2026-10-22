package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.WelcomeModel
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/**
 * HyperOS-style setup page: a quiet page, the mark and a large title at the top, native cards for the
 * choices, and one full-width rounded button at the bottom. No decoration beyond the brand mark.
 */
@Composable
internal fun MiuixWelcome(model: WelcomeModel) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(56.dp))
        model.brand(Modifier.size(72.dp))
        Spacer(Modifier.height(24.dp))
        AppText(model.title, AppTheme.typography.largeTitle.copy(fontWeight = FontWeight.Medium), maxLines = 3)
        Spacer(Modifier.height(10.dp))
        AppText(model.body, AppTheme.typography.body, color = colors.secondaryLabel)
        if (model.choices.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            MiuixGroup({
                Column {
                    model.choices.forEach { choice -> MiuixChoice(choice.label, choice.selected, choice.onSelect, Modifier) }
                }
            }, Modifier)
        }
        model.status?.let {
            Spacer(Modifier.height(20.dp))
            MiuixGroup({
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                    AppText(it, AppTheme.typography.body)
                }
            }, Modifier)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = model.stepDescription },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            repeat(model.stepCount) { i ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (i + 1 == model.step) colors.accent else colors.fill))
            }
        }
        Spacer(Modifier.height(16.dp))
        MiuixAction(model.primaryLabel, null, model.onPrimary, Modifier.fillMaxWidth().heightIn(min = 52.dp), primary = true)
        val onSecondary = model.onSecondary
        if (model.secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            MiuixAction(model.secondaryLabel!!, null, onSecondary, Modifier.fillMaxWidth().heightIn(min = 52.dp))
        } else Spacer(Modifier.height(60.dp))
        Spacer(Modifier.height(12.dp))
    }
}
