package dev.cmdrchara.songlass.theme.materialglass

import dev.cmdrchara.songlass.theme.liquidglass.CollectionArtworkViewport
import dev.cmdrchara.songlass.theme.liquidglass.collectionArtworkHeightBudget
import dev.cmdrchara.songlass.theme.liquidglass.SettingsAdjustmentContent
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import dev.cmdrchara.songlass.designsystem.HomeHighlight
import dev.cmdrchara.songlass.designsystem.SettingsGroupPosition
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import androidx.compose.ui.graphics.vector.ImageVector
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.MiniPlayerState
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalBlurStrength
import dev.cmdrchara.songlass.theme.liquidglass.GlassLook
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassSource

/** Material 3 Expressive content and semantic system colors, with glass reserved for navigation. */
@Stable
internal class MaterialLook(private val base: SeaPalette) : GlassLook {
    override val contentHorizontalInset = 16.dp
    override val tintsNavigation: Boolean get() = !base.usesSystemColors
    override val sectionTitleBottomPadding = 6.dp
    override val libraryCategoriesAsRows = false
    override val compactPlaylistForm = false

    @Composable
    override fun ActionSheet(visible: Boolean, title: String, subtitle: String?, artwork: Artwork?, actions: List<dev.cmdrchara.songlass.designsystem.SheetAction>, cancelLabel: String, onDismiss: () -> Unit) =
        MaterialActionSheet(visible, title, subtitle, artwork, actions, cancelLabel, onDismiss, base)

    @Composable
    override fun InfoSheet(visible: Boolean, title: String, rows: List<dev.cmdrchara.songlass.designsystem.InfoRow>, closeLabel: String, onDismiss: () -> Unit) =
        MaterialInfoSheet(visible, title, rows, closeLabel, onDismiss, base)

    @Composable
    override fun PromptSheet(visible: Boolean, title: String, initialText: String, placeholder: String, confirmLabel: String, cancelLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) =
        MaterialPromptSheet(visible, title, initialText, placeholder, confirmLabel, cancelLabel, onConfirm, onDismiss, base)

    @Composable
    override fun PlaybackToolsSheet(visible: Boolean, title: String, subtitle: String?, groups: List<dev.cmdrchara.songlass.designsystem.PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, onDismiss: () -> Unit) =
        MaterialPlaybackTools(visible, title, subtitle, groups, closeLabel, backLabel, onBack, onDismiss, base)

    @Composable
    override fun Welcome(model: dev.cmdrchara.songlass.designsystem.WelcomeModel) = MaterialWelcome(model, palette())

    @Composable
    override fun PlayerUtilityBar(actions: List<dev.cmdrchara.songlass.designsystem.PlayerUtilityAction>, modifier: Modifier) =
        MaterialPlayerUtilities(actions, modifier, palette())
    override val lyricsPresentation = dev.cmdrchara.songlass.designsystem.LyricsPresentation(fontSize = 32f, lineHeight = 42f, weight = 700, verticalPadding = 16f, selectionCorner = 28f)

    override val lyricsCardStyle = dev.cmdrchara.songlass.designsystem.LyricsCardStyle(corner = 112f, coverCorner = 64f, verseCorner = 88f, verseInset = 52f, fontSize = 64f, accentMark = true)

    @Composable
    override fun BackPageTitle(text: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        MaterialBackPageTitle(text, backLabel, onBack, modifier)

    @Composable
    override fun PageTitle(text: String, trailing: (@Composable () -> Unit)?, modifier: Modifier) =
        MaterialPageTitle(text, trailing, modifier)

    // The page still owns shared lazy sections; Material chooses its title and highlights.
    @Composable
    override fun HomePage(title: String, trailing: @Composable () -> Unit, contentPadding: PaddingValues, modifier: Modifier, content: LazyListScope.() -> Unit) =
        dev.cmdrchara.songlass.theme.liquidglass.GlassHomePage(title, trailing, contentPadding, modifier, content)

    @Composable
    override fun HomeHighlights(items: List<HomeHighlight>, itemWidth: Dp, modifier: Modifier) =
        MaterialHomeHighlights(items, modifier)


    @Composable
    override fun FormField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, modifier: Modifier) =
        dev.cmdrchara.songlass.theme.liquidglass.GlassFormField(value, onValueChange, label, enabled, material = true, modifier = modifier)

