package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.core.CubicBezierEasing

/** Shared timing for feedback, navigation and modal surfaces. Never animate the blur filter. */
internal object LiquidGlassMotion {
    const val FeedbackMillis = 120
    const val FadeMillis = 160
    const val ReducedMillis = 100
    const val NavigationMillis = 320
    const val SheetMillis = 360
    val Emphasized = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}
