package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.StatFigure
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.theme.liquidglass.ListeningPlotStyle

/** M3 tonal cards: the lead figure in the primary container, the others on the card tone. */
@Composable
internal fun MaterialStatSummary(figures: List<StatFigure>, modifier: Modifier) {
    val palette = currentPalette()
    val typography = AppTheme.typography
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        figures.forEachIndexed { index, figure ->
            val lead = index == 0
            val ink = if (lead) palette.onPrimaryContainer else palette.label
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                    .background(if (lead) palette.primaryContainer else palette.container)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                AppText(figure.value, typography.largeTitle.copy(fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 40.sp), color = ink, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                AppText(figure.label, typography.subheadline, color = ink, maxLines = 2)
                figure.change?.let {
                    Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
                    AppText(it, typography.caption1, color = ink.copy(alpha = 0.72f), maxLines = 3)
                }
            }
        }
    }
}

/** A card row whose position sits in an expressive shape: the top three in the primary roles. */
@Composable
internal fun MaterialRankedRow(
    rank: Int, title: String, subtitle: String?, leading: (@Composable () -> Unit)?, value: String,
    highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier,
) {
    val palette = currentPalette()
    val typography = AppTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val podium = rank <= 3
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) palette.primaryContainer else palette.container)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = MaterialRipple, onClick = onClick) else Modifier)
            .heightIn(min = 64.dp).padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(if (rank == 1) ExpressiveShapes.Cookie else CircleShape)
                .background(if (podium) palette.primary else palette.containerHigh),
            contentAlignment = Alignment.Center,
        ) {
            AppText(rank.toString(), typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = if (podium) palette.onPrimary else palette.label, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            AppText(title, typography.body, color = if (highlighted) palette.onPrimaryContainer else palette.label, maxLines = 3)
            subtitle?.let { AppText(it, typography.subheadline, color = palette.secondaryLabel, maxLines = 1) }
        }
        Spacer(Modifier.width(8.dp))
        AppText(value, typography.footnote.copy(fontWeight = FontWeight.Medium), color = palette.secondaryLabel, maxLines = 1)
    }
}

/** Pill bars in the tonal roles: the selected one in primary, the others in primary container. */
@Composable
internal fun materialPlotStyle(): ListeningPlotStyle {
    val palette = currentPalette()
    return ListeningPlotStyle(bars = true, barRoundness = 0.5f, bar = palette.primaryContainer, selectedBar = palette.primary,
        container = palette.container, corner = 18.dp)
}
