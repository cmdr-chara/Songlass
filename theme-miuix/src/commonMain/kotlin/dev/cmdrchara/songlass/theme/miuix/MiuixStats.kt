package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    NativeCard(modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(horizontal = 8.dp, vertical = 18.dp),
        colors = CardDefaults.defaultColors(color = colors.secondaryBackground)) {
        Row(Modifier.fillMaxWidth()) {
            figures.forEach { figure ->
                Column(Modifier.weight(1f).padding(horizontal = 4.dp).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(figure.value, typography.title1.copy(fontWeight = FontWeight.SemiBold), color = colors.label, maxLines = 1)
                    AppText(figure.label, typography.footnote, color = colors.secondaryLabel, maxLines = 2, textAlign = TextAlign.Center)
                    figure.change?.let {
                        Spacer(Modifier.height(4.dp))
                        AppText(it, typography.caption1, color = colors.accent, maxLines = 3, textAlign = TextAlign.Center)
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
