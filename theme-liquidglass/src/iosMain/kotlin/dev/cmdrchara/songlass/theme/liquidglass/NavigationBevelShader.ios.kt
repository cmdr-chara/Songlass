package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/** Snapshot only when geometry/theme changes; no animation state or display-list mutation. */
internal actual fun createNavigationBevelShader(): NavigationBevelShader? = runCatching {
    val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(NavigationBevelProgram))
    object : NavigationBevelShader {
        private var lastWidth = Float.NaN
        private var lastHeight = Float.NaN
        private var lastDensity = Float.NaN
        private var lastDark: Boolean? = null
        private var lastBrush: Brush? = null

        override fun brush(width: Float, height: Float, density: Float, dark: Boolean): Brush {
            val geometryChanged = width != lastWidth || height != lastHeight || density != lastDensity
            if (!geometryChanged && dark == lastDark) return requireNotNull(lastBrush)
            if (geometryChanged) {
                builder.uniform("size", width, height)
                builder.uniform("px", density)
                lastWidth = width
                lastHeight = height
                lastDensity = density
            }
            if (dark != lastDark) {
                builder.uniform("dark", if (dark) 1f else 0f)
                lastDark = dark
            }
            return ShaderBrush(builder.makeShader().asComposeShader()).also { lastBrush = it }
        }
    }
}.getOrNull()
