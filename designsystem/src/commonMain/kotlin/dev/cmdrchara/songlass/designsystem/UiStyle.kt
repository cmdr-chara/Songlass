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

/**
 * Cover art. [imageKey] identifies the real image for the platform [ArtworkImageLoader]
 * (a content URI on Android); [colors] are the placeholder gradient shown while it loads
 * or when the file has no cover.
 */
@Immutable
/**
 * A cover: [imageKey] loads the real image when there is one; otherwise styles draw a
 * generated cover from [seed] with the [monogram] initials. [colors] tint pages around it.
 */
data class Artwork(
    val colors: List<Color>,
    val imageKey: String? = null,
    val seed: Int = 0,
    val monogram: String? = null,
)

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

@Immutable
data class InfoRow(val label: String, val value: String)

/** Accessibility labels for playback controls, supplied by the screen layer (localized). */
@Immutable
data class PlaybackLabels(
    val play: String,
    val pause: String,
    val next: String,
    val previous: String,
)

/** Spatial relationship between navigation destinations. */
enum class NavigationDirection { Forward, Backward, Crossfade }

/**
 * The building blocks every screen is written against. Each UI style (Liquid Glass, Miuix,
 * Material Glass) implements this once; screens never check which style is active.
 *
 * Methods have no default arguments on purpose: the defaults live in the wrapper functions
 * in `Components.kt`, which are what screens call.
 */
@Stable
interface UiStyle {

    /** Keeps outgoing content alive until its transition completes. Render the supplied snapshot. */
    @Composable
    fun <T> ScreenTransition(
        targetState: T,
        direction: (T, T) -> NavigationDirection,
        modifier: Modifier,
        content: @Composable (T) -> Unit,
    )


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

    /** Opaque grouped settings surface. Screens supply section headings separately. */
    @Composable
    fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier)

    @Composable
    fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier)

    @Composable
    fun SettingsToggleRow(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean,
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
    fun SeekBar(positionMs: Long, durationMs: Long, qualityLabel: String?, isPlaying: Boolean, onSeek: (Long) -> Unit, modifier: Modifier)

    /** Volume slider between a quiet and a loud speaker, [value] 0..1. */
    @Composable
    fun VolumeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier)

    @Composable
    fun TransportControls(
        isPlaying: Boolean,
        labels: PlaybackLabels,
        onPrevious: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        modifier: Modifier,
    )

    /** Readable lyric line, optionally tappable when a timestamp is available. */
    @Composable
    fun LyricLine(text: String, active: Boolean, subdued: Boolean, progress: Float?, isPlaying: Boolean, seekLabel: String, onSeek: (() -> Unit)?, modifier: Modifier)

    @Composable
    fun LyricsViewport(modifier: Modifier, content: @Composable () -> Unit)

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
        artworkVisible: Boolean,
        artworkExpanded: Boolean,
        artworkLabel: String,
        expandArtworkLabel: String,
        closeArtworkLabel: String,
        onArtworkExpandedChange: (Boolean) -> Unit,
        dismissLabel: String,
        onDismiss: () -> Unit,
        content: @Composable (onArtworkTap: () -> Unit) -> Unit,
    )

    @Composable
    fun InfoSheet(visible: Boolean, title: String, rows: List<InfoRow>, closeLabel: String, onDismiss: () -> Unit)

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

    /**
     * A page drawn in [artwork]'s tint (see ArtworkTint): background, text, list rows and buttons
     * below become shades of the cover's color. Used by album and playlist pages.
     */
    @Composable
    fun ArtworkTintedPage(artwork: Artwork, modifier: Modifier, content: @Composable () -> Unit)

    /** Edge-to-edge square cover that dissolves into the page background at its bottom. */
    @Composable
    fun ArtworkHeader(artwork: Artwork, contentDescription: String?, modifier: Modifier)

    /** Small capsule label such as "Album" or "Playlist". */
    @Composable
    fun Tag(text: String, modifier: Modifier)

    /**
     * Tall featured card, as in Apple Music's Top Picks: a short [label] saying why it is there
     * above the card, the cover filling its top and a band in the cover's tint with [title] and
     * [subtitle]. Used for the Home highlights (favorites, shuffle all, rediscover).
     */
    @Composable
    fun PosterCard(label: String, title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier)

    /**
     * Compact shortcut for the Home's "jump back in" grid: small cover at the start and a title,
     * on a filled rounded surface. [circular] shows the cover as a circle (artists).
     */
    @Composable
    fun ShortcutTile(title: String, artwork: Artwork, circular: Boolean, onClick: () -> Unit, modifier: Modifier)

    /**
     * Library category tile: a tonal card with an [icon], [title], [subtitle] and a small fan of
     * [artworks] (shown as circles when [circular], e.g. artists).
     */
    @Composable
    fun CollectionTile(title: String, subtitle: String, icon: ImageVector, artworks: List<Artwork>, circular: Boolean, onClick: () -> Unit, modifier: Modifier)

    /** Back control that floats over artwork and scrolling content, always legible. */
    @Composable
    fun FloatingBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier)

    /** The page's primary action as a large solid circle (e.g. Play on an album). */
    @Composable
    fun ProminentButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier)
}

val LocalUiStyle = staticCompositionLocalOf<UiStyle> { error("No UiStyle provided: wrap the UI in a theme") }