    override val playerOpenMotion = spring<Float>(dampingRatio = 1f, stiffness = 340f, visibilityThreshold = 0.002f)
    override val playerCloseMotion = spring<Float>(dampingRatio = 1f, stiffness = 480f, visibilityThreshold = 0.002f)
    override fun playerThumbnailShape() = RoundedCornerShape(MaterialShapes.Medium)

    @Composable
    override fun FolderChoiceRow(choice: dev.cmdrchara.songlass.designsystem.FolderChoice, onExpand: () -> Unit, onExcludedChange: (Boolean) -> Unit, modifier: Modifier) =
        dev.cmdrchara.songlass.theme.liquidglass.GlassFolderChoiceRow(choice, onExpand, onExcludedChange, material = true, modifier = modifier)

    @Composable
    override fun ListeningChart(points: List<dev.cmdrchara.songlass.designsystem.ListeningChartPoint>, summary: String, unrecordedLabel: String, modifier: Modifier) =
        dev.cmdrchara.songlass.theme.liquidglass.ListeningHistoryPlot(points, summary, unrecordedLabel, materialPlotStyle(), modifier)

    @Composable
    override fun StatSummary(figures: List<dev.cmdrchara.songlass.designsystem.StatFigure>, modifier: Modifier) = MaterialStatSummary(figures, modifier)

    @Composable
    override fun RankedRow(rank: Int, title: String, subtitle: String?, leading: (@Composable () -> Unit)?, value: String, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier) =
        MaterialRankedRow(rank, title, subtitle, leading, value, highlighted, onClick, modifier)

    /** Native roles remain stable over cover art; the fallback can still follow a tinted page. */
    @Composable
    internal fun palette(): SeaPalette {
        if (base.usesSystemColors) return base
        val colors = AppTheme.colors
        return remember(colors.accent, colors.isDark, base.hue) {
            val accent = Hsl.of(colors.accent)
            SeaPalette(if (accent.s > 0.12f) accent.h else base.hue, colors.isDark)
        }
    }

    @Composable
    override fun ArtworkTintedPage(artwork: Artwork, modifier: Modifier, content: @Composable () -> Unit) {
        if (base.usesSystemColors) {
            CollectionArtworkViewport(modifier.background(AppTheme.colors.background), content)
        } else super.ArtworkTintedPage(artwork, modifier, content)
    }

    @Composable
    override fun playerTint(tint: dev.cmdrchara.songlass.designsystem.theme.ArtworkTint): dev.cmdrchara.songlass.designsystem.theme.ArtworkTint =
        if (!base.usesSystemColors) tint else tint.copy(isDark = base.dark, background = base.background,
            content = base.label, secondaryContent = base.secondaryLabel, fill = base.containerHigh,
            activeFill = base.primary, onActiveFill = base.onPrimary)

    @Composable
    override fun navigationSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier =
        glassSurface(modifier, shape, interactionSource, edges, navigationBar = false)

    @Composable
    override fun navigationBarSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier =
        glassSurface(modifier, shape, interactionSource, edges, navigationBar = true)

    @Composable
    override fun navigationSelectionSurface(modifier: Modifier, shape: RoundedCornerShape): Modifier {
        val palette = palette()
        return modifier.clip(shape).background(palette.secondaryContainer)
    }

