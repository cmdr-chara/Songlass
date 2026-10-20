package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** The resting cover bounds in the player's untransformed content coordinate space. */
data class PlayerArtworkTarget(val bounds: Rect, val cornerRadius: Float)

/** The sheet and its Square artwork exchange geometry without depending on a theme. */
class PlayerArtworkTransition(val imageInFlight: State<Boolean>) {
    private var container by mutableStateOf<LayoutCoordinates?>(null)
    private var artwork by mutableStateOf<LayoutCoordinates?>(null)
    private var radiusPx = 0f
    private var lastTarget: PlayerArtworkTarget? = null

    fun placeContainer(coordinates: LayoutCoordinates) {
        container = coordinates
        rememberTarget()
    }

    fun placeArtwork(coordinates: LayoutCoordinates, cornerRadius: Float) {
        artwork = coordinates
        radiusPx = cornerRadius
        rememberTarget()
    }

    private fun measuredTarget(): PlayerArtworkTarget? {
        val parent = container?.takeIf { it.isAttached } ?: return null
        val child = artwork?.takeIf { it.isAttached } ?: return null
        // Cancels the shared controls-layer transform. Window bounds would move the target
        // while opening/closing; relative coordinates retain the actual Square layout.
        val raw = parent.localBoundingBoxOf(child, clipBounds = false)
        val bounds = parent.localBoundingBoxOf(child, clipBounds = true)
        if (!bounds.left.isFinite() || !bounds.top.isFinite() ||
            !bounds.width.isFinite() || !bounds.height.isFinite() ||
            bounds.width <= 0f || bounds.height <= 0f) return null
        val scale = bounds.width / child.size.width.coerceAtLeast(1)
        // The content slot can crop an oversized cover on short/narrow windows. Use the
        // visible crop so the first closing frame does not suddenly widen the artwork.
        val clipped = kotlin.math.abs(raw.width - bounds.width) > 1f ||
            kotlin.math.abs(raw.height - bounds.height) > 1f
        return PlayerArtworkTarget(bounds, if (clipped) 0f else radiusPx * scale)
    }

    private fun rememberTarget() { measuredTarget()?.let { lastTarget = it } }

    // Read live geometry: the Square artwork also scales when playback pauses/resumes.
    fun target(): PlayerArtworkTarget? = measuredTarget() ?: lastTarget

    fun clearArtwork() { rememberTarget(); artwork = null }
    fun clearContainer() { rememberTarget(); container = null }
}

val LocalPlayerArtworkTransition = staticCompositionLocalOf<PlayerArtworkTransition?> { null }

/** Keep the resting cover measured and interactive, but draw only one cover in flight. */
@Composable
fun Modifier.playerArtworkTarget(cornerRadius: Dp): Modifier {
    val transition = LocalPlayerArtworkTransition.current ?: return this
    val radius = with(LocalDensity.current) { cornerRadius.toPx() }
    DisposableEffect(transition) { onDispose { transition.clearArtwork() } }
    return onGloballyPositioned { transition.placeArtwork(it, radius) }
        .graphicsLayer { alpha = if (transition.imageInFlight.value) 0f else 1f }
}
