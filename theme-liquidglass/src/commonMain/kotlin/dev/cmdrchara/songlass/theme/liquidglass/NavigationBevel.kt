package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

internal interface NavigationBevelShader {
    fun brush(width: Float, height: Float, density: Float, dark: Boolean): Brush
}

internal expect fun createNavigationBevelShader(): NavigationBevelShader?

/** Static transparent bevel over the original Glass body, without changing its shadow or centre. */
@Composable
internal fun Modifier.navigationMetalBevel(shape: RoundedCornerShape): Modifier {
    val colors = AppTheme.colors
    val dark = colors.isDark
    val shader = remember { createNavigationBevelShader() }
    val fallbackRim = colors.label.copy(alpha = if (dark) 0.12f else 0.14f)
    return drawWithCache {
        val width = size.width
        val height = size.height
        val outline = shape.createOutline(size, layoutDirection, this)
        val fallbackClip = Path().apply {
            when (outline) {
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val stroke = Stroke(1.5f * density)
        onDrawWithContent {
            drawContent()
            val material = shader?.brush(width, height, density, dark)
            if (material != null) drawRect(material)
            else clipPath(fallbackClip) { drawOutline(outline, fallbackRim, style = stroke) }
        }
    }
}

/** Shared static profile, expressed in dp, for the unified and standalone renderers. */
internal val NavigationBevelFunctions = """
    float3 navigationBevel(float depth, float2 normal, float dark) {
        float3 base = mix(float3(0.949, 0.949, 0.969), float3(0.110, 0.110, 0.118), dark);
        // The neutral face does no lighting work beyond this shallow perimeter.
        if (depth >= 3.2) return base;
        normal /= max(length(normal), 0.001);
        float toward = dot(normal, float2(-0.4103647, -0.9119215));
        float lit = max(toward, 0.0);
        float shade = max(-toward, 0.0);
        float outer = 1.0 - smoothstep(0.15, 0.55, depth);
        float crest = smoothstep(0.2, 0.75, depth) * (1.0 - smoothstep(1.0, 1.75, depth));
        float groove = smoothstep(1.4, 2.0, depth) * (1.0 - smoothstep(2.2, 3.2, depth));
        float3 rgb = base + lit * crest * mix(0.04, 0.16, dark)
                         - shade * crest * mix(0.08, 0.028, dark)
                         - outer * mix(0.11, 0.07, dark)
                         - groove * mix(0.025, 0.045, dark);
        return clamp(rgb, 0.0, 1.0);
    }
""".trimIndent()

internal val NavigationBevelProgram = """
    uniform float2 size;
    uniform float px;
    uniform float dark;
    ${NavigationBevelFunctions}
    half4 main(float2 point) {
        float2 halfSize = size * 0.5;
        float radius = min(halfSize.x, halfSize.y);
        float2 segment = max(halfSize - radius, 0.0);
        float2 radial = point - halfSize;
        radial -= clamp(radial, -segment, segment);
        float radialLength = length(radial);
        float distance = radialLength - radius;
        // The Glass face and its exterior remain untouched by this overlay.
        if (distance > 0.0) return half4(0.0);
        float depth = max(-distance, 0.0) / max(px, 0.001);
        if (depth >= 3.2) return half4(0.0);
        float aa = max(px * 0.35, 0.65);
        float coverage = 1.0 - smoothstep(-aa, aa, distance);
        float alpha = coverage * (1.0 - smoothstep(2.4, 3.2, depth));
        float2 normal = radial / max(radialLength, 0.001);
        float3 reflected = navigationBevel(depth, normal, dark);
        return half4(half3(reflected * alpha), half(alpha));
    }
""".trimIndent()
