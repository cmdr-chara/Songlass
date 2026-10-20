package dev.cmdrchara.songlass.theme.liquidglass

import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.HomeHighlight
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Editorial music cards; a collection represents multiple records, not a random single album. */
@Composable
internal fun LiquidHomeHighlights(items: List<HomeHighlight>, itemWidth: Dp, modifier: Modifier) {
    // Keep a useful peek of the next recommendation, including on narrower phones.
    val width = minOf(itemWidth, 192.dp)
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.key }) { pick ->
            val action = pick.icon
            val interaction = remember { MutableInteractionSource() }
            val scale = pressScale(interaction)
            Column(Modifier.width(width).graphicsLayer { scaleX = scale; scaleY = scale }
                .prefetchArtworkOnPress(pick.artwork.takeIf { pick.icon == null })
                .clickable(interaction, indication = null, role = Role.Button, onClick = pick.onClick)) {
                AppText(pick.label, AppTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
                    color = AppTheme.colors.secondaryLabel, maxLines = 1,
                    modifier = Modifier.padding(start = 1.dp, bottom = 6.dp))
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
                    if (action == null) LiquidGlassAlbumArt(pick.artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
                    else {
                        LiquidCollectionMosaic(pick.artworks.ifEmpty { listOf(pick.artwork) })
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                            0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.34f))))
                        Box(Modifier.align(Alignment.BottomEnd).padding(12.dp).size(36.dp)
                            .clip(CircleShape).background(AppTheme.colors.background.copy(alpha = 0.94f)),
                            contentAlignment = Alignment.Center) {
                            AppIcon(action, null, size = 20.dp, tint = AppTheme.colors.accent)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                AppText(pick.title, AppTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
                pick.subtitle?.let { AppText(it, AppTheme.typography.footnote,
                    color = AppTheme.colors.secondaryLabel, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun LiquidCollectionMosaic(artworks: List<Artwork>) {
    if (artworks.size == 1) LiquidGlassAlbumArt(artworks.first(), cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
    else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(2) { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(2) { col ->
                    LiquidGlassAlbumArt(artworks[(row * 2 + col) % artworks.size], cornerRadius = 0.dp,
                        modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
