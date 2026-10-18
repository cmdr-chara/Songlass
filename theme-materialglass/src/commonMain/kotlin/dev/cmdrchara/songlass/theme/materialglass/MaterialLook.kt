package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
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

    /**
     * The palette where the surface is drawn: on pages tinted by a cover (album, folder, player)
     * the surrounding colours carry that cover's hue, so the glass matches the page; elsewhere it
     * is the hue of what is playing.
     */
    @Composable
    internal fun palette(): SeaPalette {
        val colors = AppTheme.colors
        return remember(colors.accent, colors.isDark, base.hue) {
            val accent = Hsl.of(colors.accent)
            SeaPalette(if (accent.s > 0.12f) accent.h else base.hue, colors.isDark)
        }
    }

    @OptIn(ExperimentalHazeApi::class)
    @Composable
    override fun navigationSurface(modifier: Modifier, shape: RoundedCornerShape, interactionSource: InteractionSource?, edges: Boolean): Modifier {
        val colors = AppTheme.colors
        val palette = palette()
        val accessibility = AppTheme.accessibility
        val source = LocalGlassSource.current
        if (accessibility.reduceTransparency || source == null) {
            return modifier.seaDecor(shape, palette, glow = edges, base = colors.secondaryBackground)
        }
        val style = remember(shape, palette.hue, palette.dark, edges) {
            GlassStyle.regular.then {
                shape(shape)
                backgroundColor(colors.background)
                optics(
                    GlassDefaults.optics.copy(
                        depth = OpticalSizeValue.Fixed(1f),
                        blurRadius = OpticalSizeValue.Fixed(24.dp),
                        refractionDisplacement = if (edges) 14.dp else 0.dp,
                        refractionStrength = if (edges) 0.42f else 0f,
                    ),
                )
                // Fill 2: the surface colour at 33%; fill 1 (white, luminosity) lifts it to pastel.
                tint(palette.vivid.copy(alpha = 0.33f))
                whitePoint(if (palette.dark) -0.05f else 0.4f)
                chromaMultiplier(1.15f)
                contentNormalBlend(0f)
                // The file's glass has no specular or reflection of its own: light comes from the
                // inner shadows drawn below.
                specularIntensity(0f)
                ambientResponse(0.08f)
                edgeShadow(Color.Transparent)
                chromaticAberrationStrength(if (edges) 0.5f else 0f)
                pressed { lightingIntensity(0.35f) }
            }
        }
        val glass = modifier
            .seaGlow(shape, palette, enabled = edges)
            .hazeGlass(input = HazeInput.Sources(source), style = style, interactionSource = interactionSource)
        return if (edges) glass.seaLight(shape, palette) else glass
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

    @Composable
    override fun SettingsGroup(content: @Composable () -> Unit, modifier: Modifier) = MaterialSettingsGroup(content, modifier)

    // Pills everywhere and generously rounded cards (the file's cards use 24–32 px at 0.6 dp/px).
    override fun corner(base: Dp): Dp = base * 1.6f

    // Material 3 structure: a navigation bar that stays put, bottom sheets, back arrows, ripples
    // and M3 selection controls. The glass stays the file's.
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
private fun Modifier.seaLight(shape: Shape, palette: SeaPalette): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val dark = palette.dark
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
