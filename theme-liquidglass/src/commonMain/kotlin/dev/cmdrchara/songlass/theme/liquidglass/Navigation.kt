package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dev.cmdrchara.songlass.designsystem.foundation.SpringFollower
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.cmdrchara.songlass.designsystem.MiniPlayerState
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.ArtworkTint
import dev.cmdrchara.songlass.designsystem.theme.LocalNavigationTint
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor

private val Capsule = RoundedCornerShape(percent = 50)
private val BarHorizontalMargin = 16.dp
private val BarSpacing = 8.dp
private val MinimizedHeight = 56.dp
private val MiniArtInset = 4.dp
private val MinimizeScrollThreshold = 24.dp

/**
 * Content fills the screen and is the glass source; the navigation layer floats above it at
 * the bottom. Liquid keeps search in its own bubble and folds the mini player inline on
 * scroll; Material uses its unified navigation bar and keeps the mini player above it.
 */
@Composable
internal fun LiquidGlassScaffold(
    tabs: List<TabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    accessory: (@Composable () -> Unit)?,
    content: @Composable (PaddingValues) -> Unit,
) {
    val morph = LocalPlayerMorph.current
    val accessibility = AppTheme.accessibility
    val look = LocalGlassLook.current
    val hazeState = if (accessibility.reduceTransparency || !look.usesHazeBackdrop) null else rememberHazeState()
    val density = LocalDensity.current
    val navigationBarInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var expandedBarsHeightPx by remember { mutableIntStateOf(0) }
    var minimizeRequested by remember { mutableStateOf(false) }
    LaunchedEffect(accessibility.reduceMotion) {
        if (accessibility.reduceMotion) minimizeRequested = false
    }
    val scrollConnection = remember(density, accessibility.reduceMotion) {
        MinimizeOnScroll(thresholdPx = with(density) { MinimizeScrollThreshold.toPx() }) { minimizeRequested = it }
    }
    // Without an accessory there is nothing to show inline, so the bar stays expanded.
    // Large text keeps the accessory at full width instead of squeezing it between two
    // circles. Use the actual scaled line heights (including nonlinear Android scaling).
    val minimized = look.minimizesOnScroll && !accessibility.reduceMotion && minimizeRequested &&
        accessory != null && with(density) {
            // Miuix shares this scaffold but does not minimize; its native line heights may
            // be Unspecified. Only explicit sp metrics can prove that the compact text fits.
            val titleLine = AppTheme.typography.subheadline.lineHeight
            val subtitleLine = AppTheme.typography.footnote.lineHeight
            titleLine.isSp && subtitleLine.isSp &&
                titleLine.toDp() + subtitleLine.toDp() + MiniArtInset * 2 <= MinimizedHeight
        }
    val colors = AppTheme.colors

    // App opening, after the launch splash: the page settles in from slightly larger while the
    // floating bars rise into place a beat later, like an iOS app zooming open. Fade only with
    // Reduce Motion.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, if (accessibility.reduceMotion) tween(LiquidGlassMotion.ReducedMillis) else spring(dampingRatio = 0.9f, stiffness = 180f))
    }
    val barsRise = with(density) { 72.dp.toPx() }
    // Behind the page there is black, visible around it while it recedes under the player.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Like a card stacked under a sheet: smaller, rounded and darker as the player opens.
                val r = if (look.recedesUnderPlayer) morph.recede else 0f
                val transform = if (accessibility.reduceMotion) 0f else r
                scaleX = 1f - 0.08f * transform
                scaleY = 1f - 0.08f * transform
                shape = RoundedCornerShape(28.dp * transform)
                clip = transform > 0f
            }
            .drawWithContent {
                drawContent()
                if (look.recedesUnderPlayer) drawRect(Color.Black.copy(alpha = 0.35f * morph.recede))
            }
            .background(colors.background),
    ) {
        // The background must be inside hazeSource, otherwise the glass samples transparent pixels.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (accessibility.reduceMotion) Modifier else Modifier.nestedScroll(scrollConnection))
                .then(look.contentSource(Modifier))
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                .background(colors.background),
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val e = entrance.value
                    alpha = (e * 1.6f).coerceAtMost(1f)
                    if (!accessibility.reduceMotion) {
                        scaleX = 1.05f - 0.05f * e
                        scaleY = 1.05f - 0.05f * e
                    }
                },
            ) {
                // Keep the content inset at the expanded height while the floating bars animate.
                // Measuring the animated container would make the list jump during expansion.
                content(PaddingValues(bottom = with(density) { expandedBarsHeightPx.toDp() } + navigationBarInset + BarSpacing * 2))
            }
        }
        val navigationTint = if (look.tintsNavigation) LocalNavigationTint.current else null
        CompositionLocalProvider(LocalGlassSource provides hazeState) {
            NavigationTintScope(navigationTint) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        // The bars follow the page with a short delay.
                        val e = ((entrance.value - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        alpha = e
                        if (!accessibility.reduceMotion) translationY = (1f - e) * barsRise
                    }
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = BarHorizontalMargin)
                    .padding(bottom = BarSpacing),
            ) {
                NavigationLayer(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { index ->
                        minimizeRequested = false
                        onTabSelected(index)
                    },
                    onExpand = { minimizeRequested = false },
                    accessory = accessory,
                    minimized = minimized,
                    onExpandedHeightChanged = { expandedBarsHeightPx = it },
                )
            }
            }
        }
    }
    }
}

