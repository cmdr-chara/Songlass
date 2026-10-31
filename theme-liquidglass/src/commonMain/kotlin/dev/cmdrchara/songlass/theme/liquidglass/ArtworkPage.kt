package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.LARGE_ARTWORK_PX
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkImage
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkPresentation
import dev.cmdrchara.songlass.designsystem.foundation.LocalArtworkPresentation
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkTint
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

@Composable
internal fun LiquidGlassArtworkTintedPage(artwork: Artwork, modifier: Modifier, content: @Composable () -> Unit) {
    val fullBleed = LocalArtworkPresentation.current == ArtworkPresentation.FullBleed
    val tint = animateArtworkTint(rememberArtworkTint(artwork, AppTheme.colors.isDark, followArtwork = fullBleed))
    ProvideArtworkTint(tint) {
        CollectionArtworkViewport(modifier.background(tint.background), content)
    }
}

private val LocalCollectionViewport = staticCompositionLocalOf<DpSize?> { null }

/** Collection headers size against the measured page, including after a window resize. */
@Composable
fun CollectionArtworkViewport(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalCollectionViewport provides DpSize(maxWidth, maxHeight), content = content)
    }
}

@Composable
private fun collectionViewport(): DpSize {
    LocalCollectionViewport.current?.let { return it }
    val window = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(window.width.toDp(), window.height.toDp()) }
}

/** Keep collection art in proportion to a compact window, independently of text scaling. */
@Composable
fun collectionArtworkHeightBudget(): Dp = (collectionViewport().height * 0.28f).coerceIn(136.dp, 260.dp)

private val CollectionArtworkFadeStops = arrayOf(
    0f to 0f,
    0.4f to 0f,
    0.55f to 0.18f,
    0.7f to 0.5f,
    0.82f to 0.8f,
    0.93f to 0.97f,
    1f to 1f,
)

/** The same opacity used by the header brush, at a measured position within that header. */
fun collectionArtworkFadeAlpha(fraction: Float): Float {
    val position = fraction.coerceIn(0f, 1f)
    val upper = CollectionArtworkFadeStops.indexOfFirst { position <= it.first }
    if (upper <= 0) return CollectionArtworkFadeStops.first().second
    val (start, startAlpha) = CollectionArtworkFadeStops[upper - 1]
    val (end, endAlpha) = CollectionArtworkFadeStops[upper]
    return startAlpha + (endAlpha - startAlpha) * ((position - start) / (end - start))
}

/**
 * The cover fills the width and fades into the page in its lower part, so title and actions
 * read as part of the artwork. The gradient uses the page background (the artwork's tint).
 */
@Composable
internal fun LiquidGlassArtworkHeader(artwork: Artwork, contentDescription: String?, modifier: Modifier) {
    if (LocalArtworkPresentation.current == ArtworkPresentation.Square) {
        // Rounded card, as on the classic album page.
        BoxWithConstraints(
            modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 52.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassAlbumArt(
                artwork,
                cornerRadius = 12.dp,
                modifier = Modifier.size(minOf(maxWidth * 0.72f, LocalGlassLook.current.collectionArtworkHeight()))
                    .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
            )
        }
        return
    }
    val background = AppTheme.colors.background
    val viewport = collectionViewport()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(minOf(viewport.width, viewport.height * 0.40f))
            .clipToBounds()
            .drawWithCache {
                val fade = Brush.verticalGradient(
                    *Array(CollectionArtworkFadeStops.size) { index ->
                        val (position, alpha) = CollectionArtworkFadeStops[index]
                        position to when (alpha) {
                            0f -> Color.Transparent
                            1f -> background
                            else -> background.copy(alpha = alpha)
                        }
                    },
                    endY = (size.height - 1f).coerceAtLeast(1f),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(fade)
                }
            }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        val image = rememberArtworkImage(artwork, with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(LARGE_ARTWORK_PX))
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            LiquidGlassAlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun LiquidGlassTag(text: String, modifier: Modifier) {
    Box(
        LocalGlassLook.current.controlSurface(modifier, CircleShape, AppTheme.colors.fill, prominent = false)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        AppText(text, AppTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.secondaryLabel, maxLines = 1)
    }
}

/** 56 dp solid circle in the accent with the icon in the background color. */
@Composable
internal fun LiquidGlassProminentButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.92f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "prominentPress",
    )
    Box(
        modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .let { LocalGlassLook.current.controlSurface(it, CircleShape, AppTheme.colors.accent, prominent = true) }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription, size = 26.dp, tint = AppTheme.colors.background)
    }
}

@Composable
internal fun LiquidGlassFloatingBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier) =
    FloatingCircleButton(LocalGlassLook.current.backIcon, contentDescription, onClick, modifier)

/** 44 dp target; the visible circle uses the page background so it reads over any cover. */
@Composable
internal fun FloatingCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val scale by animateFloatAsState(if (pressed && !reduceMotion) 0.9f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f), label = "floatingBackPress")
    Box(
        modifier
            .size(44.dp)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(AppTheme.colors.background.copy(alpha = if (pressed) 0.6f else 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription, size = 22.dp, tint = AppTheme.colors.label)
        }
    }
}
