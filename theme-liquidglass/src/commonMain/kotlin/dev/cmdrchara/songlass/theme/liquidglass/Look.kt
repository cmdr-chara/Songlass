package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.MiniPlayerState
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/**
 * How the shared glass components look. Structure, gestures and motion are the same for every
 * glass style; a look decides the materials and shapes. [LiquidLook] is Apple's Liquid Glass;
 * other styles (e.g. Material Glass) provide their own through [LocalGlassLook].
 */
@Stable
interface GlassLook {
    /**
     * Glass of navigation-layer elements (tab bar, search, mini player), refracting the content
     * layer published in [LocalGlassSource]. [edges] is false for the bare material of the
     * bridge between two shapes (no rim light or lens).
     */
    @Composable
    fun navigationSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier

    /** Tab-bar glass has its own contrast and lens strength, independent of the mini player. */
    @Composable
    fun navigationBarSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier =
        modifier.appleGlass(shape, interactionSource, edges, navigationBar = true)

    /**
     * A filled control inside the content layer (buttons, chips, fields, groups) in [color].
     * Content controls never sample the backdrop: they are part of what the glass refracts.
     * [prominent] marks the page's primary action.
     */
    @Composable
    fun controlSurface(modifier: Modifier, shape: Shape, color: Color, prominent: Boolean): Modifier

    /** Fill of the bubble that marks the selected tab. */
    @Composable
    fun selection(): Color

    /** Corner radius of cards and grouped surfaces designed at [base] for Liquid Glass. */
    fun corner(base: Dp): Dp

    /** Whether scrolling down folds the tab bar into one row with the mini player (iOS 26). */
    val minimizesOnScroll: Boolean get() = true

    /** Menus as a bottom sheet attached to the screen edge instead of iOS's floating card with Cancel. */
    val bottomSheets: Boolean get() = false

    /** Icon of back controls. */
    val backIcon: ImageVector get() = AppIcons.ChevronLeft

    /** Press feedback for rows and buttons (e.g. a ripple); null keeps each component's own highlight. */
    @Composable
    fun pressIndication(): Indication? = null

    // Components a look may redraw in its own shape. Kept here, not in a separate UiStyle, so a
    // change of look keeps the same composition tree (no rebuilt screens when switching style).

    @Composable
    fun FolderChoiceRow(choice: dev.cmdrchara.songlass.designsystem.FolderChoice, onExpand: () -> Unit, onExcludedChange: (Boolean) -> Unit, modifier: Modifier) =
        GlassFolderChoiceRow(choice, onExpand, onExcludedChange, material = false, modifier = modifier)

    @Composable
    fun ListeningChart(points: List<dev.cmdrchara.songlass.designsystem.ListeningChartPoint>, summary: String, modifier: Modifier) =
        ListeningHistoryPlot(points, summary, bars = false, modifier = modifier)

    @Composable
    fun SeekBar(positionMs: Long, durationMs: Long, qualityLabel: String?, isPlaying: Boolean, onSeek: (Long) -> Unit, modifier: Modifier) =
        LiquidGlassSeekBar(positionMs, durationMs, qualityLabel, onSeek, modifier)

    @Composable
    fun TransportControls(isPlaying: Boolean, labels: PlaybackLabels, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, modifier: Modifier) =
        LiquidGlassTransportControls(isPlaying, labels, onPrevious, onPlayPause, onNext, modifier)

    @Composable
    fun ListRow(
        title: String,
        subtitle: String?,
        leading: (@Composable () -> Unit)?,
        trailing: (@Composable () -> Unit)?,
        highlighted: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier,
    ) = LiquidGlassListRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)

    @Composable
    fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier) = LiquidGlassSettingsGroup(content, modifier)

    @Composable
    fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) =
        LiquidGlassSettingsChoiceRow(title, selected, onClick, modifier)

    @Composable
    fun SettingsToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, modifier: Modifier) =
        LiquidGlassSettingsToggleRow(title, subtitle, checked, onCheckedChange, enabled, modifier)

    @Composable
    fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        LiquidGlassSegmentedControl(options, selectedIndex, onSelect, modifier)

    @Composable
    fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier) = LiquidGlassBackButton(label, onClick, modifier)

    @Composable
    fun MiniPlayer(
        state: MiniPlayerState,
        labels: PlaybackLabels,
        onClick: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        modifier: Modifier,
    ) = LiquidGlassMiniPlayer(state, labels, onClick, onPlayPause, onNext, onPrevious, modifier)

    @Composable
    fun VolumeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier) =
        LiquidGlassVolumeSlider(value, onValueChange, label, modifier)

    @Composable
    fun ArtworkShelf(items: List<ShelfItem>, style: ShelfStyle, itemSize: Dp, modifier: Modifier) =
        LiquidGlassArtworkShelf(items, style, itemSize, modifier)

    @Composable
    fun PosterCard(label: String, title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier) =
        LiquidGlassPosterCard(label, title, subtitle, artwork, onClick, modifier)

    @Composable
    fun ShortcutTile(title: String, artwork: Artwork, circular: Boolean, onClick: () -> Unit, modifier: Modifier) =
        LiquidGlassShortcutTile(title, artwork, circular, onClick, modifier)

    @Composable
    fun CollectionTile(title: String, subtitle: String, icon: ImageVector, artworks: List<Artwork>, circular: Boolean, onClick: () -> Unit, modifier: Modifier) =
        LiquidGlassCollectionTile(title, subtitle, icon, artworks, circular, onClick, modifier)

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
        action: @Composable () -> Unit,
        modifier: Modifier,
    ) = LiquidGlassCollectionDetails(kind, title, subtitle, onSubtitleClick, meta, playLabel, shuffleLabel, onPlay, onShuffle, action, modifier)

    @Composable
    fun FilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        LiquidGlassFilterChips(options, selectedIndex, onSelect, modifier)

    @Composable
    fun ArtistAvatar(artwork: Artwork, modifier: Modifier) = LiquidGlassArtistAvatar(artwork, modifier)

    @Composable
    fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, clearLabel: String, modifier: Modifier) =
        LiquidGlassSearchField(query, onQueryChange, placeholder, clearLabel, modifier)

    @Composable
    fun IconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, selected: Boolean, filled: Boolean, modifier: Modifier) =
        LiquidGlassIconButton(icon, contentDescription, onClick, selected, modifier, filled = filled)

    @Composable
    fun Tag(text: String, modifier: Modifier) = LiquidGlassTag(text, modifier)

    /** The expanded tab bar (Search included, if any). */
    @Composable
    fun NavigationBar(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) =
        LiquidNavigationBar(tabs, selectedTab, onTabSelected, modifier)
}

/** Apple's iOS 26 Liquid Glass. */
object LiquidLook : GlassLook {
    @Composable
    override fun navigationSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier =
        modifier.appleGlass(shape, interactionSource, edges)

    @Composable
    override fun controlSurface(modifier: Modifier, shape: Shape, color: Color, prominent: Boolean): Modifier =
        modifier.clip(shape).background(color)

    @Composable
    override fun selection(): Color = AppTheme.colors.fill

    override fun corner(base: Dp): Dp = base
}

val LocalGlassLook = staticCompositionLocalOf<GlassLook> { LiquidLook }
