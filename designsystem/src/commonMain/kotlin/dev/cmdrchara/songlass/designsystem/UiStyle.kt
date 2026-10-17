package dev.cmdrchara.songlass.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** Placeholder artwork: a gradient until real cover art is loaded from files. */
@Immutable
data class Artwork(val colors: List<Color>)

/**
 * A top-level destination. [isSearch] marks the search tab, which some styles place apart
 * (iOS 26 shows it as a separate button at the trailing end of the tab bar).
 */
@Immutable
data class TabItem(val label: String, val icon: ImageVector, val isSearch: Boolean = false)

@Immutable
data class MiniPlayerState(
    val title: String,
    val subtitle: String,
    val artwork: Artwork,
    val isPlaying: Boolean,
    /** 0..1 */
    val progress: Float,
)

/** One entry of an [UiStyle.ActionSheet]. */
@Immutable
data class SheetAction(
    val label: String,
    val icon: ImageVector?,
    val onClick: () -> Unit,
)

/** Accessibility labels for playback controls, supplied by the screen layer (localized). */
@Immutable
data class PlaybackLabels(
    val play: String,
    val pause: String,
    val next: String,
    val previous: String,
)

/**
 * The building blocks every screen is written against. Each UI style (Liquid Glass, Miuix,
 * Material Glass) implements this once; screens never check which style is active.
 *
 * Methods have no default arguments on purpose: the defaults live in the wrapper functions
 * in `Components.kt`, which are what screens call.
 */
@Stable
interface UiStyle {

    /**
     * Root layout of a tabbed screen: [content] fills the screen and scrolls under the
     * navigation layer (tab bar plus optional [accessory], e.g. the mini player).
     * [content] receives the padding needed to keep its last items visible.
     * Styles may react to [content] scrolling (e.g. minimizing the tab bar).
     */
    @Composable
    fun Scaffold(
        tabs: List<TabItem>,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        accessory: (@Composable () -> Unit)?,
        content: @Composable (PaddingValues) -> Unit,
    )

    @Composable
    fun MiniPlayer(
        state: MiniPlayerState,
        labels: PlaybackLabels,
        onClick: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        modifier: Modifier,
    )

    @Composable
    fun ListRow(
        title: String,
        subtitle: String?,
        leading: (@Composable () -> Unit)?,
        trailing: (@Composable () -> Unit)?,
        highlighted: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    )

    @Composable
    fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier)

    /** Prominent action such as "Play" or "Shuffle" on an album. */
    @Composable
    fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier)

    /**
     * Icon-only button with a hit area of at least 44×44 dp. [filled] adds a translucent circle
     * behind the icon (for secondary actions on media backgrounds).
     */
    @Composable
    fun IconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, selected: Boolean, filled: Boolean, modifier: Modifier)

    @Composable
    fun AlbumArt(artwork: Artwork, cornerRadius: Dp, modifier: Modifier)

    @Composable
    fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier)

    @Composable
    fun TransportControls(
        isPlaying: Boolean,
        labels: PlaybackLabels,
        onPrevious: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        modifier: Modifier,
    )

    @Composable
    fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier)

    /**
     * Full-screen sheet for Now Playing, with a grabber and swipe-down to dismiss.
     * Content colors inside are chosen by the style to stay legible on [artwork].
     */
    @Composable
    fun NowPlayingSheet(
        visible: Boolean,
        artwork: Artwork?,
        dismissLabel: String,
        onDismiss: () -> Unit,
        content: @Composable () -> Unit,
    )

    /**
     * Modal list of actions about one item (e.g. a track), shown from the bottom with a header.
     * Choosing an action runs it and dismisses the sheet.
     */
    @Composable
    fun ActionSheet(
        visible: Boolean,
        title: String,
        subtitle: String?,
        artwork: Artwork?,
        actions: List<SheetAction>,
        cancelLabel: String,
        onDismiss: () -> Unit,
    )
}

val LocalUiStyle = staticCompositionLocalOf<UiStyle> { error("No UiStyle provided: wrap the UI in a theme") }
