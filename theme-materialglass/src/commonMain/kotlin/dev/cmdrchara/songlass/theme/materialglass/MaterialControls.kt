package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.Modifier.Node
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalUiAccessibility
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

// Material 3 controls for Material Glass, drawn from the M3 specs (sizes, shapes, states) with
// the look's tonal palette. No Material library: everything here is foundation code.

/**
 * Material feedback: paired focus/hover state layers and a ripple that grows from the touch point
 * and fades after release. Reduced motion keeps the ripple spatially static.
 */
internal object MaterialRipple : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = RippleNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 7
}

private class RippleNode(private val source: InteractionSource) : Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val radius = Animatable(0f)
    private val alpha = Animatable(0f)
    private var origin = Offset.Unspecified
    private var activePress: PressInteraction.Press? = null
    private val focuses = mutableSetOf<FocusInteraction.Focus>()
    private val hovers = mutableSetOf<HoverInteraction.Enter>()
    private var rippleJob: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            radius.snapTo(0f)
            alpha.snapTo(0f)
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        activePress = interaction
                        origin = interaction.pressPosition
                        rippleJob?.cancel()
                        val reduceMotion = currentValueOf(LocalUiAccessibility).reduceMotion
                        rippleJob = launch {
                            alpha.snapTo(1f)
                            radius.snapTo(if (reduceMotion) 1f else 0f)
                            if (!reduceMotion) radius.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                        }
                    }
                    is PressInteraction.Release -> finishPress(interaction.press)
                    is PressInteraction.Cancel -> finishPress(interaction.press)
                    is FocusInteraction.Focus -> { focuses.add(interaction); invalidateDraw() }
                    is FocusInteraction.Unfocus -> { focuses.remove(interaction.focus); invalidateDraw() }
                    is HoverInteraction.Enter -> { hovers.add(interaction); invalidateDraw() }
                    is HoverInteraction.Exit -> { hovers.remove(interaction.enter); invalidateDraw() }
                }
            }
        }
    }

    private fun finishPress(press: PressInteraction.Press) {
        if (activePress !== press) return
        activePress = null
        rippleJob?.cancel()
        val reduceMotion = currentValueOf(LocalUiAccessibility).reduceMotion
        rippleJob = coroutineScope.launch {
            // Complete the current ripple before fading, without letting an old release fade a new press.
            if (reduceMotion) {
                radius.snapTo(1f)
                alpha.snapTo(0f)
            } else {
                // A release can arrive before the press animation coroutine has drawn its first frame.
                alpha.snapTo(1f)
                if (radius.value < 1f) radius.animateTo(1f, tween(160))
                alpha.animateTo(0f, MaterialMotion.effects())
            }
        }
    }

    override fun onDetach() {
        rippleJob?.cancel()
        rippleJob = null
        activePress = null
        origin = Offset.Unspecified
        focuses.clear()
        hovers.clear()
        // Animatable values are reset on the next attach before this node is drawn again.
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        clipRect {
            val color = currentValueOf(LocalContentColor)
            val stateAlpha = when {
                focuses.isNotEmpty() -> 0.12f
                hovers.isNotEmpty() -> 0.08f
                else -> 0f
            }
            val a = alpha.value
            drawRect(color.copy(alpha = max(stateAlpha, 0.08f * a)))
            if (a <= 0f) return@clipRect
            val center = if (origin.isSpecified) origin else Offset(size.width / 2f, size.height / 2f)
            val farthest = max(
                max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
                max(hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y)),
            )
            drawCircle(color.copy(alpha = 0.1f * a), farthest * (if (currentValueOf(LocalUiAccessibility).reduceMotion) 1f else radius.value), center)
        }
    }
}

/** Material top app bar navigation icon: a back arrow on a tonal circle, 48 dp touch target. */
@Composable
internal fun MaterialBackButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
            .size(48.dp)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(palette.container).indication(interaction, MaterialRipple),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.ArrowBack, contentDescription = null, size = 24.dp, tint = AppTheme.colors.label)
        }
    }
}

/** Whole-row radio choices use a quiet tonal selection and a trailing state indicator. */
@Composable
internal fun MaterialSettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val dot by animateDpAsState(if (selected) 10.dp else 0.dp, if (reduceMotion) snap() else MaterialMotion.spatial(), label = "radioDot")
    val ring by animateColorAsState(if (selected) palette.primary else palette.secondaryLabel,
        if (reduceMotion) snap() else MaterialMotion.effects(), label = "radioRing")
    val fill by animateColorAsState(if (selected) palette.primary.copy(alpha = 0.05f) else Color.Transparent,
        if (reduceMotion) snap() else MaterialMotion.effects(), label = "radioRowFill")
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).background(fill)
            .selectable(selected, interaction, indication = MaterialRipple, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(title, AppTheme.typography.body, color = palette.label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(24.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(dot.coerceIn(0.dp, 10.dp)).clip(CircleShape).background(palette.primary))
            }
        }
    }
}