/** Draws the navigation layer in the page's tint when there is one, so its glass matches. */
@Composable
private fun NavigationTintScope(tint: ArtworkTint?, content: @Composable () -> Unit) {
    if (tint == null) content() else ProvideArtworkTint(animateArtworkTint(tint), content)
}

/** Directional hysteresis: down minimizes, up expands without requiring the list's top. */
private class MinimizeOnScroll(
    private val thresholdPx: Float,
    private val onMinimizedChange: (Boolean) -> Unit,
) : NestedScrollConnection {
    private var travelled = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        when {
            consumed.y < 0f -> {
                travelled = minOf(travelled, 0f) + consumed.y
                if (travelled < -thresholdPx) {
                    onMinimizedChange(true)
                    travelled = 0f
                }
            }
            consumed.y > 0f -> {
                travelled = maxOf(travelled, 0f) + consumed.y
                if (travelled > thresholdPx) {
                    onMinimizedChange(false)
                    travelled = 0f
                }
            }
        }
        if (available.y > 0f) {
            travelled = 0f
            onMinimizedChange(false)
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y > 0f) onMinimizedChange(false)
        return Velocity.Zero
    }
}

@Composable
private fun NavigationLayer(
    tabs: List<TabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onExpand: () -> Unit,
    accessory: (@Composable () -> Unit)?,
    minimized: Boolean,
    onExpandedHeightChanged: (Int) -> Unit,
) {
    val searchIndex = tabs.indexOfFirst { it.isSearch }
    val reduceMotion = AppTheme.accessibility.reduceMotion
    AnimatedContent(
        targetState = minimized,
        transitionSpec = {
            if (reduceMotion) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0)) using null
            } else {
                fadeIn(tween(LiquidGlassMotion.FadeMillis, delayMillis = 60)) togetherWith
                    fadeOut(tween(LiquidGlassMotion.FeedbackMillis)) using SizeTransform(clip = false) { _, _ ->
                        tween(LiquidGlassMotion.NavigationMillis, easing = LiquidGlassMotion.Emphasized)
                    }
            }
        },
        contentAlignment = Alignment.BottomCenter,
        label = "tabBarMinimize",
    ) { isMinimized ->
        if (isMinimized) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinimizedHeight),
                horizontalArrangement = Arrangement.spacedBy(BarSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val current = tabs[selectedTab]
                GlassCircleButton(current.icon, current.label, selected = true, size = MinimizedHeight, onClick = onExpand)
                Box(Modifier.weight(1f)) { accessory?.invoke() }
                if (searchIndex >= 0 && searchIndex != selectedTab) {
                    GlassCircleButton(AppIcons.Search, tabs[searchIndex].label, selected = false, size = MinimizedHeight) {
                        onTabSelected(searchIndex)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.onSizeChanged { onExpandedHeightChanged(it.height) },
                verticalArrangement = Arrangement.spacedBy(BarSpacing),
            ) {
                accessory?.invoke()
                LocalGlassLook.current.NavigationBar(tabs, selectedTab, onTabSelected, Modifier)
            }
        }
    }
}

