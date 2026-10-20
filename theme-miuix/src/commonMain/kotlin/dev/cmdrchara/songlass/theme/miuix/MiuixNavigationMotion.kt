package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import dev.cmdrchara.songlass.designsystem.foundation.SpringFollower
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/** One pursuit spring for all input paths; release stays expanded until travel has settled. */
@Stable
internal class NavigationMotion(initial: Int) {
    val location = SpringFollower(initial.toFloat())
    var destination by mutableFloatStateOf(initial.toFloat())
    var touching by mutableStateOf(false)
    var panelPull by mutableFloatStateOf(0f)
}

@Composable
internal fun rememberNavigationMotion(initial: Int, count: Int, reduced: Boolean): NavigationMotion {
    val motion = remember(count) { NavigationMotion(initial.coerceIn(0, count - 1)) }
    LaunchedEffect(motion, reduced) {
        while (true) {
            // Wake only when the destination differs from where the lens rests.
            snapshotFlow { motion.destination }.first { it != motion.location.value || motion.location.velocity != 0f }
            if (reduced) motion.location.snapTo(motion.destination)
            // One pursuit for taps and drags alike: the finger moves the target, never restarts it.
            else motion.location.follow({ motion.destination }, dampingRatio = 0.93f, stiffness = 950f, threshold = 0.001f)
        }
    }
    return motion
}

@Composable
internal fun navigationPressure(motion: NavigationMotion, reduced: Boolean): State<Float> {
    val engaged by remember(motion) {
        derivedStateOf { motion.touching || abs(motion.destination - motion.location.value) > 0.018f }
    }
    return animateFloatAsState(if (engaged && !reduced) 1f else 0f,
        if (reduced) snap() else spring(dampingRatio = 0.90f, stiffness = 800f), label = "Navigation pressure")
}

internal data class NavigationExpansion(val width: State<Float>, val height: State<Float>)

@Composable
internal fun navigationExpansion(motion: NavigationMotion, reduced: Boolean): NavigationExpansion {
    val engaged by remember(motion) {
        derivedStateOf { motion.touching || abs(motion.destination - motion.location.value) > 0.018f }
    }
    val width = animateFloatAsState(if (engaged && !reduced) 1.34f else 1f,
        if (reduced) snap() else spring(dampingRatio = 0.65f, stiffness = 300f), label = "Navigation lens width")
    val height = animateFloatAsState(if (engaged && !reduced) 1.40f else 1f,
        if (reduced) snap() else spring(dampingRatio = 0.72f, stiffness = 340f), label = "Navigation lens height")
    return NavigationExpansion(width, height)
}
