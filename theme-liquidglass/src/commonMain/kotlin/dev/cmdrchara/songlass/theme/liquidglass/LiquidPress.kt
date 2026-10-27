package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import androidx.compose.runtime.withFrameNanos
import dev.cmdrchara.songlass.designsystem.foundation.SpringFollower
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The touch response of interactive Liquid Glass, kept restrained as the HIG asks ("use Liquid
 * Glass effects sparingly", motion that looks purposeful): on touch the element grows slightly,
 * leans a little toward the finger; on release it settles back. The material renderer owns
 * lighting so no post-composition wash reduces ink contrast. Glass and content move together. Gestures are only
 * observed, never consumed, so clicks work as before. Disabled with Reduce Motion.
 *
 * @param grow extra scale while pressed (0.04 = 4% bigger).
 * @param reach farthest the element leans toward the finger.
 */
@Composable
internal fun Modifier.liquidPress(
    grow: Float = 0.04f,
    reach: Dp = 2.dp,
    link: LiquidLink? = null,
    side: LinkSide = LinkSide.Start,
): Modifier {
    if (AppTheme.accessibility.reduceMotion) return this
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val body = remember(grow, reach, density, direction) {
        LiquidBody(
            press = Animatable(0f),
            pull = Animatable(Offset.Zero, Offset.VectorConverter),
            grow = grow,
            reachPx = with(density) { reach.toPx() },
            leanPx = with(density) { LEAN.toPx() },
            layoutDirection = direction,
        )
    }
    if (link != null) {
        DisposableEffect(link, side, body) {
            link.bodies[side] = body
            onDispose { if (link.bodies[side] === body) link.bodies.remove(side) }
        }
    }
    return onSizeChanged { body.widthPx = it.width.toFloat() }.pointerInput(body) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val center = Offset(size.width / 2f, size.height / 2f)
            // Follows the finger closely at first, then saturates at the reach.
            fun rubber(d: Float) = if (body.reachPx > 0f) body.reachPx * tanh(d / (body.reachPx * 4f)) else 0f
            fun pullFor(position: Offset) = (position - center).let { Offset(rubber(it.x), rubber(it.y)) }
            scope.launch { body.press.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
            scope.launch { body.pull.animateTo(pullFor(down.position) * 0.5f, spring(dampingRatio = 0.85f, stiffness = 700f)) }
            // The lean pursues the finger with one spring per axis; restarting animateTo on
            // every move would stall it while the finger keeps moving.
            val leanX = SpringFollower()
            val leanY = SpringFollower()
            var goal = Offset.Zero
            var leaning: Job? = null
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    goal = pullFor(change.position)
                    if (leaning?.isActive != true) {
                        val start = body.pull.value
                        val velocity = body.pull.velocity
                        leanX.snapTo(start.x, velocity.x)
                        leanY.snapTo(start.y, velocity.y)
                        leaning = scope.launch {
                            var last = withFrameNanos { it }
                            while (true) {
                                val now = withFrameNanos { it }
                                val seconds = (now - last) / 1_000_000_000f
                                val movingX = leanX.step(goal.x, seconds, dampingRatio = 0.9f, stiffness = 900f, threshold = 0.05f)
                                val movingY = leanY.step(goal.y, seconds, dampingRatio = 0.9f, stiffness = 900f, threshold = 0.05f)
                                body.pull.snapTo(Offset(leanX.value, leanY.value))
                                if (!movingX && !movingY) break
                                last = now
                            }
                        }
                    }
                }
            } finally {
                // A cancelled pointer stream must release both the lens and its follower.
                // The remembered scope can settle the body when pointerInput restarts.
                val releaseVelocity = if (leaning != null) Offset(leanX.velocity, leanY.velocity) else body.pull.velocity
                leaning?.cancel()
                scope.launch { body.press.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 380f)) }
                scope.launch { body.pull.animateTo(Offset.Zero, spring(dampingRatio = 0.75f, stiffness = 380f), initialVelocity = releaseVelocity) }
            }
        }
    }.graphicsLayer {
        val scale = body.scale()
        val offset = body.offset(link, side)
        scaleX = scale
        scaleY = scale
        translationX = offset.x
        translationY = offset.y
    }
}

/** Animated state of one pressable glass element, shared with a [LiquidLink]. */
@Stable
internal class LiquidBody(
    val press: Animatable<Float, AnimationVector1D>,
    val pull: Animatable<Offset, AnimationVector2D>,
    val grow: Float,
    val reachPx: Float,
    val leanPx: Float,
    val layoutDirection: Float,
) {
    /** Untransformed width, needed to place the end caps of a link bridge. */
    var widthPx by mutableFloatStateOf(0f)

    fun scale(): Float = 1f + grow * press.value

    /** Own lean toward the finger, plus a small lean toward the neighbour while they merge. */
    fun offset(link: LiquidLink?, side: LinkSide): Offset {
        val merge = link?.merge() ?: 0f
        return pull.value + Offset(side.towardNeighbour * layoutDirection * merge * leanPx, 0f)
    }

    /** 0..1: how much this element is pressed and leaning toward its neighbour on [side]. */
    fun reachToward(side: LinkSide): Float {
        val reach = if (reachPx > 0f) (pull.value.x * side.towardNeighbour * layoutDirection / reachPx).coerceIn(0f, 1f) else 0f
        return press.value * (0.3f + 0.7f * reach)
    }
}

