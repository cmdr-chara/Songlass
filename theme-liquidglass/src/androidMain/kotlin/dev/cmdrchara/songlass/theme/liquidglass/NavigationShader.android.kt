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
        override fun effect(pill: FloatArray, search: FloatArray, smoothing: Float, density: Float, dark: Boolean): androidx.compose.ui.graphics.RenderEffect {
            program.setFloatUniform("pill", pill)
            program.setFloatUniform("search", search)
            program.setFloatUniform("smoothing", smoothing)
            program.setFloatUniform("px", density)
            program.setFloatUniform("dark", if (dark) 1f else 0f)
            if (blurDensity != density) {
                blurDensity = density
                blur = RenderEffect.createBlurEffect(18f * density, 18f * density, Shader.TileMode.CLAMP)
            }
            return RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(program, "backdrop"), requireNotNull(blur),
            ).asComposeRenderEffect()
        }
    }
}.onFailure { android.util.Log.w("NavigationGlass", "Custom shader unavailable; using solid fallback", it) }.getOrNull()
