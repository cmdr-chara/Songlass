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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
        Box(modifier.background(tint.background)) { content() }
    }
}

/**
 * The cover fills the width and fades into the page in its lower part, so title and actions
 * read as part of the artwork. The gradient uses the page background (the artwork's tint).
 */
@Composable
internal fun LiquidGlassArtworkHeader(artwork: Artwork, contentDescription: String?, modifier: Modifier) {
    if (LocalArtworkPresentation.current == ArtworkPresentation.Square) {
        // Rounded card, as on the classic album page.
        Box(
            modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 52.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassAlbumArt(
                artwork,
                cornerRadius = 12.dp,
                modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f)
                    .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
            )
        }
        return
    }
    val background = AppTheme.colors.background
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        val image = rememberArtworkImage(artwork, with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(LARGE_ARTWORK_PX))
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            LiquidGlassAlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.4f to Color.Transparent,
                    0.55f to background.copy(alpha = 0.18f),
                    0.7f to background.copy(alpha = 0.5f),
                    0.82f to background.copy(alpha = 0.8f),
                    0.93f to background.copy(alpha = 0.97f),
                    1f to background,
                ),
            ),
        )
    }
}

@Composable
internal fun LiquidGlassTag(text: String, modifier: Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(AppTheme.colors.fill)
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
            .clip(CircleShape)
            .background(AppTheme.colors.accent)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription, size = 26.dp, tint = AppTheme.colors.background)
    }
}

@Composable
internal fun LiquidGlassFloatingBackButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier) =
    FloatingCircleButton(dev.cmdrchara.songlass.designsystem.icons.AppIcons.ChevronLeft, contentDescription, onClick, modifier)

/** 44 dp target; the visible circle uses the page background so it reads over any cover. */
@Composable
internal fun FloatingCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .size(44.dp)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.background.copy(alpha = if (pressed) 0.6f else 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription, size = 22.dp, tint = AppTheme.colors.label)
        }
    }
}

/** Featured card: the artwork's tint as background, title and subtitle, cover on the right. */
@Composable
internal fun LiquidGlassHighlightCard(title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier) {
    val tint = animateArtworkTint(rememberArtworkTint(artwork, AppTheme.colors.isDark))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.97f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "highlightPress",
    )
    ProvideArtworkTint(tint) {
        androidx.compose.foundation.layout.Row(
            modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .background(tint.background)
                .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                AppText(title, AppTheme.typography.title3.copy(fontWeight = FontWeight.Bold), maxLines = 2)
                if (subtitle != null) {
                    AppText(subtitle, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 2)
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            LiquidGlassAlbumArt(artwork, cornerRadius = 12.dp, modifier = Modifier.size(84.dp))
        }
    }
}