/** iOS 26 tab bar: the tab pill and, beside it, the search circle, merging when pressed together. */
@Composable
internal fun LiquidNavigationBar(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) {
    val searchIndex = tabs.indexOfFirst { it.isSearch }
    val barHeight = LocalGlassLook.current.navigationBarHeight()
    val link = remember { LiquidLink() }
    Box(
        modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 360.dp).fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (searchIndex >= 0) LiquidBridge(link)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(BarSpacing), verticalAlignment = Alignment.CenterVertically) {
            TabPill(tabs, selectedTab, onTabSelected, Modifier.weight(1f), link)
            if (searchIndex >= 0) {
                val search = tabs[searchIndex]
                GlassCircleButton(search.icon, search.label, selected = selectedTab == searchIndex, size = barHeight, link = link) {
                    onTabSelected(searchIndex)
                }
            }
        }
    }
}

/**
 * The tab capsule. The selection is a single bubble that slides between tabs; as in iOS 26 it
 * can be grabbed and dragged along the bar, lifting while it follows the finger, and selects the
 * tab it is dropped on. Taps still select directly.
 */
@Composable
private fun TabPill(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier, link: LiquidLink? = null) {
    val slots = remember(tabs) { tabs.indices.filterNot { tabs[it].isSearch } }
    val barHeight = LocalGlassLook.current.navigationBarHeight()
    val selectedSlot = slots.indexOf(selectedTab)
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var slotWidth by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val bubbleX = remember { Animatable(0f) }
    // 0 at rest, 1 while the bubble is held and lifted off the bar.
    val lift = remember { Animatable(0f) }
    val travelLift = remember { Animatable(0f) }
    val currentSelected by rememberUpdatedState(selectedTab)
    val currentOnSelected by rememberUpdatedState(onTabSelected)

    fun slotAt(x: Float): Int = if (slotWidth <= 0f) 0 else ((x + slotWidth / 2f) / slotWidth).toInt().coerceIn(slots.indices)

    LaunchedEffect(selectedSlot, slotWidth, reduceMotion) {
        // A quick switch to Search can interrupt the previous hop midway.
        travelLift.snapTo(0f)
        if (reduceMotion) lift.snapTo(0f)
        if (slotWidth <= 0f || selectedSlot < 0 || dragging) return@LaunchedEffect
        val target = selectedSlot * slotWidth
        if (!placed || reduceMotion) {
            bubbleX.snapTo(target)
            travelLift.snapTo(0f)
        } else if (abs(target - bubbleX.value) > 1f) {
            launch {
                travelLift.animateTo(0.65f, tween(80))
                travelLift.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 500f))
            }
            bubbleX.animateTo(target, spring(dampingRatio = 0.76f, stiffness = 480f))
        }
        placed = true
    }
    // Search owns the separate circle, so hide this lens while that destination is selected.
    val bubbleAlphaState = animateFloatAsState(
        targetValue = if (selectedSlot >= 0 || dragging) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "tabBubbleAlpha",
    )
    val bubbleAlpha by bubbleAlphaState
    val bubbleShown by remember { derivedStateOf { bubbleAlphaState.value > 0f } }
    // Ink changes at slot boundaries; the lens still follows every spring frame in
    // layout/drawing without recomposing the full glass capsule while dragged.
    val shownSlot by remember(slots, selectedSlot) {
        derivedStateOf { if (dragging) slotAt(bubbleX.value) else selectedSlot }
    }
    val inset = 5.dp

    Box(
        modifier = modifier
            .height(barHeight)
            .liquidPress(grow = 0.015f, reach = 2.dp, link = link, side = LinkSide.Start)
            .pointerInput(slots, reduceMotion, rtl) {
                val insetPx = inset.toPx()
                fun dragTargetAt(x: Float): Float {
                    val logicalX = if (rtl) size.width - x else x
                    return (logicalX - insetPx - slotWidth / 2f).coerceIn(0f, slotWidth * (slots.size - 1))
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!reduceMotion) scope.launch { lift.animateTo(0.45f, spring(dampingRatio = 0.85f, stiffness = 900f)) }
                    var dragged = false
                    var lastDragX = bubbleX.value
                    // One pursuit spring per gesture: the finger moves its goal, never restarts it.
                    val pursuit = SpringFollower()
                    var pursuitGoal = bubbleX.value
                    var pursuing: Job? = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Consuming the release keeps the tab under the finger from also clicking.
                            if (dragged) {
                                lastDragX = dragTargetAt(change.position.x)
                                change.consume()
                            }
                            break
                        }
                        if (!dragged && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                            dragged = true
                            dragging = true
                            if (!reduceMotion) scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f)) }
                        }
                        if (dragged) {
                            change.consume()
                            // Row and offset use logical order; pointer coordinates are physical.
                            val x = dragTargetAt(change.position.x)
                            lastDragX = x
                            pursuitGoal = x
                            if (reduceMotion) {
                                scope.launch { bubbleX.snapTo(x) }
                            } else if (pursuing?.isActive != true) {
                                pursuit.snapTo(bubbleX.value, bubbleX.velocity)
                                pursuing = scope.launch {
                                    pursuit.follow({ pursuitGoal }, dampingRatio = 0.85f, stiffness = 2200f, threshold = 0.5f) { bubbleX.snapTo(it) }
                                }
                            }
                        }
                    }
                    scope.launch { lift.animateTo(0f, if (reduceMotion) snap() else spring(dampingRatio = 0.65f, stiffness = 500f)) }
                    if (dragged) {
                        // Commit the finger's destination; the pursuit spring may still lag
                        // behind it when rendering is busy. Keep the same settling animation.
                        val slot = slotAt(lastDragX)
                        dragging = false
                        val releaseVelocity = if (pursuing != null) pursuit.velocity else bubbleX.velocity
                        pursuing?.cancel()
                        scope.launch {
                            val target = slot * slotWidth
                            if (reduceMotion) bubbleX.snapTo(target)
                            else bubbleX.animateTo(target, spring(dampingRatio = 0.6f, stiffness = 450f), initialVelocity = releaseVelocity)
                        }
                        if (slots[slot] != currentSelected) currentOnSelected(slots[slot])
                    }
                }
            },
    ) {
        // Separate siblings: the bar never clips or filters the lens that lifts above it.
        Box(Modifier.matchParentSize().navigationBarGlass(Capsule))
        Box(Modifier.fillMaxSize().padding(inset).onSizeChanged {
            slotWidth = it.width / slots.size.coerceAtLeast(1).toFloat()
        }) {
            Box(
                Modifier
                    .offset { IntOffset(bubbleX.value.roundToInt(), 0) }
                    .width(with(density) { slotWidth.toDp() })
                    .fillMaxHeight()
                    .graphicsLayer {
                        val l = if (reduceMotion) 0f else maxOf(lift.value, travelLift.value).coerceIn(0f, 1f)
                        alpha = bubbleAlpha
                        // More volume while held, with the rim free to rise above the capsule.
                        scaleX = 1f + 0.10f * l
                        scaleY = 1f + 0.42f * l
                        translationY = -2.dp.toPx() * l
                    }
                    .then(if (bubbleShown) LocalGlassLook.current.navigationSelectionSurface(Modifier, Capsule) else Modifier),
            )
            Row(Modifier.fillMaxSize()) {
                slots.forEachIndexed { slot, index ->
                    val tab = tabs[index]
                    val selected = index == selectedTab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                val magnification = if (slot == shownSlot && !reduceMotion) {
                                    1f + 0.08f * maxOf(lift.value, travelLift.value).coerceIn(0f, 1f)
                                } else 1f
                                scaleX = magnification
                                scaleY = magnification
                            }
                            .selectable(selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onTabSelected(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        TabLabel(tab) { slot == shownSlot }
                    }
                }
            }
        }
    }
}