    @OptIn(ExperimentalHazeApi::class)
    @Composable
    private fun glassSurface(
        modifier: Modifier,
        shape: RoundedCornerShape,
        interactionSource: InteractionSource?,
        edges: Boolean,
        navigationBar: Boolean,
    ): Modifier {
        val colors = AppTheme.colors
        val palette = palette()
        val accessibility = AppTheme.accessibility
        val source = LocalGlassSource.current
        // Mini player retains its protective tonal material for title and artist legibility.
        // The navigation bar lets more of the same backdrop through, without a second blur.
        val outline = if (edges) Modifier.border(0.75.dp, palette.secondaryLabel.copy(alpha = 0.12f), shape) else Modifier
        val blur = LocalBlurStrength.current
        if (accessibility.reduceTransparency || !blur.blurred || source == null) {
            return modifier.clip(shape).background(palette.containerHigh).then(outline)
        }
        val style = remember(shape, palette, edges, colors.background, navigationBar, blur) {
            GlassStyle.regular.then {
                shape(shape)
                backgroundColor(colors.background)
                optics(
                    GlassDefaults.optics.copy(
                        depth = OpticalSizeValue.Fixed(1f),
                        blurRadius = OpticalSizeValue.Fixed(blur.scale(20.dp)),
                        refractionDisplacement = if (edges) { if (navigationBar) 10.dp else 6.dp } else 0.dp,
                        refractionStrength = if (edges) { if (navigationBar) 0.32f else 0.18f } else 0f,
                    ),
                )
                val tintAlpha = if (navigationBar) {
                    if (palette.dark) 0.62f else 0.68f
                } else {
                    if (palette.dark) 0.80f else 0.86f
                }
                tint(palette.containerHigh.copy(alpha = tintAlpha))
                whitePoint(if (palette.dark) -0.04f else 0.10f)
                chromaMultiplier(1.05f)
                contentNormalBlend(0f)
                specularIntensity(if (navigationBar && edges) 0.25f else 0.08f)
                ambientResponse(0.12f)
                edgeShadow(Color.Transparent)
                chromaticAberrationStrength(if (edges) 0.025f else 0f)
                pressed { lightingIntensity(0.35f) }
            }
        }
        return modifier
            .hazeGlass(input = HazeInput.Sources(source), style = style, interactionSource = interactionSource)
            .then(outline)
    }

    @Composable
    override fun controlSurface(modifier: Modifier, shape: Shape, color: Color, prominent: Boolean): Modifier {
        // Content controls are tonal; the backdrop material belongs to navigation only.
        // The page's main action uses the saturated primary role.
        val fill = if (prominent) palette().primary else color
        return modifier.clip(shape).background(fill)
    }

    @Composable
    override fun selection(): Color =
        palette().secondaryContainer

    @Composable
    override fun SeekBar(positionMs: Long, durationMs: Long, qualityLabel: String?, isPlaying: Boolean, onSeek: (Long) -> Unit, modifier: Modifier) =
        WavySeekBar(positionMs, durationMs, qualityLabel, isPlaying, onSeek, modifier)

    @Composable
    override fun TransportControls(isPlaying: Boolean, labels: PlaybackLabels, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, modifier: Modifier) =
        MaterialTransportControls(isPlaying, labels, onPrevious, onPlayPause, onNext, modifier)

    @Composable
    override fun ListRow(
        title: String,
        subtitle: String?,
        leading: (@Composable () -> Unit)?,
        trailing: (@Composable () -> Unit)?,
        highlighted: Boolean,
        onClick: (() -> Unit)?,
        modifier: Modifier,
        fullText: Boolean,
    ) = MaterialListRow(title, subtitle, leading, trailing, highlighted, onClick, modifier, fullText)

    override val usesInsetSettingsForm = false

    @Composable
    override fun SettingsPage(content: @Composable () -> Unit) = MaterialSettingsPage(content)

    @Composable
    override fun SettingsToolPage(content: @Composable () -> Unit) = MaterialSettingsPage(content)

