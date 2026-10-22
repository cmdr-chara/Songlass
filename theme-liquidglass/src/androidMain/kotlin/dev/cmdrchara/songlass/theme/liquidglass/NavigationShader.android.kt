package dev.cmdrchara.songlass.theme.liquidglass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect

internal actual val unifiedNavigationSupported: Boolean = true

internal actual fun createNavigationShader(): NavigationShader? = runCatching {
    val program = RuntimeShader(NavigationGlassProgram)
    object : NavigationShader {
        private var blurDensity = -1f
        private var blur: RenderEffect? = null
        override fun effect(frame: NavigationOpticsFrame): androidx.compose.ui.graphics.RenderEffect {
            program.setFloatUniform("pill", frame.pill)
            program.setFloatUniform("search", frame.search)
            program.setFloatUniform("pillLens", frame.pillLens)
            program.setFloatUniform("searchLens", frame.searchLens)
            program.setFloatUniform("lensOpacity", frame.lensOpacity)
            program.setFloatUniform("lensPressure", frame.lensPressure)
            program.setFloatUniform("smoothing", frame.smoothing)
            program.setFloatUniform("px", frame.density)
            program.setFloatUniform("dark", if (frame.dark) 1f else 0f)
            if (blurDensity != frame.density) {
                blurDensity = frame.density
                blur = RenderEffect.createBlurEffect(8f * frame.density, 8f * frame.density, Shader.TileMode.CLAMP)
            }
            return RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(program, "backdrop"), requireNotNull(blur),
            ).asComposeRenderEffect()
        }
    }
}.onFailure { android.util.Log.w("NavigationGlass", "Custom shader unavailable; using solid fallback", it) }.getOrNull()


internal actual fun createNavigationInkShader(): NavigationInkShader? = runCatching {
    val program = RuntimeShader(NavigationInkProgram)
    object : NavigationInkShader {
        override fun effect(bounds: androidx.compose.ui.geometry.Rect, pressure: Float, opacity: Float, density: Float): androidx.compose.ui.graphics.RenderEffect {
            program.setFloatUniform("lens", bounds.center.x, bounds.center.y,
                ((bounds.width-bounds.height)/2f).coerceAtLeast(0f), bounds.height/2f)
            program.setFloatUniform("pressure", pressure)
            program.setFloatUniform("opacity", opacity)
            program.setFloatUniform("px", density)
            return RenderEffect.createRuntimeShaderEffect(program, "ink").asComposeRenderEffect()
        }
    }
}.onFailure { android.util.Log.w("NavigationGlass", "Ink shader unavailable; using clipped scene", it) }.getOrNull()
