package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The playful "liquid" response of iOS 26 glass controls: on touch the element swells, leans
 * toward the finger, follows it with a rubber-band pull while dragged (stretching along the
 * drag) and springs back with a little bounce on release. The whole element moves, glass and
 * content together. Gestures are only observed, never consumed, so clicks work as before.
 * Disabled with Reduce Motion.
 *
 * @param grow extra scale while pressed (0.12 = 12% bigger).
 * @param reach farthest the element travels toward the finger.
 */
@Composable
internal fun Modifier.liquidPress(grow: Float = 0.08f, reach: Dp = 5.dp): Modifier {
    if (AppTheme.accessibility.reduceMotion) return this
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    // Jelly squash: wider and flatter while pressed, wobbling through the rest shape on release.
    val squash = remember { Animatable(0f) }
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val reachPx = with(LocalDensity.current) { reach.toPx() }
    return pointerInput(reachPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val center = Offset(size.width / 2f, size.height / 2f)
            // Rubber band: follows closely near the element, saturates at [reach].
            fun rubber(d: Float) = reachPx * tanh(d / (reachPx * 3f))
            fun pullFor(position: Offset) = (position - center).let { Offset(rubber(it.x), rubber(it.y)) }
            scope.launch { press.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 700f)) }
            scope.launch { squash.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }
            scope.launch { pull.animateTo(pullFor(down.position) * 0.25f, spring(dampingRatio = 0.7f, stiffness = 900f)) }
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                scope.launch { pull.animateTo(pullFor(change.position), spring(dampingRatio = 0.85f, stiffness = 1500f)) }
            }
            scope.launch { press.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f)) }
            scope.launch { squash.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 520f)) }
            scope.launch { pull.animateTo(Offset.Zero, spring(dampingRatio = 0.58f, stiffness = 380f)) }
        }
    }.graphicsLayer {
        val p = press.value
        val o = pull.value
        // Swell and squash, then stretch along the pull while keeping the area roughly constant.
        // The pull is short, so the stretch is strong: the shape deforms more than it travels.
        val stretchX = 1f + abs(o.x) / size.width.coerceAtLeast(1f) * 1.6f
        val stretchY = 1f + abs(o.y) / size.height.coerceAtLeast(1f) * 1.6f
        val swell = 1f + grow * p
        val q = squash.value * SQUASH
        scaleX = swell * (1f + q) * stretchX / sqrt(stretchY)
        scaleY = swell * (1f - q) * stretchY / sqrt(stretchX)
        translationX = o.x
        translationY = o.y
    }
}

/** How much wider/flatter the element gets while pressed. */
private const val SQUASH = 0.07f
