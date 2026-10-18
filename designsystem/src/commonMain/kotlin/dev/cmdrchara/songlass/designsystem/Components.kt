package dev.cmdrchara.songlass.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Entry points used by screens. Each one delegates to the active UiStyle.

@Composable
fun <T> ScreenTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    direction: (T, T) -> NavigationDirection = { _, _ -> NavigationDirection.Crossfade },
    content: @Composable (T) -> Unit,
) = LocalUiStyle.current.ScreenTransition(targetState, direction, modifier, content)


@Composable
fun AppScaffold(
    tabs: List<TabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    accessory: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) = LocalUiStyle.current.Scaffold(tabs, selectedTab, onTabSelected, accessory, content)

@Composable
fun MiniPlayer(
    state: MiniPlayerState,
    labels: PlaybackLabels,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.MiniPlayer(state, labels, onClick, onPlayPause, onNext, onPrevious, modifier)

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) = LocalUiStyle.current.ListRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)

@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    LocalUiStyle.current.SettingsGroup(content, modifier)

@Composable
fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.SettingsChoiceRow(title, selected, onClick, modifier)

@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) = LocalUiStyle.current.SettingsToggleRow(title, subtitle, checked, onCheckedChange, enabled, modifier)

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.SegmentedControl(options, selectedIndex, onSelect, modifier)

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) = LocalUiStyle.current.ActionButton(text, icon, onClick, modifier)

@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    filled: Boolean = false,
) = LocalUiStyle.current.IconButton(icon, contentDescription, onClick, selected, filled, modifier)

@Composable
fun AlbumArt(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) = LocalUiStyle.current.AlbumArt(artwork, cornerRadius, modifier)

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    qualityLabel: String? = null,
    /** Lets styles animate the bar only while music plays. */
    isPlaying: Boolean = false,
) = LocalUiStyle.current.SeekBar(positionMs, durationMs, qualityLabel, isPlaying, onSeek, modifier)

@Composable
fun TransportControls(
    isPlaying: Boolean,
    labels: PlaybackLabels,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.TransportControls(isPlaying, labels, onPrevious, onPlayPause, onNext, modifier)

@Composable
fun LyricLine(
    text: String,
    active: Boolean,
    subdued: Boolean,
    progress: Float?,
    isPlaying: Boolean,
    seekLabel: String,
    onSeek: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.LyricLine(text, active, subdued, progress, isPlaying, seekLabel, onSeek, modifier)

@Composable
fun LyricsViewport(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    LocalUiStyle.current.LyricsViewport(modifier, content)

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.SearchField(query, onQueryChange, placeholder, clearLabel, modifier)

@Composable
fun NowPlayingSheet(
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
) = LocalUiStyle.current.NowPlayingSheet(visible, artwork, artworkVisible, artworkExpanded, artworkLabel, expandArtworkLabel, closeArtworkLabel, onArtworkExpandedChange, dismissLabel, onDismiss, content)

@Composable
fun ActionSheet(
    visible: Boolean,
    title: String,
    actions: List<SheetAction>,
    cancelLabel: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    artwork: Artwork? = null,
) = LocalUiStyle.current.ActionSheet(visible, title, subtitle, artwork, actions, cancelLabel, onDismiss)

@Composable
fun InfoSheet(visible: Boolean, title: String, rows: List<InfoRow>, closeLabel: String, onDismiss: () -> Unit) =
    LocalUiStyle.current.InfoSheet(visible, title, rows, closeLabel, onDismiss)

@Composable
fun ArtworkTintedPage(artwork: Artwork, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    LocalUiStyle.current.ArtworkTintedPage(artwork, modifier, content)

@Composable
fun ArtworkHeader(artwork: Artwork, modifier: Modifier = Modifier, contentDescription: String? = null) =
    LocalUiStyle.current.ArtworkHeader(artwork, contentDescription, modifier)

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) = LocalUiStyle.current.Tag(text, modifier)

@Composable
fun ProminentButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    LocalUiStyle.current.ProminentButton(icon, contentDescription, onClick, modifier)

@Composable
fun FloatingBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    LocalUiStyle.current.FloatingBackButton(contentDescription, onClick, modifier)

@Composable
fun PosterCard(label: String, title: String, artwork: Artwork, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) =
    LocalUiStyle.current.PosterCard(label, title, subtitle, artwork, onClick, modifier)

@Composable
fun ShortcutTile(title: String, artwork: Artwork, onClick: () -> Unit, modifier: Modifier = Modifier, circular: Boolean = false) =
    LocalUiStyle.current.ShortcutTile(title, artwork, circular, onClick, modifier)

@Composable
fun CollectionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    artworks: List<Artwork>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) = LocalUiStyle.current.CollectionTile(title, subtitle, icon, artworks, circular, onClick, modifier)

@Composable
fun VolumeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier = Modifier) =
    LocalUiStyle.current.VolumeSlider(value, onValueChange, label, modifier)

@Composable
fun CollectionDetails(
    kind: String,
    title: String,
    subtitle: String?,
    onSubtitleClick: (() -> Unit)?,
    meta: String,
    playLabel: String,
    shuffleLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) = LocalUiStyle.current.CollectionDetails(kind, title, subtitle, onSubtitleClick, meta, playLabel, shuffleLabel, onPlay, onShuffle, action, modifier)

@Composable
fun FilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    LocalUiStyle.current.FilterChips(options, selectedIndex, onSelect, modifier)

@Composable
fun ArtistAvatar(artwork: Artwork, modifier: Modifier = Modifier) = LocalUiStyle.current.ArtistAvatar(artwork, modifier)

@Composable
fun ArtworkShelf(items: List<ShelfItem>, style: ShelfStyle, itemSize: Dp, modifier: Modifier = Modifier) =
    LocalUiStyle.current.ArtworkShelf(items, style, itemSize, modifier)

@Composable
fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    LocalUiStyle.current.BackButton(label, onClick, modifier)

@Composable
fun EqualizerEditor(
    bands: List<EqBand>,
    curve: List<Float>,
    maxGainDb: Float,
    enabled: Boolean,
    formatGain: (Float) -> String,
    onBandChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.EqualizerEditor(bands, curve, maxGainDb, enabled, formatGain, onBandChange, modifier)

@Composable
fun PromptSheet(
    visible: Boolean,
    title: String,
    placeholder: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialText: String = "",
) = LocalUiStyle.current.PromptSheet(visible, title, initialText, placeholder, confirmLabel, cancelLabel, onConfirm, onDismiss)


@Composable
fun FolderChoiceRow(choice: FolderChoice, onExpand: () -> Unit, onExcludedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) =
    LocalUiStyle.current.FolderChoiceRow(choice, onExpand, onExcludedChange, modifier)
