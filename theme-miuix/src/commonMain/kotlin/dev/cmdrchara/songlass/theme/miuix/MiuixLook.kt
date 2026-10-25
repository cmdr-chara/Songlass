package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import dev.cmdrchara.songlass.designsystem.HomeHighlight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.theme.liquidglass.GlassLook
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.MiuixIndication

/** HyperOS-style controls and surfaces, independent of the Liquid and Material recipes. */
object MiuixLook : GlassLook {
    override val contentHorizontalInset = 16.dp
    override val sectionTitleBottomPadding = 6.dp
    override val libraryCategoriesAsRows = false
    override val compactPlaylistForm = false

    @Composable
    override fun PlaybackToolsSheet(visible: Boolean, title: String, subtitle: String?, groups: List<PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, onDismiss: () -> Unit) =
        MiuixPlaybackTools(visible, title, subtitle, groups, closeLabel, backLabel, onBack, onDismiss)

    @Composable
    override fun Welcome(model: dev.cmdrchara.songlass.designsystem.WelcomeModel) = MiuixWelcome(model)

    @Composable
    override fun PlayerUtilityBar(actions: List<PlayerUtilityAction>, modifier: Modifier) =
        MiuixPlayerUtilities(actions, modifier)
    override val homeShortcutKindMenu = true
    override val lyricsPresentation = dev.cmdrchara.songlass.designsystem.LyricsPresentation(fontSize = 32f, lineHeight = 43f, weight = 600, verticalPadding = 12f, selectionCorner = 18f)

    override val lyricsCardStyle = dev.cmdrchara.songlass.designsystem.LyricsCardStyle(corner = 60f, coverCorner = 40f, verseCorner = 48f, verseInset = 48f, fontSize = 62f)

    @Composable
    override fun HomePage(title: String, trailing: @Composable () -> Unit, contentPadding: PaddingValues, modifier: Modifier, content: LazyListScope.() -> Unit) =
        MiuixHomePage(title, trailing, contentPadding, modifier, content)

    @Composable
    override fun HomeHighlights(items: List<HomeHighlight>, itemWidth: Dp, modifier: Modifier) = MiuixHomeHighlights(items, modifier)

    @Composable
    override fun navigationBarHeight(): Dp = with(LocalDensity.current) { maxOf(64.dp, 42.dp + 15.sp.toDp()) }

    override val minimizesOnScroll = false
    override val usesHazeBackdrop = false
    override val recedesUnderPlayer = false
    override val tintsNavigation = false
    override val artworkHeaderCoversStatusBar = false
    @Composable
    override fun contentSource(modifier: Modifier): Modifier = LocalMiuixBackdrop.current?.let { modifier.layerBackdrop(it) } ?: modifier