internal enum class LinkSide(val towardNeighbour: Float) {
    Start(1f), End(-1f);

    val other: LinkSide get() = if (this == Start) End else Start
}

/**
 * Two neighbouring glass elements that blend like Liquid Glass shapes in a shared container:
 * pressing one, more so when leaning toward the other, grows a glass bridge between them
 * (see [metaballNeck]); releasing lets it thin out and part.
 */
@Stable
internal class LiquidLink {
    internal val bodies = mutableStateMapOf<LinkSide, LiquidBody>()
    internal val lenses = mutableStateMapOf<LinkSide, LiquidLens>()

    /** 0..1 strength of the bridge. */
    fun merge(): Float = maxOf(
        bodies[LinkSide.Start]?.reachToward(LinkSide.Start) ?: 0f,
        bodies[LinkSide.End]?.reachToward(LinkSide.End) ?: 0f,
    )
}

/** How far the neighbour leans in while the two merge. */
private val LEAN = 1.5.dp

/** The bridge between two circles: [fill] covers the neck, [top]/[bottom] are its outer edges. */
internal class Neck(val fill: Path, val top: Path, val bottom: Path)

/**
 * The classic "metaball" bridge between two circles (centres [c1], [c2], radii [r1], [r2]):
 * concave curves leave each circle at an angle that widens with [spread] (0..1) and meet in a
 * waist between them. [inset] is how far the fill reaches inside each circle along its facing
 * arc. Returns null when there is nothing to draw.
 */
internal fun metaballNeck(c1: Offset, r1: Float, c2: Offset, r2: Float, spread: Float, inset: Float): Neck? {
    val d = (c2 - c1).getDistance()
    if (spread <= 0.001f || d <= abs(r1 - r2) || d == 0f) return null
    val overlapping = d < r1 + r2
    val u1 = if (overlapping) acos(((r1 * r1 + d * d - r2 * r2) / (2f * r1 * d)).coerceIn(-1f, 1f)) else 0f
    val u2 = if (overlapping) acos(((r2 * r2 + d * d - r1 * r1) / (2f * r2 * d)).coerceIn(-1f, 1f)) else 0f
    val base = atan2(c2.y - c1.y, c2.x - c1.x)
    val maxSpread = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
    val pi = PI.toFloat()
    val a1 = base + u1 + (maxSpread - u1) * spread
    val a2 = base - u1 - (maxSpread - u1) * spread
    val a3 = base + pi - u2 - (pi - u2 - maxSpread) * spread
    val a4 = base - pi + u2 + (pi - u2 - maxSpread) * spread
    fun at(c: Offset, angle: Float, r: Float) = Offset(c.x + cos(angle) * r, c.y + sin(angle) * r)
    val p1 = at(c1, a1, r1)
    val p2 = at(c1, a2, r1)
    val p3 = at(c2, a3, r2)
    val p4 = at(c2, a4, r2)
    val total = r1 + r2
    val handle = min(spread * HANDLE_SIZE, (p1 - p3).getDistance() / total) * min(1f, d * 2f / total)
    val h1 = at(p1, a1 - pi / 2f, r1 * handle)
    val h2 = at(p2, a2 + pi / 2f, r1 * handle)
    val h3 = at(p3, a3 + pi / 2f, r2 * handle)
    val h4 = at(p4, a4 - pi / 2f, r2 * handle)
    // The fill reaches a little inside each circle along its facing arc, so no hairline gap
    // shows where the bridge meets shapes drawn over it.
    fun inner(c: Offset, angle: Float, r: Float) = at(c, angle, (r - inset).coerceAtLeast(0f))
    fun degrees(angle: Float) = angle * 180f / pi
    val inner3 = inner(c2, a3, r2)
    val inner2 = inner(c1, a2, r1)
    val fill = Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y)
        lineTo(inner3.x, inner3.y)
        arcTo(Rect(c2, (r2 - inset).coerceAtLeast(0f)), degrees(a3), degrees(a4 + 2f * pi - a3), false)
        lineTo(p4.x, p4.y)
        cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y)
        lineTo(inner2.x, inner2.y)
        arcTo(Rect(c1, (r1 - inset).coerceAtLeast(0f)), degrees(a2), degrees(a1 - a2), false)
        close()
    }
    // With y pointing down, p1-p3 is the lower edge and p4-p2 the upper one.
    val bottom = Path().apply { moveTo(p1.x, p1.y); cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y) }
    val top = Path().apply { moveTo(p4.x, p4.y); cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y) }
    return Neck(fill, top, bottom)
}

private const val HANDLE_SIZE = 2.4f

/** Live draw-time lens geometry, local to its pressable body before body transforms. */
internal class LiquidLens(val bounds: () -> Rect, val opacity: () -> Float, val pressure: () -> Float = { 0f })
