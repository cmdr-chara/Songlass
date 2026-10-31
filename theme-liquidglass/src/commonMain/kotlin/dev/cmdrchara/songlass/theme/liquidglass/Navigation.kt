package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import dev.cmdrchara.songlass.designsystem.LocalWindowLayout
import dev.cmdrchara.songlass.designsystem.WindowBarEdge
import dev.cmdrchara.songlass.designsystem.WindowLayout
import dev.cmdrchara.songlass.designsystem.WindowRect
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.ArtworkTint
import dev.cmdrchara.songlass.designsystem.theme.LocalNavigationTint
import dev.cmdrchara.songlass.designsystem.theme.LocalSearchInNavigationBar
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.designsystem.theme.LocalBlurStrength

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
    // Moving between bottom chrome and a system-requested side rail must not dispose forms,
    // lazy-list state, or mini-player state held by the content's existing composition.
    val latestContent by rememberUpdatedState(content)
    val retainedContent = remember { movableContentOf<PaddingValues> { padding -> latestContent(padding) } }
    val latestAccessory by rememberUpdatedState(accessory)
    val retainedAccessory = remember { movableContentOf { latestAccessory?.invoke() } }
    val visibleAccessory: (@Composable () -> Unit)? = if (accessory == null) null else retainedAccessory
    val morph = LocalPlayerMorph.current
    val accessibility = AppTheme.accessibility
    val look = LocalGlassLook.current
    // No source is recorded at all when the blur is off: every glass surface then uses its opaque,
    // readable fallback, and the scaffold does not pay for a backdrop nobody samples.
    val hazeState = if (accessibility.reduceTransparency || !LocalBlurStrength.current.blurred || !look.requiresHazeBackdrop()) {
        null
    } else rememberHazeState()
    val density = LocalDensity.current
    val windowLayout = LocalWindowLayout.current
    val hasReservedContent = windowLayout.specified && windowLayout.regions.any {
        it.bounds.intersection(windowLayout.safeBounds) != null
    }
    if (windowLayout.specified && (windowLayout.barEdge != WindowBarEdge.Bottom || hasReservedContent)) {
        ReservedWindowScaffold(windowLayout, tabs, selectedTab, onTabSelected, visibleAccessory, hazeState, retainedContent)
        return
    }
    val navigationBarInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var expandedBarsHeightPx by remember { mutableIntStateOf(0) }
    var navigationHeightPx by remember { mutableIntStateOf(0) }
    val footerColor = look.navigationContentFadeColor()
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
                .background(colors.background)
                .drawWithCache {
                    val fadeHeight = (navigationHeightPx + 8.dp.toPx()).coerceAtMost(size.height)
                    val top = size.height - fadeHeight
                    val fade = Brush.verticalGradient(
                        0f to footerColor.copy(alpha = 0f),
                        0.35f to footerColor.copy(alpha = 0.90f),
                        1f to footerColor,
                        startY = top, endY = size.height,
                    )
                    onDrawWithContent {
                        drawContent()
                        if (navigationHeightPx > 0) drawRect(fade, Offset(0f, top), Size(size.width, fadeHeight))
                    }
                },
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
                retainedContent(PaddingValues(bottom = with(density) { expandedBarsHeightPx.toDp() } + navigationBarInset + BarSpacing * 2))
            }
        }
        val navigationTint = if (look.tintsNavigation) LocalNavigationTint.current else null
        // Fully covered chrome must not request frames behind the expanded player.
        val metalVisible by remember(morph) { derivedStateOf { morph.recede < 0.999f } }
        CompositionLocalProvider(LocalGlassSource provides hazeState, LocalMetalMotionVisible provides metalVisible) {
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
                    .onSizeChanged { navigationHeightPx = it.height }
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
                    accessory = visibleAccessory,
                    minimized = minimized,
                    onExpandedHeightChanged = { expandedBarsHeightPx = it },
                )
            }
            }
        }
    }
    }
}

/**
 * The same three looks, placed inside the current window's unobstructed pane. Standard phones
 * never take this branch. A system-requested vertical bar stays on its semantic leading/trailing
 * edge; a partial fold never runs through a list row, mini-player, or navigation target.
 */
