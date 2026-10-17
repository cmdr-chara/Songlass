package dev.cmdrchara.songlass.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Entry points used by screens. Each one delegates to the active UiStyle.

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
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.MiniPlayer(state, labels, onClick, onPlayPause, onNext, modifier)

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
) = LocalUiStyle.current.IconButton(icon, contentDescription, onClick, selected, modifier)

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
) = LocalUiStyle.current.SeekBar(positionMs, durationMs, onSeek, modifier)

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
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) = LocalUiStyle.current.SearchField(query, onQueryChange, placeholder, modifier)

@Composable
fun NowPlayingSheet(
    visible: Boolean,
    artwork: Artwork?,
    dismissLabel: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) = LocalUiStyle.current.NowPlayingSheet(visible, artwork, dismissLabel, onDismiss, content)
