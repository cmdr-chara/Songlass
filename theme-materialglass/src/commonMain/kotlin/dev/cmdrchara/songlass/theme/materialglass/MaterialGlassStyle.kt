package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.core.animateDpAsState
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
import dev.cmdrchara.songlass.theme.liquidglass.LiquidLook
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
internal fun MaterialSettingsGroup(content: @Composable () -> Unit, modifier: Modifier) =
    // Rows inside a group are part of its card, not cards of their own.
    LiquidLook.SettingsGroup({ CompositionLocalProvider(LocalInsideGroup provides true, content = content) }, modifier)

@Composable
internal fun MaterialListRow(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    if (LocalInsideGroup.current) LiquidLook.ListRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)
    else CardRow(title, subtitle, leading, trailing, highlighted, onClick, modifier)
}

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
        if (reduceMotion) snap() else spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "transportPress",
    )
    val corner by animateDpAsState(
        if (playing) 26.dp else 36.dp,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "transportShape",
    )
    val fill = if (prominent) palette.primary else palette.primaryContainer
    val iconTint = if (prominent) palette.onPrimary else palette.onPrimaryContainer
    Box(
        Modifier
            .size(width = width, height = 72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .background(fill)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = description, size = if (prominent) 34.dp else 28.dp, tint = iconTint)
    }
}

/** A list row as its own rounded card, the current song lit in the surface colour. */
@Composable
private fun CardRow(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val palette = currentPalette()
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.98f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "cardRowPress",
    )
    val fill = if (highlighted) palette.primaryContainer else palette.container
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .heightIn(min = if (subtitle != null) 64.dp else 48.dp)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            val content = LocalContentColor.current
            AppText(title, AppTheme.typography.body, color = if (highlighted) palette.onPrimaryContainer else content, maxLines = 1)
            if (subtitle != null) {
                AppText(subtitle, AppTheme.typography.subheadline, color = content.copy(alpha = 0.6f), maxLines = 1)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
