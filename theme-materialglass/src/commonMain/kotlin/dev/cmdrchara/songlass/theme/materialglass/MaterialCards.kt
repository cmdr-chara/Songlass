package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

// Material 3 Expressive cards for Home and Library: tonal containers, large corners, covers in
// expressive shapes, ripples. The Liquid versions keep Apple Music's layout.

/** "For you" card: the cover fills the card, the reason sits in a chip, the title on the cover. */
@Composable
internal fun MaterialPosterCard(label: String, title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    Box(
        modifier
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.container)
            .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = onClick),
    ) {
        AlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )
        AppText(
            label,
            AppTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
            color = palette.onPrimaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(12.dp).clip(CircleShape).background(palette.primaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
            AppText(title, AppTheme.typography.title2.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 2)
            if (subtitle != null) AppText(subtitle, AppTheme.typography.subheadline, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

/** "Jump back in" shortcut as a tonal card with an inset cover (artists in a scalloped shape). */
@Composable
internal fun MaterialShortcutTile(title: String, artwork: Artwork, circular: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    Row(
        modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(MaterialShapes.Medium))
            .background(palette.container)
            .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(if (circular) ExpressiveShapes.Cookie else RoundedCornerShape(14.dp))) {
            AlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(10.dp))
        AppText(title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium), maxLines = 2, modifier = Modifier.weight(1f).padding(end = 4.dp))
    }
}

/** Compact category surface: icon and count above a naturally measured, two-line title. */
@Composable
internal fun MaterialCollectionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    artworks: List<Artwork>,
    circular: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val palette = currentPalette()
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(MaterialShapes.Large))
            .background(palette.container)
            .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(MaterialShapes.Medium)).background(palette.primaryContainer),
                contentAlignment = Alignment.Center) {
                AppIcon(icon, contentDescription = null, size = 24.dp, tint = palette.onPrimaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            AppText(subtitle, AppTheme.typography.subheadline, color = palette.secondaryLabel,
                modifier = Modifier.weight(1f), maxLines = 2)
        }
        Spacer(Modifier.height(8.dp))
        AppText(title, AppTheme.typography.headline, color = palette.label, maxLines = 2)
    }
}
