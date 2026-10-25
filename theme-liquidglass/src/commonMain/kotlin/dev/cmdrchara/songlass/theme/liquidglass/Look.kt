package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import dev.cmdrchara.songlass.designsystem.HomeHighlight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** A parent shelf can own the page inset instead of applying it again inside each row. */
val LocalGlassRowHorizontalInset = staticCompositionLocalOf<Dp?> { null }

/**
 * Presentation of the shared component contracts. A look owns materials, shapes and the
 * composition of style-specific sections while screens keep data and actions shared.
 * [LiquidLook] is the default; other styles provide their own through [LocalGlassLook].
 */
@Stable
interface GlassLook {
    /** Content rhythm and category navigation, independent of the glass navigation layer. */
    val contentHorizontalInset: Dp get() = 20.dp
    val sectionTitleBottomPadding: Dp get() = 8.dp
    val libraryCategoriesAsRows: Boolean get() = true
    /** Compact, inset iOS form for smart-playlist editing; other looks retain their own builder. */
    val compactPlaylistForm: Boolean get() = true
    /** Long shortcut categories use the look’s native menu rather than compressed tabs. */
    val homeShortcutKindMenu: Boolean get() = false
    val lyricsPresentation: dev.cmdrchara.songlass.designsystem.LyricsPresentation
        get() = dev.cmdrchara.songlass.designsystem.LyricsPresentation()

    val lyricsCardStyle: dev.cmdrchara.songlass.designsystem.LyricsCardStyle
        get() = dev.cmdrchara.songlass.designsystem.LyricsCardStyle()

    @Composable
    fun HomePage(title: String, trailing: @Composable () -> Unit, contentPadding: PaddingValues, modifier: Modifier, content: LazyListScope.() -> Unit) =
        GlassHomePage(title, trailing, contentPadding, modifier, content)

    @Composable
    fun HomeHighlights(items: List<HomeHighlight>, itemWidth: Dp, modifier: Modifier) = LiquidHomeHighlights(items, itemWidth, modifier)

    /** Styles own the backdrop source and whether the background recedes under the player. */
    val usesHazeBackdrop: Boolean get() = true
    val recedesUnderPlayer: Boolean get() = true
    val tintsNavigation: Boolean get() = true
    /** False when collection artwork is inset below a status bar on the theme canvas. */
    val artworkHeaderCoversStatusBar: Boolean get() = true
    @Composable
    fun contentSource(modifier: Modifier): Modifier = modifier

    @Composable
    fun BackPageTitle(text: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        GlassBackPageTitle(text, backLabel, onBack, modifier)

    @Composable
    fun PageTitle(text: String, trailing: (@Composable () -> Unit)?, modifier: Modifier) = LiquidPageTitle(text, trailing, modifier)

    @Composable
    fun ActionSheet(visible: Boolean, title: String, subtitle: String?, artwork: Artwork?, actions: List<dev.cmdrchara.songlass.designsystem.SheetAction>, cancelLabel: String, onDismiss: () -> Unit) =
        LiquidGlassActionSheet(visible, title, subtitle, artwork, actions, cancelLabel, onDismiss)

    @Composable
    fun InfoSheet(visible: Boolean, title: String, rows: List<dev.cmdrchara.songlass.designsystem.InfoRow>, closeLabel: String, onDismiss: () -> Unit) =
        LiquidGlassActionSheet(visible, title, null, null, emptyList(), closeLabel, onDismiss, rows)

    @Composable
    fun PlaybackToolsSheet(visible: Boolean, title: String, subtitle: String?, groups: List<dev.cmdrchara.songlass.designsystem.PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, onDismiss: () -> Unit) =
        LiquidPlaybackTools(visible, title, subtitle, groups, closeLabel, backLabel, onBack, onDismiss)

    @Composable
    fun Welcome(model: dev.cmdrchara.songlass.designsystem.WelcomeModel) = LiquidWelcome(model)

    @Composable
    fun PlayerUtilityBar(actions: List<dev.cmdrchara.songlass.designsystem.PlayerUtilityAction>, modifier: Modifier) =
        LiquidPlayerUtilities(actions, modifier)

    @Composable
    fun PromptSheet(visible: Boolean, title: String, initialText: String, placeholder: String, confirmLabel: String, cancelLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) =
        LiquidGlassPromptSheet(visible, title, initialText, placeholder, confirmLabel, cancelLabel, onConfirm, onDismiss)

    @Composable
    fun ArtworkTintedPage(artwork: Artwork, modifier: Modifier, content: @Composable () -> Unit) = LiquidGlassArtworkTintedPage(artwork, modifier, content)

    @Composable
    fun ArtworkHeader(artwork: Artwork, contentDescription: String?, modifier: Modifier) = LiquidGlassArtworkHeader(artwork, contentDescription, modifier)

    @Composable
    fun FloatingBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier) = LiquidGlassFloatingBackButton(contentDescription, onClick, modifier)

