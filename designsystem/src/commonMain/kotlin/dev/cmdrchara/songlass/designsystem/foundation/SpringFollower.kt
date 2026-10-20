package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A damped spring that pursues a target which may move on every frame, such as a finger.
 *
 * Restarting `Animatable.animateTo` on each pointer event stalls on devices that deliver an
 * event every frame: a new animation spends its first frame at play time zero, so a target that
 * keeps changing is never approached until the finger rests. This follower integrates one spring
 * continuously instead, with the same unit-mass damping ratio and stiffness as `spring(...)`.
 * Velocity is in units per second, like an Animatable's.
 */
@Stable
class SpringFollower(initial: Float = 0f) {
    var value by mutableFloatStateOf(initial)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set

    fun snapTo(target: Float, velocity: Float = 0f) {
        value = target
        this.velocity = velocity
    }

    /** Advances toward [target] by [seconds]; returns false once settled exactly on it. */
    fun step(target: Float, seconds: Float, dampingRatio: Float, stiffness: Float, threshold: Float): Boolean {
        var x = value
        var v = velocity
        var remaining = seconds.coerceIn(0f, MaxFrameSeconds)
        val damping = 2f * dampingRatio * sqrt(stiffness)
        // Semi-implicit Euler in short substeps stays stable at the stiffest springs used here.
        while (remaining > 0f) {
            val h = minOf(remaining, SubstepSeconds)
            v += (-stiffness * (x - target) - damping * v) * h
            x += v * h
            remaining -= h
        }
        val settled = abs(x - target) < threshold && abs(v) < threshold * 10f
        value = if (settled) target else x
        velocity = if (settled) 0f else v
        return !settled
    }

    /**
     * Follows [target] frame by frame until it settles on it. [target] is read on every frame,
     * so callers move it freely without restarting anything. [onFrame] sees each new value.
     */
    suspend fun follow(
        target: () -> Float,
        dampingRatio: Float,
        stiffness: Float,
        threshold: Float,
        onFrame: suspend (Float) -> Unit = {},
    ) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val moving = step(target(), (now - last) / 1_000_000_000f, dampingRatio, stiffness, threshold)
            onFrame(value)
            if (!moving) return
            last = now
        }
    }

    private companion object {
        const val SubstepSeconds = 0.002f
        // A long stall (app paused, dropped frames) must not fling the spring.
        const val MaxFrameSeconds = 0.05f
    }
}
