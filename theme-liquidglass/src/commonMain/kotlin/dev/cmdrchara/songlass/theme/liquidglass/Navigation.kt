package dev.cmdrchara.songlass.theme.liquidglass

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Content fills the screen and is the glass source; the tab bar and accessory float
 * above it at the bottom, as in iOS 26.
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
    var barsHeightPx by remember { mutableIntStateOf(0) }
    val colors = AppTheme.colors

    Box(Modifier.fillMaxSize().background(colors.background)) {
        // The background must be inside hazeSource, otherwise the glass samples transparent pixels.
        Box(Modifier.fillMaxSize().hazeSource(hazeState).background(colors.background)) {
            content(PaddingValues(bottom = with(density) { barsHeightPx.toDp() } + BarSpacing))
        }
        CompositionLocalProvider(LocalGlassSource provides hazeState) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { barsHeightPx = it.height }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = BarHorizontalMargin)
                    .padding(bottom = BarSpacing),
                verticalArrangement = Arrangement.spacedBy(BarSpacing),
            ) {
                accessory?.invoke()
                TabBar(tabs, selectedTab, onTabSelected)
            }
        }
    }
}

@Composable
private fun TabBar(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .liquidGlass(Capsule)
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTab
            val colors = AppTheme.colors
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(Capsule)
                    .background(if (selected) colors.fill else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabSelected(index) }
                    .semantics { role = Role.Tab; this.selected = selected },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (selected) colors.accent else colors.label
                AppIcon(tab.icon, contentDescription = null, size = 24.dp, tint = tint)
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

/** Glass capsule shown above the tab bar while something is playing. */
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
            .height(56.dp)
            .liquidGlass(Capsule, interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidGlassAlbumArt(state.artwork, cornerRadius = 20.dp, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    hitSize: androidx.compose.ui.unit.Dp = 44.dp,
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
            .background(if (pressed) LocalContentColor.current.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
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
