package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/** Reuse the builder; each changed frame retains its own immutable uniform snapshot. */
internal actual fun createNavigationRingShader(): NavigationRingShader? = runCatching {
    val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(NavigationRingProgram))
    object : NavigationRingShader {
        private var lastWidth = Float.NaN
        private var lastHeight = Float.NaN
        private var lastDensity = Float.NaN
        private var lastPhase = Float.NaN
        private var lastDark: Boolean? = null
        private var lastBrush: Brush? = null

        override fun brush(width: Float, height: Float, density: Float, phase: Float, dark: Boolean): Brush {
            val geometryChanged = width != lastWidth || height != lastHeight || density != lastDensity
            if (!geometryChanged && phase == lastPhase && dark == lastDark) return requireNotNull(lastBrush)
            if (geometryChanged) {
                builder.uniform("size", width, height)
                builder.uniform("px", density)
                lastWidth = width
                lastHeight = height
                lastDensity = density
            }
            if (phase != lastPhase) {
                builder.uniform("phase", phase)
                lastPhase = phase
            }
            if (dark != lastDark) {
                builder.uniform("dark", if (dark) 1f else 0f)
                lastDark = dark
            }
            return ShaderBrush(builder.makeShader().asComposeShader()).also { lastBrush = it }
        }
    }
}.getOrNull()