@Composable
private fun ReservedWindowScaffold(
    window: WindowLayout,
    tabs: List<TabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    accessory: (@Composable () -> Unit)?,
    hazeState: dev.chrisbanes.haze.HazeState?,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val look = LocalGlassLook.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val morph = LocalPlayerMorph.current
    val metalVisible by remember(morph) { derivedStateOf { morph.recede < 0.999f } }
    var barsHeight by remember { mutableIntStateOf(0) }
    val side = window.barEdge != WindowBarEdge.Bottom
    BoxWithConstraints(Modifier.fillMaxSize().background(AppTheme.colors.background), contentAlignment = AbsoluteAlignment.TopLeft) {
        val viewport = WindowRect(0f, 0f, maxWidth.value, maxHeight.value)
        val chrome = window.chrome(viewport, rtl = rtl) ?: return@BoxWithConstraints
        val pane = chrome.pane
        val rail = chrome.rail
        val mainLeft = chrome.content.left.dp
        val mainWidth = chrome.content.width.dp
        val safe = window.safeBounds
        val leftInset = safe.left.dp
        val rightInset = (window.width - safe.right).dp
        val consumed = PaddingValues(start = if (rtl) rightInset else leftInset,
            top = safe.top.dp, end = if (rtl) leftInset else rightInset,
            bottom = (window.height - safe.bottom).dp)
        Box(Modifier.fillMaxSize()
            .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
            .background(AppTheme.colors.background), contentAlignment = AbsoluteAlignment.TopLeft) {
            Box(Modifier.absoluteOffset(mainLeft, pane.top.dp).width(mainWidth).height(pane.height.dp)
                .consumeWindowInsets(consumed)) {
                content(PaddingValues(bottom = with(density) { barsHeight.toDp() } + BarSpacing * 2))
            }
        }
        CompositionLocalProvider(LocalGlassSource provides hazeState, LocalMetalMotionVisible provides metalVisible) {
            NavigationTintScope(if (look.tintsNavigation) LocalNavigationTint.current else null) {
                if (side && rail != null) {
                    Column(Modifier.absoluteOffset(rail.left.dp, rail.top.dp)
                        .width(rail.width.dp).height(rail.height.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                        .then(navigationContainerSurface(Capsule))
                        .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val selected = selectedTab == index
                            Column(Modifier.fillMaxWidth()
                                .then(if (selected) fallbackNavigationSelectionSurface() else Modifier)
                                .clip(RoundedCornerShape(24.dp))
                                .selectable(selected, role = Role.Tab, onClick = { onTabSelected(index) })
                                .padding(horizontal = 2.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                AppIcon(tab.icon, null, size = 26.dp, tint = tabTint(selected))
                                AppText(tab.label, AppTheme.typography.footnote, color = tabTint(selected), maxLines = 2)
                            }
                        }
                    }
                    if (accessory != null) Box(Modifier.absoluteOffset(mainLeft + 8.dp, pane.top.dp)
                        .width((mainWidth - 16.dp).coerceAtLeast(1.dp)).height(pane.height.dp)
                        .consumeWindowInsets(consumed), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.padding(bottom = 8.dp).onSizeChanged { barsHeight = it.height }) { accessory() }
                    }
                } else {
                    Box(Modifier.absoluteOffset(pane.left.dp + BarHorizontalMargin, pane.top.dp)
                        .width((pane.width.dp - BarHorizontalMargin * 2).coerceAtLeast(1.dp)).height(pane.height.dp)
                        .consumeWindowInsets(consumed), contentAlignment = Alignment.BottomCenter) {
                        Column(Modifier.padding(bottom = BarSpacing).onSizeChanged { barsHeight = it.height },
                            verticalArrangement = Arrangement.spacedBy(BarSpacing)) {
                            accessory?.invoke()
                            look.NavigationBar(tabs, selectedTab, onTabSelected, Modifier)
                        }
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Keep a useful title column after the cover, spacing and both 44 dp controls.
        val minimumAccessoryWidth = MinimizedHeight + 10.dp + 44.dp * 2 + 80.dp * LocalDensity.current.fontScale
        val circleCount = if (searchIndex >= 0 && searchIndex != selectedTab) 2 else 1
        val accessoryWidth = maxWidth - (MinimizedHeight + BarSpacing) * circleCount
        AnimatedContent(
            targetState = minimized && accessoryWidth >= minimumAccessoryWidth,
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
}

/** iOS 26 tab bar: the tab pill and, beside it, the search circle, merging when pressed together. */
@Composable
internal fun LiquidNavigationBar(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) {
    val searchInBar = LocalSearchInNavigationBar.current
    val searchIndex = tabs.indexOfFirst { it.isSearch }
    val separateSearch = searchIndex >= 0 && !searchInBar
    val barHeight = LocalGlassLook.current.navigationBarHeight()
    val link = remember { LiquidLink() }
    // One preview owner for the pill and separate Search, on every rendering backend.
    val tabTouching = remember { mutableStateOf(false) }
    // Moving Search changes the silhouette, never the optical material or selection lens.
    val sharedSurface = unifiedNavigationSupported && !AppTheme.accessibility.reduceTransparency &&
        LocalGlassSource.current != null && LocalBlurStrength.current.blurred
    val preferredWidth = with(LocalDensity.current) { maxOf(232.dp, 166.dp + 66.sp.toDp()) }
    Box(
        modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = preferredWidth).fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (sharedSurface) {
            Box(Modifier.matchParentSize().unifiedNavigationGlass(link, separateSearch))
        } else if (separateSearch) LiquidBridge(link)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(BarSpacing), verticalAlignment = Alignment.CenterVertically) {
            TabPill(tabs, selectedTab, onTabSelected, Modifier.weight(1f), link, sharedSurface,
                includeSearch = searchInBar, touchingState = tabTouching)
            if (separateSearch) {
                val search = tabs[searchIndex]
                GlassCircleButton(search.icon, search.label, selected = selectedTab == searchIndex,
                    size = barHeight, link = link, sharedSurface = sharedSurface,
                    highlighted = selectedTab == searchIndex && !tabTouching.value) {
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
private fun TabPill(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier, link: LiquidLink? = null, sharedSurface: Boolean = false, includeSearch: Boolean = false, touchingState: MutableState<Boolean>) {
    val slots = remember(tabs, includeSearch) { if (includeSearch) tabs.indices.toList() else tabs.indices.filterNot { tabs[it].isSearch } }
    val barHeight = LocalGlassLook.current.navigationBarHeight()
    val selectedSlot = slots.indexOf(selectedTab)
    val reduceMotion = AppTheme.accessibility.reduceMotion
    // Both finishes use the same Glass refraction of the foreground.
    val refractedInk = sharedSurface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var slotWidth by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var touching by touchingState
    DisposableEffect(touchingState) {
        onDispose { touchingState.value = false }
    }
    val gestureVersion = remember { intArrayOf(0) }
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
        if (slotWidth <= 0f || selectedSlot < 0 || touching || dragging) return@LaunchedEffect
        val target = selectedSlot * slotWidth
        if (!placed || reduceMotion) {
            bubbleX.snapTo(target)
            travelLift.snapTo(0f)
        } else if (abs(target - bubbleX.value) > 1f) {
            launch {
                travelLift.animateTo(0.3f, tween(80))
                travelLift.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 500f))
            }
            bubbleX.animateTo(target, spring(dampingRatio = 0.76f, stiffness = 480f))
        }
        placed = true
    }
    // Search owns the separate circle, so hide this lens while that destination is selected.
    val bubbleAlphaState = animateFloatAsState(
        targetValue = if (selectedSlot >= 0 || touching || dragging) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "tabBubbleAlpha",
    )
    val bubbleAlpha by bubbleAlphaState
    val bubbleShown by remember { derivedStateOf { bubbleAlphaState.value > 0f } }
    // Ink changes at slot boundaries; the lens still follows every spring frame in
    // layout/drawing without recomposing the full glass capsule while dragged.
    val shownSlot by remember(slots, selectedSlot) {
        derivedStateOf { if (dragging || touching) slotAt(bubbleX.value) else selectedSlot }
    }
    val inset = 4.dp
    val opticalLens = remember(link, density, reduceMotion, rtl, barHeight) {
        LiquidLens(bounds = {
            with(density) {
                val l = if (reduceMotion) 0f else maxOf(lift.value, travelLift.value).coerceIn(0f, 1f)
                val bodyWidth = link?.bodies?.get(LinkSide.Start)?.widthPx
                    ?: (slotWidth * slots.size + inset.toPx() * 2f)
                val logicalCenter = inset.toPx() + bubbleX.value + slotWidth / 2f
                val cx = if (rtl) bodyWidth - logicalCenter else logicalCenter
                val cy = barHeight.toPx() / 2f - 2.dp.toPx() * l
                val speed = if (reduceMotion || slotWidth <= 0f) 0f else (bubbleX.velocity / (slotWidth * 18f)).coerceIn(-0.09f, 0.09f)
                val halfWidth = slotWidth * (1f + 0.35f * l + abs(speed)) / 2f
                val halfHeight = (barHeight - inset * 2).toPx() * (1f + 0.35f * l - abs(speed) * 0.4f) / 2f
                Rect(cx-halfWidth, cy-halfHeight, cx+halfWidth, cy+halfHeight)
            }
        }, opacity = { bubbleAlphaState.value }, pressure = { if (reduceMotion) 0f else maxOf(lift.value, travelLift.value) })
    }
    DisposableEffect(link, sharedSurface, opticalLens) {
        if (sharedSurface && link != null) link.lenses[LinkSide.Start] = opticalLens
        onDispose {
            if (link?.lenses?.get(LinkSide.Start) === opticalLens) link.lenses.remove(LinkSide.Start)
        }
    }

    val sceneBounds: () -> Rect? = {
        if (slotWidth <= 0f) null else opticalLens.bounds().translate(Offset(-with(density) { inset.toPx() }, -with(density) { inset.toPx() }))
    }
    val genericInkProtection = if (metalFinishSelected()) false else LocalMetalInkProtection.current
    CompositionLocalProvider(LocalMetalInkProtection provides genericInkProtection) {
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
                    val version = ++gestureVersion[0]
                    touching = true
                    var dragged = false
                    var released = false
                    var lastDragX = dragTargetAt(down.position.x)
                    val pursuit = SpringFollower()
                    var pursuitGoal = bubbleX.value
                    var pursuing: Job? = null
                    // Preview the touched destination immediately, without navigating until release.
                    val previewTarget = slotAt(lastDragX) * slotWidth
                    val preview = scope.launch {
                        val target = previewTarget
                        if (reduceMotion) bubbleX.snapTo(target)
                        else bubbleX.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 620f))
                    }
                    if (!reduceMotion) scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 740f)) }
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                if (dragged) {
                                    lastDragX = dragTargetAt(change.position.x)
                                    change.consume()
                                }
                                break
                            }
                            if (!dragged && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                                preview.cancel()
                                dragged = true
                                dragging = true
                            }
                            if (dragged) {
                                change.consume()
                                lastDragX = dragTargetAt(change.position.x)
                                pursuitGoal = lastDragX
                                if (reduceMotion) {
                                    scope.launch { bubbleX.snapTo(lastDragX) }
                                } else if (pursuing?.isActive != true) {
                                    pursuit.snapTo(bubbleX.value, bubbleX.velocity)
                                    pursuing = scope.launch {
                                        pursuit.follow({ pursuitGoal }, dampingRatio = 0.85f, stiffness = 2200f, threshold = 0.5f) { bubbleX.snapTo(it) }
                                    }
                                }
                            }
                        }
                    } finally {
                        preview.cancel()
                        val releaseVelocity = if (pursuing != null) pursuit.velocity else bubbleX.velocity
                        pursuing?.cancel()
                        touching = false
                        dragging = false
                        // A missing/cancelled pointer restores the committed tab, never a preview.
                        val destination = if (released) slotAt(lastDragX) else slots.indexOf(currentSelected).coerceAtLeast(0)
                        if (released && dragged && slots[destination] != currentSelected) currentOnSelected(slots[destination])
                        scope.launch {
                            try {
                                if (reduceMotion) bubbleX.snapTo(destination * slotWidth)
                                else bubbleX.animateTo(destination * slotWidth, spring(dampingRatio = 0.86f, stiffness = 620f), initialVelocity = releaseVelocity)
                            } finally {
                                // Position animation may be superseded by a newer selection.
                                // Shape relaxation must still complete, unless a new gesture owns it.
                                scope.launch {
                                    if (gestureVersion[0] == version) lift.animateTo(0f, if (reduceMotion) snap() else spring(dampingRatio = 0.86f, stiffness = 560f))
                                }
                            }
                        }
                    }
                }
            },
    ) {
        // Separate siblings: the bar never clips or filters the lens that lifts above it.
        if (!sharedSurface) Box(Modifier.matchParentSize().then(navigationContainerSurface(Capsule)))
        Box(Modifier.fillMaxSize().padding(inset).onSizeChanged {
            slotWidth = it.width / slots.size.coerceAtLeast(1).toFloat()
        }) {
            if (!sharedSurface) Box(
                Modifier
                    .offset { IntOffset(bubbleX.value.roundToInt(), 0) }
                    .width(with(density) { slotWidth.toDp() })
                    .fillMaxHeight()
                    .graphicsLayer {
                        val l = if (reduceMotion) 0f else maxOf(lift.value, travelLift.value).coerceIn(0f, 1f)
                        alpha = bubbleAlpha
                        // More volume while held, with the rim free to rise above the capsule.
                        scaleX = 1f + 0.04f * l
                        scaleY = 1f + 0.16f * l
                        translationY = -2.dp.toPx() * l
                    }
                    .then(if (bubbleShown) fallbackNavigationSelectionSurface() else Modifier),
            )
            if (refractedInk) {
                // Only the decorative icons are split across the lens boundary. Keep their
                // normal ink outside and the refracted accent ink inside, with matching layout.
                Row(Modifier.fillMaxSize().clearAndSetSemantics {}
                    .navigationInkCutout(sceneBounds, opticalLens.opacity)) {
                    slots.forEach { index ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)) {
                            TabLabel(tabs[index], opticalScene = true, drawLabel = false) { false }
                        }
                    }
                }
                Row(Modifier.fillMaxSize().clearAndSetSemantics {}
                    .navigationInkScene(sceneBounds, opticalLens.pressure, opticalLens.opacity)) {
                    slots.forEach { index ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)) {
                            TabLabel(tabs[index], opticalScene = true, drawLabel = false) { true }
                        }
                    }
                }
            }
            // The interactive row paints the label once, above the optical material. Its
            // ink remains whole even while the lens edge crosses the middle of a word.
            Row(Modifier.fillMaxSize()) {
                slots.forEachIndexed { slot, index ->
                    val tab = tabs[index]
                    val selected = index == selectedTab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                val magnification = if (!sharedSurface && slot == shownSlot && !reduceMotion) {
                                    1f + 0.035f * maxOf(lift.value, travelLift.value).coerceIn(0f, 1f)
                                } else 1f
                                scaleX = magnification
                                scaleY = magnification
                            }
                            .selectable(selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onTabSelected(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                    ) {
                        TabLabel(tab, opticalScene = sharedSurface, drawIcon = !refractedInk) { slot == shownSlot }
                    }
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dark = AppTheme.colors.isDark
    val barHeight = LocalGlassLook.current.navigationBarHeight()
    val width = barHeight / 2 + BarSpacing + barHeight + 8.dp
    val neck by remember(link, density, width, barHeight, rtl) {
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
                val capX = w - h - BarSpacing.toPx() - r + (start.widthPx / 2f - r) * (s1 - 1f)
                val s2 = end.scale()
                val o2 = end.offset(link, LinkSide.End)
                // Mirror the resting/scaled geometry; body offsets are already physical.
                metaballNeck(
                    c1 = Offset((if (rtl) w - capX else capX) + o1.x, r + o1.y),
                    r1 = r * s1,
                    c2 = Offset((if (rtl) r else w - r) + o2.x, r + o2.y),
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
    val viewport by remember(link, density, width, barHeight, rtl) {
        derivedStateOf {
            val bounds = neck?.fill?.getBounds()
            val fullWidth = with(density) { width.roundToPx() }
            val height = with(density) { barHeight.roundToPx() }
            val inset = with(density) { (barHeight / 4).roundToPx() }
            val end = fullWidth - height / 2
            val left = if (rtl) fullWidth - end else inset
            val right = if (rtl) fullWidth - inset else end
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
            .then(navigationContainerSurface(RoundedCornerShape(0), edges = false))
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
private fun TabLabel(
    tab: TabItem,
    opticalScene: Boolean = false,
    drawIcon: Boolean = true,
    drawLabel: Boolean = true,
    highlighted: () -> Boolean,
) {
    val selected = highlighted()
    val tint = if (opticalScene) {
        if (selected) AppTheme.colors.accent else AppTheme.colors.label
    } else tabTint(selected)
    @Composable fun ink() {
        if (drawIcon) AppIcon(tab.icon, contentDescription = null, size = 24.dp, tint = tint)
        else Spacer(Modifier.size(24.dp))
        AppText(
            text = tab.label,
            style = AppTheme.typography.caption2.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (opticalScene) FontWeight.Normal else if (selected) FontWeight.Medium else FontWeight.Normal),
            // Keep the real label's measured height, including scaled text, in icon-only layers.
            modifier = if (drawLabel) Modifier else Modifier.drawWithContent {},
            color = tint,
            maxLines = 1,
        )
    }
    ink()
}

@Composable
private fun metalFinishSelected(): Boolean =
    LocalGlassLook.current === LiquidLook && LocalLiquidFinish.current == LiquidFinish.Metal

/** Original Glass surface; Metal adds only its transparent rim. */
@Composable
private fun navigationContainerSurface(
    shape: RoundedCornerShape,
    interaction: InteractionSource? = null,
    edges: Boolean = true,
): Modifier = Modifier.navigationBarGlass(shape,interaction,edges)

/** Platforms without the unified shader use the same active-ring treatment. */
@Composable
private fun fallbackNavigationSelectionSurface(): Modifier {
    val glass = LocalGlassLook.current.navigationSelectionSurface(Modifier, Capsule)
    return if (metalFinishSelected()) Modifier.liquidMetalRim(Capsule).then(glass) else glass
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
private fun GlassCircleButton(icon: ImageVector, label: String, selected: Boolean, size: Dp, link: LiquidLink? = null, sharedSurface: Boolean = false, highlighted: Boolean = selected, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val selectionAlphaState = animateFloatAsState(
        if (highlighted) 1f else 0f,
        if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "circleSelection",
    )
    val selectionAlpha by selectionAlphaState
    // A fully transparent lens still costs a glass pass every frame the circle moves.
    val lensShown by remember { derivedStateOf { selectionAlphaState.value > 0f } }
    val density = LocalDensity.current
    val opticalLens = remember(size, density) {
        LiquidLens(bounds = {
            with(density) { Rect(4.dp.toPx(), 4.dp.toPx(), (size-4.dp).toPx(), (size-4.dp).toPx()) }
        }, opacity = { selectionAlphaState.value })
    }
    DisposableEffect(link, sharedSurface, opticalLens) {
        if (sharedSurface && link != null) link.lenses[LinkSide.End] = opticalLens
        onDispose {
            if (link?.lenses?.get(LinkSide.End) === opticalLens) link.lenses.remove(LinkSide.End)
        }
    }
    val genericInkProtection = if (metalFinishSelected()) false else LocalMetalInkProtection.current
    CompositionLocalProvider(LocalMetalInkProtection provides genericInkProtection) {
    Box(
        modifier = Modifier
            .size(size)
            .liquidPress(grow = 0.05f, reach = 3.dp, link = link, side = LinkSide.End)
            .then(if (sharedSurface) Modifier else navigationContainerSurface(Capsule, interaction))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        if (lensShown && !sharedSurface) {
            Box(
                Modifier.matchParentSize().padding(4.dp).graphicsLayer { alpha = selectionAlpha }
                    .then(fallbackNavigationSelectionSurface()),
            )
        }
        TabIcon(icon, highlighted)
    }
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
    // This accessory knows its actual foreground bounds; do not also matte its whole center.
    val genericInkProtection = if (metalFinishSelected()) false else LocalMetalInkProtection.current
    CompositionLocalProvider(LocalMetalInkProtection provides genericInkProtection) {
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
                @Composable fun metadata() {
                    AppText(state.title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.miniPlayerTitleMotion(), maxLines = 1)
                    AppText(state.subtitle, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
                }
                metadata()
                Spacer(Modifier.height(3.dp))
                MiniPlayerProgress(state.progress)
            }
        }
        LiquidGlassIconButton(
            icon = if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (state.isPlaying) labels.pause else labels.play,
            onClick = onPlayPause,
            selected = false,
            modifier = Modifier,
            hitSize = 48.dp,
        )
        LiquidGlassIconButton(AppIcons.Next, labels.next, onNext, selected = false, modifier = Modifier, hitSize = 48.dp)
    }
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
            .liquidFocusIndicator(interaction, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (filled) Modifier.padding(6.dp) else Modifier)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .let {
                    when {
                        filled -> LocalGlassLook.current.controlSurface(it, CircleShape, background, prominent = solid)
                        background.alpha > 0f -> it.clip(CircleShape).background(background)
                        else -> it
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                icon = icon,
                contentDescription = contentDescription,
                modifier = Modifier,
                size = if (filled) iconSize * 0.75f else iconSize,
                tint = tint,
            )
        }
    }
}
