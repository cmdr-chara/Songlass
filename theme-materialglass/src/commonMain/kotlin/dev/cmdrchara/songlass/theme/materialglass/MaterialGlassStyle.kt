package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.SettingsGroupPosition
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** Material transport distinguishes the primary action by shape as well as tonal emphasis. */
@Composable
internal fun MaterialTransportControls(
    isPlaying: Boolean,
    labels: PlaybackLabels,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Reserve both side buttons symmetrically before measuring the prominent control.
        val gap = minOf(12.dp, ((maxWidth - 144.dp) / 2).coerceAtLeast(0.dp))
        val buttonsWidth = minOf(232.dp, maxWidth - gap * 2)
        val sideWidth = (buttonsWidth * (64f / 232f)).coerceAtLeast(48.dp)
        val playWidth = (buttonsWidth - sideWidth * 2).coerceAtLeast(48.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(AppIcons.Previous, labels.previous, onPrevious, width = sideWidth, prominent = false, playing = false)
            TransportButton(
                icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                description = if (isPlaying) labels.pause else labels.play,
                onClick = onPlayPause,
                width = playWidth,
                prominent = true,
                playing = isPlaying,
            )
            TransportButton(AppIcons.Next, labels.next, onNext, width = sideWidth, prominent = false, playing = false)
        }
    }
}

@Composable
internal fun MaterialSettingsGroup(content: @Composable () -> Unit, modifier: Modifier) {
    CompositionLocalProvider(LocalInsideGroup provides true) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(currentPalette().container).padding(vertical = 4.dp)) { content() }
    }
}

/** Adjacent lazy items share one settings surface without repeating their outside corners. */
@Composable
internal fun MaterialSettingsGroupFragment(position: SettingsGroupPosition, modifier: Modifier, content: @Composable () -> Unit) {
    val first = position == SettingsGroupPosition.Single || position == SettingsGroupPosition.First
    val last = position == SettingsGroupPosition.Single || position == SettingsGroupPosition.Last
    CompositionLocalProvider(LocalInsideGroup provides true) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(
            topStart = if (first) 24.dp else 0.dp, topEnd = if (first) 24.dp else 0.dp,
            bottomStart = if (last) 24.dp else 0.dp, bottomEnd = if (last) 24.dp else 0.dp,
        )).background(currentPalette().container)
            .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)) { content() }
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
 * The primary rounded rectangle changes shape with playback and press state. Skip controls
 * stay circular and quieter. Only the visual surface scales, keeping the hit target stable.
 */
@Composable
private fun TransportButton(icon: ImageVector, description: String, onClick: () -> Unit, width: Dp, prominent: Boolean, playing: Boolean) {
    val palette = currentPalette()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.96f else 1f,
        if (reduceMotion) snap() else MaterialMotion.press(),
        label = "transportPress",
    )
    val corner by animateDpAsState(
        when {
            pressed && !reduceMotion -> MaterialShapes.Large
            playing -> MaterialShapes.LargeIncreased
            else -> MaterialShapes.ExtraLarge
        },
        if (reduceMotion) snap() else MaterialMotion.spatial(),
        label = "transportShape",
    )
    val fill = if (prominent) palette.primary else palette.primaryContainer
    val iconTint = if (prominent) palette.onPrimary else palette.onPrimaryContainer
    val shape = if (prominent) RoundedCornerShape(corner) else CircleShape
    Box(
        Modifier
            .size(width = width, height = if (prominent) 72.dp else width)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides iconTint) {
            Box(Modifier.fillMaxSize()
                .graphicsLayer { scaleX = scale.coerceIn(0.9f, 1f); scaleY = scale.coerceIn(0.9f, 1f) }
                .clip(shape).background(fill).indication(interaction, MaterialRipple),
                contentAlignment = Alignment.Center) {
                AppIcon(icon, contentDescription = null, size = if (prominent) 34.dp else 28.dp, tint = iconTint)
            }
        }
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
