package dev.cmdrchara.songlass.theme.liquidglass

import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val actions = items.filter { it.icon != null }
    val recommendations = items.filter { it.icon == null }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        actions.forEach { pick ->
            androidx.compose.runtime.key(pick.key) {
                val interaction = remember { MutableInteractionSource() }
                val scale = pressScale(interaction)
                Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.secondaryBackground)
                    .clickable(interaction, indication = null, role = Role.Button, onClick = pick.onClick)
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) {
                        LiquidCollectionMosaic(pick.artworks.ifEmpty { listOf(pick.artwork) })
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        AppText(pick.title, AppTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
                        pick.subtitle?.let { AppText(it, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 2) }
                    }
                    pick.icon?.let { AppIcon(it, null, size = 24.dp, tint = AppTheme.colors.accent) }
                }
            }
        }
        if (recommendations.size == 1) {
            val pick = recommendations.single()
            val interaction = remember { MutableInteractionSource() }
            val scale = pressScale(interaction)
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .prefetchArtworkOnPress(pick.artwork)
                .clickable(interaction, indication = null, role = Role.Button, onClick = pick.onClick),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LiquidGlassAlbumArt(pick.artwork, cornerRadius = 8.dp, modifier = Modifier.size(88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(pick.label, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
                    AppText(pick.title, AppTheme.typography.headline, maxLines = 2)
                    pick.subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 2) }
                }
            }
        } else if (recommendations.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(recommendations, key = { it.key }) { pick ->
                val interaction = remember { MutableInteractionSource() }
                val scale = pressScale(interaction)
                Column(Modifier.width(minOf(itemWidth, 192.dp)).graphicsLayer { scaleX = scale; scaleY = scale }
                    .prefetchArtworkOnPress(pick.artwork)
                    .clickable(interaction, indication = null, role = Role.Button, onClick = pick.onClick)) {
                    AppText(pick.label, AppTheme.typography.footnote.copy(fontWeight = FontWeight.Medium),
                        color = AppTheme.colors.secondaryLabel, maxLines = 1,
                        modifier = Modifier.padding(start = 1.dp, bottom = 6.dp))
                    LiquidGlassAlbumArt(pick.artwork, cornerRadius = 10.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.height(8.dp))
                    AppText(pick.title, AppTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
                    pick.subtitle?.let { AppText(it, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1) }
                }
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
