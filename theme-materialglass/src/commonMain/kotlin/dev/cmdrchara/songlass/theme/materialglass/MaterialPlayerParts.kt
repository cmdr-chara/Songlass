package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.MiniPlayerState
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import dev.cmdrchara.songlass.theme.liquidglass.followTrackSwipe
import dev.cmdrchara.songlass.theme.liquidglass.miniPlayerCover
import dev.cmdrchara.songlass.theme.liquidglass.miniPlayerFrame
import dev.cmdrchara.songlass.theme.liquidglass.rememberTrackSwipe
import dev.cmdrchara.songlass.theme.liquidglass.trackSwipe
import dev.cmdrchara.songlass.theme.liquidglass.trackSwipeActions

/**
 * Material mini player in the navigation glass: a rounded-square cover, title and artist, Play in
 * a filled primary circle wrapped by a circular progress indicator, and Next as an icon button.
 * Swiping sideways changes track, as in the Liquid style.
 */
@Composable
internal fun MaterialMiniPlayer(
    state: MiniPlayerState,
    labels: PlaybackLabels,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier,
) {
    val look = LocalGlassLook.current
    val palette = currentPalette()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val swipe = rememberTrackSwipe()
    val progress by animateFloatAsState(state.progress.coerceIn(0f, 1f), if (reduceMotion) snap() else tween(250), label = "miniProgress")
    Row(
        look.navigationSurface(modifier.fillMaxWidth().heightIn(min = 64.dp).miniPlayerFrame(), RoundedCornerShape(percent = 50), interaction, edges = true)
            .trackSwipe(swipe, onNext, onPrevious)
            .clickable(interaction, indication = MaterialRipple, onClick = onClick)
            .trackSwipeActions(labels, onNext, onPrevious)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).followTrackSwipe(swipe), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).miniPlayerCover()) {
                AlbumArt(state.artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AppText(state.title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                AppText(state.subtitle, AppTheme.typography.footnote, color = AppTheme.colors.label.copy(alpha = 0.8f), maxLines = 1)
            }
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = onPlayPause)
                .semantics { contentDescription = if (state.isPlaying) labels.pause else labels.play },
            contentAlignment = Alignment.Center,
        ) {
            val track = palette.primaryContainer
            val indicator = palette.primary
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f + 1.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (progress > 0f) drawArc(indicator, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Box(Modifier.size(36.dp).clip(CircleShape).background(palette.primary), contentAlignment = Alignment.Center) {
                AppIcon(if (state.isPlaying) AppIcons.Pause else AppIcons.Play, contentDescription = null, size = 20.dp, tint = palette.onPrimary)
            }
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = onNext)
                .semantics { contentDescription = labels.next },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Next, contentDescription = null, size = 24.dp, tint = AppTheme.colors.label)
        }
    }
}

/**
 * Material 3 Expressive slider for the volume: a 24 dp track split by a slim vertical handle with
 * a gap on each side, the speaker icon inset in the track and a stop dot at the end. Tapping
 * jumps to the spot and the handle narrows while held, as Material sliders do.
 */
@Composable
internal fun MaterialVolumeSlider(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier, showSpeaker: Boolean = true) {
    val content = LocalContentColor.current
    val onActive = AppTheme.colors.background
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val currentOnChange by rememberUpdatedState(onValueChange)
    var held by remember { mutableStateOf(false) }
    val handleWidth by animateDpAsState(if (held) 2.dp else 4.dp, if (reduceMotion) snap() else MaterialMotion.press(), label = "volumeHandle")
    val icon = rememberVectorPainter(if (value < 0.5f) AppIcons.SpeakerLow else AppIcons.AudioOutput)
    Canvas(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v -> currentOnChange(v.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    held = true
                    tryAwaitRelease()
                    held = false
                }) { offset -> if (size.width > 0) currentOnChange((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { held = true },
                    onDragEnd = { held = false },
                    onDragCancel = { held = false },
                ) { change, _ ->
                    change.consume()
                    if (size.width > 0) currentOnChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val track = 24.dp.toPx()
        val outer = track / 2f
        val inner = 4.dp.toPx()
        val hw = handleWidth.toPx()
        val gap = 6.dp.toPx()
        val top = size.height / 2f - track / 2f
        val bottom = top + track
        val x = (size.width * value.coerceIn(0f, 1f)).coerceIn(hw / 2f, size.width - hw / 2f)
        val activeEnd = x - hw / 2f - gap
        val inactiveStart = x + hw / 2f + gap
        if (activeEnd > 0f) drawSegment(0f, activeEnd, top, bottom, outer, inner, startOuter = true, color = content)
        if (inactiveStart < size.width) {
            drawSegment(inactiveStart, size.width, top, bottom, outer, inner, startOuter = false, color = content.copy(alpha = 0.22f))
            // Stop indicator: where the track ends.
            if (size.width - inactiveStart > track) drawCircle(content, 2.dp.toPx(), Offset(size.width - outer, size.height / 2f))
        }
        drawRoundRect(content, Offset(x - hw / 2f, size.height / 2f - 22.dp.toPx()), Size(hw, 44.dp.toPx()), CornerRadius(hw / 2f))
        // Inset icon: inside the active part when it fits, otherwise just after the handle.
        val iconSize = 16.dp.toPx()
        val pad = 8.dp.toPx()
        val (iconX, tint) = if (activeEnd > iconSize + pad * 2) pad to onActive else inactiveStart + pad to content
        if (showSpeaker && iconX + iconSize < size.width) {
            translate(iconX, size.height / 2f - iconSize / 2f) {
                with(icon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(tint)) }
            }
        }
    }
}

/** A track segment: fully rounded on the outer end, slightly rounded where it meets the handle. */
private fun DrawScope.drawSegment(left: Float, right: Float, top: Float, bottom: Float, outer: Float, inner: Float, startOuter: Boolean, color: Color) {
    val width = right - left
    val o = CornerRadius(minOf(outer, width / 2f))
    val i = CornerRadius(minOf(inner, width / 2f))
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left, top, right, bottom,
                topLeftCornerRadius = if (startOuter) o else i,
                bottomLeftCornerRadius = if (startOuter) o else i,
                topRightCornerRadius = if (startOuter) i else o,
                bottomRightCornerRadius = if (startOuter) i else o,
            ),
        )
    }
    drawPath(path, color)
}
