package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.TrimMemoryLevel
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectLayoutScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.hazeEffect
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlin.math.sqrt

internal expect val unifiedNavigationSupported: Boolean
internal expect fun createNavigationShader(): NavigationShader?

/** Mutable GPU resources belong to a single effect node, never the shared factory. */
internal interface NavigationShader {
    fun effect(pill: FloatArray, search: FloatArray, smoothing: Float, density: Float, dark: Boolean): RenderEffect
}

internal data class NavigationGlassStyle(val link: LiquidLink, val dark: Boolean)

@Composable
internal fun Modifier.unifiedNavigationGlass(link: LiquidLink): Modifier {
    val source = LocalGlassSource.current ?: return this
    val dark = AppTheme.colors.isDark
    val style = remember(link, dark) { NavigationGlassStyle(link, dark) }
    return hazeEffect(NavigationGlassFactory, HazeInput.Sources(source), style, HazeSampling.FullResolution)
}

private object NavigationGlassFactory : HazeEffectFactory<NavigationGlassStyle> {
    override fun createRenderer() = NavigationGlassRenderer()
}

private class NavigationGlassRenderer : HazeEffectRenderer<NavigationGlassStyle> {
    private var context: GraphicsContext? = null
    private var input: GraphicsLayer? = null
    private var shader: NavigationShader? = null
    private var attemptedShader = false
    private val pill = FloatArray(4)
    private val search = FloatArray(4)

    // A fixed input margin keeps capture geometry independent of the moving contour.
    override fun HazeEffectLayoutScope.calculateLayerBounds(style: NavigationGlassStyle): Rect =
        modifierBounds.inflate(32.dp.toPx())

    override fun HazeEffectDrawScope.draw(style: NavigationGlassStyle) {
        if (!attemptedShader) {
            shader = createNavigationShader()
            attemptedShader = true
        }
        val bounds = modifierBounds
        val h = bounds.height
        val gap = 8.dp.toPx()
        val pillWidth = bounds.width - h - gap
        if (pillWidth <= 0f || h <= 0f) return
        val left = style.link.bodies[LinkSide.Start]
        val right = style.link.bodies[LinkSide.End]
        val ls = left?.scale() ?: 1f
        val rs = right?.scale() ?: 1f
        val lo = left?.offset(style.link, LinkSide.Start) ?: Offset.Zero
        val ro = right?.offset(style.link, LinkSide.End) ?: Offset.Zero
        val rtl = layoutDirection == LayoutDirection.Rtl
        pill[0] = (if (rtl) bounds.right - pillWidth / 2f else bounds.left + pillWidth / 2f) + lo.x
        pill[1] = bounds.top + h / 2f + lo.y
        pill[2] = (pillWidth - h) / 2f * ls
        pill[3] = h / 2f * ls
        search[0] = (if (rtl) bounds.left + h / 2f else bounds.right - h / 2f) + ro.x
        search[1] = bounds.top + h / 2f + ro.y
        search[2] = 0f
        search[3] = h / 2f * rs
        val smoothing = 48.dp.toPx() * sqrt(style.link.merge().coerceIn(0f, 1f))
        val optical = shader
        if (optical == null) {
            // Shader construction failure must still leave a readable navigation surface.
            val fill = if (style.dark) Color(0xFF252527) else Color(0xFFF0F0F3)
            drawRoundRect(
                fill, Offset(pill[0] - bounds.left - pillWidth / 2f, 0f),
                Size(pillWidth, h), CornerRadius(h / 2f),
            )
            drawCircle(fill, h / 2f, Offset(search[0] - bounds.left, h / 2f))
            return
        }
        val graphics = currentValueOf(LocalGraphicsContext)
        if (context !== graphics) {
            dispose()
            context = graphics
        }
        val layer = input ?: graphics.createGraphicsLayer().also { input = it }
        val sourceScope = this
        val captureSize = Size(bounds.width + 2f * bounds.left, bounds.height + 2f * bounds.top)
        layer.record(size = captureSize.roundToIntSize()) {
            sourceScope.draw(this, layoutDirection, drawContext.canvas, size) {
                sourceScope.drawInput()
            }
        }
        layer.renderEffect = optical.effect(pill, search, smoothing, density, style.dark)
        translate(-bounds.left, -bounds.top) { drawLayer(layer) }
    }

    override fun onTrimMemory(level: TrimMemoryLevel) = dispose()

    override fun dispose() {
        input?.let { context?.releaseGraphicsLayer(it) }
        input = null
        context = null
    }
}

/**
 * Original two-capsule optical model. One distance field controls coverage, refraction and
 * lighting, including the connecting waist. No code or constants from the native Apple study.
 */
internal val NavigationGlassProgram = """
    uniform shader backdrop;
    uniform float4 pill;
    uniform float4 search;
    uniform float smoothing;
    uniform float px;
    uniform float dark;

    // Distance and its analytic gradient: only two lengths per surface evaluation.
    float3 capsule(float2 p, float4 body) {
        float2 q = p - body.xy;
        q.x -= clamp(q.x, -body.z, body.z);
        float lengthQ = length(q);
        return float3(lengthQ - body.w, q / max(lengthQ, 0.001));
    }
    float3 surface(float2 p) {
        float3 a = capsule(p, pill);
        float3 b = capsule(p, search);
        float k = max(smoothing, 0.001);
        float overlap = max(1.0 - abs(a.x-b.x)/k, 0.0);
        float weightA = clamp(0.5 + (b.x-a.x)/(2.0*k), 0.0, 1.0);
        return float3(min(a.x,b.x) - k * overlap * overlap * 0.25,
                      mix(b.yz, a.yz, weightA));
    }
    half4 main(float2 p) {
        float3 geometry = surface(p);
        float d = geometry.x;
        float aa = max(px * 0.35, 0.65);
        if (d > 10.0 * px) return half4(0.0);
        float coverage = 1.0 - smoothstep(-aa, aa, d);
        // A bounded contact shadow follows the same contour, including the waist.
        float shadow = 0.0;
        if (d > -aa) {
            float shadowDepth = max(surface(p-float2(0.0,1.5*px)).x / px, 0.0);
            shadow = 0.10 * exp(-shadowDepth / 2.5);
        }
        if (coverage <= 0.0) return half4(0.0,0.0,0.0,half(shadow));
        float2 normal = geometry.yz / max(length(geometry.yz), 0.001);
        float depth = max(-d, 0.0) / px;
        float band = 1.0 - smoothstep(0.0, 10.0, depth);
        // Refraction and edge light share the normal of the complete, fused shape.
        float2 ray = normal * (3.8 * px * band * band);
        half4 sample = backdrop.eval(p - ray);
        float3 rgb = float3(sample.rgb) / max(float(sample.a), 0.001);
        float luminance = dot(rgb, float3(0.2126,0.7152,0.0722));
        rgb = mix(float3(luminance), rgb, 1.04);
        float3 base = mix(float3(0.98), float3(0.08), dark);
        rgb = mix(rgb, base, mix(0.56, 0.48, dark));
        float topLight = max(-normal.y, 0.0);
        float bottomLight = max(normal.y, 0.0) * 0.45;
        float rim = exp(-depth / 0.65);
        float soft = exp(-depth / 4.0);
        rgb += (topLight + bottomLight) * (rim * 0.20 + soft * 0.035);
        rgb -= (1.0-abs(normal.y)) * rim * 0.035;
        rgb = clamp(rgb, 0.0, 1.0);
        return half4(half3(rgb * coverage), half(coverage + shadow * (1.0-coverage)));
    }
""".trimIndent()
