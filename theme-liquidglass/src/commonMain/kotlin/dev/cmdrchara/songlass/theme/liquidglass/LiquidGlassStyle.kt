package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.NavigationDirection
import dev.cmdrchara.songlass.designsystem.MiniPlayerState
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.UiStyle

/** Apple-inspired style: iOS 26 Liquid Glass navigation layer over standard content. */
object LiquidGlassStyle : UiStyle {

    @Composable
    override fun <T> ScreenTransition(
        targetState: T,
        direction: (T, T) -> NavigationDirection,
        modifier: Modifier,
        content: @Composable (T) -> Unit,
    ) = LiquidGlassScreenTransition(targetState, direction, modifier, content)


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
    override fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier) =
        LiquidGlassSettingsGroup(content, modifier)

    @Composable
    override fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) =
        LiquidGlassSettingsChoiceRow(title, selected, onClick, modifier)

    @Composable
    override fun SettingsToggleRow(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean,
        modifier: Modifier,
    ) = LiquidGlassSettingsToggleRow(title, subtitle, checked, onCheckedChange, enabled, modifier)

    @Composable
    override fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        LiquidGlassSegmentedControl(options, selectedIndex, onSelect, modifier)

    @Composable
    override fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier) =
        LiquidGlassActionButton(text, icon, onClick, modifier)

    @Composable
    override fun IconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, selected: Boolean, filled: Boolean, modifier: Modifier) =
        LiquidGlassIconButton(icon, contentDescription, onClick, selected, modifier, filled = filled)

    @Composable
    override fun AlbumArt(artwork: Artwork, cornerRadius: Dp, modifier: Modifier) =
        LiquidGlassAlbumArt(artwork, cornerRadius, modifier)

    @Composable
    override fun SeekBar(positionMs: Long, durationMs: Long, qualityLabel: String?, onSeek: (Long) -> Unit, modifier: Modifier) =
        LiquidGlassSeekBar(positionMs, durationMs, qualityLabel, onSeek, modifier)

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
    override fun LyricLine(text: String, active: Boolean, subdued: Boolean, progress: Float?, isPlaying: Boolean, seekLabel: String, onSeek: (() -> Unit)?, modifier: Modifier) =
        LiquidGlassLyricLine(text, active, subdued, progress, isPlaying, seekLabel, onSeek, modifier)

    @Composable
    override fun LyricsViewport(modifier: Modifier, content: @Composable () -> Unit) =
        LiquidGlassLyricsViewport(modifier, content)

    @Composable
    override fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier) =
        LiquidGlassSearchField(query, onQueryChange, placeholder, modifier)

    @Composable
    override fun NowPlayingSheet(
        visible: Boolean,
        artwork: Artwork?,
        artworkVisible: Boolean,
        artworkExpanded: Boolean,
        artworkLabel: String,
        expandArtworkLabel: String,
        closeArtworkLabel: String,
        onArtworkExpandedChange: (Boolean) -> Unit,
        dismissLabel: String,
        onDismiss: () -> Unit,
        content: @Composable (onArtworkTap: () -> Unit) -> Unit,
    ) = LiquidGlassNowPlayingSheet(visible, artwork, artworkVisible, artworkExpanded, artworkLabel, expandArtworkLabel, closeArtworkLabel, onArtworkExpandedChange, dismissLabel, onDismiss, content)

    @Composable
    override fun InfoSheet(visible: Boolean, title: String, rows: List<InfoRow>, closeLabel: String, onDismiss: () -> Unit) =
        LiquidGlassActionSheet(visible, title, null, null, emptyList(), closeLabel, onDismiss, rows)

    @Composable
    override fun ActionSheet(
        visible: Boolean,
        title: String,
        subtitle: String?,
        artwork: Artwork?,
        actions: List<SheetAction>,
        cancelLabel: String,
        onDismiss: () -> Unit,
    ) = LiquidGlassActionSheet(visible, title, subtitle, artwork, actions, cancelLabel, onDismiss)
}
