package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

// Pieces every style's mini player shares: the anchor of the Now Playing transition and the
// sideways swipe that changes track (as on the iOS 26 mini player; Material players do the same).

/** Marks the mini player's bounds, the shape Now Playing grows out of and returns into. */
@Composable
fun Modifier.miniPlayerFrame(): Modifier {
    val morph = LocalPlayerMorph.current
    return onGloballyPositioned { morph.source = it.boundsInWindow() }
}

/** Marks the mini player's cover, where the big cover flies from. */
@Composable
fun Modifier.miniPlayerCover(): Modifier {
    val morph = LocalPlayerMorph.current
    return onGloballyPositioned { morph.thumb = it.boundsInWindow() }
        .graphicsLayer { alpha = if (morph.artworkInFlight) 0f else 1f }
}

/** Horizontal offset of the mini player's content while it is swiped. */
@Composable
fun rememberTrackSwipe(): Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }

/**
 * Swipe sideways to change track: the content follows the finger; past a quarter of the width
 * or with a fling, left plays the next track and right the previous one, and the new track slides
 * in from the other side. A short swipe springs back. Taps still go through.
 */
@Composable
fun Modifier.trackSwipe(offset: Animatable<Float, AnimationVector1D>, onNext: () -> Unit, onPrevious: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val reduceMotion = AppTheme.accessibility.reduceMotion
    return pointerInput(reduceMotion) {
        val tracker = VelocityTracker()
        val flingSpeed = 500.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().x
                val width = size.width.toFloat()
                val direction = when {
                    offset.value < -width * 0.25f || (velocity < -flingSpeed && offset.value < 0f) -> 1
                    offset.value > width * 0.25f || (velocity > flingSpeed && offset.value > 0f) -> -1
                    else -> 0
                }
                scope.launch {
                    if (direction == 0) {
                        offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                        return@launch
                    }
                    if (!reduceMotion) offset.animateTo(-direction * width * 0.5f, tween(110))
                    if (direction > 0) next() else previous()
                    if (reduceMotion) {
                        offset.snapTo(0f)
                    } else {
                        offset.snapTo(direction * width * 0.35f)
                        offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
                    }
                }
            },
            onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) } },
        ) { change, amount ->
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
            scope.launch { offset.snapTo(offset.value + amount) }
        }
    }
}

/** Moves and fades the swiped content with [offset]. */
fun Modifier.followTrackSwipe(offset: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    translationX = offset.value
    alpha = if (size.width > 0f) (1f - abs(offset.value) / size.width * 1.6f).coerceIn(0f, 1f) else 1f
}

/** Next and previous as accessibility actions, so the swipe is never the only way. */
fun Modifier.trackSwipeActions(labels: PlaybackLabels, onNext: () -> Unit, onPrevious: () -> Unit): Modifier = semantics {
    customActions = listOf(
        CustomAccessibilityAction(labels.next) { onNext(); true },
        CustomAccessibilityAction(labels.previous) { onPrevious(); true },
    )
}
