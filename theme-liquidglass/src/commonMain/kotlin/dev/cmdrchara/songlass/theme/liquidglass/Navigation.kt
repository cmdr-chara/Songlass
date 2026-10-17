package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor

private val Capsule = RoundedCornerShape(percent = 50)
private val BarHorizontalMargin = 16.dp
private val BarSpacing = 8.dp
private val TabBarHeight = 62.dp
private val MinimizedHeight = 56.dp
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
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var expandedBarsHeightPx by remember { mutableIntStateOf(0) }
    var minimizeRequested by remember { mutableStateOf(false) }
    val scrollConnection = remember(density) {
        MinimizeOnScroll(thresholdPx = with(density) { MinimizeScrollThreshold.toPx() }) { minimizeRequested = it }
    }
    // Without an accessory there is nothing to show inline, so the bar stays expanded.
    val minimized = minimizeRequested && accessory != null
    val colors = AppTheme.colors

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // The background must be inside hazeSource, otherwise the glass samples transparent pixels.
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection)
                .hazeSource(hazeState)
                .background(colors.background),
        ) {
            content(PaddingValues(bottom = with(density) { expandedBarsHeightPx.toDp() } + BarSpacing))
        }
        CompositionLocalProvider(LocalGlassSource provides hazeState) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { if (!minimized) expandedBarsHeightPx = it.height }
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
                )
            }
        }
    }
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
) {
    val searchIndex = tabs.indexOfFirst { it.isSearch }
    AnimatedContent(
        targetState = minimized,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) using SizeTransform(clip = false) },
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
            Column(verticalArrangement = Arrangement.spacedBy(BarSpacing)) {
                accessory?.invoke()
                Row(horizontalArrangement = Arrangement.spacedBy(BarSpacing), verticalAlignment = Alignment.CenterVertically) {
                    TabPill(tabs, selectedTab, onTabSelected, Modifier.weight(1f))
                    if (searchIndex >= 0) {
                        val search = tabs[searchIndex]
                        GlassCircleButton(search.icon, search.label, selected = selectedTab == searchIndex, size = TabBarHeight) {
                            onTabSelected(searchIndex)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(TabBarHeight)
            .liquidGlass(Capsule)
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            if (tab.isSearch) return@forEachIndexed
            val selected = index == selectedTab
            val colors = AppTheme.colors
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(Capsule)
                    .background(if (selected) colors.fill else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabSelected(index) }
                    .semantics { role = Role.Tab; this.selected = selected },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = tabTint(selected)
                AppIcon(tab.icon, contentDescription = null, size = 26.dp, tint = tint)
                AppText(
                    text = tab.label,
                    style = AppTheme.typography.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    color = tint,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Selected tabs use the accent; others are a muted monochrome, as in the HIG. */
@Composable
private fun tabTint(selected: Boolean): Color =
    if (selected) AppTheme.colors.accent else AppTheme.colors.label.copy(alpha = 0.72f)

@Composable
private fun GlassCircleButton(icon: ImageVector, label: String, selected: Boolean, size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
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

/** Glass capsule shown above (or inline with) the tab bar while something is playing. */
@Composable
internal fun LiquidGlassMiniPlayer(
    state: MiniPlayerState,
    labels: PlaybackLabels,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MinimizedHeight)
            .liquidGlass(Capsule, interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidGlassAlbumArt(state.artwork, cornerRadius = 6.dp, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            AppText(state.title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            AppText(state.subtitle, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
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
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(hitSize)
            .graphicsLayer {
                val s = if (pressed) 0.88f else 1f
                scaleX = s; scaleY = s
            }
            .clip(CircleShape)
            .background(if (pressed) LocalContentColor.current.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            icon = icon,
            contentDescription = contentDescription,
            size = iconSize,
            tint = if (selected) AppTheme.colors.accent else LocalContentColor.current,
        )
    }
}
