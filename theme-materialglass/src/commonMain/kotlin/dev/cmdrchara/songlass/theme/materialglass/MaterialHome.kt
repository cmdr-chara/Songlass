package dev.cmdrchara.songlass.theme.materialglass

import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.HomeHighlight
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

@Composable
internal fun MaterialPageTitle(text: String, trailing: (@Composable () -> Unit)?, modifier: Modifier) {
    Row(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
        .padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AppText(text, AppTheme.typography.largeTitle,
            modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

@Composable
internal fun MaterialBackPageTitle(text: String, backLabel: String, onBack: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        MaterialBackButton(backLabel, onBack, Modifier)
        AppText(text, AppTheme.typography.title2.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() }, maxLines = 2)
    }
}

/** The main collection is an action; an album recommendation is a separate, quieter content card. */
@Composable
internal fun MaterialHomeHighlights(items: List<HomeHighlight>, modifier: Modifier) {
    val palette = currentPalette()
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { pick -> key(pick.key) {
            val action = pick.icon
            val interaction = remember { MutableInteractionSource() }
            val background = if (action != null) palette.primaryContainer else palette.container
            val foreground = if (action != null) palette.onPrimaryContainer else palette.label
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (action != null) MaterialShapes.ExtraLargeIncreased else MaterialShapes.LargeIncreased)).background(background)
                .prefetchArtworkOnPress(pick.artwork.takeIf { pick.icon == null })
                .clickable(interaction, MaterialRipple, role = Role.Button, onClick = pick.onClick)
                .padding(if (action != null) 16.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (action == null) AlbumArt(pick.artwork, Modifier.size(96.dp), cornerRadius = 18.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (action != null) {
                        Box(Modifier.size(40.dp).clip(ExpressiveShapes.Cookie).background(palette.primary),
                            contentAlignment = Alignment.Center) {
                            AppIcon(action, null, size = 24.dp, tint = palette.onPrimary)
                        }
                        Spacer(Modifier.height(4.dp))
                    } else AppText(pick.label, AppTheme.typography.caption1,
                        color = palette.secondaryLabel, maxLines = 1)
                    AppText(pick.title, if (action != null) AppTheme.typography.title2
                        else AppTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = foreground, maxLines = 2)
                    pick.subtitle?.let { AppText(it, AppTheme.typography.footnote,
                        color = if (action != null) foreground else palette.secondaryLabel, maxLines = 2) }
                }
                if (action != null) MaterialCollectionMosaic(pick.artworks.ifEmpty { listOf(pick.artwork) },
                    Modifier.size(88.dp).clip(RoundedCornerShape(MaterialShapes.LargeIncreased)))
                else AppIcon(AppIcons.ChevronRight, null, size = 16.dp, tint = palette.secondaryLabel)
            }
        } }
    }
}

@Composable
private fun MaterialCollectionMosaic(artworks: List<Artwork>, modifier: Modifier) {
    if (artworks.size == 1) AlbumArt(artworks.first(), modifier, cornerRadius = 0.dp)
    else Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(2) { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { col -> AlbumArt(artworks[(row * 2 + col) % artworks.size],
                    Modifier.weight(1f).fillMaxHeight(), cornerRadius = 0.dp) }
            }
        }
    }
}
