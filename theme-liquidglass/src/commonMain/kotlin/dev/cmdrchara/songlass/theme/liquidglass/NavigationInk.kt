package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

internal expect fun createNavigationInkShader(): NavigationInkShader?

internal interface NavigationInkShader {
    fun effect(bounds: Rect, pressure: Float, opacity: Float, density: Float): RenderEffect
}

/** The real row owns input and accessibility. This mask only changes its painted ink. */
@Composable
internal fun Modifier.navigationInkCutout(bounds: () -> Rect?, opacity: () -> Float): Modifier {
    val contour = remember { Path() }
    return drawWithContent {
        val rect = bounds()
        if (rect == null || opacity() < 0.999f) drawContent()
        else {
            contour.reset()
            contour.addRoundRect(RoundRect(rect, CornerRadius(rect.height / 2f)))
            clipPath(contour, ClipOp.Difference) { this@drawWithContent.drawContent() }
        }
    }
}

/**
 * Sharp accent ink is recorded separately from the blurred page. A single local effect
 * bends that recording inside the moving lens; it never feeds back into the page capture.
 */
@Composable
internal fun Modifier.navigationInkScene(bounds: () -> Rect?, pressure: () -> Float, opacity: () -> Float): Modifier {
    val layer = rememberGraphicsLayer()
    val shader = remember { createNavigationInkShader() }
    val contour = remember { Path() }
    return drawWithContent {
        val rect = bounds() ?: return@drawWithContent
        val alpha = opacity().coerceIn(0f, 1f)
        if (alpha <= 0f) return@drawWithContent
        layer.record { this@drawWithContent.drawContent() }
        layer.renderEffect = shader?.effect(rect, pressure().coerceIn(0f, 1f), alpha, density)
        if (shader != null) drawLayer(layer)
        else {
            contour.reset()
            contour.addRoundRect(RoundRect(rect, CornerRadius(rect.height / 2f)))
            clipPath(contour) { drawLayer(layer) }
        }
    }
}

// Original Songlass ink-only optical model. A bounded monotonic bend avoids repeated glyphs.
internal const val NavigationInkProgram = """
    uniform shader ink;
    uniform float4 lens;
    uniform float pressure;
    uniform float opacity;
    uniform float px;
    half4 main(float2 p) {
        float2 q = p - lens.xy;
        q.x -= clamp(q.x, -lens.z, lens.z);
        float len = length(q);
        float distance = len - lens.w;
        float aa = max(0.6, px*0.3);
        float coverage = (1.0-smoothstep(-aa,aa,distance))*opacity;
        if (coverage <= 0.0) return half4(0.0);
        float2 normal = q/max(len,0.001);
        float edge = 1.0-smoothstep(0.0,7.0*px,max(-distance,0.0));
        float2 center = lens.xy;
        float2 uv = center + (p-center)/(1.0+0.12*pressure);
        uv -= normal*(1.8*px*pressure*edge*edge);
        // Match the unfiltered icon at the lens contour; bend it progressively
        // farther inside so the two painted halves meet without a spatial seam.
        float rimBlend = smoothstep(0.0, 8.0*px, max(-distance, 0.0));
        uv = mix(p, uv, rimBlend);
        half4 color = ink.eval(uv);
        return color*half(coverage);
    }
"""
