package dev.cmdrchara.songlass.theme.liquidglass

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush

/** Static capsule/circle material: one compiled shader and brush per container. */
internal actual fun createNavigationBevelShader(): NavigationBevelShader? = runCatching {
    val shader = RuntimeShader(NavigationBevelProgram)
    val brush = ShaderBrush(shader)
    object : NavigationBevelShader {
        private var lastWidth = Float.NaN
        private var lastHeight = Float.NaN
        private var lastDensity = Float.NaN
        private var lastDark: Boolean? = null

        override fun brush(width: Float, height: Float, density: Float, dark: Boolean): Brush {
            if (width != lastWidth || height != lastHeight || density != lastDensity) {
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
}.onFailure {
    android.util.Log.w("NavigationBevel", "Using neutral static navigation rim", it)
}.getOrNull()
