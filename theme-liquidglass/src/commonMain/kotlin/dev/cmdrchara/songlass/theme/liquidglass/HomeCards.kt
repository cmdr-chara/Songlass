package dev.cmdrchara.songlass.theme.liquidglass

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
                    .clip(RoundedCornerShape(16.dp))
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
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.secondaryBackground)
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
