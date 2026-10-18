package dev.cmdrchara.songlass.theme.liquidglass

import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.ShelfItem
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkTint
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Slight shrink while pressed, the HIG's feedback for tappable content. */
@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.97f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "cardPress",
    )
    return scale
}

/** Top Picks–style card: reason label, cover on top, title in a band of the cover's tint. */
@Composable
internal fun LiquidGlassPosterCard(label: String, title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier) {
    val tint = animateArtworkTint(rememberArtworkTint(artwork, AppTheme.colors.isDark))
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Column(modifier) {
        AppText(
            label,
            AppTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
            color = AppTheme.colors.secondaryLabel,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        ProvideArtworkTint(tint) {
            Column(
                Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(LocalGlassLook.current.corner(16.dp)))
                    .background(tint.background)
                    .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    LiquidGlassAlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
                    // The cover melts into the band below, so the card reads as one piece.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, tint.background))),
                    )
                }
                Column(
                    Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppText(title, AppTheme.typography.headline.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    if (subtitle != null) {
                        AppText(subtitle, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** "Jump back in" shortcut: cover at the start and the title on a filled rounded surface. */
@Composable
internal fun LiquidGlassShortcutTile(title: String, artwork: Artwork, circular: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Row(
        modifier
            .height(ShortcutHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .let { LocalGlassLook.current.controlSurface(it, RoundedCornerShape(LocalGlassLook.current.corner(10.dp)), AppTheme.colors.secondaryBackground, prominent = false) }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (circular) {
            LiquidGlassAlbumArt(artwork, cornerRadius = 22.dp, modifier = Modifier.padding(6.dp).size(44.dp))
        } else {
            LiquidGlassAlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.size(ShortcutHeight))
        }
        AppText(
            title,
            AppTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

private val ShortcutHeight = 56.dp

/**
 * Library category tile: title, count and icon over a tonal card, with up to three covers fanned
 * out from the bottom right corner like records pulled from a shelf.
 */
@Composable
internal fun LiquidGlassCollectionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    artworks: List<Artwork>,
    circular: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    val look = LocalGlassLook.current
    val colors = AppTheme.colors
    Box(
        modifier
            .height(CollectionTileHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .let { look.controlSurface(it, RoundedCornerShape(look.corner(18.dp)), colors.secondaryBackground, prominent = false) }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        val covers = artworks.take(3)
        covers.forEachIndexed { index, artwork ->
            // Back to front: the last cover is the most visible.
            val i = covers.size - 1 - index
            val size = 54.dp - 4.dp * i
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp + 30.dp * i, bottom = 12.dp + 6.dp * i)
                    .graphicsLayer { rotationZ = if (circular) 0f else (8f - i * 9f) }
                    .shadow(4.dp, if (circular) CircleShape else RoundedCornerShape(8.dp))
                    .size(size),
            ) {
                LiquidGlassAlbumArt(artwork, cornerRadius = if (circular) size / 2 else 8.dp, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(14.dp)) {
            AppIcon(icon, contentDescription = null, size = 22.dp, tint = colors.accent)
            Spacer(Modifier.height(8.dp))
            AppText(title, AppTheme.typography.headline.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            AppText(subtitle, AppTheme.typography.footnote, color = colors.secondaryLabel, maxLines = 1)
        }
    }
}

private val CollectionTileHeight = 132.dp

/**
 * Apple Music shelf: covers in a row with the next one peeking, title and subtitle below.
 * Pins are smaller with a centred title; artists are round; actions show their icon on a tile.
 */
@Composable
internal fun LiquidGlassArtworkShelf(items: List<ShelfItem>, style: ShelfStyle, itemSize: Dp, modifier: Modifier) {
    val pins = style == ShelfStyle.Pins
    val small = itemSize < 130.dp
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(if (small) 12.dp else 14.dp)) {
        items(items, key = { it.key }) { item ->
            val interaction = remember { MutableInteractionSource() }
            val scale = pressScale(interaction)
            val corner = when {
                item.circular -> itemSize / 2
                pins -> 14.dp
                small -> 12.dp
                else -> 10.dp
            }
            Column(
                Modifier
                    .width(itemSize)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clickable(interaction, indication = null, role = Role.Button, onClick = item.onClick),
                horizontalAlignment = if (pins || item.icon != null) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                val artwork = item.artwork
                val subtitle = item.subtitle
                if (item.icon != null || artwork == null) {
                    Box(
                        Modifier.size(itemSize).clip(RoundedCornerShape(corner)).background(AppTheme.colors.secondaryBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        item.icon?.let { AppIcon(it, contentDescription = null, size = 32.dp, tint = AppTheme.colors.accent) }
                    }
                } else {
                    LiquidGlassAlbumArt(artwork, cornerRadius = corner, modifier = Modifier.size(itemSize))
                }
                Spacer(Modifier.height(6.dp))
                val titleStyle = if (pins || small) AppTheme.typography.footnote else AppTheme.typography.subheadline
                AppText(item.title, titleStyle.copy(fontWeight = FontWeight.Medium), maxLines = 1, textAlign = if (pins || item.icon != null) TextAlign.Center else TextAlign.Start)
                if (!pins && subtitle != null) {
                    AppText(subtitle, if (small) AppTheme.typography.caption1 else AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
                }
            }
        }
    }
}