    @Composable
    override fun SettingsToolPageTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier, legacy: @Composable () -> Unit) =
        MaterialSettingsPageTitle(title, backLabel, onBack, modifier)

    @Composable
    override fun SettingsToolSectionTitle(title: String, legacy: @Composable () -> Unit) = MaterialSettingsSectionTitle(title)

    @Composable
    override fun SettingsPageTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        MaterialSettingsPageTitle(title, backLabel, onBack, modifier)

    @Composable
    override fun SettingsPageGroup(modifier: Modifier, content: @Composable () -> Unit) = SettingsGroup(content, modifier)

    @Composable
    override fun SettingsGroupFragment(position: SettingsGroupPosition, modifier: Modifier, content: @Composable () -> Unit) =
        MaterialSettingsGroupFragment(position, modifier, content)

    @Composable
    override fun SettingsValueChoice(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier) =
        MaterialSettingsChoiceOptions(title, labels, selectedIndex, onSelect, closeLabel, modifier,
            inlineTitle = true, leadingIcon = null, emphasized = false, contentInset = 16.dp)

    @Composable
    override fun SettingsDurationField(value: String, onValueChange: (String) -> Unit, label: String, onSubmit: () -> Unit, modifier: Modifier) =
        MaterialSettingsDurationField(value, onValueChange, label, onSubmit, modifier)

    @Composable
    override fun SettingsDestinationGroup(modifier: Modifier, content: @Composable () -> Unit) = MaterialSettingsDestinationGroup(modifier, content)

    @Composable
    override fun SettingsSectionTitle(title: String) = MaterialSettingsSectionTitle(title)

    @Composable
    override fun SettingsDestination(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean, onClick: () -> Unit) =
        MaterialSettingsDestination(title, summary, icon, summaryIsValue, onClick)

    @Composable
    override fun SettingsSearchResult(title: String, path: String, value: String?, onClick: () -> Unit, modifier: Modifier) =
        MaterialSettingsSearchResult(title, path, value, onClick, modifier)

    @Composable
    override fun SettingsCategoryIcon(icon: ImageVector, modifier: Modifier) = MaterialSettingsCategoryIcon(icon, modifier)

    @Composable
    override fun SettingsChoiceOptions(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier) =
        MaterialSettingsChoiceOptions(title, labels, selectedIndex, onSelect, closeLabel, modifier)

    @Composable
    override fun SettingsAppearanceSectionTitle(title: String) = Unit

    @Composable
    override fun SettingsAppearanceHint(text: String, legacy: @Composable () -> Unit) = Unit

    @Composable
    override fun SettingsAppearanceModeOptions(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier, legacy: @Composable () -> Unit) =
        MaterialSettingsChoiceOptions(title, labels, selectedIndex, onSelect, closeLabel, modifier, inlineTitle = true, leadingIcon = AppIcons.Contrast)

    @Composable
    override fun SettingsVisualChoices(kind: dev.cmdrchara.songlass.designsystem.SettingsVisualChoiceKind, title: String, choices: List<dev.cmdrchara.songlass.designsystem.SettingsVisualChoice>, closeLabel: String, description: String?, lastInGroup: Boolean, modifier: Modifier) =
        MaterialSettingsVisualChoices(kind, title, choices, closeLabel, description, modifier)

    @Composable
    override fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) =
        MaterialActionButton(text, icon, onClick, modifier, enabled)

    @Composable
    override fun SortOptions(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        MaterialSortOptions(options, selectedIndex, onSelect, modifier)

    @Composable
    override fun SettingsPreviewChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) =
        MaterialSettingsPreviewChoice(title, selected, onClick, modifier, preview)

    @Composable
    override fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier) = MaterialSettingsGroup(content, modifier)

    // Shared primitives still request legacy sizes; map them to discrete Material roles.
    override fun corner(base: Dp): Dp = when {
        base <= 8.dp -> MaterialShapes.Small
        base <= 12.dp -> MaterialShapes.Medium
        base <= 18.dp -> MaterialShapes.LargeIncreased
        base <= 28.dp -> MaterialShapes.ExtraLarge
        else -> MaterialShapes.ExtraLargeIncreased
    }

    // Material 3 structure: a navigation bar that stays put, bottom sheets, back arrows, ripples
    // and M3 selection controls. The glass is a restrained tonal navigation surface.
    @Composable
    override fun navigationBarHeight(): Dp = materialNavigationBarHeight()

    override val minimizesOnScroll: Boolean get() = false
    override val bottomSheets: Boolean get() = true
    override val backIcon: ImageVector get() = AppIcons.ArrowBack

    @Composable
    override fun pressIndication(): Indication = MaterialRipple

    @Composable
    override fun artworkTileFeedback(modifier: Modifier, interactionSource: InteractionSource, shape: Shape): Modifier =
        modifier.indication(interactionSource, remember(shape) { MaterialShapeRipple(shape) })

    @Composable
    override fun NavigationBar(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) =
        MaterialNavigationBar(tabs, selectedTab, onTabSelected, modifier)

    @Composable
    override fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier) = MaterialBackButton(label, onClick, modifier)

    @Composable
    override fun MiniPlayer(
        state: MiniPlayerState,
        labels: PlaybackLabels,
        onClick: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        modifier: Modifier,
    ) = MaterialMiniPlayer(state, labels, onClick, onPlayPause, onNext, onPrevious, modifier)

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
    ) = MaterialCollectionDetails(kind, title, subtitle, onSubtitleClick, meta, playLabel, shuffleLabel, onPlay, onShuffle, action, modifier)

    @Composable
    override fun FilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        MaterialFilterChips(options, selectedIndex, onSelect, modifier)

    @Composable
    override fun ArtistAvatar(artwork: Artwork, modifier: Modifier) = MaterialArtistAvatar(artwork, modifier)

    @Composable
    override fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, clearLabel: String, modifier: Modifier) =
        MaterialSearchField(query, onQueryChange, placeholder, clearLabel, modifier)

    @Composable
    override fun IconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, selected: Boolean, filled: Boolean, modifier: Modifier) =
        MaterialIconButton(icon, contentDescription, onClick, selected, filled, modifier)

    @Composable
    override fun Tag(text: String, modifier: Modifier) = MaterialTag(text, modifier)

    @Composable
    override fun ArtworkShelf(items: List<ShelfItem>, style: ShelfStyle, itemSize: Dp, modifier: Modifier) =
        MaterialArtworkShelf(items, style, itemSize, modifier)

    @Composable
    override fun PosterCard(label: String, title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier) =
        MaterialPosterCard(label, title, subtitle, artwork, onClick, modifier)

    @Composable
    override fun ShortcutTile(title: String, artwork: Artwork, circular: Boolean, onClick: () -> Unit, modifier: Modifier) =
        MaterialShortcutTile(title, artwork, circular, onClick, modifier)

    @Composable
    override fun CollectionTile(title: String, subtitle: String, icon: ImageVector, artworks: List<Artwork>, circular: Boolean, onClick: () -> Unit, modifier: Modifier) =
        MaterialCollectionTile(title, subtitle, icon, artworks, circular, onClick, modifier)

    @Composable
    override fun collectionArtworkHeight(): Dp = collectionArtworkHeightBudget()

    @Composable
    override fun SettingsAdjustment(title: String, value: String, hint: String?, enabled: Boolean,
        lessLabel: String, moreLabel: String, resetLabel: String?, onLess: () -> Unit, onMore: () -> Unit,
        onReset: (() -> Unit)?, modifier: Modifier, resetEnabled: Boolean, slider: @Composable (Modifier) -> Unit) =
        SettingsAdjustmentContent(title, value, hint, enabled, lessLabel, moreLabel, resetLabel,
            onLess, onMore, onReset, modifier, AppTheme.typography.headline, resetEnabled, slider)

    @Composable
    override fun ValueSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier) =
        MaterialVolumeSlider(value, onValueChange, label, modifier, showSpeaker = false)

    @Composable
    override fun VolumeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier) =
        MaterialVolumeSlider(value, onValueChange, label, modifier)

    @Composable
    override fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) =
        MaterialSettingsChoiceRow(title, selected, onClick, modifier)

    @Composable
    override fun SettingsToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, modifier: Modifier) =
        MaterialSettingsToggleRow(title, subtitle, checked, onCheckedChange, enabled, modifier)

    @Composable
    override fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) =
        MaterialSegmentedControl(options, selectedIndex, onSelect, modifier)
}
