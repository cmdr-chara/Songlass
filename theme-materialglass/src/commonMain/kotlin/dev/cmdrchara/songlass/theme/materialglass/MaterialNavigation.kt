package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.cmdrchara.songlass.designsystem.foundation.SpringFollower
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook

/** Inline tabs keep the dock compact; larger type returns to a stacked arrangement. */
@Composable
internal fun materialNavigationBarHeight() = with(LocalDensity.current) {
    if (fontScale <= 1.2f) maxOf(56.dp, 32.dp + 20.sp.toDp())
    else maxOf(72.dp, 48.dp + 20.sp.toDp())
}

/** A movable filled destination bubble in Songlass's compact Material enclosure. */
@Composable
internal fun MaterialNavigationBar(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) {
    if (tabs.isEmpty()) return
    val look = LocalGlassLook.current
    // Convert a label-sized sp value: Android's nonlinear scaling does not enlarge 168.sp like twelve 14.sp labels.
    val preferredWidth = with(LocalDensity.current) { maxOf(312.dp, 144.dp + 14.sp.toDp() * 12) }
    val inlineLabels = LocalDensity.current.fontScale <= 1.2f
    val shape = RoundedCornerShape(if (inlineLabels) MaterialShapes.ExtraLarge else 16.dp)
    val selectionShape = if (inlineLabels) CircleShape else RoundedCornerShape(16.dp)
    val palette = currentPalette()
    // Keep the selected Material primary opaque. Darken/lighten only its outer bands
    // away from the onPrimary ink, so the glint cannot reduce the ink contrast.
    val bubbleFill = remember(palette.primary, palette.onPrimary) {
        val awayFromInk = if (palette.onPrimary.luminance() > palette.primary.luminance()) Color.Black else Color.White
        Brush.verticalGradient(
            0f to lerp(palette.primary, awayFromInk, 0.12f),
            0.30f to palette.primary,
            0.70f to palette.primary,
            1f to lerp(palette.primary, awayFromInk, 0.08f),
        )
    }
    val bubbleRim = remember(palette.onPrimary) {
        Brush.verticalGradient(
            0f to palette.onPrimary.copy(alpha = 0.22f),
            0.5f to palette.onPrimary.copy(alpha = 0.04f),
            1f to palette.onPrimary.copy(alpha = 0.14f),
        )
    }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduced = AppTheme.accessibility.reduceMotion
    val position = remember { SpringFollower(selectedTab.toFloat()) }
    var goal by remember { mutableFloatStateOf(selectedTab.toFloat()) }
    var touching by remember { mutableStateOf(false) }
    var rowWidth by remember { mutableFloatStateOf(0f) }
    val latestSelected by rememberUpdatedState(selectedTab)
    val latestActivate by rememberUpdatedState(onTabSelected)
    val preview by remember(tabs.size) {
        derivedStateOf { if (touching) goal.roundToInt().coerceIn(tabs.indices) else latestSelected }
    }
    val pressure = animateFloatAsState(if (touching && !reduced) 1f else 0f,
        if (reduced) snap() else MaterialMotion.press(), label = "materialBubblePressure")
    LaunchedEffect(selectedTab) { if (!touching) goal = selectedTab.toFloat() }
    LaunchedEffect(position, reduced) {
        while (true) {
            snapshotFlow { goal }.first { abs(it-position.value) > 0.0001f || abs(position.velocity) > 0.001f }
            if (reduced) position.snapTo(goal)
            else position.follow({ goal }, dampingRatio = MaterialMotion.SpatialDamping, stiffness = MaterialMotion.SpatialStiffness, threshold = 0.0005f)
        }
    }
    Box(modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = preferredWidth).fillMaxWidth().height(look.navigationBarHeight())) {
        // Only the outer panel samples the page; the bubble keeps its opaque tonal core.
        Box(look.navigationBarSurface(Modifier.matchParentSize(), shape, null, edges = true))
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp)
            .onSizeChanged { rowWidth = it.width.toFloat() }
            .pointerInput(tabs.size, rtl) {
                fun target(x: Float): Float {
                    val logical = if (rtl) size.width-x else x
                    val slot = size.width.toFloat()/tabs.size
                    return (logical/slot-0.5f).coerceIn(0f, (tabs.size-1).toFloat())
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // A tap is committed by selectable on release. Do not preview a new
                    // destination merely because the finger touched its slot.
                    goal = latestSelected.toFloat()
                    touching = target(down.position.x).roundToInt() == latestSelected
                    var dragged = false
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // Compose represents a cancelled pointer as a consumed up.
                                // Check before we consume a successful drag's release.
                                released = !change.isConsumed &&
                                    change.position.y in -viewConfiguration.touchSlop..(size.height+viewConfiguration.touchSlop)
                                if (dragged) {
                                    goal = target(change.position.x)
                                    change.consume()
                                }
                                break
                            }
                            val dx = abs(change.position.x-down.position.x)
                            val dy = abs(change.position.y-down.position.y)
                            if (!dragged && dy > viewConfiguration.touchSlop && dy > dx) break
                            if (!dragged && dx > viewConfiguration.touchSlop) {
                                dragged = true
                                touching = true
                            }
                            if (dragged) {
                                goal = target(change.position.x)
                                change.consume()
                            }
                        }
                    } finally {
                        val destination = if (released && dragged) goal.roundToInt().coerceIn(tabs.indices) else latestSelected
                        touching = false
                        goal = destination.toFloat()
                        // Foundation handles taps/keyboard activation; only a drag commits here.
                        if (released && dragged) latestActivate(destination)
                    }
                }
            }) {
            val slotWidth = rowWidth/tabs.size
            Box(Modifier.width(with(density) { slotWidth.toDp() }).fillMaxHeight().graphicsLayer {
                val at = if (reduced) goal else position.value
                translationX = (if (rtl) -at else at)*slotWidth
            }.padding(horizontal = 4.dp, vertical = 4.dp).graphicsLayer {
                // Selection stays inside its measured tab. Pressure may compress it,
                // but velocity must not expand the highlight into neighbouring tabs.
                scaleX = 1f - pressure.value * 0.02f
                scaleY = 1f - pressure.value * 0.02f
                this.shape = selectionShape
                clip = false
                shadowElevation = 1.dp.toPx()+2.dp.toPx()*pressure.value
                ambientShadowColor = palette.primary.copy(alpha = 0.10f)
                spotShadowColor = Color.Black.copy(alpha = if (palette.dark) 0.22f else 0.12f)
            }.background(bubbleFill, selectionShape).border(0.75.dp, bubbleRim, selectionShape))
            Row(Modifier.fillMaxSize().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    MaterialNavigationItem(tab, index == selectedTab, index == preview, inlineLabels,
                        { onTabSelected(index) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MaterialNavigationItem(tab: TabItem, selected: Boolean, highlighted: Boolean, inlineLabel: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val reduced = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(if (pressed && highlighted && !reduced) 1.05f else 1f,
        if (reduced) snap() else MaterialMotion.press(), label = "materialNavPress")
    val tint = animateColorAsState(if (highlighted) palette.onPrimary else palette.secondaryLabel,
        if (reduced) snap() else MaterialMotion.effects(), label = "materialNavTint")
    val target = modifier.fillMaxHeight().clip(if (inlineLabel) CircleShape else RoundedCornerShape(16.dp))
        .selectable(selected, interactionSource = interaction, indication = MaterialNavigationIndication, role = Role.Tab, onClick = onClick)
    if (inlineLabel) {
        Row(target.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            MaterialNavigationIcon(tab, tint, press)
            MaterialNavigationLabel(tab.label, inlineLabel = true, tint = tint,
                modifier = Modifier.weight(1f, fill = false))
        }
    } else {
        Column(target.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            MaterialNavigationIcon(tab, tint, press)
            MaterialNavigationLabel(tab.label, inlineLabel = false, tint = tint)
        }
    }
}

/** Read animated ink inside its own scope; rebuilding the selectable target is unnecessary. */
@Composable
private fun MaterialNavigationIcon(tab: TabItem, tint: State<Color>, press: State<Float>) {
    AppIcon(tab.icon, contentDescription = null, size = 22.dp, tint = tint.value,
        modifier = Modifier.graphicsLayer { scaleX = press.value; scaleY = press.value })
}

@Composable
private fun MaterialNavigationLabel(text: String, inlineLabel: Boolean, tint: State<Color>, modifier: Modifier = Modifier) {
    AppText(text, if (inlineLabel) AppTheme.typography.subheadline else AppTheme.typography.caption1,
        color = tint.value, maxLines = 1, modifier = modifier)
}
