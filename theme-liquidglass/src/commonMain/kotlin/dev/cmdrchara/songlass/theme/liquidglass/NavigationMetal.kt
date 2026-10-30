/*
 * Portions adapted from Paper Shaders by Paper Design, Apache License 2.0.
 * https://github.com/paper-design/shaders/tree/1923e9f536ea079e2a95d23291cbfacfd69d37f6
 * Source: packages/shaders/src/shaders/liquid-metal.ts
 * SHA-256: 626419e92474cacbfb4f77bc8be6a80414bd2a10bf3e4da4f05ab8d591e64e4d
 * Source: packages/shaders/src/shader-utils.ts (simplexNoise)
 * SHA-256: 5805b80c74323b0a8fce8c8c3628b17372014f7417d0c7de3346fa532c6c903a
 * License: https://www.apache.org/licenses/LICENSE-2.0
 *
 * Modified for Songlass, 2026: GLSL to AGSL/SkSL; shape=none only; fixed Vengeance
 * material settings; analytic pixel softening in place of fragment derivatives;
 * periodic noise trajectory and integer stripe-cycle time; capsule ring coverage.
 * Image processing, textures, tinting and all unused shape branches are omitted.
 * Distributed under the License on an AS IS basis, without warranties or conditions.
 */
package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

internal interface NavigationRingShader {
    fun brush(width: Float, height: Float, density: Float, phase: Float, dark: Boolean): Brush
}

internal expect fun createNavigationRingShader(): NavigationRingShader?

private val NavigationRingCapsule = RoundedCornerShape(percent = 50)

/** Opaque neutral selection with a narrow animated rim; no backdrop or extra render layer. */
@Composable
internal fun Modifier.navigationMetalRing(): Modifier {
    val dark = AppTheme.colors.isDark
    val core = if (dark) Color.Black else Color.White
    val shader = remember { createNavigationRingShader() }
    val phase = if (shader != null) rememberMetalPhase() else null
    return clip(NavigationRingCapsule).background(core).drawWithCache {
        val width = size.width
        val height = size.height
        val rimColor = if (dark) Color(0xFF8C8C91) else Color(0xFF77777C)
        val inset = density * 0.5f
        val rimSize = Size((width - density).coerceAtLeast(0f), (height - density).coerceAtLeast(0f))
        val radius = minOf(rimSize.width, rimSize.height) * 0.5f
        val rimStroke = Stroke(density)
        onDrawBehind {
            if (shader != null) {
                // State is observed here only; a frame invalidates drawing, not composition.
                drawRect(shader.brush(width, height, density, phase?.value ?: 0f, dark))
            } else {
                drawRoundRect(
                    color = rimColor,
                    topLeft = Offset(inset, inset),
                    size = rimSize,
                    cornerRadius = CornerRadius(radius),
                    style = rimStroke,
                )
            }
        }
    }
}

/**
 * Paper's shape=none reflection path, with Vengeance's repetition=4, distortion=.15,
 * softness=0, shiftRed=.3, shiftBlue=-.3, angle=45 and Paper's default contour=.4.
 * point is in the capsule's local coordinates; center is its half-size and radius
 * is max(center.x, center.y), retaining Paper's isotropic larger-dimension mapping.
 * Call only for ring pixels: the neutral interior does not need noise or reflection work.
 */
