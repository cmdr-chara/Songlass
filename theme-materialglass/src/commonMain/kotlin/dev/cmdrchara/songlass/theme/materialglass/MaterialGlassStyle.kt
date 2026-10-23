package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** Material Glass components: a wavy seek bar, pill transport and rows as cards (see [MaterialLook]). */
@Composable
internal fun MaterialTransportControls(
    isPlaying: Boolean,
    labels: PlaybackLabels,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportPill(AppIcons.Previous, labels.previous, onPrevious, width = 84.dp, prominent = false, playing = false)
        TransportPill(
            icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
            description = if (isPlaying) labels.pause else labels.play,
            onClick = onPlayPause,
            width = 124.dp,
            prominent = true,
            playing = isPlaying,
        )
        TransportPill(AppIcons.Next, labels.next, onNext, width = 84.dp, prominent = false, playing = false)
    }
}

@Composable
internal fun MaterialSettingsGroup(content: @Composable () -> Unit, modifier: Modifier) {
    CompositionLocalProvider(LocalInsideGroup provides true) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(currentPalette().container).padding(vertical = 4.dp)) { content() }
    }
}

@Composable
internal fun MaterialListRow(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) = MaterialContentRow(title, subtitle, leading, trailing, highlighted, onClick, modifier, LocalInsideGroup.current)

/** True inside a grouped surface, where rows must not become cards. */
private val LocalInsideGroup = staticCompositionLocalOf { false }

/** The current [SeaPalette], as the Material look sees it where this is drawn. */
@Composable
internal fun currentPalette(): SeaPalette {
    val look = LocalGlassLook.current
    return if (look is MaterialLook) look.palette() else SeaPalette(275f, AppTheme.colors.isDark)
}

/**
 * Pill transport button. The play button is wider and in the vivid surface colour; while music
 * plays it squares off a little, as expressive buttons change shape with their state.
 */
@Composable
private fun TransportPill(icon: ImageVector, description: String, onClick: () -> Unit, width: Dp, prominent: Boolean, playing: Boolean) {
    val palette = currentPalette()
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.93f else 1f,
        if (reduceMotion) snap() else MaterialMotion.press(),
        label = "transportPress",
    )
    val corner by animateDpAsState(
        if (playing) 26.dp else 36.dp,
        if (reduceMotion) snap() else MaterialMotion.spatial(),
        label = "transportShape",
    )
    val fill = if (prominent) palette.primary else palette.primaryContainer
    val iconTint = if (prominent) palette.onPrimary else palette.onPrimaryContainer
    Box(
        Modifier
            .size(width = width, height = 72.dp)
            .graphicsLayer { scaleX = scale.coerceAtMost(1f); scaleY = scale.coerceAtMost(1f) }
            .clip(RoundedCornerShape(corner))
            .background(fill)
            .clickable(interaction, indication = MaterialRipple, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = description, size = if (prominent) 34.dp else 28.dp, tint = iconTint)
    }
}

/** Content lists stay quiet; only the active row receives a tonal container. */
@Composable
private fun MaterialContentRow(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    grouped: Boolean,
) {
    val palette = currentPalette()
    val content = if (highlighted) palette.onPrimaryContainer else LocalContentColor.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = if (grouped) 0.dp else 8.dp, vertical = if (grouped) 0.dp else 2.dp)
            .clip(RoundedCornerShape(if (grouped) 0.dp else MaterialShapes.Medium))
            .background(if (highlighted) palette.primaryContainer else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = MaterialRipple, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = if (subtitle != null) 64.dp else 56.dp)
            .padding(horizontal = if (grouped) 16.dp else 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            AppText(title, AppTheme.typography.body, color = content, maxLines = 2)
            if (subtitle != null) AppText(subtitle, AppTheme.typography.subheadline,
                color = if (highlighted) palette.onPrimaryContainer else palette.secondaryLabel, maxLines = 2)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
