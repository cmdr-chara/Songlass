package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import dev.cmdrchara.songlass.designsystem.foundation.SpringFollower
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/** Independent Songlass implementation of the source-analyzed floating navigation behavior. */
@Composable
internal fun MiuixNavigation(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) {
    if (tabs.isEmpty()) return
    val reduced = AppTheme.accessibility.reduceMotion
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selected = selectedTab.coerceIn(tabs.indices)
    val latestSelection by rememberUpdatedState(selected)
    val latestActivate by rememberUpdatedState(onTabSelected)
    val motion = rememberNavigationMotion(selected, tabs.size, reduced)
    val pressure = navigationPressure(motion, reduced)
    val expansion = navigationExpansion(motion, reduced)
    // The panel give follows every pointer move: drive it outside composition so a drag
    // never recomposes the bar, only updates its layer.
    val pull = remember { SpringFollower(0f) }
    LaunchedEffect(motion, reduced) {
        while (true) {
            snapshotFlow { if (reduced) 0f else motion.panelPull }.first { it != pull.value || pull.velocity != 0f }
            if (reduced) pull.snapTo(0f)
            else pull.follow({ motion.panelPull }, dampingRatio = 0.88f, stiffness = 460f, threshold = 0.0005f)
        }
    }
    val root = LocalMiuixBackdrop.current.takeUnless { AppTheme.accessibility.reduceTransparency }
    val accentLayer = if (root != null && !reduced) rememberLayerBackdrop() else null
    val scene = remember(root, accentLayer) { if (root != null && accentLayer != null) NavigationScene(listOf(root, accentLayer)) else null }
    val lighting = rememberNavigationLighting(root != null && !reduced)
    LaunchedEffect(selected) { if (!motion.touching) motion.destination = selected.toFloat() }
    val activate: (Int) -> Unit = { index ->
        motion.destination = index.toFloat()
        latestActivate(index) // Repeated Search activation is meaningful in Songlass.
    }
    val inset = 4.dp
    val height = MiuixLook.navigationBarHeight()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Keep the reference's broad floating footprint with three musical destinations.
        // The scaffold already reserves 16 dp at each side; add 12 dp here for a 28 dp margin.
        val width = minOf((maxWidth - 24.dp).coerceAtLeast(1.dp), 360.dp)
        val slot = ((width - inset * 2) / tabs.size).coerceAtLeast(1.dp)
        val slotPx = with(density) { slot.toPx() }
        val fullPx = with(density) { width.toPx() }
        val insetPx = with(density) { inset.toPx() }
        fun indexAt(x: Float): Int {
            val logical = if (rtl) fullPx - x else x
            return ((logical - insetPx) / slotPx).toInt().coerceIn(tabs.indices)
        }
        Box(Modifier.width(width).height(height)
            .graphicsLayer {
                translationX = 3.dp.toPx() * pull.value
            }
            .pointerInput(tabs.size, slotPx, rtl) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    motion.touching = true
                    motion.destination = indexAt(down.position.x).toFloat()
                    var activePointer = down.id
                    var previousX = down.position.x
                    var travel = 0f
                    var committed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == activePointer } ?: break
                            if (!change.pressed) {
                                val continuation = event.changes.firstOrNull { it.pressed && it.id != activePointer }
                                if (continuation != null) {
                                    change.consume()
                                    activePointer = continuation.id
                                    previousX = continuation.position.x
                                    continue
                                }
                                change.consume()
                                committed = true
                                motion.touching = false
                                activate(motion.destination.roundToInt().coerceIn(tabs.indices))
                                break
                            }
                            val x = change.position.x
                            if (x in 0f..fullPx && previousX in 0f..fullPx) {
                                val delta = x - previousX
                                motion.destination = (motion.destination + (if (rtl) -delta else delta) / slotPx).coerceIn(0f, tabs.lastIndex.toFloat())
                                travel += delta
                                motion.panelPull = tanh(travel / (fullPx * 0.40f).coerceAtLeast(1f))
                            }
                            previousX = x
                            change.consume()
                        }
                    } finally {
                        motion.touching = false
                        motion.panelPull = 0f
                        if (!committed) motion.destination = latestSelection.toFloat()
                    }
                }
            }) {
            // Base scene is neutral. A separate, noninteractive accent scene is recorded for the lens.
            Box(miuixNavigationGlass(Modifier.matchParentSize(), RoundedCornerShape(percent = 50),
                source = root, pressure = { pressure.value }, rim = { lighting.value }, layerBlock = {
                    val expansion = if (reduced) 0f else pressure.value.coerceIn(0f, 1f) * minOf(0.04f, 12.dp.toPx() / size.width.coerceAtLeast(1f))
                    scaleX = 1f + expansion; scaleY = 1f + expansion
                })) {
                if (root != null) NavigationGlow(pressure = { pressure.value }, spot = { (motion.location.value + 0.5f) / tabs.size }, Modifier.matchParentSize())
                NavigationItems(tabs, selected, colors.label.copy(alpha = 0.94f), { 1f }, activate,
                    Modifier.fillMaxSize().padding(inset).selectableGroup())
            }
            if (accentLayer != null) {
                Box(Modifier.matchParentSize().graphicsLayer { alpha = 0f }.layerBackdrop(accentLayer).clearAndSetSemantics {}) {
                    Box(miuixNavigationGlass(Modifier.matchParentSize(), RoundedCornerShape(percent = 50),
                        source = root, pressure = { pressure.value }, rim = { null }, castShadow = false)) {
                        NavigationGlow(pressure = { pressure.value }, spot = { (motion.location.value + 0.5f) / tabs.size }, Modifier.matchParentSize())
                    }
                    NavigationItems(tabs, selected, colors.accent,
                        { 1f + 0.18f * pressure.value.coerceIn(0f, 1f) }, null, Modifier.fillMaxSize().padding(inset))
                }
            }
            Box(Modifier.fillMaxSize().padding(inset)) {
                val lensModifier = Modifier.offset {
                    IntOffset((motion.location.value.coerceIn(0f, tabs.lastIndex.toFloat()) * slotPx).roundToInt(), 0)
                }.width(slot).fillMaxHeight()
                // A calm, solid selection at rest avoids refracted page edges looking like a
                // dirty shadow. Its accent ink is clipped, decorative and never a second tab.
                Box(lensModifier.graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(percent = 50)
                    alpha = if (scene == null) 1f else 1f - (pressure.value * 3f).coerceIn(0f, 1f)
                }.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                    colors.label.copy(alpha = if (colors.isDark) 0.16f else 0.085f),
                    colors.label.copy(alpha = if (colors.isDark) 0.09f else 0.045f),
                ))).border(0.6.dp, colors.label.copy(alpha = if (colors.isDark) 0.10f else 0.055f), RoundedCornerShape(percent = 50))) {
                    NavigationItems(tabs, selected, colors.accent, { 1f }, null,
                        Modifier.wrapContentWidth(Alignment.Start, unbounded = true).requiredWidth(width - inset * 2).fillMaxHeight().offset {
                            IntOffset(-(motion.location.value.coerceIn(0f, tabs.lastIndex.toFloat()) * slotPx).roundToInt(), 0)
                        }.clearAndSetSemantics {})
                }
                if (scene != null) {
                    Box(miuixNavigationGlass(lensModifier.graphicsLayer { alpha = (pressure.value * 3f).coerceIn(0f, 1f) },
                        RoundedCornerShape(percent = 50), bubble = true,
                        source = scene, pressure = { pressure.value }, rim = { lighting.value },
                        layerBlock = {
                            val p = pressure.value.coerceIn(0f, 1f)
                            val speed = (abs(motion.location.velocity) / 9f).coerceIn(0f, 1f)
                            scaleX = expansion.width.value + 0.12f * speed * p
                            scaleY = expansion.height.value - 0.07f * speed * p
                        }))
                }
            }
        }
    }
}

@Composable
private fun NavigationItems(tabs: List<TabItem>, selected: Int, tint: Color, magnification: () -> Float, activate: ((Int) -> Unit)?, modifier: Modifier) {
    val label = AppTheme.typography.caption1.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
    Row(modifier) {
        tabs.forEachIndexed { index, tab ->
            Column(Modifier.weight(1f).fillMaxHeight()
                .then(if (activate == null) Modifier else Modifier.selectable(index == selected, role = Role.Tab, onClick = { activate(index) }))
                .graphicsLayer { scaleX = magnification(); scaleY = magnification() },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AppIcon(tab.icon, null, size = 25.dp, tint = tint)
                Spacer(Modifier.height(1.dp))
                AppText(tab.label, label, color = tint, maxLines = 1)
            }
        }
    }
}
