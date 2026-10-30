package dev.cmdrchara.songlass.theme.liquidglass

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush

/** One compiled program and brush per selection; update only changed uniforms. */
internal actual fun createNavigationRingShader(): NavigationRingShader? = runCatching {
    val shader = RuntimeShader(NavigationRingProgram)
    val brush = ShaderBrush(shader)
    object : NavigationRingShader {
        private var lastWidth = Float.NaN
        private var lastHeight = Float.NaN
        private var lastDensity = Float.NaN
        private var lastPhase = Float.NaN
        private var lastDark: Boolean? = null

        override fun brush(width: Float, height: Float, density: Float, phase: Float, dark: Boolean): Brush {
            if (width != lastWidth || height != lastHeight || density != lastDensity) {
                shader.setFloatUniform("size", width, height)
                shader.setFloatUniform("px", density)
                lastWidth = width
                lastHeight = height
                lastDensity = density
            }
            if (phase != lastPhase) {
                shader.setFloatUniform("phase", phase)
                lastPhase = phase
            }
            if (dark != lastDark) {
                shader.setFloatUniform("dark", if (dark) 1f else 0f)
                lastDark = dark
            }
            return brush
        }
    }
}.onFailure {
    android.util.Log.w("NavigationMetal", "Using static selection rim", it)
}.getOrNull()