/**
 * Glass bridge that joins the end of the tab pill and the search circle while they merge, like
 * neighbouring shapes in a Liquid Glass container. It sits beneath them and fills only the space
 * in between with the bare material (no rim light or lens), continuing their rims along the waist.
 */
@Composable
private fun LiquidBridge(link: LiquidLink) {
    val merging by remember(link) { derivedStateOf { link.merge() > 0.02f } }
    if (!merging) return
    val density = LocalDensity.current
    val dark = AppTheme.colors.isDark
    val barHeight = LocalGlassLook.current.navigationBarHeight()
    val width = barHeight / 2 + BarSpacing + barHeight + 8.dp
    val neck by remember(link, density) {
        derivedStateOf {
            with(density) {
                val start = link.bodies[LinkSide.Start] ?: return@derivedStateOf null
                val end = link.bodies[LinkSide.End] ?: return@derivedStateOf null
                val w = width.toPx()
                val h = barHeight.toPx()
                val r = h / 2f
                // The pill scales around its own centre, which moves its end cap outwards.
                val s1 = start.scale()
                val o1 = start.offset(link, LinkSide.Start)
                val capX = w - h - BarSpacing.toPx() - r + (start.widthPx / 2f - r) * (s1 - 1f) + o1.x
                val s2 = end.scale()
                val o2 = end.offset(link, LinkSide.End)
                metaballNeck(
                    c1 = Offset(capX, r + o1.y),
                    r1 = r * s1,
                    c2 = Offset(w - r + o2.x, r + o2.y),
                    r2 = r * s2,
                    spread = 0.6f * link.merge(),
                    inset = 3.dp.toPx(),
                )
            }
        }
    }
    // Keep a fixed capture around the gap: changing its size/position with the path would
    // invalidate Haze's retained blur on every move, even over a stationary page. The slot
    // and vertical gradient stay unchanged; Haze still samples its normal blur margins.
    val viewport by remember(link, density, width, barHeight) {
        derivedStateOf {
            val bounds = neck?.fill?.getBounds()
            val fullWidth = with(density) { width.roundToPx() }
            val height = with(density) { barHeight.roundToPx() }
            val left = with(density) { (barHeight / 4).roundToPx() }
            val right = fullWidth - height / 2
            // Keep an AA pixel of padding. Unusual geometry always gets the complete slot.
            if (bounds != null && bounds.left >= left + 1 && bounds.right <= right - 1) {
                IntRect(left, 0, right, height)
            } else IntRect(0, 0, fullWidth, height)
        }
    }
    // Use the capsule's lighting across the complete slot. A separate white fill
    // would brighten only the waist and reveal the join over colourful content.
    val rim = remember(dark) { liquidNavigationRim(dark) }
    Box(
        Modifier
            .width(width)
            .height(barHeight)
            .layout { measurable, constraints ->
                val crop = viewport
                val child = measurable.measure(constraints.copy(minWidth = crop.width, maxWidth = crop.width))
                layout(constraints.maxWidth, child.height) { child.place(crop.left, 0) }
            }
            .graphicsLayer {
                val fill = neck?.fill
                val shift = Offset(-viewport.left.toFloat(), 0f)
                clip = true
                shape = GenericShape { _, _ -> if (fill != null) addPath(fill, shift) }
            }
            .navigationBarGlass(RoundedCornerShape(0), edges = false)
            .drawWithContent {
                drawContent()
                val n = neck ?: return@drawWithContent
                translate(left = -viewport.left.toFloat()) {
                    // Continue the rim of the two shapes along the waist.
                    drawPath(n.top, rim, style = Stroke(0.65.dp.toPx()))
                    drawPath(n.bottom, rim, style = Stroke(0.65.dp.toPx()))
                }
            },
    )
}

