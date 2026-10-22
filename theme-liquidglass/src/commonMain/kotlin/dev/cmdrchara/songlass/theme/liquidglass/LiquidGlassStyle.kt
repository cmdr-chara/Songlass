package dev.cmdrchara.songlass.theme.liquidglass

import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.EqBand
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import dev.cmdrchara.songlass.designsystem.HomeHighlight
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
    override fun HomePage(title: String, trailing: @Composable () -> Unit, contentPadding: PaddingValues, modifier: Modifier, content: LazyListScope.() -> Unit) =
        LocalGlassLook.current.HomePage(title, trailing, contentPadding, modifier, content)

    @Composable
    override fun HomeHighlights(items: List<HomeHighlight>, itemWidth: Dp, modifier: Modifier) = LocalGlassLook.current.HomeHighlights(items, itemWidth, modifier)

    @Composable
    override fun BackPageTitle(text: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.BackPageTitle(text, backLabel, onBack, modifier)

    @Composable
    override fun PageTitle(text: String, trailing: (@Composable () -> Unit)?, modifier: Modifier) = LocalGlassLook.current.PageTitle(text, trailing, modifier)


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
        onPrevious: () -> Unit,
        modifier: Modifier,
    ) = LocalGlassLook.current.MiniPlayer(state, labels, onClick, onPlayPause, onNext, onPrevious, modifier)

    @Composable
    override fun ListRow(
        title: String,
        subtitle: String?,
        leading: (@Composable () -> Unit)?,
        trailing: (@Composable () -> Unit)?,
        highlighted: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    ) = LocalGlassLook.current.ListRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)

    @Composable
    override fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.SettingsGroup(content, modifier)

    @Composable
    override fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.SettingsChoiceRow(title, selected, onClick, modifier)

    @Composable
    override fun SettingsToggleRow(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean,
        modifier: Modifier,
    ) = LocalGlassLook.current.SettingsToggleRow(title, subtitle, checked, onCheckedChange, enabled, modifier)

    @Composable
    override fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        LocalGlassLook.current.SegmentedControl(options, selectedIndex, onSelect, modifier)

    @Composable
    override fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.ActionButton(text, icon, onClick, modifier)

    @Composable
    override fun IconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, selected: Boolean, filled: Boolean, modifier: Modifier) =
        LocalGlassLook.current.IconButton(icon, contentDescription, onClick, selected, filled, modifier)

    @Composable
    override fun AlbumArt(artwork: Artwork, cornerRadius: Dp, modifier: Modifier) =
        LiquidGlassAlbumArt(artwork, cornerRadius, modifier)

    @Composable
    override fun SeekBar(positionMs: Long, durationMs: Long, qualityLabel: String?, isPlaying: Boolean, onSeek: (Long) -> Unit, modifier: Modifier) =
        LocalGlassLook.current.SeekBar(positionMs, durationMs, qualityLabel, isPlaying, onSeek, modifier)

    @Composable
    override fun VolumeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier) =
        LocalGlassLook.current.VolumeSlider(value, onValueChange, label, modifier)

    @Composable
    override fun TransportControls(
        isPlaying: Boolean,
        labels: PlaybackLabels,
        onPrevious: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        modifier: Modifier,
    ) = LocalGlassLook.current.TransportControls(isPlaying, labels, onPrevious, onPlayPause, onNext, modifier)

    @Composable
    override fun LyricLine(text: String, active: Boolean, subdued: Boolean, progress: Float?, isPlaying: Boolean, seekLabel: String, onSeek: (() -> Unit)?, modifier: Modifier, words: List<dev.cmdrchara.songlass.designsystem.LyricWordTiming>, positionMs: Long) =
        LiquidGlassLyricLine(text, active, subdued, progress, isPlaying, seekLabel, onSeek, modifier, words, positionMs)

    @Composable
    override fun LyricsViewport(modifier: Modifier, content: @Composable () -> Unit) =
        LiquidGlassLyricsViewport(modifier, content)

    @Composable
    override fun FormField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, modifier: Modifier) =
        LocalGlassLook.current.FormField(value, onValueChange, label, enabled, modifier)

    @Composable
    override fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, clearLabel: String, modifier: Modifier) =
        LocalGlassLook.current.SearchField(query, onQueryChange, placeholder, clearLabel, modifier)

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
        onStatusBarAppearanceChange: (Boolean?) -> Unit,
        content: @Composable (onArtworkTap: () -> Unit) -> Unit,
    ) = LiquidGlassNowPlayingSheet(visible, artwork, artworkVisible, artworkExpanded, artworkLabel, expandArtworkLabel, closeArtworkLabel, onArtworkExpandedChange, dismissLabel, onDismiss, onStatusBarAppearanceChange, content)

    @Composable
    override fun InfoSheet(visible: Boolean, title: String, rows: List<InfoRow>, closeLabel: String, onDismiss: () -> Unit) =
        LocalGlassLook.current.InfoSheet(visible, title, rows, closeLabel, onDismiss)

    @Composable
    override fun PlaybackToolsSheet(visible: Boolean, title: String, subtitle: String?, groups: List<dev.cmdrchara.songlass.designsystem.PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, onDismiss: () -> Unit) =
        LocalGlassLook.current.PlaybackToolsSheet(visible, title, subtitle, groups, closeLabel, backLabel, onBack, onDismiss)

    @Composable
    override fun Welcome(model: dev.cmdrchara.songlass.designsystem.WelcomeModel) = LocalGlassLook.current.Welcome(model)

    @Composable
    override fun PlayerUtilityBar(actions: List<dev.cmdrchara.songlass.designsystem.PlayerUtilityAction>, modifier: Modifier) =
        LocalGlassLook.current.PlayerUtilityBar(actions, modifier)

    @Composable
    override fun ActionSheet(
        visible: Boolean,
        title: String,
        subtitle: String?,
        artwork: Artwork?,
        actions: List<SheetAction>,
        cancelLabel: String,
        onDismiss: () -> Unit,
    ) = LocalGlassLook.current.ActionSheet(visible, title, subtitle, artwork, actions, cancelLabel, onDismiss)

    @Composable
    override fun ArtworkTintedPage(artwork: Artwork, modifier: Modifier, content: @Composable () -> Unit) =
        LocalGlassLook.current.ArtworkTintedPage(artwork, modifier, content)

    @Composable
    override fun ArtworkHeader(artwork: Artwork, contentDescription: String?, modifier: Modifier) =
        LocalGlassLook.current.ArtworkHeader(artwork, contentDescription, modifier)

    @Composable
    override fun Tag(text: String, modifier: Modifier) = LocalGlassLook.current.Tag(text, modifier)

    @Composable
    override fun ProminentButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.ProminentButton(icon, contentDescription, onClick, modifier)

    @Composable
    override fun FloatingBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.FloatingBackButton(contentDescription, onClick, modifier)

    @Composable
    override fun PosterCard(label: String, title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.PosterCard(label, title, subtitle, artwork, onClick, modifier)

    @Composable
    override fun PromptSheet(
        visible: Boolean,
        title: String,
        initialText: String,
        placeholder: String,
        confirmLabel: String,
        cancelLabel: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit,
    ) = LocalGlassLook.current.PromptSheet(visible, title, initialText, placeholder, confirmLabel, cancelLabel, onConfirm, onDismiss)

    @Composable
    override fun CollectionDetails(
        kind: String,
        title: String,
        subtitle: String?,
        onSubtitleClick: (() -> Unit)?,
        meta: String,
        playLabel: String,
        shuffleLabel: String,
        onPlay: () -> Unit,
        onShuffle: () -> Unit,
        action: @Composable () -> Unit,
        modifier: Modifier,
    ) = LocalGlassLook.current.CollectionDetails(kind, title, subtitle, onSubtitleClick, meta, playLabel, shuffleLabel, onPlay, onShuffle, action, modifier)

    @Composable
    override fun FilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        LocalGlassLook.current.FilterChips(options, selectedIndex, onSelect, modifier)

    @Composable
    override fun ArtistAvatar(artwork: Artwork, modifier: Modifier) = LocalGlassLook.current.ArtistAvatar(artwork, modifier)

    @Composable
    override fun ArtworkShelf(items: List<ShelfItem>, style: ShelfStyle, itemSize: Dp, modifier: Modifier) =
        LocalGlassLook.current.ArtworkShelf(items, style, itemSize, modifier)

    @Composable
    override fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.BackButton(label, onClick, modifier)

    @Composable
    override fun FolderChoiceRow(choice: dev.cmdrchara.songlass.designsystem.FolderChoice, onExpand: () -> Unit, onExcludedChange: (Boolean) -> Unit, modifier: Modifier) =
        LocalGlassLook.current.FolderChoiceRow(choice, onExpand, onExcludedChange, modifier)

    @Composable
    override fun ListeningChart(points: List<dev.cmdrchara.songlass.designsystem.ListeningChartPoint>, summary: String, unrecordedLabel: String, modifier: Modifier) =
        LocalGlassLook.current.ListeningChart(points, summary, unrecordedLabel, modifier)

    @Composable
    override fun StatSummary(figures: List<dev.cmdrchara.songlass.designsystem.StatFigure>, modifier: Modifier) =
        LocalGlassLook.current.StatSummary(figures, modifier)

    @Composable
    override fun RankedRow(rank: Int, title: String, subtitle: String?, leading: (@Composable () -> Unit)?, value: String, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier) =
        LocalGlassLook.current.RankedRow(rank, title, subtitle, leading, value, highlighted, onClick, modifier)

    @Composable
    override fun EqualizerEditor(
        bands: List<EqBand>,
        curve: List<Float>,
        maxGainDb: Float,
        enabled: Boolean,
        formatGain: (Float) -> String,
        onBandChange: (Int, Float) -> Unit,
        modifier: Modifier,
    ) = LiquidGlassEqualizerEditor(bands, curve, maxGainDb, enabled, formatGain, onBandChange, modifier)

    @Composable
    override fun CollectionTile(title: String, subtitle: String, icon: ImageVector, artworks: List<Artwork>, circular: Boolean, onClick: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.CollectionTile(title, subtitle, icon, artworks, circular, onClick, modifier)

    @Composable
    override fun ShortcutTile(title: String, artwork: Artwork, circular: Boolean, onClick: () -> Unit, modifier: Modifier) =
        LocalGlassLook.current.ShortcutTile(title, artwork, circular, onClick, modifier)
}
