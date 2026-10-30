package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/** Paper rim over Glass, compiled once per visible surface. */
internal actual fun createMetalSurfaceShader(): MetalSurfaceShader? = runCatching {
    val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(MetalSurfaceProgram))
    object : MetalSurfaceShader {
        private var lastWidth = Float.NaN
        private var lastHeight = Float.NaN
        private var lastDensity = Float.NaN
        private var lastPhase = Float.NaN
        private val lastCorners = FloatArray(4) { Float.NaN }
        private var lastBrush: Brush? = null

        override fun brush(width: Float, height: Float, corners: FloatArray, density: Float, phase: Float): Brush {
            val geometryChanged = width != lastWidth || height != lastHeight ||
                density != lastDensity || !corners.contentEquals(lastCorners)
            if (!geometryChanged && phase == lastPhase) return requireNotNull(lastBrush)
            if (geometryChanged) {
                builder.uniform("size", width, height)
                builder.uniform("corners", corners[0], corners[1], corners[2], corners[3])
                builder.uniform("px", density)
                lastWidth = width
                lastHeight = height
                lastDensity = density
                corners.copyInto(lastCorners)
            }
            if (phase != lastPhase) {
                builder.uniform("phase", phase)
                lastPhase = phase
            }
            // In-flight display lists retain their own immutable uniform snapshot.
            return ShaderBrush(builder.makeShader().asComposeShader()).also { lastBrush = it }
        }
    }
}.getOrNull()

/** Same static feather as Android; snapshot only when measured bounds or theme change. */
internal actual fun createMetalInkShader(): MetalInkShader? = runCatching {
    val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(MetalInkProgram))
    object : MetalInkShader {
        private var lastWidth = Float.NaN
        private var lastHeight = Float.NaN
        private var lastDensity = Float.NaN
        private var lastDark: Boolean? = null
        private var lastBrush: Brush? = null

        override fun brush(width: Float, height: Float, density: Float, dark: Boolean): Brush {
            val geometryChanged = width != lastWidth || height != lastHeight || density != lastDensity
            if (!geometryChanged && dark == lastDark) return requireNotNull(lastBrush)
            if (geometryChanged) {
                // Uniform size describes the measured ink core, not the feather's outset.
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
            // Immutable snapshots remain valid for any in-flight display list.
            return ShaderBrush(builder.makeShader().asComposeShader()).also { lastBrush = it }
        }
    }
}.getOrNull()
