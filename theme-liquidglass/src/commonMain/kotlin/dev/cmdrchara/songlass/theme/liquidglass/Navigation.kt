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
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
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
private val TabBarHeight = 62.dp
private val MinimizedHeight = 56.dp
private val MiniArtInset = 4.dp
private val MinimizeScrollThreshold = 24.dp

/**
 * Content fills the screen and is the glass source; the navigation layer floats above it at
 * the bottom, as in iOS 26: a pill with the tabs, a separate circle for search, and the
 * accessory (mini player) above them. Scrolling down minimizes the tab bar and moves the
 * accessory inline; scrolling back to the top or tapping a tab restores it.
 */
@Composable
internal fun LiquidGlassScaffold(
    tabs: List<TabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    accessory: (@Composable () -> Unit)?,
    content: @Composable (PaddingValues) -> Unit,
) {
    val accessibility = AppTheme.accessibility
    val hazeState = if (accessibility.reduceTransparency) null else rememberHazeState()
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
    val minimized = LocalGlassLook.current.minimizesOnScroll && !accessibility.reduceMotion && minimizeRequested && accessory != null
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
                val r = PlayerMorph.recede
                if (r <= 0f) return@graphicsLayer
                if (!accessibility.reduceMotion) {
                    scaleX = 1f - 0.08f * r
                    scaleY = 1f - 0.08f * r
                    shape = RoundedCornerShape(28.dp * r)
                    clip = true
                }
            }
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = 0.35f * PlayerMorph.recede))
            }
            .background(colors.background),
    ) {
        // The background must be inside hazeSource, otherwise the glass samples transparent pixels.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (accessibility.reduceMotion) Modifier else Modifier.nestedScroll(scrollConnection))
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
        val navigationTint = LocalNavigationTint.current
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

/** Minimizes on downward scrolling; expands when an upward scroll or fling hits the top. */
private class MinimizeOnScroll(
    private val thresholdPx: Float,
    private val onMinimizedChange: (Boolean) -> Unit,
) : NestedScrollConnection {
    private var travelled = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        when {
            consumed.y < 0f -> {
                travelled += consumed.y
                if (travelled < -thresholdPx) {
                    onMinimizedChange(true)
                    travelled = 0f
                }
            }
            consumed.y > 0f -> travelled = 0f
        }
        if (available.y > 0f) onMinimizedChange(false)
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
                modifier = Modifier.fillMaxWidth().height(MinimizedHeight),
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
    val link = remember { LiquidLink() }
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        if (searchIndex >= 0) LiquidBridge(link)
        Row(horizontalArrangement = Arrangement.spacedBy(BarSpacing), verticalAlignment = Alignment.CenterVertically) {
            TabPill(tabs, selectedTab, onTabSelected, Modifier.weight(1f), link)
            if (searchIndex >= 0) {
                val search = tabs[searchIndex]
                GlassCircleButton(search.icon, search.label, selected = selectedTab == searchIndex, size = TabBarHeight, link = link) {
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
    val selectedSlot = slots.indexOf(selectedTab)
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var slotWidth by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val bubbleX = remember { Animatable(0f) }
    // 0 at rest, 1 while the bubble is held and lifted off the bar.
    val lift = remember { Animatable(0f) }
    val currentSelected by rememberUpdatedState(selectedTab)
    val currentOnSelected by rememberUpdatedState(onTabSelected)

    fun slotAt(x: Float): Int = if (slotWidth <= 0f) 0 else ((x + slotWidth / 2f) / slotWidth).toInt().coerceIn(slots.indices)

    LaunchedEffect(selectedSlot, slotWidth) {
        if (slotWidth <= 0f || selectedSlot < 0 || dragging) return@LaunchedEffect
        val target = selectedSlot * slotWidth
        if (!placed || reduceMotion) bubbleX.snapTo(target) else bubbleX.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 500f))
        placed = true
    }
    // Hidden while Search (outside the pill) is selected.
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (selectedSlot >= 0 || dragging) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "tabBubbleAlpha",
    )
    val shownSlot = if (dragging) slotAt(bubbleX.value) else selectedSlot
    val inset = 4.dp

    Box(
        modifier = modifier
            .height(TabBarHeight)
            .liquidPress(grow = 0.015f, reach = 2.dp, link = link, side = LinkSide.Start)
            .liquidGlass(Capsule)
            .pointerInput(slots) {
                val insetPx = inset.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var dragged = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Consuming the release keeps the tab under the finger from also clicking.
                            if (dragged) change.consume()
                            break
                        }
                        if (!dragged && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                            dragged = true
                            dragging = true
                            if (!reduceMotion) scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f)) }
                        }
                        if (dragged) {
                            change.consume()
                            val x = (change.position.x - insetPx - slotWidth / 2f).coerceIn(0f, slotWidth * (slots.size - 1))
                            scope.launch { bubbleX.animateTo(x, spring(dampingRatio = 0.85f, stiffness = 2200f)) }
                        }
                    }
                    if (dragged) {
                        val slot = slotAt(bubbleX.value)
                        dragging = false
                        scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 450f)) }
                        scope.launch {
                            val target = slot * slotWidth
                            if (reduceMotion) bubbleX.snapTo(target) else bubbleX.animateTo(target, spring(dampingRatio = 0.6f, stiffness = 450f))
                        }
                        if (slots[slot] != currentSelected) currentOnSelected(slots[slot])
                    }
                }
            }
            .padding(inset)
            .onSizeChanged { slotWidth = it.width / slots.size.coerceAtLeast(1).toFloat() },
    ) {
        Box(
            Modifier
                .offset { IntOffset(bubbleX.value.roundToInt(), 0) }
                .width(with(density) { slotWidth.toDp() })
                .fillMaxHeight()
                .graphicsLayer {
                    val l = lift.value
                    alpha = bubbleAlpha
                    // Lifted, the bubble swells slightly past the bar.
                    scaleX = 1f + 0.05f * l
                    scaleY = 1f + 0.12f * l
                }
                .clip(Capsule)
                .background(lerp(LocalGlassLook.current.selection(), colors.label.copy(alpha = 0.08f), lift.value))
                .border(1.dp, Color.White.copy(alpha = 0.25f * lift.value), Capsule),
        )
        Row(Modifier.fillMaxSize()) {
            slots.forEachIndexed { slot, index ->
                val tab = tabs[index]
                val selected = index == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabSelected(index) }
                        .semantics { role = Role.Tab; this.selected = selected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = tabTint(slot == shownSlot)
                    AppIcon(tab.icon, contentDescription = null, size = 26.dp, tint = tint)
                    AppText(
                        text = tab.label,
                        style = AppTheme.typography.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = tint,
                        maxLines = 1,
                    )
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
    val width = TabBarHeight / 2 + BarSpacing + TabBarHeight + 8.dp
    val neck by remember(link, density) {
        derivedStateOf {
            with(density) {
                val start = link.bodies[LinkSide.Start] ?: return@derivedStateOf null
                val end = link.bodies[LinkSide.End] ?: return@derivedStateOf null
                val w = width.toPx()
                val h = TabBarHeight.toPx()
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
    // Both edges of the waist need a rim as visible as the outlines of the two shapes, otherwise
    // only the upper curve shows and reads as two spikes.
    val rimTop = Color.White.copy(alpha = if (dark) 0.42f else 0.65f)
    val rimBottom = Color.White.copy(alpha = if (dark) 0.3f else 0.45f)
    // The bare material lacks the edge lighting that lifts the shapes' bodies a little.
    val lift = Color.White.copy(alpha = if (dark) 0.05f else 0.08f)
    Box(
        Modifier
            .width(width)
            .height(TabBarHeight)
            .graphicsLayer {
                val fill = neck?.fill
                clip = true
                shape = GenericShape { _, _ -> if (fill != null) addPath(fill) }
            }
            .liquidGlass(RoundedCornerShape(0), edges = false)
            .drawWithContent {
                drawContent()
                val n = neck ?: return@drawWithContent
                drawPath(n.fill, lift)
                // Continue the rim of the two shapes along the waist.
                drawPath(n.top, rimTop, style = Stroke(1.dp.toPx()))
                drawPath(n.bottom, rimBottom, style = Stroke(1.dp.toPx()))
            },
    )
}

/** Selected tabs use the accent; others the label colour, full strength for legibility on glass. */
@Composable
private fun tabTint(selected: Boolean): Color {
    val tint by animateColorAsState(
        targetValue = if (selected) AppTheme.colors.accent else AppTheme.colors.label,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "tabSelectionTint",
    )
    return tint
}

@Composable
private fun GlassCircleButton(icon: ImageVector, label: String, selected: Boolean, size: Dp, link: LiquidLink? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .liquidPress(grow = 0.05f, reach = 3.dp, link = link, side = LinkSide.End)
            .liquidGlass(Capsule, interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = null, size = 26.dp, tint = tabTint(selected))
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
            .height(MinimizedHeight)
            // Where the Now Playing transition starts from and returns to.
            .miniPlayerFrame()
            .liquidPress(grow = 0.015f, reach = 2.dp)
            .liquidGlass(Capsule, interaction)
            .trackSwipe(swipe, onNext, onPrevious)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .trackSwipeActions(labels, onNext, onPrevious)
            .padding(start = MiniArtInset, end = 4.dp),
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
