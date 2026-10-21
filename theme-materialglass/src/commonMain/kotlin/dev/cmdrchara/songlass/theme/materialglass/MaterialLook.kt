package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import dev.cmdrchara.songlass.theme.liquidglass.GlassLook
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassSource
import kotlin.random.Random

/**
 * The "Google Sea Glass" material. Figma stacks, per surface: a white luminosity fill, the surface
 * colour at 33%, a soft-light white gradient, native glass (blur 47, refraction 23 / 0.42,
 * chromatic aberration 0.5), light from inside at the top, a coloured inner rim, a reflection from
 * the bottom, a coloured glow underneath (screen) and grain. Sizes are scaled from the 912 px board
 * (a 104 px pill is our 62 dp tab bar, so about 0.6 dp per px).
 */
@Stable
internal class MaterialLook(private val base: SeaPalette) : GlassLook {
    override val contentHorizontalInset = 16.dp
    override val tintsNavigation: Boolean get() = !base.usesSystemColors
    override val sectionTitleBottomPadding = 6.dp
    override val libraryCategoriesAsRows = false
    override val compactPlaylistForm = false

    @Composable
    override fun PlaybackToolsSheet(visible: Boolean, title: String, subtitle: String?, groups: List<dev.cmdrchara.songlass.designsystem.PlaybackToolGroup>, closeLabel: String, backLabel: String?, onBack: (() -> Unit)?, onDismiss: () -> Unit) =
        MaterialPlaybackTools(visible, title, subtitle, groups, closeLabel, backLabel, onBack, onDismiss, palette())