    @Composable
    fun ProminentButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier) = LiquidGlassProminentButton(icon, contentDescription, onClick, modifier)

    @Composable
    fun PlayerContent(tint: dev.cmdrchara.songlass.designsystem.theme.ArtworkTint, content: @Composable () -> Unit) = ProvideArtworkTint(tint, content)

    /** Music-specific artwork transition remains shared; each style supplies its palette. */
    @Composable
    fun playerTint(tint: dev.cmdrchara.songlass.designsystem.theme.ArtworkTint): dev.cmdrchara.songlass.designsystem.theme.ArtworkTint = tint

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

    /** Raised, refracting selection lens. Material overrides it with a tonal surface. */
    @Composable
    fun navigationSelectionSurface(modifier: Modifier, shape: RoundedCornerShape): Modifier =
        modifier.navigationLens(shape)

    /** Keep icon and label clear at larger system text sizes. */
    @Composable
    fun navigationBarHeight(): Dp = with(LocalDensity.current) { maxOf(64.dp, 44.dp + 14.sp.toDp()) }

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

    /** Liquid folds the mini player inline on scroll; Material opts out. */
    val minimizesOnScroll: Boolean get() = true

    /** One reversible motion drives both the sheet and its cover. */
    val playerOpenMotion: FiniteAnimationSpec<Float> get() = spring(dampingRatio = 0.92f, stiffness = 300f, visibilityThreshold = 0.002f)
    val playerCloseMotion: FiniteAnimationSpec<Float> get() = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = 0.002f)

    /** Shape at the thumbnail end of the shared artwork transition. */
    fun playerThumbnailShape(): RoundedCornerShape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 20, bottomEndPercent = 20)

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
    fun ListeningChart(points: List<dev.cmdrchara.songlass.designsystem.ListeningChartPoint>, summary: String, unrecordedLabel: String, modifier: Modifier) =
        ListeningHistoryPlot(points, summary, unrecordedLabel, ListeningPlotStyle.Liquid, modifier)

    /** Liquid: one inset group with equal columns and hairline dividers, like grouped iOS summaries. */
    @Composable
    fun StatSummary(figures: List<dev.cmdrchara.songlass.designsystem.StatFigure>, modifier: Modifier) =
        LiquidStatSummary(figures, modifier)

    /** Liquid: the position in secondary text before the artwork, as in chart lists on iOS. */
    @Composable
    fun RankedRow(rank: Int, title: String, subtitle: String?, leading: (@Composable () -> Unit)?, value: String, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier) =
        LiquidRankedRow(rank, title, subtitle, leading, value, highlighted, onClick, modifier)

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
    fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier) = LiquidGlassActionButton(text, icon, onClick, modifier)

    /** The grouped preferences page has a different content plane from media browsing. */
    val usesInsetSettingsForm: Boolean get() = true

    val settingsRootHorizontalInset: Dp get() = contentHorizontalInset

    @Composable
    fun SettingsRootChrome(enabled: Boolean, title: String, listState: androidx.compose.foundation.lazy.LazyListState, content: @Composable () -> Unit) = content()

    @Composable
    fun SettingsRootTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        SettingsPageTitle(title, backLabel, onBack, modifier)

    @Composable
    fun SettingsPage(content: @Composable () -> Unit) = LiquidSettingsPage(content)

    /** Auxiliary tools retain their legacy composition unless a look supplies a settings frame. */
    @Composable
    fun SettingsToolPage(content: @Composable () -> Unit) = content()

    @Composable
    fun SettingsToolPageTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier, legacy: @Composable () -> Unit) = legacy()

    @Composable
    fun SettingsToolSectionTitle(title: String, legacy: @Composable () -> Unit) = legacy()

    @Composable
    fun SettingsPageTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        LiquidSettingsPageTitle(title, backLabel, onBack, modifier)

    @Composable
    fun SettingsPageGroup(modifier: Modifier, content: @Composable () -> Unit) = LiquidSettingsPageGroup(modifier, content)

    /** Adjacent preference items can share one outline without moving search anchors. */
    @Composable
    fun SettingsGroupFragment(position: dev.cmdrchara.songlass.designsystem.SettingsGroupPosition, modifier: Modifier, content: @Composable () -> Unit) =
        LiquidSettingsGroupFragment(position, modifier, content)

    @Composable
    fun SettingsValueChoice(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier) =
        LiquidSettingsValueChoice(title, labels, selectedIndex, onSelect, closeLabel, modifier)

    @Composable
    fun SettingsDurationField(value: String, onValueChange: (String) -> Unit, label: String, onSubmit: () -> Unit, modifier: Modifier) =
        GlassFormField(value, onValueChange, label, true, false, modifier,
            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), KeyboardActions(onDone = { onSubmit() }))

    @Composable
    fun SettingsDestinationGroup(modifier: Modifier, content: @Composable () -> Unit) = SettingsPageGroup(modifier, content)

    @Composable
    fun SettingsSectionTitle(title: String) = GlassPlaylistFormSection(title)

    @Composable
    fun SettingsDestination(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean, onClick: () -> Unit) =
        LiquidSettingsDestination(title, summary, icon, summaryIsValue, onClick)

    /** Search results retain their full breadcrumb and current value at every text size. */
    @Composable
    fun SettingsSearchResult(title: String, path: String, value: String?, onClick: () -> Unit, modifier: Modifier) =
        LiquidSettingsSearchResult(title, path, value, onClick, modifier)

    /** Decorative category badges and radio preview cards have distinct recipes in all three looks. */
    @Composable
    fun SettingsCategoryIcon(icon: ImageVector, modifier: Modifier) = LiquidSettingsCategoryIcon(icon, modifier)

    @Composable
    fun SettingsChoiceOptions(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier) =
        SettingsPageGroup(modifier.selectableGroup()) {
            labels.forEachIndexed { index, label -> SettingsChoiceRow(label, index == selectedIndex, { onSelect(index) }, Modifier) }
        }

    /** Appearance fields carry their own label; group fragments preserve lazy search anchors. */
    @Composable
    fun SettingsAppearanceSectionTitle(title: String) = Unit

    @Composable
    fun SettingsAppearanceHint(text: String, legacy: @Composable () -> Unit) = Unit

    @Composable
    fun SettingsAppearanceModeOptions(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier, legacy: @Composable () -> Unit) =
        LiquidAppearanceModeOptions(title, labels, selectedIndex, onSelect, closeLabel, modifier)

    /** Each look chooses whether to show previews or open a compact preference picker. */
    @Composable
    fun SettingsVisualChoices(kind: dev.cmdrchara.songlass.designsystem.SettingsVisualChoiceKind, title: String, choices: List<dev.cmdrchara.songlass.designsystem.SettingsVisualChoice>, closeLabel: String, description: String?, lastInGroup: Boolean, modifier: Modifier) =
        LiquidAppearanceChoices(kind, title, choices, closeLabel, description, lastInGroup, modifier)

    @Composable
    fun SettingsPreviewChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) =
        LiquidSettingsPreviewChoice(title, selected, onClick, modifier, preview)

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

    /** Numeric setting, with the look's own slider and without speaker decorations. */
    @Composable
    fun ValueSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier) =
        LiquidGlassVolumeSlider(value, onValueChange, label, modifier, showSpeakers = false)

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
    fun FormField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, modifier: Modifier) =
        GlassFormField(value, onValueChange, label, enabled, material = false, modifier = modifier)

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