    @Composable
    override fun BackPageTitle(text: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        MiuixHeading(text, backLabel, onBack, modifier = modifier)

    @Composable
    override fun PageTitle(text: String, trailing: (@Composable () -> Unit)?, modifier: Modifier) =
        MiuixHeading(text, trailing = trailing, modifier = modifier)
    @Composable
    override fun ActionSheet(visible: Boolean, title: String, subtitle: String?, artwork: Artwork?, actions: List<SheetAction>, cancelLabel: String, onDismiss: () -> Unit) =
        MiuixActions(visible, title, subtitle, artwork, actions, cancelLabel, onDismiss)
    @Composable
    override fun InfoSheet(visible: Boolean, title: String, rows: List<InfoRow>, closeLabel: String, onDismiss: () -> Unit) = MiuixInfo(visible, title, rows, closeLabel, onDismiss)
    @Composable
    override fun PromptSheet(visible: Boolean, title: String, initialText: String, placeholder: String, confirmLabel: String, cancelLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) =
        MiuixPrompt(visible, title, initialText, placeholder, confirmLabel, cancelLabel, onConfirm, onDismiss)
    @Composable
    override fun ArtworkTintedPage(artwork: Artwork, modifier: Modifier, content: @Composable () -> Unit) {
        androidx.compose.foundation.layout.Box(modifier.background(AppTheme.colors.background)) { content() }
    }
    @Composable
    override fun ArtworkHeader(artwork: Artwork, contentDescription: String?, modifier: Modifier) = MiuixArtworkHeader(artwork, contentDescription, modifier)
    @Composable
    override fun FloatingBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier) = MiuixIconAction(AppIcons.ArrowBack, contentDescription, onClick, false, true, modifier)
    @Composable
    override fun ProminentButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier) = MiuixIconAction(icon, contentDescription, onClick, true, true, modifier)
    @Composable
    override fun PlayerContent(tint: dev.cmdrchara.songlass.designsystem.theme.ArtworkTint, content: @Composable () -> Unit) {
        // Keep Miuix's neutral surfaces and primary accent inside the music-specific player.
        content()
    }
    @Composable
    override fun playerTint(tint: dev.cmdrchara.songlass.designsystem.theme.ArtworkTint): dev.cmdrchara.songlass.designsystem.theme.ArtworkTint {
        val colors = AppTheme.colors
        return tint.copy(background = colors.background, content = colors.label,
            secondaryContent = colors.secondaryLabel, fill = colors.fill, activeFill = colors.accent,
            onActiveFill = if (colors.accent.luminance() > 0.179f) Color.Black else Color.White)
    }

    override val bottomSheets = true
    override val backIcon get() = AppIcons.ArrowBack
    override fun corner(base: Dp): Dp = (base * 1.2f).coerceAtMost(28.dp)
    override fun playerThumbnailShape() = RoundedCornerShape(16.dp)

    @Composable
    override fun navigationSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier =
        miuixSurface(modifier, shape)
    @Composable
    override fun navigationBarSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier =
        navigationSurface(modifier, shape, interactionSource, edges)
    @Composable
    override fun navigationSelectionSurface(modifier: Modifier, shape: RoundedCornerShape): Modifier = miuixSurface(modifier, shape, selected = true)
    @Composable
    override fun controlSurface(modifier: Modifier, shape: Shape, color: Color, prominent: Boolean): Modifier = modifier.clip(shape).background(color)
    @Composable
    override fun selection(): Color = AppTheme.colors.accent.copy(alpha = 0.13f)
    @Composable
    override fun pressIndication(): Indication? = if (AppTheme.accessibility.reduceMotion) null else MiuixIndication(AppTheme.colors.label)

    @Composable
    override fun NavigationBar(tabs: List<TabItem>, selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier) = MiuixNavigation(tabs, selectedTab, onTabSelected, modifier)
    @Composable
    override fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier) = MiuixAction(text, icon, onClick, modifier)
    override val usesInsetSettingsForm = false
    override val settingsRootHorizontalInset = 12.dp

    @Composable
    override fun SettingsPage(content: @Composable () -> Unit) = content()

    @Composable
    override fun SettingsRootChrome(enabled: Boolean, title: String, listState: LazyListState, content: @Composable () -> Unit) =
        MiuixSettingsRootChrome(enabled, title, listState, content)

    @Composable
    override fun SettingsRootTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        MiuixSettingsRootTitle(title, modifier)

    @Composable
    override fun SettingsPageTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        BackPageTitle(title, backLabel, onBack, modifier)

    @Composable
    override fun SettingsPageGroup(modifier: Modifier, content: @Composable () -> Unit) = SettingsGroup(content, modifier)

    @Composable
    override fun SettingsGroupFragment(position: SettingsGroupPosition, modifier: Modifier, content: @Composable () -> Unit) =
        MiuixSettingsGroupFragment(position, modifier, content)

    @Composable
    override fun SettingsValueChoice(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier) =
        MiuixSettingsValueChoice(title, labels, selectedIndex, onSelect, closeLabel, modifier)

    @Composable
    override fun SettingsDurationField(value: String, onValueChange: (String) -> Unit, label: String, onSubmit: () -> Unit, modifier: Modifier) =
        top.yukonga.miuix.kmp.basic.TextField(value, onValueChange, modifier,
            label = label, cornerRadius = 18.dp, singleLine = true,
            colors = readableMiuixFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }))

    @Composable
    override fun SettingsAppearanceSectionTitle(title: String) = Unit

    @Composable
    override fun SettingsAppearanceHint(text: String, legacy: @Composable () -> Unit) = Unit

    @Composable
    override fun SettingsAppearanceModeOptions(title: String, labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier, legacy: @Composable () -> Unit) =
        MiuixSettingsAppearanceModeOptions(title, labels, selectedIndex, onSelect, closeLabel, modifier)

    @Composable
    override fun SettingsVisualChoices(kind: SettingsVisualChoiceKind, title: String, choices: List<SettingsVisualChoice>, closeLabel: String, description: String?, lastInGroup: Boolean, modifier: Modifier) =
        MiuixSettingsVisualChoices(kind, title, choices, closeLabel, description, lastInGroup, modifier)

    @Composable
    override fun SettingsDestinationGroup(modifier: Modifier, content: @Composable () -> Unit) = MiuixSettingsDestinationGroup(modifier, content)

    @Composable
    override fun SettingsSectionTitle(title: String) = MiuixSettingsSectionTitle(title)

    @Composable
    override fun SettingsDestination(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean, onClick: () -> Unit) =
        MiuixSettingsDestination(title, summary, icon, summaryIsValue, onClick)

    @Composable
    override fun SettingsSearchResult(title: String, path: String, value: String?, onClick: () -> Unit, modifier: Modifier) =
        MiuixSettingsSearchResult(title, path, value, onClick, modifier)

    @Composable
    override fun SettingsCategoryIcon(icon: ImageVector, modifier: Modifier) = MiuixSettingsCategoryIcon(icon, modifier)

    @Composable
    override fun SettingsPreviewChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) =
        MiuixSettingsPreviewChoice(title, selected, onClick, modifier, preview)

    @Composable
    override fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier) = MiuixGroup(content, modifier)
    @Composable
    override fun ListRow(title: String, subtitle: String?, leading: (@Composable () -> Unit)?, trailing: (@Composable () -> Unit)?, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier) = MiuixRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)
    @Composable
    override fun SettingsChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) = MiuixChoice(title, selected, onClick, modifier)
    @Composable
    override fun SettingsToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, modifier: Modifier) = MiuixToggle(title, subtitle, checked, onCheckedChange, enabled, modifier)
    @Composable
    override fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) = MiuixSegments(options, selectedIndex, onSelect, modifier)
    @Composable
    override fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier) = MiuixBack(label, onClick, modifier)
    @Composable
    override fun FormField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, modifier: Modifier) = MiuixField(value, onValueChange, label, enabled, modifier)
    @Composable
    override fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, clearLabel: String, modifier: Modifier) = MiuixSearch(query, onQueryChange, placeholder, clearLabel, modifier)
    @Composable
    override fun IconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, selected: Boolean, filled: Boolean, modifier: Modifier) = MiuixIconAction(icon, contentDescription, onClick, selected, filled, modifier)
    @Composable
    override fun ValueSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier) = MiuixVolume(value, onValueChange, label, modifier)

    @Composable
    override fun VolumeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier) = MiuixVolume(value, onValueChange, label, modifier)
    @Composable
    override fun SeekBar(positionMs: Long, durationMs: Long, qualityLabel: String?, isPlaying: Boolean, onSeek: (Long) -> Unit, modifier: Modifier) = MiuixSeek(positionMs, durationMs, qualityLabel, onSeek, modifier)
    @Composable
    override fun TransportControls(isPlaying: Boolean, labels: PlaybackLabels, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, modifier: Modifier) = MiuixTransport(isPlaying, labels, onPrevious, onPlayPause, onNext, modifier)
    @Composable
    override fun MiniPlayer(state: MiniPlayerState, labels: PlaybackLabels, onClick: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, modifier: Modifier) = MiuixMini(state, labels, onClick, onPlayPause, onNext, onPrevious, modifier)
    @Composable
    override fun FilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) = MiuixFilters(options, selectedIndex, onSelect, modifier)
    @Composable
    override fun PosterCard(label: String, title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier) = MiuixPoster(label, title, subtitle, artwork, onClick, modifier)
    @Composable
    override fun ShortcutTile(title: String, artwork: Artwork, circular: Boolean, onClick: () -> Unit, modifier: Modifier) = MiuixShortcut(title, artwork, circular, onClick, modifier)
    @Composable
    override fun CollectionTile(title: String, subtitle: String, icon: ImageVector, artworks: List<Artwork>, circular: Boolean, onClick: () -> Unit, modifier: Modifier) = MiuixCollection(title, subtitle, icon, artworks, circular, onClick, modifier)
    @Composable
    override fun ArtworkShelf(items: List<ShelfItem>, style: ShelfStyle, itemSize: Dp, modifier: Modifier) = MiuixShelf(items, style, itemSize, modifier)
    @Composable
    override fun ArtistAvatar(artwork: Artwork, modifier: Modifier) = AlbumArt(artwork, modifier, cornerRadius = 22.dp)
    @Composable
    override fun FolderChoiceRow(choice: FolderChoice, onExpand: () -> Unit, onExcludedChange: (Boolean) -> Unit, modifier: Modifier) = MiuixFolder(choice, onExpand, onExcludedChange, modifier)
    @Composable
    override fun Tag(text: String, modifier: Modifier) = MiuixTag(text, modifier)

    @Composable
    override fun ListeningChart(points: List<ListeningChartPoint>, summary: String, unrecordedLabel: String, modifier: Modifier) =
        dev.cmdrchara.songlass.theme.liquidglass.ListeningHistoryPlot(points, summary, unrecordedLabel, miuixPlotStyle(), modifier)

    @Composable
    override fun StatSummary(figures: List<StatFigure>, modifier: Modifier) = MiuixStatSummary(figures, modifier)

    @Composable
    override fun RankedRow(rank: Int, title: String, subtitle: String?, leading: (@Composable () -> Unit)?, value: String, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier) =
        MiuixRankedRow(rank, title, subtitle, leading, value, highlighted, onClick, modifier)
    @Composable
    override fun CollectionDetails(kind: String, title: String, subtitle: String?, onSubtitleClick: (() -> Unit)?, meta: String, playLabel: String, shuffleLabel: String, onPlay: () -> Unit, onShuffle: () -> Unit, action: @Composable () -> Unit, modifier: Modifier) =
        MiuixDetails(kind, title, subtitle, onSubtitleClick, meta, playLabel, shuffleLabel, onPlay, onShuffle, action, modifier)
}
