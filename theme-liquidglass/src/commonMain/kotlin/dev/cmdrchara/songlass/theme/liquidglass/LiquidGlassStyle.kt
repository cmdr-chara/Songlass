package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.MiniPlayerState
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.UiStyle

/** Apple-inspired style: iOS 26 Liquid Glass navigation layer over standard content. */
object LiquidGlassStyle : UiStyle {

    @Composable
    override fun Scaffold(
        tabs: List<TabItem>,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        accessory: (@Composable () -> Unit)?,
        content: @Composable (PaddingValues) -> Unit,
    ) = LiquidGlassScaffold(tabs, selectedTab, onTabSelected, accessory, content)

    @Composable
    override fun MiniPlayer(
        state: MiniPlayerState,
        labels: PlaybackLabels,
        onClick: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        modifier: Modifier,
    ) = LiquidGlassMiniPlayer(state, labels, onClick, onPlayPause, onNext, modifier)

    @Composable
    override fun ListRow(
        title: String,
        subtitle: String?,
        leading: (@Composable () -> Unit)?,
        trailing: (@Composable () -> Unit)?,
        highlighted: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    ) = LiquidGlassListRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)

    @Composable
    override fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        LiquidGlassSegmentedControl(options, selectedIndex, onSelect, modifier)

    @Composable
    override fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier) =
        LiquidGlassActionButton(text, icon, onClick, modifier)

    @Composable
    override fun IconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, selected: Boolean, modifier: Modifier) =
        LiquidGlassIconButton(icon, contentDescription, onClick, selected, modifier)

    @Composable
    override fun AlbumArt(artwork: Artwork, cornerRadius: Dp, modifier: Modifier) =
        LiquidGlassAlbumArt(artwork, cornerRadius, modifier)

    @Composable
    override fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier) =
        LiquidGlassSeekBar(positionMs, durationMs, onSeek, modifier)

    @Composable
    override fun TransportControls(
        isPlaying: Boolean,
        labels: PlaybackLabels,
        onPrevious: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        modifier: Modifier,
    ) = LiquidGlassTransportControls(isPlaying, labels, onPrevious, onPlayPause, onNext, modifier)

    @Composable
    override fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier) =
        LiquidGlassSearchField(query, onQueryChange, placeholder, modifier)

    @Composable
    override fun NowPlayingSheet(
        visible: Boolean,
        artwork: Artwork?,
        dismissLabel: String,
        onDismiss: () -> Unit,
        content: @Composable () -> Unit,
    ) = LiquidGlassNowPlayingSheet(visible, artwork, dismissLabel, onDismiss, content)
}
