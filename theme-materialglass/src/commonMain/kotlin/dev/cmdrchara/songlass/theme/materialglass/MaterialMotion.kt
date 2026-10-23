package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Songlass motion roles: geometry can settle softly; color and opacity never overshoot. */
internal object MaterialMotion {
    const val SpatialDamping = 0.8f
    const val SpatialStiffness = 500f
    const val PressDamping = 0.9f
    const val PressStiffness = 800f

    fun <T> spatial(): SpringSpec<T> = spring(SpatialDamping, SpatialStiffness)
    fun <T> press(): SpringSpec<T> = spring(PressDamping, PressStiffness)
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 700f)
}
