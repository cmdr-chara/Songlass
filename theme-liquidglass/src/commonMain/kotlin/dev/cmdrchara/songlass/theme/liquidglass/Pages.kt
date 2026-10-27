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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
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
import androidx.compose.ui.platform.LocalDensity
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/**
 * Music collection hierarchy: quiet type/action line, centred metadata, then labelled transport.
 * Independent Compose layout, informed by visual references rather than imported UI code.
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
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppText(kind, AppTheme.typography.footnote, color = colors.secondaryLabel, modifier = Modifier.weight(1f))
            action()
        }
        AppText(title, AppTheme.typography.title2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), maxLines = 4)
        if (!subtitle.isNullOrBlank()) AppText(subtitle, AppTheme.typography.body,
            color = if (onSubtitleClick != null) colors.accent else colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .then(if (onSubtitleClick != null) Modifier.heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onSubtitleClick) else Modifier)
                .padding(vertical = 8.dp))
        if (meta.isNotBlank()) AppText(meta, AppTheme.typography.footnote, color = colors.secondaryLabel,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidCollectionAction(playLabel, AppIcons.Play, true, onPlay, Modifier.weight(1f))
            LiquidCollectionAction(shuffleLabel, AppIcons.Shuffle, false, onShuffle, Modifier.weight(1f))
        }
    }
}

/** Two labelled actions stay readable without competing with collection metadata. */
@Composable
private fun LiquidCollectionAction(text: String, icon: ImageVector, prominent: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(interaction)
    val surface = if (prominent) colors.accent else colors.fill
    val foreground = if (prominent) colors.background else colors.accent
    val shape = RoundedCornerShape(16.dp)
    // Keep the hit target and keyboard focus stationary while only the face responds.
    Box(modifier.heightIn(min = 48.dp).liquidFocusIndicator(interaction, shape)
        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(surface.copy(alpha = if (pressed) .7f else surface.alpha))
            .padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, null, size = 19.dp, tint = foreground)
            Spacer(Modifier.width(8.dp))
            AppText(text, AppTheme.typography.headline, color = foreground, modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
        }
    }
}

/**
 * Scope pills, as on Apple Music's search: the selected one carries an accent label on
 * a quiet neutral surface; a short press shrinks a pill slightly.
 */
@Composable
internal fun LiquidGlassFilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.accessibility.reduceMotion
    LazyRow(
        modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = LocalGlassLook.current.contentHorizontalInset),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options) { index, option ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed && !reduceMotion) 0.95f else 1f, if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f), label = "chipPress")
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .selectable(selected, interaction, indication = null, role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 5.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.fill else colors.secondaryBackground)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    option,
                    AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) colors.accent else colors.secondaryLabel,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Small ordering choices wrap with larger text rather than introducing a horizontal strip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiquidGlassSortOptions(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val look = LocalGlassLook.current
    val shape = RoundedCornerShape(look.corner(16.dp))
    FlowRow(modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, option ->
            val selected = selectedIndex == index
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .selectable(selected, interaction, indication = look.pressIndication(), role = Role.RadioButton) { onSelect(index) }
                .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Box(look.controlSurface(Modifier.fillMaxWidth(), shape,
                    if (selected) colors.accent.copy(alpha = 0.15f) else colors.fill, prominent = false)
                    .padding(horizontal = 12.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                    AppText(option, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) colors.accent else colors.label)
                }
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