    @Composable
    override fun PlayerUtilityBar(output: dev.cmdrchara.songlass.designsystem.PlayerOutputAction?, audioLabel: String, onAudio: () -> Unit, actions: List<dev.cmdrchara.songlass.designsystem.PlayerUtilityAction>, modifier: Modifier) =
        MaterialPlayerUtilities(output, audioLabel, onAudio, actions, modifier, palette())
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
    override fun playerThumbnailShape() = RoundedCornerShape(14.dp)

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
            androidx.compose.foundation.layout.Box(modifier.background(base.background)) { content() }
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
        return modifier.clip(shape).background(Brush.verticalGradient(listOf(
            androidx.compose.ui.graphics.lerp(palette.primaryContainer, Color.White, if (palette.dark) 0.04f else 0.015f),
            palette.primaryContainer,
        )))
            .border(0.75.dp, palette.primary.copy(alpha = 0.12f), shape)
    }

    @OptIn(ExperimentalHazeApi::class)
    @Composable
    private fun glassSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean, navigationBar: Boolean): Modifier {
        val colors = AppTheme.colors
        val palette = palette()
        val accessibility = AppTheme.accessibility
        val source = LocalGlassSource.current
        if (accessibility.reduceTransparency || source == null) {
            return if (navigationBar) modifier.clip(shape).background(palette.containerHigh)
                .border(0.75.dp, palette.secondaryLabel.copy(alpha = 0.12f), shape)
            else modifier.seaDecor(shape, palette, glow = edges, base = colors.secondaryBackground)
        }
        val style = remember(shape, palette.hue, palette.dark, edges, navigationBar) {
            GlassStyle.regular.then {
                shape(shape)
                backgroundColor(colors.background)
                optics(
                    GlassDefaults.optics.copy(
                        depth = OpticalSizeValue.Fixed(1f),
                        // Stronger than the file's look: over busy covers the labels must stay legible.
                        blurRadius = OpticalSizeValue.Fixed(if (navigationBar) 22.dp else 36.dp),
                        refractionDisplacement = if (!edges) 0.dp else if (navigationBar) 12.dp else 14.dp,
                        refractionStrength = if (!edges) 0f else if (navigationBar) 0.35f else 0.42f,
                    ),
                )
                // Fill 2: the surface colour (33% in the file, raised for legibility); fill 1 (white,
                // luminosity) lifts it to pastel.
                // A tonal backing stabilizes label contrast; the stronger lens remains at the rim.
                tint(if (navigationBar) palette.containerHigh.copy(alpha = if (palette.dark) 0.80f else 0.86f)
                    else palette.vivid.copy(alpha = if (palette.dark) 0.55f else 0.5f))
                whitePoint(if (navigationBar) { if (palette.dark) -0.1f else 0.28f } else { if (palette.dark) -0.05f else 0.4f })
                chromaMultiplier(if (navigationBar) 1.05f else 1.15f)
                contentNormalBlend(0f)
                // The file's glass has no specular or reflection of its own: light comes from the
                // inner shadows drawn below.
                specularIntensity(if (navigationBar) 0.2f else 0f)
                ambientResponse(if (navigationBar) 0.12f else 0.08f)
                edgeShadow(Color.Transparent)
                chromaticAberrationStrength(if (!edges) 0f else if (navigationBar) 0.25f else 0.5f)
                pressed { lightingIntensity(0.35f) }
            }
        }
        val glass = modifier
            .seaGlow(shape, palette, enabled = edges && !navigationBar)
            .hazeGlass(input = HazeInput.Sources(source), style = style, interactionSource = interactionSource)
        return if (edges) glass.seaLight(shape, palette, quiet = navigationBar) else glass
    }

    @Composable
    override fun controlSurface(modifier: Modifier, shape: Shape, color: Color, prominent: Boolean): Modifier {
        // Content controls are clean tonal surfaces, as in Material 3: glass, glow and grain stay
        // in the navigation layer. The page's main action is the saturated primary.
        val fill = if (prominent) palette().primary else color
        return modifier.clip(shape).background(fill)
    }

    @Composable
    override fun selection(): Color =
        // "Selection inside the tab bar: white pill at 50%."
        Color.White.copy(alpha = if (AppTheme.colors.isDark) 0.16f else 0.5f)

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
    ) = MaterialListRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)

    override val usesInsetSettingsForm = false

    @Composable
    override fun SettingsPage(content: @Composable () -> Unit) = content()

    @Composable
    override fun SettingsPageTitle(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) =
        BackPageTitle(title, backLabel, onBack, modifier)

    @Composable
    override fun SettingsPageGroup(modifier: Modifier, content: @Composable () -> Unit) = SettingsGroup(content, modifier)

    @Composable
    override fun SettingsDurationField(value: String, onValueChange: (String) -> Unit, label: String, onSubmit: () -> Unit, modifier: Modifier) =
        dev.cmdrchara.songlass.theme.liquidglass.GlassFormField(value, onValueChange, label, true, true, modifier,
            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), KeyboardActions(onDone = { onSubmit() }))

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
    override fun SettingsPreviewChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, preview: @Composable () -> Unit) =
        MaterialSettingsPreviewChoice(title, selected, onClick, modifier, preview)

    @Composable
    override fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier) = MaterialSettingsGroup(content, modifier)

    // Pills everywhere and generously rounded cards (the file's cards use 24–32 px at 0.6 dp/px).
    override fun corner(base: Dp): Dp = base * 1.6f

    // Material 3 structure: a navigation bar that stays put, bottom sheets, back arrows, ripples
    // and M3 selection controls. The glass stays the file's.
    @Composable
    override fun navigationBarHeight(): Dp = with(androidx.compose.ui.platform.LocalDensity.current) {
        maxOf(72.dp, 44.dp + 16.sp.toDp())
    }

    override val minimizesOnScroll: Boolean get() = false
    override val bottomSheets: Boolean get() = true
    override val backIcon: ImageVector get() = AppIcons.ArrowBack

    @Composable
    override fun pressIndication(): Indication = MaterialRipple

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

/** Glass-like surface without backdrop: base fill plus the file's light, glow and grain. */
internal fun Modifier.seaDecor(shape: Shape, palette: SeaPalette, glow: Boolean, base: Color): Modifier =
    seaGlow(shape, palette, enabled = glow).clip(shape).background(base).seaLight(shape, palette)