/**
 * Settings switch as a Material list item. The M3 switch: 52×32 track, outlined when off with a
 * small thumb, filled when on with a larger thumb carrying a check; the thumb grows while pressed.
 */
@Composable
internal fun MaterialSettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val palette = currentPalette()
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = if (reduceMotion) snap<androidx.compose.ui.unit.Dp>() else MaterialMotion.spatial()
    val thumbTarget = if (pressed && enabled) 28.dp else if (checked) 24.dp else 16.dp
    val animatedThumb by animateDpAsState(thumbTarget, if (reduceMotion) snap() else MaterialMotion.press(), label = "switchThumb")
    val thumb = animatedThumb.coerceIn(16.dp, 28.dp)
    val animatedOffset by animateDpAsState(if (checked) 24.dp else 4.dp, motion, label = "switchOffset")
    val offset = animatedOffset.coerceIn(4.dp, 24.dp)
    val track by animateColorAsState(if (checked) palette.primary else palette.containerHigh, if (reduceMotion) snap() else MaterialMotion.effects(), label = "switchTrack")
    val outline = palette.secondaryLabel
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 64.dp else 72.dp)
            .toggleable(checked, interaction, indication = MaterialRipple, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(title, AppTheme.typography.body, color = colors.label)
            if (subtitle != null) AppText(subtitle, AppTheme.typography.subheadline, color = palette.secondaryLabel)
        }
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clearAndSetSemantics { }
                .clip(CircleShape)
                .background(track)
                .then(if (checked) Modifier else Modifier.border(2.dp, outline, CircleShape)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = offset + (24.dp - thumb) / 2)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(if (checked) palette.onPrimary else outline),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) AppIcon(AppIcons.Checkmark, contentDescription = null, size = 16.dp, tint = palette.primary)
            }
        }
    }
}

/** Connected choices keep labels intact, scrolling only when their measured content needs it. */
@Composable
internal fun MaterialSegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val labelStyle = AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minimumSegmentWidth = with(density) {
        (options.maxOfOrNull { measurer.measure(it, labelStyle, softWrap = false, maxLines = 1).size.width } ?: 0).toDp() + 44.dp
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rowWidth = maxOf(maxWidth, minimumSegmentWidth * options.size + 2.dp * (options.size - 1).coerceAtLeast(0))
        val scroll = rememberScrollState()
        val viewportWidth = constraints.maxWidth
        val rowWidthPx = with(density) { rowWidth.roundToPx() }
        val gapPx = with(density) { 2.dp.roundToPx() }
        LaunchedEffect(selectedIndex, rowWidthPx, viewportWidth, scroll.maxValue) {
            if (selectedIndex in options.indices) {
                val segmentWidth = (rowWidthPx - gapPx * (options.size - 1)) / options.size
                val start = selectedIndex * (segmentWidth + gapPx)
                val end = start + segmentWidth
                val target = when {
                    start < scroll.value -> start
                    end > scroll.value + viewportWidth -> end - viewportWidth
                    else -> scroll.value
                }
                scroll.scrollTo(target.coerceAtLeast(0))
            }
        }
        Box(Modifier.horizontalScroll(scroll)) {
            Row(Modifier.width(rowWidth).height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    val inner by animateDpAsState(if (selected) 12.dp else 4.dp,
                        if (reduceMotion) snap() else MaterialMotion.spatial(), label = "segmentCorner")
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 24.dp else inner.coerceIn(4.dp, 12.dp),
                        bottomStart = if (index == 0) 24.dp else inner.coerceIn(4.dp, 12.dp),
                        topEnd = if (index == options.lastIndex) 24.dp else inner.coerceIn(4.dp, 12.dp),
                        bottomEnd = if (index == options.lastIndex) 24.dp else inner.coerceIn(4.dp, 12.dp),
                    )
                    val fill by animateColorAsState(if (selected) palette.primaryContainer else palette.containerHigh,
                        if (reduceMotion) snap() else MaterialMotion.effects(), label = "segmentFill")
                    Row(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(shape).background(fill)
                            .then(if (selected) Modifier else Modifier.border(1.dp, palette.secondaryLabel.copy(alpha = 0.18f), shape))
                            .selectable(selected, remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.RadioButton) { onSelect(index) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            AppIcon(AppIcons.Checkmark, contentDescription = null, size = 18.dp, tint = palette.onPrimaryContainer)
                            Spacer(Modifier.width(6.dp))
                        }
                        AppText(option, labelStyle,
                            color = if (selected) palette.onPrimaryContainer else palette.label,
                            modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                    }
                }
            }
        }
    }
}
