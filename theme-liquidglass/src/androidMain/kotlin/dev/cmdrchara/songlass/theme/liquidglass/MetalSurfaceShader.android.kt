package dev.cmdrchara.songlass.theme.liquidglass

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush

internal actual fun createMetalSurfaceShader(): MetalSurfaceShader? = runCatching {
    val shader = RuntimeShader(MetalSurfaceProgram)
    val brush = ShaderBrush(shader)
    object : MetalSurfaceShader {
        private var lastWidth = Float.NaN
        private var lastHeight = Float.NaN
        private var lastDensity = Float.NaN
        private var lastPhase = Float.NaN
        private val lastCorners = FloatArray(4) { Float.NaN }

        override fun brush(width: Float, height: Float, corners: FloatArray, density: Float, phase: Float): Brush {
            val geometryChanged = width != lastWidth || height != lastHeight ||
                density != lastDensity || !corners.contentEquals(lastCorners)
            if (geometryChanged) {
                shader.setFloatUniform("size", width, height)
                shader.setFloatUniform("corners", corners[0], corners[1], corners[2], corners[3])
                shader.setFloatUniform("px", density)
                lastWidth = width
                lastHeight = height
                lastDensity = density
                corners.copyInto(lastCorners)
            }
            if (phase != lastPhase) {
                shader.setFloatUniform("phase", phase)
                lastPhase = phase
            }
            return brush
        }
    }
}.onFailure { android.util.Log.w("LiquidMetal", "Using static metal rim over Glass", it) }.getOrNull()

/** Static ink feather: one program per foreground component, with no animation clock. */
internal actual fun createMetalInkShader(): MetalInkShader? = runCatching {
    val shader = RuntimeShader(MetalInkProgram)
    val brush = ShaderBrush(shader)
    object : MetalInkShader {
        private var lastWidth = Float.NaN
        private var lastHeight = Float.NaN
        private var lastDensity = Float.NaN
        private var lastDark: Boolean? = null

        override fun brush(width: Float, height: Float, density: Float, dark: Boolean): Brush {
            if (width != lastWidth || height != lastHeight || density != lastDensity) {
                // Uniform size describes the measured ink core, not the feather's outset.
                shader.setFloatUniform("size", width, height)
                shader.setFloatUniform("px", density)
                lastWidth = width
                lastHeight = height
                lastDensity = density
            }
            if (dark != lastDark) {
                shader.setFloatUniform("dark", if (dark) 1f else 0f)
                lastDark = dark
            }
            return brush
        }
    }
}.onFailure { android.util.Log.w("LiquidMetal", "Using neutral ink fallback", it) }.getOrNull()
