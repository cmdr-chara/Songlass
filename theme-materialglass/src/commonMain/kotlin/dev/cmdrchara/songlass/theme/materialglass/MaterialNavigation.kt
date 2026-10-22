package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook

/** A movable tonal destination bubble in Songlass's compact Material enclosure. */
@Composable
internal fun MaterialNavigationBar(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) {
    if (tabs.isEmpty()) return
    val look = LocalGlassLook.current
    val preferredWidth = with(LocalDensity.current) { maxOf(304.dp, 196.dp + 108.sp.toDp()) }
    val shape = RoundedCornerShape(28.dp)
    val palette = currentPalette()
    val bubbleFill = remember(palette) { Brush.verticalGradient(listOf(
        lerp(palette.secondaryContainer, palette.containerHigh, 0.24f),
        palette.secondaryContainer,
        lerp(palette.secondaryContainer, palette.secondary, 0.14f),
    )) }
    val bubbleRim = remember(palette) { Brush.verticalGradient(listOf(
        Color.White.copy(alpha = if (palette.dark) 0.28f else 0.65f),
        palette.secondary.copy(alpha = 0.08f),
        palette.secondary.copy(alpha = 0.22f),
    )) }
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
        if (reduced) snap() else spring(dampingRatio = 0.85f, stiffness = 650f), label = "materialBubblePressure")
    LaunchedEffect(selectedTab) { if (!touching) goal = selectedTab.toFloat() }
    LaunchedEffect(position, reduced) {
        while (true) {
            snapshotFlow { goal }.first { abs(it-position.value) > 0.0001f || abs(position.velocity) > 0.001f }
            if (reduced) position.snapTo(goal)
            else position.follow({ goal }, dampingRatio = 0.88f, stiffness = 700f, threshold = 0.0005f)
        }
    }
    Box(modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = preferredWidth).fillMaxWidth().height(look.navigationBarHeight())) {
        // Only the outer panel samples the page. The moving bubble is an ordinary tonal fill.
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
                    touching = true
                    goal = target(down.position.x).roundToInt().toFloat()
                    var dragged = false
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = change.position.y in -viewConfiguration.touchSlop..(size.height+viewConfiguration.touchSlop)
                                if (dragged) {
                                    goal = target(change.position.x)
                                    change.consume()
                                }
                                break
                            }
                            val dx = abs(change.position.x-down.position.x)
                            val dy = abs(change.position.y-down.position.y)
                            if (!dragged && dy > viewConfiguration.touchSlop && dy > dx) break
                            if (!dragged && dx > viewConfiguration.touchSlop) dragged = true
                            if (dragged) {
                                goal = target(change.position.x)
                                change.consume()
                            }
                        }
                    } finally {
                        val destination = if (released) goal.roundToInt().coerceIn(tabs.indices) else latestSelected
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
                val speed = if (reduced) 0f else abs(position.velocity).coerceAtMost(5f)/5f
                scaleX = 1f + pressure.value*0.08f + speed*0.045f
                scaleY = 1f + pressure.value*0.18f - speed*0.035f
                translationY = -2.dp.toPx()*pressure.value
                this.shape = CircleShape
                clip = false
                shadowElevation = (4.dp.toPx()+4.dp.toPx()*pressure.value)
                ambientShadowColor = palette.secondary.copy(alpha = 0.18f)
                spotShadowColor = Color.Black.copy(alpha = if (palette.dark) 0.35f else 0.18f)
            }.background(bubbleFill, CircleShape).border(0.8.dp, bubbleRim, CircleShape))
            Row(Modifier.fillMaxSize().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    MaterialNavigationItem(tab, index == selectedTab, index == preview,
                        { onTabSelected(index) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MaterialNavigationItem(tab: TabItem, selected: Boolean, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val reduced = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(if (pressed && highlighted && !reduced) 1.05f else 1f,
        if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 750f), label = "materialNavPress")
    val iconTint by animateColorAsState(if (highlighted) palette.onSecondaryContainer else palette.secondaryLabel,
        if (reduced) snap() else tween(150), label = "materialNavIcon")
    val labelTint by animateColorAsState(if (highlighted) palette.onSecondaryContainer else palette.secondaryLabel,
        if (reduced) snap() else tween(150), label = "materialNavLabel")
    Column(modifier.fillMaxHeight().clip(CircleShape)
        .selectable(selected, interactionSource = interaction, indication = MaterialRipple, role = Role.Tab, onClick = onClick)
        .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(width = 56.dp, height = 32.dp).graphicsLayer {
            scaleX = press.value
            scaleY = press.value
        }, contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize(),
                contentAlignment = Alignment.Center) {
                AppIcon(tab.icon, contentDescription = null, size = 24.dp, tint = iconTint)
            }
        }
        // A fixed weight preserves the label's width when the selected destination changes.
        AppText(tab.label, AppTheme.typography.caption1.copy(fontSize = 12.sp,
            lineHeight = 16.sp, fontWeight = FontWeight.Medium), color = labelTint, maxLines = 1)
    }
}
