package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * User-visible strength of the blurred backdrop behind glass surfaces. Each look keeps its own
 * optical recipe and only scales the blur radii, so [Default] reproduces the reference rendering
 * exactly. [Off] is not a zero-radius blur: the looks fall back to their opaque, readable material,
 * because sharp page content under a translucent bar is unreadable and expensive to reframe.
 */
enum class BlurStrength(val radiusScale: Float) {
    Off(0f),
    Low(0.5f),
    Default(1f),
    High(1.5f);

    /** False when no blur layer must be allocated at all. */
    val blurred: Boolean get() = radiusScale > 0f

    /** Scales one reference blur radius; every other optic (tint, refraction) stays untouched. */
    fun scale(radius: Dp): Dp = if (blurred) radius * radiusScale else 0.dp
}

/** Chosen once at the app root; looks read it instead of asking which interface style is active. */
val LocalBlurStrength = staticCompositionLocalOf { BlurStrength.Default }