/**
 * Icon and label of one tab. Its own scope: the tint animation after the bubble crosses a slot
 * recomposes only this item, never the capsule with its glass modifiers.
 */
@Composable
private fun TabLabel(tab: TabItem, highlighted: () -> Boolean) {
    val tint = tabTint(highlighted())
    AppIcon(tab.icon, contentDescription = null, size = 26.dp, tint = tint)
    AppText(
        text = tab.label,
        style = AppTheme.typography.caption2.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
        color = tint,
        maxLines = 1,
    )
}

/** The search icon, in its own scope for the same reason as [TabLabel]. */
@Composable
private fun TabIcon(icon: ImageVector, selected: Boolean) {
    AppIcon(icon, contentDescription = null, size = 26.dp, tint = tabTint(selected))
}

/** Selected tabs use the accent; others the label colour, full strength for legibility on glass. */
@Composable
private fun tabTint(selected: Boolean): Color {
    val tint by animateColorAsState(
        targetValue = if (selected) lerp(AppTheme.colors.accent, AppTheme.colors.label, 0.18f) else AppTheme.colors.label,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "tabSelectionTint",
    )
    return tint
}

@Composable
private fun GlassCircleButton(icon: ImageVector, label: String, selected: Boolean, size: Dp, link: LiquidLink? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val selectionAlphaState = animateFloatAsState(
        if (selected) 1f else 0f,
        if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "circleSelection",
    )
    val selectionAlpha by selectionAlphaState
    // A fully transparent lens still costs a glass pass every frame the circle moves.
    val lensShown by remember { derivedStateOf { selectionAlphaState.value > 0f } }
    Box(
        modifier = Modifier
            .size(size)
            .liquidPress(grow = 0.05f, reach = 3.dp, link = link, side = LinkSide.End)
            .navigationBarGlass(Capsule, interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        if (lensShown) {
            Box(
                Modifier.matchParentSize().padding(4.dp).graphicsLayer { alpha = selectionAlpha }
                    .then(LocalGlassLook.current.navigationSelectionSurface(Modifier, Capsule)),
            )
        }
        TabIcon(icon, selected)
    }
}

/**
 * Glass capsule shown above (or inline with) the tab bar while something is playing, as the
 * iOS 26 tab bar accessory. Swiping it sideways changes track.
 */
@Composable
internal fun LiquidGlassMiniPlayer(
    state: MiniPlayerState,
    labels: PlaybackLabels,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val swipe = rememberTrackSwipe()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinimizedHeight)
            // Where the Now Playing transition starts from and returns to.
            .miniPlayerFrame()
            .liquidPress(grow = 0.015f, reach = 2.dp)
            .liquidGlass(Capsule, interaction)
            .trackSwipe(swipe, onNext, onPrevious)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .trackSwipeActions(labels, onNext, onPrevious)
            .padding(start = MiniArtInset, end = 4.dp, top = MiniArtInset, bottom = MiniArtInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).followTrackSwipe(swipe), verticalAlignment = Alignment.CenterVertically) {
            // The cover fills the leading end of the capsule, its rounded side concentric with it.
            Box(
                Modifier
                    .size(MinimizedHeight - MiniArtInset * 2)
                    .clip(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 20, bottomEndPercent = 20))
                    .miniPlayerCover(),
            ) {
                LiquidGlassAlbumArt(state.artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                AppText(state.title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                AppText(state.subtitle, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
            }
        }
        LiquidGlassIconButton(
            icon = if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (state.isPlaying) labels.pause else labels.play,
            onClick = onPlayPause,
            selected = false,
            modifier = Modifier,
        )
        LiquidGlassIconButton(AppIcons.Next, labels.next, onNext, selected = false, modifier = Modifier)
    }
}

