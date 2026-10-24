package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.layout.*
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
import dev.cmdrchara.songlass.theme.liquidglass.ListeningPlotStyle
import top.yukonga.miuix.kmp.basic.Card as NativeCard
import top.yukonga.miuix.kmp.basic.CardDefaults

/** One HyperOS card with centred large figures over their captions, no dividers. */
@Composable
internal fun MiuixStatSummary(figures: List<StatFigure>, modifier: Modifier) {
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    val valueStyle = typography.title1.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = constraints.maxWidth
        val cardPadding = with(density) { 16.dp.roundToPx() }
        val cellPadding = with(density) { 8.dp.roundToPx() }
        val gap = with(density) { 12.dp.roundToPx() }
        val cell = ((availableWidth - cardPadding) / figures.size.coerceAtLeast(1) - cellPadding).coerceAtLeast(1)
        fun fits(text: String, style: TextStyle, lines: Int) = !measurer.measure(text, style,
            softWrap = lines > 1, maxLines = lines, constraints = Constraints(maxWidth = cell)).hasVisualOverflow
        val vertical = figures.any { !fits(it.value, valueStyle, 1) || !fits(it.label, typography.footnote, 2) ||
            it.change?.let { change -> !fits(change, typography.caption1, 3) } == true }
        NativeCard(Modifier.fillMaxWidth(), cornerRadius = 24.dp,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = if (vertical) 12.dp else 18.dp),
            colors = CardDefaults.defaultColors(color = colors.secondaryBackground)) {
            if (vertical) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                figures.forEach { figure ->
                    val pairFits = measurer.measure(figure.value, valueStyle, softWrap = false).size.width +
                        measurer.measure(figure.label, typography.footnote, softWrap = false).size.width + gap <= availableWidth - cardPadding - cellPadding
                    if (pairFits) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AppText(figure.label, typography.footnote, color = colors.secondaryLabel)
                            figure.change?.let { AppText(it, typography.caption1, color = colors.accent) }
                        }
                        AppText(figure.value, valueStyle, color = colors.label)
                    } else Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(figure.value, valueStyle, color = colors.label, textAlign = TextAlign.Center)
                        AppText(figure.label, typography.footnote, color = colors.secondaryLabel, textAlign = TextAlign.Center)
                        figure.change?.let { AppText(it, typography.caption1, color = colors.accent, textAlign = TextAlign.Center) }
                    }
                }
            } else Row(Modifier.fillMaxWidth()) {
                figures.forEach { figure ->
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp).semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(figure.value, valueStyle, color = colors.label)
                        AppText(figure.label, typography.footnote, color = colors.secondaryLabel, textAlign = TextAlign.Center)
                        figure.change?.let {
                            Spacer(Modifier.height(4.dp))
                            AppText(it, typography.caption1, color = colors.accent, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** A Miuix row with the position before the artwork; the top three take the accent. */
@Composable
internal fun MiuixRankedRow(
    rank: Int, title: String, subtitle: String?, leading: (@Composable () -> Unit)?, value: String,
    highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier,
) {
    val colors = AppTheme.colors
    MiuixRow(title, subtitle, leading = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(rank.toString(), AppTheme.typography.headline.copy(fontWeight = FontWeight.SemiBold),
                color = if (rank <= 3) colors.accent else colors.secondaryLabel, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 22.dp))
            if (leading != null) { Spacer(Modifier.width(12.dp)); leading() }
        }
    }, trailing = { AppText(value, AppTheme.typography.footnote, color = colors.secondaryLabel, maxLines = 1) },
        highlighted = highlighted, onClick = onClick, modifier = modifier, titleMaxLines = 3)
}

/** Softly squared bars on solid hairlines, with the selected figure leading as in HyperOS summaries. */
@Composable
internal fun miuixPlotStyle(): ListeningPlotStyle = ListeningPlotStyle(
    bars = true, barRoundness = 0.3f, dashedGrid = false, valueFirst = true,
    bar = AppTheme.colors.accent.copy(alpha = 0.3f), selectedBar = AppTheme.colors.accent, corner = 20.dp,
)