/**
 * Coloured glow underneath (effect 6: `C'` at 70%, y 29, blur 8.9, spread −20, screen): a pale
 * rounded shape, inset and shifted down, softened by stacking a few fading copies. Only controls
 * between 56 dp wide and 96 dp tall glow: grouped surfaces would light up the page, and under a
 * small round icon button the halo reads as a drip.
 */
private fun Modifier.seaGlow(shape: Shape, palette: SeaPalette, enabled: Boolean): Modifier = drawBehind {
    // Small round buttons get none: under a 36 dp icon the halo reads as a drip.
    if (!enabled || size.height > 96.dp.toPx() || size.width < 56.dp.toPx()) return@drawBehind
    // Proportional to the control, so small round buttons get a small halo, not a drip.
    val inset = minOf(12.dp.toPx(), size.width * 0.18f)
    val drop = minOf(12.dp.toPx(), size.height * 0.2f)
    val steps = 6
    val spread = minOf(1.2.dp.toPx(), size.height * 0.03f)
    val radius = size.height / 2f
    val alpha = if (palette.dark) 0.22f else 0.6f
    for (i in steps downTo 0) {
        val grow = i * spread
        drawRoundRect(
            color = palette.glow.copy(alpha = alpha / (steps + 1)),
            topLeft = Offset(inset - grow, drop - grow),
            size = Size((size.width - inset * 2 + grow * 2).coerceAtLeast(0f), size.height + grow * 2),
            cornerRadius = CornerRadius(radius + grow),
        )
    }
}

/**
 * Light inside the surface: the soft-light white gradient, light from the top (inner shadow
 * white 25%, y 20, blur 29), a reflection from the bottom (white 17%, y −21, add), a thin lit
 * rim and grain (effects 7–8).
 */
private fun Modifier.seaLight(shape: Shape, palette: SeaPalette, quiet: Boolean = false): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val dark = palette.dark
    if (quiet) {
        drawPath(path, Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (dark) 0.20f else 0.52f), palette.secondaryLabel.copy(alpha = 0.07f))),
            style = Stroke(0.75.dp.toPx()))
        return@drawWithContent
    }
    clipPath(path) {
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.06f else 0.15f), 1f to Color.White.copy(alpha = if (dark) 0.03f else 0.11f)))
        val top = minOf(size.height * 0.6f, 30.dp.toPx())
        drawRect(
            Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.14f else 0.32f), 1f to Color.Transparent, endY = top),
            size = Size(size.width, top),
        )
        val bottom = minOf(size.height * 0.5f, 22.dp.toPx())
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.White.copy(alpha = if (dark) 0.10f else 0.22f), startY = size.height - bottom, endY = size.height),
            topLeft = Offset(0f, size.height - bottom),
            size = Size(size.width, bottom),
        )
        drawGrain(if (dark) 0.05f else 0.07f)
    }
    drawPath(
        path,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.35f else 0.85f), palette.vivid.copy(alpha = 0.18f))),
        style = Stroke(0.8.dp.toPx()),
    )
}

private fun DrawScope.drawGrain(alpha: Float) {
    drawRect(ShaderBrush(ImageShader(Grain.bitmap, TileMode.Repeated, TileMode.Repeated)), alpha = alpha)
}

/** Static noise tile (generated once, never per frame), the file's grain and duotone noise. */
private object Grain {
    val bitmap: ImageBitmap by lazy {
        val size = 96
        val image = ImageBitmap(size, size)
        val canvas = Canvas(image)
        val paint = Paint()
        val random = Random(7)
        for (y in 0 until size) for (x in 0 until size) {
            val v = random.nextFloat()
            paint.color = if (v > 0.5f) Color.White.copy(alpha = (v - 0.5f) * 1.6f) else Color.Black.copy(alpha = (0.5f - v) * 1.6f)
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
        }
        image
    }
}