/** Icon button: 44×44 dp hit area, subtle scale on press. */
@Composable
internal fun LiquidGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier,
    iconSize: Dp = 24.dp,
    hitSize: Dp = 44.dp,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val content = LocalContentColor.current
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.92f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = 700f),
        label = "iconButtonPress",
    )
    // A selected filled button is a solid circle in the accent (the artwork tint in the player).
    val solid = filled && selected
    val background by animateColorAsState(
        targetValue = when {
            solid -> AppTheme.colors.accent.copy(alpha = if (pressed) 0.8f else 1f)
            filled -> content.copy(alpha = if (pressed) 0.24f else 0.14f)
            pressed -> content.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        animationSpec = if (reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "iconButtonBackground",
    )
    val tint by animateColorAsState(
        targetValue = when {
            solid -> AppTheme.colors.background
            selected -> AppTheme.colors.accent
            else -> content
        },
        animationSpec = if (reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "iconButtonTint",
    )
    // Only the visual layer scales: the touch and accessibility target stays at hitSize.
    Box(
        modifier = modifier
            .size(hitSize)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (filled) Modifier.padding(6.dp) else Modifier)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .let { if (background.alpha > 0f) LocalGlassLook.current.controlSurface(it, CircleShape, background, prominent = solid) else it },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                icon = icon,
                contentDescription = contentDescription,
                size = if (filled) iconSize * 0.75f else iconSize,
                tint = tint,
            )
        }
    }
}
