package dev.cmdrchara.songlass.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Localized live values; the screen owns observations and actions, the style owns composition. */
@Immutable
data class PlaybackToolRow(
    val title: String,
    val value: String,
    val icon: ImageVector? = null,
    val onClick: (() -> Unit)? = null,
    /** A primary action renders as a centered button instead of a navigational detail row. */
    val prominent: Boolean = false,
) {
    // Keep existing positional callbacks and trailing lambdas, including calls without an icon.
    constructor(title: String, value: String, icon: ImageVector? = null, onClick: (() -> Unit)?) :
        this(title, value, icon, onClick, false)
}

@Immutable
data class PlaybackToolGroup(val title: String?, val rows: List<PlaybackToolRow>, val ordered: Boolean = false)

@Immutable
/** An icon in the player's utility row. [badge] is a short live value (such as the sleep timer's minutes) shown next to it. */
data class PlayerUtilityAction(
    val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit,
    val accessibilityLabel: String = label, val badge: String? = null,
) {
    /** The allocator and every style reserve the same extra space for a live value. */
    val layoutWeight: Float get() = if (badge != null) 1.8f else 1f
}

/** A single sheet owns both overview and detail pages; changing the page does not stack overlays. */
@Composable
fun PlaybackToolsSheet(
    visible: Boolean,
    title: String,
    subtitle: String?,
    groups: List<PlaybackToolGroup>,
    closeLabel: String,
    backLabel: String?,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
) = LocalUiStyle.current.PlaybackToolsSheet(visible, title, subtitle, groups, closeLabel, backLabel, onBack, onDismiss)

/** One quiet row of icons under the player; the labels are the accessible names. */
@Composable
fun PlayerUtilityBar(
    actions: List<PlayerUtilityAction>,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.PlayerUtilityBar(actions, modifier)

/** Shared responsive allocation only; each look supplies its own control composition and shape. */
@Composable
fun PlayerUtilityRows(actions: List<PlayerUtilityAction>, spacing: Dp, modifier: Modifier = Modifier, content: @Composable RowScope.(PlayerUtilityAction, Int, Int) -> Unit) {
    if (actions.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minimumWidth = 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val rowSizes = playerUtilityRowSizes(actions.map { it.layoutWeight }, maxWidth.value, spacing.value, minimumWidth.value)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            var start = 0
            rowSizes.forEach { size ->
                val row = actions.subList(start, start + size)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { index, action -> content(action, index, row.size) }
                }
                start += size
            }
        }
    }
}

/**
 * Keep order, use the fewest legal rows, then avoid lone controls and balance their sizes.
 * A singleton is the fallback only when even that control's preferred width cannot fit.
 */
internal fun playerUtilityRowSizes(weights: List<Float>, availableWidth: Float, spacing: Float, minimumCellWidth: Float): List<Int> {
    val suffixRows = MutableList(weights.size + 1) { emptyList<Int>() }
    for (start in weights.lastIndex downTo 0) {
        var best: List<Int>? = null
        var rowWeight = 0f
        for (end in start..weights.lastIndex) {
            rowWeight += weights[end]
            val size = end - start + 1
            val requiredWidth = minimumCellWidth * rowWeight + spacing * (size - 1)
            if (size > 1 && requiredWidth > availableWidth + 0.001f) break
            val candidate = listOf(size) + suffixRows[end + 1]
            if (best == null || betterUtilityRows(candidate, best)) best = candidate
        }
        suffixRows[start] = checkNotNull(best)
    }
    return suffixRows.first()
}

private fun betterUtilityRows(candidate: List<Int>, current: List<Int>): Boolean {
    if (candidate.size != current.size) return candidate.size < current.size
    val candidateSingles = candidate.count { it == 1 }
    val currentSingles = current.count { it == 1 }
    if (candidateSingles != currentSingles) return candidateSingles < currentSingles
    val candidateSpread = candidate.sumOf { it * it }
    val currentSpread = current.sumOf { it * it }
    if (candidateSpread != currentSpread) return candidateSpread < currentSpread
    return candidate.zip(current).firstOrNull { (left, right) -> left != right }?.let { (left, right) -> left > right } ?: false
}

/** The native surface owns interaction; its live value wraps below the icon only when needed. */
@Composable
fun PlayerUtilityContent(action: PlayerUtilityAction, iconTint: Color, badgeTint: Color = iconTint) {
    val badge = action.badge
    if (badge == null) {
        AppIcon(action.icon, null, size = 22.dp, tint = iconTint)
        return
    }
    val density = LocalDensity.current
    val inlineGap = with(density) { 4.dp.roundToPx() }
    val stackedGap = with(density) { 2.dp.roundToPx() }
    Layout(content = {
        AppIcon(action.icon, null, size = 22.dp, tint = iconTint)
        AppText(badge, AppTheme.typography.caption1, color = badgeTint,
            modifier = Modifier.clearAndSetSemantics {}, textAlign = TextAlign.Center)
    }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val icon = measurables[0].measure(loose)
        val valueWidth = measurables[1].maxIntrinsicWidth(Constraints.Infinity)
        val availableWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else icon.width + inlineGap + valueWidth
        val inline = icon.width + inlineGap + valueWidth <= availableWidth
        val value = measurables[1].measure(loose.copy(maxWidth =
            if (inline) (availableWidth - icon.width - inlineGap).coerceAtLeast(0) else availableWidth))
        val width = constraints.constrainWidth(if (inline) icon.width + inlineGap + value.width else maxOf(icon.width, value.width))
        val height = constraints.constrainHeight(if (inline) maxOf(icon.height, value.height) else icon.height + stackedGap + value.height)
        layout(width, height) {
            if (inline) {
                icon.placeRelative(0, (height - icon.height) / 2)
                value.placeRelative(icon.width + inlineGap, (height - value.height) / 2)
            } else {
                icon.placeRelative((width - icon.width) / 2, 0)
                value.placeRelative((width - value.width) / 2, icon.height + stackedGap)
            }
        }
    }
}
