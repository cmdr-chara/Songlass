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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/**
 * Apple Music collection details: type tag, bold title, then the artist and details beside
 * Shuffle and a large round Play, so the actions don't need a row of their own.
 */
@Composable
internal fun LiquidGlassCollectionDetails(
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
) {
    val look = LocalGlassLook.current
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        look.Tag(kind, Modifier)
        Spacer(Modifier.height(8.dp))
        AppText(title, AppTheme.typography.title1.copy(fontWeight = FontWeight.Bold), maxLines = 3)
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (subtitle != null) {
                    AppText(
                        subtitle,
                        AppTheme.typography.headline,
                        color = AppTheme.colors.secondaryLabel,
                        maxLines = 1,
                        modifier = if (onSubtitleClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onSubtitleClick).padding(vertical = 4.dp)
                        } else Modifier.padding(vertical = 4.dp),
                    )
                }
                AppText(meta, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
            }
            Spacer(Modifier.width(8.dp))
            action()
            look.IconButton(AppIcons.Shuffle, shuffleLabel, onShuffle, false, true, Modifier)
            Spacer(Modifier.width(12.dp))
            LiquidGlassProminentButton(AppIcons.Play, playLabel, onPlay, Modifier)
        }
    }
}

/**
 * Scope pills, as on Apple Music's search: the selected one filled in the accent, the others on
 * the grey fill; a short press shrinks a pill slightly.
 */
@Composable
internal fun LiquidGlassFilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.accessibility.reduceMotion
    LazyRow(
        modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options) { index, option ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed && !reduceMotion) 0.95f else 1f, if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f), label = "chipPress")
            Box(
                Modifier
                    .height(34.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(if (selected) colors.accent else colors.fill)
                    .selectable(selected, interaction, indication = null, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    option,
                    AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) colors.background else colors.label,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Round artist picture, as in Apple Music. */
@Composable
internal fun LiquidGlassArtistAvatar(artwork: Artwork, modifier: Modifier) {
    Box(modifier.clip(CircleShape)) {
        LiquidGlassAlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
    }
}
