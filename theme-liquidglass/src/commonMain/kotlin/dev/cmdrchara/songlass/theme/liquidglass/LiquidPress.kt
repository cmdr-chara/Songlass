package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.Animatable
import kotlin.math.max
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
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
internal fun Modifier.liquidPress(
    grow: Float = 0.08f,
    reach: Dp = 5.dp,
    link: LiquidLink? = null,
    side: LinkSide = LinkSide.Start,
): Modifier {
    if (AppTheme.accessibility.reduceMotion) return this
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    // Jelly squash: wider and flatter while pressed, wobbling through the rest shape on release.
    val squash = remember { Animatable(0f) }
    // Wobble received from a linked neighbour, like a soap film passing on a ripple.
    val jiggle = remember { Animatable(0f) }
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val reachPx = with(LocalDensity.current) { reach.toPx() }
    val ringWidth = with(LocalDensity.current) { 1.5.dp.toPx() }
    if (link != null) {
        DisposableEffect(link, side) {
            link.pulls[side] = pull
            link.kicks[side] = { strength ->
                scope.launch {
                    jiggle.animateTo(strength, spring(dampingRatio = 1f, stiffness = 1800f))
                    jiggle.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 420f))
                }
            }
            onDispose {
                link.pulls.remove(side)
                link.kicks.remove(side)
            }
        }
    }
    return pointerInput(reachPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val center = Offset(size.width / 2f, size.height / 2f)
            // Rubber band: follows closely near the element, saturates at [reach].
            fun rubber(d: Float) = reachPx * tanh(d / (reachPx * 3f))
            fun pullFor(position: Offset) = (position - center).let { Offset(rubber(it.x), rubber(it.y)) }
            link?.kick(side.other, 0.5f)
            scope.launch { press.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 700f)) }
            scope.launch { squash.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }
            scope.launch { pull.animateTo(pullFor(down.position) * 0.25f, spring(dampingRatio = 0.7f, stiffness = 900f)) }
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                scope.launch { pull.animateTo(pullFor(change.position), spring(dampingRatio = 0.85f, stiffness = 1500f)) }
            }
            link?.kick(side.other, -0.8f)
            scope.launch { press.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 420f)) }
            scope.launch { squash.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 520f)) }
            scope.launch { pull.animateTo(Offset.Zero, spring(dampingRatio = 0.58f, stiffness = 380f)) }
        }
    }.graphicsLayer {
        val p = press.value
        val own = pull.value
        // A neighbour pushed against this bubble shoves it back a little and flattens it, the way
        // two soap bubbles press into a shared wall.
        val contact = link?.attractionTo(side) ?: 0f
        val o = own - Offset(side.towardNeighbour * contact * PUSH, 0f)
        val flatten = contact / size.height.coerceAtLeast(1f)
        // Swell and squash, then stretch along the own pull while keeping the area roughly constant.
        val stretchX = 1f + abs(own.x) / size.width.coerceAtLeast(1f) * 1.6f
        val stretchY = 1f + abs(own.y) / size.height.coerceAtLeast(1f) * 1.6f
        val swell = 1f + grow * p
        val q = squash.value * SQUASH + jiggle.value * JIGGLE
        scaleX = swell * (1f + q) * stretchX / sqrt(stretchY) * (1f - flatten * 0.5f)
        scaleY = swell * (1f - q) * stretchY / sqrt(stretchX) * (1f + flatten * 0.35f)
        translationX = o.x
        translationY = o.y
    }.drawWithContent {
        drawContent()
        // Thin-film iridescence on the rim while touched, turning with the finger.
        val p = press.value
        if (p > 0.01f) {
            val d = pull.value.let { if (it.getDistance() < 0.5f) Offset(1f, 1f) else it }
            val dir = d / d.getDistance()
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = maxOf(size.width, size.height) / 2f
            val radius = minOf(size.width, size.height) / 2f
            drawRoundRect(
                brush = Brush.linearGradient(
                    IridescentColors.map { it.copy(alpha = it.alpha * p) },
                    start = c - dir * r,
                    end = c + dir * r,
                ),
                topLeft = Offset(ringWidth / 2f, ringWidth / 2f),
                size = Size(size.width - ringWidth, size.height - ringWidth),
                cornerRadius = CornerRadius(radius),
                style = Stroke(ringWidth),
            )
        }
    }
}

/** How much wider/flatter the element gets while pressed. */
private const val SQUASH = 0.07f

internal enum class LinkSide(val towardNeighbour: Float) {
    Start(1f), End(-1f);

    val other: LinkSide get() = if (this == Start) End else Start
}

/**
 * Two neighbouring glass elements that react to each other like soap bubbles: pushing one into
 * the other flattens and nudges the neighbour, and presses and releases send it a ripple.
 */
@Stable
internal class LiquidLink {
    internal val pulls = mutableStateMapOf<LinkSide, Animatable<Offset, AnimationVector2D>>()
    internal val kicks = mutableMapOf<LinkSide, (Float) -> Unit>()

    /** Sends a ripple to [side]: positive squashes it first, negative stretches it first. */
    fun kick(side: LinkSide, strength: Float) {
        kicks[side]?.invoke(strength)
    }

    private fun pullX(side: LinkSide): Float = pulls[side]?.value?.x ?: 0f

    /** How far the other element is pulled toward [side], in px (0 when pulled away). */
    fun attractionTo(side: LinkSide): Float = when (side) {
        LinkSide.Start -> max(0f, -pullX(LinkSide.End))
        LinkSide.End -> max(0f, pullX(LinkSide.Start))
    }

}

/** Share of a neighbour's push that moves this element back. */
private const val PUSH = 0.35f

/** Squash amplitude of a ripple received from the neighbour. */
private const val JIGGLE = 0.05f

/** Soft soap-film rainbow for the rim. */
private val IridescentColors = listOf(
    Color(0x73FF9AD5), Color(0x73FFE59A), Color(0x739AFFE0), Color(0x739AC8FF), Color(0x73D49AFF), Color(0x73FF9AD5),
)
