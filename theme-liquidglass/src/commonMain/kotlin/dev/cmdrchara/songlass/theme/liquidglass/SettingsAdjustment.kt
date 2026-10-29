package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Shared geometry, with each look's own card, buttons, typography and slider. */
@Composable
fun SettingsAdjustmentContent(title: String, value: String, hint: String?, enabled: Boolean,
    lessLabel: String, moreLabel: String, resetLabel: String?, onLess: () -> Unit, onMore: () -> Unit,
    onReset: (() -> Unit)?, modifier: Modifier, titleStyle: TextStyle,
    slider: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().padding(12.dp)) {
        val buttonWidth = with(density) {
            maxOf(64.dp, maxOf(measurer.measure(lessLabel, AppTheme.typography.headline).size.width,
                measurer.measure(moreLabel, AppTheme.typography.headline).size.width).toDp() + 40.dp)
        }
        val largeText = density.fontScale > 1.3f
        val inline = !largeText && maxWidth >= buttonWidth * 2 + 16.dp + 140.dp
        val hasReset = enabled && resetLabel != null && onReset != null
        val titleWidth = measurer.measure(title, titleStyle, softWrap = false).size.width
        val valueWidth = measurer.measure(value, AppTheme.typography.subheadline, softWrap = false).size.width
        val stackValue = largeText || titleWidth + valueWidth + with(density) { 12.dp.roundToPx() } > constraints.maxWidth
        @Composable fun reset(modifier: Modifier) {
            if (enabled && resetLabel != null && onReset != null) {
                ActionButton(resetLabel, onReset, modifier.semantics { contentDescription = "$title, $resetLabel" })
            }
        }
        @Composable fun step(text: String, action: () -> Unit, modifier: Modifier) {
            ActionButton(text, action, modifier.semantics { contentDescription = "$title, $text" })
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (inline && hasReset) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    AppText(title, titleStyle)
                    AppText(value, AppTheme.typography.subheadline, color = AppTheme.colors.accent)
                }
                reset(Modifier)
            } else if (stackValue) {
                AppText(title, titleStyle)
                AppText(value, AppTheme.typography.subheadline, color = AppTheme.colors.accent)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(title, titleStyle, Modifier.weight(1f))
                AppText(value, AppTheme.typography.subheadline, color = AppTheme.colors.accent)
            }
            if (hint != null) AppText(hint, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
            if (enabled) {
                if (inline) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    slider(Modifier.weight(1f))
                    step(lessLabel, onLess, Modifier.width(buttonWidth))
                    step(moreLabel, onMore, Modifier.width(buttonWidth))
                } else {
                    slider(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        step(lessLabel, onLess, Modifier.weight(1f))
                        if (hasReset && !largeText) reset(Modifier.weight(1.5f))
                        step(moreLabel, onMore, Modifier.weight(1f))
                    }
                    if (hasReset && largeText) reset(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
