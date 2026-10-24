package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.StatFigure
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Equal columns in one inset group, separated by hairlines; each column is read as one item. */
@Composable
internal fun LiquidStatSummary(figures: List<StatFigure>, modifier: Modifier) {
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    val look = LocalGlassLook.current
    val valueStyle = typography.title1.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = constraints.maxWidth
        val padding = with(density) { 24.dp.roundToPx() }
        val divider = with(density) { 1.dp.roundToPx() }
        val gap = with(density) { 12.dp.roundToPx() }
        val cell = ((availableWidth - divider * (figures.size - 1).coerceAtLeast(0)) / figures.size.coerceAtLeast(1) - padding).coerceAtLeast(1)
        fun fits(text: String, style: TextStyle, lines: Int) = !measurer.measure(text, style,
            softWrap = lines > 1, maxLines = lines, constraints = Constraints(maxWidth = cell)).hasVisualOverflow
        val vertical = figures.any { !fits(it.value, valueStyle, 1) || !fits(it.label, typography.footnote, 2) ||
            it.change?.let { change -> !fits(change, typography.caption1, 2) } == true }
        val surface = look.controlSurface(Modifier.fillMaxWidth(), RoundedCornerShape(look.corner(20.dp)), colors.secondaryBackground, prominent = false)
        if (vertical) Column(surface) {
            figures.forEachIndexed { index, figure ->
                if (index > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(0.5.dp).background(colors.separator))
                val pairFits = measurer.measure(figure.value, valueStyle, softWrap = false).size.width +
                    measurer.measure(figure.label, typography.footnote, softWrap = false).size.width + gap <= availableWidth - padding
                if (pairFits) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        AppText(figure.label, typography.footnote, color = colors.secondaryLabel)
                        figure.change?.let { AppText(it, typography.caption1, color = colors.secondaryLabel) }
                    }
                    AppText(figure.value, valueStyle, color = colors.label)
                } else Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).semantics(mergeDescendants = true) {}) {
                    AppText(figure.label, typography.footnote, color = colors.secondaryLabel)
                    AppText(figure.value, valueStyle, color = colors.label)
                    figure.change?.let { AppText(it, typography.caption1, color = colors.secondaryLabel) }
                }
            }
        } else Row(surface.height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
            figures.forEachIndexed { index, figure ->
                if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(colors.separator))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp).semantics(mergeDescendants = true) {}) {
                    AppText(figure.label, typography.footnote, color = colors.secondaryLabel)
                    AppText(figure.value, valueStyle, color = colors.label)
                    figure.change?.let { AppText(it, typography.caption1, color = colors.secondaryLabel) }
                }
            }
        }
    }
}

/** Position, artwork, title and the count, as in iOS chart lists; ties repeat the position. */
@Composable
internal fun LiquidRankedRow(
    rank: Int, title: String, subtitle: String?, leading: (@Composable () -> Unit)?, value: String,
    highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier,
) {
    val colors = AppTheme.colors
    LiquidGlassListRow(
        title, subtitle,
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(rank.toString(), AppTheme.typography.headline, color = if (rank <= 3) colors.accent else colors.secondaryLabel,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 24.dp))
                if (leading != null) { Spacer(Modifier.width(10.dp)); leading() }
            }
        },
        trailing = { AppText(value, AppTheme.typography.footnote, color = colors.secondaryLabel) },
        highlighted = highlighted, onClick = onClick, modifier = modifier, titleMaxLines = 3,
    )
}
