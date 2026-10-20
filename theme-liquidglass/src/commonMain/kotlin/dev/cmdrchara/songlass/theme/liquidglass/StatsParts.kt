package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Equal columns in one inset group, separated by hairlines; each column is read as one item. */
@Composable
internal fun LiquidStatSummary(figures: List<StatFigure>, modifier: Modifier) {
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    val look = LocalGlassLook.current
    Row(
        look.controlSurface(modifier.fillMaxWidth(), RoundedCornerShape(look.corner(20.dp)), colors.secondaryBackground, prominent = false)
            .height(IntrinsicSize.Min).padding(vertical = 14.dp),
    ) {
        figures.forEachIndexed { index, figure ->
            if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(colors.separator))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp).semantics(mergeDescendants = true) {}) {
                AppText(figure.label, typography.footnote, color = colors.secondaryLabel, maxLines = 2)
                AppText(figure.value, typography.title1.copy(fontWeight = FontWeight.Bold), color = colors.label, maxLines = 1)
                figure.change?.let { AppText(it, typography.caption1, color = colors.secondaryLabel, maxLines = 2) }
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
