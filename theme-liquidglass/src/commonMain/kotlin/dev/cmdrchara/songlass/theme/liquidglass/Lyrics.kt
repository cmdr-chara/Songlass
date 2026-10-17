package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor

/** Scoped to the player: other text and controls retain their normal appearance colors. */
internal val LocalLyricsTint = staticCompositionLocalOf { Color.Unspecified }

@Composable
internal fun LiquidGlassLyricLine(
    text: String,
    active: Boolean,
    subdued: Boolean,
    progress: Float?,
    isPlaying: Boolean,
    seekLabel: String,
    onSeek: (() -> Unit)?,
    modifier: Modifier,
) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val tint = LocalLyricsTint.current.takeIf { it.isSpecified } ?: LocalContentColor.current
    val sweeping = active && progress != null && !reduceMotion
    val targetProgress = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val sweep = remember(text) { Animatable(targetProgress) }
    var previousProgress by remember(text) { mutableFloatStateOf(targetProgress) }
    LaunchedEffect(targetProgress, sweeping, isPlaying) {
        val delta = targetProgress - previousProgress
        previousProgress = targetProgress
        // Each sample is already the current playback position. Start there and
        // predict the next 250 ms instead of spending a tick catching up to it.
        if (!sweeping || !isPlaying || targetProgress <= 0f || delta <= 0f || delta > 0.2f) {
            sweep.snapTo(targetProgress)
        } else {
            // Small sampling jitter must not pull an ordinary forward sweep backwards.
            val start = maxOf(targetProgress, sweep.value)
            val predicted = maxOf(start, (targetProgress + delta).coerceAtMost(1f))
            sweep.snapTo(start)
            sweep.animateTo(predicted, tween(250, easing = LinearEasing))
        }
    }
    val baseColor by animateColorAsState(
        targetValue = tint.copy(alpha = if (subdued && (!active || sweeping)) 0.64f else 1f),
        animationSpec = if (reduceMotion) snap() else tween(LiquidGlassMotion.FadeMillis),
        label = "lyric emphasis",
    )
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val glyphs = remember(layout) { layout?.sweepGlyphs().orEmpty() }
    Box(
        modifier.fillMaxWidth().heightIn(min = 44.dp)
            .semantics { selected = active }
            .then(if (onSeek == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = seekLabel, onClick = onSeek))
            .padding(vertical = 18.dp),
    ) {
        BasicText(
            text = text,
            style = AppTheme.typography.title1.copy(
                fontSize = 32.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.Bold,
                color = baseColor,
            ),
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                val measured = layout
                if (sweeping && measured != null && glyphs.isNotEmpty()) {
                    val consumed = sweep.value * glyphs.size
                    val mask = Path()
                    glyphs.forEachIndexed { index, glyph ->
                        val fraction = (consumed - index).coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            val box = glyph.bounds
                            mask.addRect(if (glyph.rtl) {
                                Rect(box.right - box.width * fraction, box.top, box.right, box.bottom)
                            } else {
                                Rect(box.left, box.top, box.left + box.width * fraction, box.bottom)
                            })
                        }
                    }
                    // Drawing from the same layout keeps wrapping and glyph positions exact,
                    // and adds no second text node for screen readers to announce.
                    clipPath(mask) { drawText(measured, color = tint) }
                }
            },
        )
    }
}

private data class SweepGlyph(val bounds: Rect, val rtl: Boolean)

/** Use actual character boxes, so proportional fonts, wrapped lines and bidi runs sweep correctly. */
private fun TextLayoutResult.sweepGlyphs(): List<SweepGlyph> {
    val text = layoutInput.text.text
    val glyphs = mutableListOf<SweepGlyph>()
    var offset = 0
    while (offset < text.length) {
        val character = text[offset]
        if (character != '\n' && character != '\r') {
            glyphs += SweepGlyph(getBoundingBox(offset), getBidiRunDirection(offset) == ResolvedTextDirection.Rtl)
        }
        // A supplementary Unicode character is one visual unit, not two UTF-16 halves.
        offset += if (character.isHighSurrogate() && offset + 1 < text.length && text[offset + 1].isLowSurrogate()) 2 else 1
    }
    return glyphs
}

/** Fade only the viewport edges; lines remain sharp and touch/scroll coordinates are unchanged. */
@Composable
internal fun LiquidGlassLyricsViewport(modifier: Modifier, content: @Composable () -> Unit) {
    val fade = if (AppTheme.accessibility.reduceTransparency) Modifier else Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (size.height > 0f) {
                val edge = (24.dp.toPx() / size.height).coerceAtMost(0.5f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        edge to Color.Black,
                        (1f - edge) to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    Box(modifier.then(fade)) { content() }
}