internal val NavigationMetalFunctions = """
    float3 navMetalPermute(float3 x) {
        return mod(((x * 34.0) + 1.0) * x, 289.0);
    }
    float navMetalNoise(float2 v) {
        const float4 C = float4(0.211324865405187, 0.366025403784439,
                               -0.577350269189626, 0.024390243902439);
        float2 i = floor(v + dot(v, C.yy));
        float2 x0 = v - i + dot(i, C.xx);
        float2 i1 = x0.x > x0.y ? float2(1.0, 0.0) : float2(0.0, 1.0);
        float4 x12 = x0.xyxy + C.xxzz;
        x12.xy -= i1;
        i = mod(i, 289.0);
        float3 p = navMetalPermute(navMetalPermute(i.y + float3(0.0, i1.y, 1.0))
                                  + i.x + float3(0.0, i1.x, 1.0));
        float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy),
                                  dot(x12.zw, x12.zw)), 0.0);
        m = m * m;
        m = m * m;
        float3 x = 2.0 * fract(p * C.www) - 1.0;
        float3 h = abs(x) - 0.5;
        float3 ox = floor(x + 0.5);
        float3 a0 = x - ox;
        m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
        float3 g;
        g.x = a0.x * x0.x + h.x * x0.y;
        g.yz = a0.yz * x12.xz + h.yz * x12.yw;
        return 130.0 * dot(m, g);
    }
    float2 navMetalRotate(float2 uv, float angle) {
        float c = cos(angle);
        float s = sin(angle);
        return float2(c * uv.x - s * uv.y, s * uv.x + c * uv.y);
    }
    float navMetalBands(float c1, float c2, float stripe, float3 w, float blur, float bump) {
        float ch = mix(c2, c1, smoothstep(0.0, 2.0 * blur, stripe));
        float border = w.x;
        ch = mix(ch, c2, smoothstep(border, border + 2.0 * blur, stripe));
        border = w.x + 0.4 * (1.0 - bump) * w.y;
        ch = mix(ch, c1, smoothstep(border, border + 2.0 * blur, stripe));
        border = w.x + 0.5 * (1.0 - bump) * w.y;
        ch = mix(ch, c2, smoothstep(border, border + 2.0 * blur, stripe));
        border = w.x + w.y;
        ch = mix(ch, c1, smoothstep(border, border + 2.0 * blur, stripe));
        float gradientT = (stripe - w.x - w.y) / w.z;
        float gradient = mix(c1, c2, smoothstep(0.0, 1.0, gradientT));
        return mix(ch, gradient, smoothstep(border, border + 0.5 * blur, stripe));
    }
    float3 navigationMetal(float2 point, float2 center, float radius, float phase) {
        float2 halfSize = max(center, float2(1.0));
        float scale = max(2.0 * radius, 1.0);
        float2 uv = (point - center) / scale + 0.5;
        uv.y = 1.0 - uv.y;
        // Paper's internal rotation is (-angle + 70) degrees, hence 25 degrees.
        float2 rotatedUV = navMetalRotate(uv - 0.5, 0.4363323129985824) + 0.5;
        float2 borderUV = point / (2.0 * halfSize);
        float2 mask = min(borderUV, 1.0 - borderUV);
        float2 thickness = min(float2(250.0) / (2.0 * halfSize), float2(0.5));
        float maskX = pow(smoothstep(0.0, thickness.x, mask.x), 0.25);
        float maskY = pow(smoothstep(0.0, thickness.y, mask.y), 0.25);
        float edge = 1.2 * clamp(1.0 - maskX * maskY, 0.0, 1.0);
        float diagBLtoTR = rotatedUV.x - rotatedUV.y;
        float diagTLtoBR = rotatedUV.x + rotatedUV.y;
        float3 color1 = float3(0.98, 0.98, 1.0);
        float3 color2 = float3(0.1, 0.1, 0.1 + 0.1 * smoothstep(0.7, 1.3, diagTLtoBR));
        float2 gradUV = uv - 0.5;
        float dist = length(gradUV + float2(0.0, 0.2 * diagBLtoTR));
        gradUV = navMetalRotate(gradUV, (0.25 - 0.2 * diagBLtoTR) * 3.141592653589793);
        float direction = gradUV.x;
        float bump = (1.0 - pow(1.8 * dist, 1.2)) * pow(max(uv.y, 0.0), 0.3);
        // shape=none doubles the configured repetition of four.
        float cycleWidth = 8.0;
        float thin1Ratio = 0.12 / cycleWidth * (1.0 - 0.4 * bump);
        float thin2Ratio = 0.07 / cycleWidth * (1.0 + 0.4 * bump);
        float3 w = float3(cycleWidth * thin1Ratio, cycleWidth * thin2Ratio,
                          1.0 - thin1Ratio - thin2Ratio);
        // The upstream unbounded diagonal translation is replaced with a circular
        // noise trajectory. Stripe travel is exactly one cycle per shared 2 PI phase;
        // both the field and its velocity therefore agree across the clock wrap.
        float2 trajectory = float2(sin(phase), cos(phase) - 1.0) * 0.55;
        float noise = navMetalNoise(uv - float2(0.84) - trajectory);
        edge += (1.0 - edge) * 0.15 * noise;
        direction += diagBLtoTR;
        float edgeT = smoothstep(0.0, 1.0, edge);
        direction -= 2.0 * noise * diagBLtoTR * edgeT * (1.0 - edgeT);
        // contour=.4 leaves the optional second-range edge-direction warp disabled.
        direction += 0.00512 * (1.0 - edgeT);
        bump *= clamp(pow(max(uv.y, 0.0), 0.1), 0.3, 1.0);
        direction *= 0.1 + (1.1 - edge) * bump;
        direction *= 0.4 + 0.6 * (1.0 - smoothstep(0.5, 1.0, edge));
        direction += 0.18 * smoothstep(0.1, 0.2, uv.y) * (1.0 - smoothstep(0.2, 0.4, uv.y));
        direction += 0.03 * smoothstep(0.1, 0.2, 1.0 - uv.y) * (1.0 - smoothstep(0.2, 0.4, 1.0 - uv.y));
        direction *= 0.5 + 0.5 * uv.y * uv.y;
        direction = direction * cycleWidth - (0.84 + phase / 6.283185307179586);
        float dispersion = clamp(1.0 - bump, 0.0, 1.0);
        float red = dispersion + 0.03 * bump * noise;
        red += 5.0 * smoothstep(-0.1, 0.2, uv.y) * (1.0 - smoothstep(0.1, 0.5, uv.y))
               * smoothstep(0.4, 0.6, bump) * (1.0 - smoothstep(0.4, 1.0, bump));
        red = (red - diagBLtoTR) * 0.015;
        float blue = 1.3 * dispersion;
        blue += smoothstep(0.0, 0.4, uv.y) * (1.0 - smoothstep(0.1, 0.8, uv.y))
                * smoothstep(0.4, 0.6, bump) * (1.0 - smoothstep(0.4, 0.8, bump));
        blue = (blue - 0.2 * edge) * -0.015;
        w.y -= 0.02 * smoothstep(0.0, 1.0, edge + bump);
        // Runtime effects do not expose GLSL fwidth. Approximate one physical pixel
        // analytically; this is antialiasing, while the requested softness stays zero.
        float blur = clamp(1.5 / max(radius, 1.0), 0.002, 0.05);
        return clamp(float3(
            navMetalBands(color1.r, color2.r, fract(direction + red), w, blur, bump),
            navMetalBands(color1.g, color2.g, fract(direction), w, blur, bump),
            navMetalBands(color1.b, color2.b, fract(direction - blue), w, blur, bump)
        ), 0.0, 1.0);
    }
""".trimIndent()

internal val NavigationRingProgram = """
    uniform float2 size;
    uniform float px;
    uniform float phase;
    uniform float dark;
    ${NavigationMetalFunctions}
    half4 main(float2 point) {
        float2 halfSize = size * 0.5;
        float radius = min(halfSize.x, halfSize.y);
        float2 q = abs(point - halfSize) - halfSize + radius;
        float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
        float depth = -distance;
        float ring = 2.25 * px;
        float3 core = dark > 0.5 ? float3(0.0) : float3(1.0);
        // The broad neutral face avoids all reflection and simplex work.
        if (depth > ring + 0.5) return half4(core, 1.0);
        float3 reflected = navigationMetal(point, halfSize, max(halfSize.x, halfSize.y), phase);
        float coverage = 1.0 - smoothstep(ring - 0.5, ring + 0.5, depth);
        return half4(mix(core, reflected, coverage), 1.0);
    }
""".trimIndent()
