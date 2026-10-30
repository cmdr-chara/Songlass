package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import dev.cmdrchara.songlass.designsystem.theme.BlurStrength
import dev.cmdrchara.songlass.designsystem.theme.LocalBlurStrength
import kotlin.math.sqrt

internal expect val unifiedNavigationSupported: Boolean
internal expect fun createNavigationShader(): NavigationShader?

internal const val NavigationBackdropBlurDp = 6.5f

/** Mutable GPU resources belong to a single effect node, never the shared factory. */
internal interface NavigationShader {
    fun effect(frame: NavigationOpticsFrame): RenderEffect
}

/** Reused frame uniforms, owned by one renderer; never passed as Haze style state. */
internal class NavigationOpticsFrame {
    val pill = FloatArray(4)
    val search = FloatArray(4)
    val pillLens = FloatArray(4)
    val searchLens = FloatArray(4)
    val lensOpacity = FloatArray(2)
    val lensPressure = FloatArray(2)
    var smoothing = 0f
    var density = 1f
    var blurRadiusPx = NavigationBackdropBlurDp
    var dark = false
    var metal = false
    var phase = 0f
}

internal data class NavigationGlassStyle(
    val link: LiquidLink,
    val dark: Boolean,
    val blur: BlurStrength,
    val separateSearch: Boolean,
    val metal: Boolean,
    val phase: State<Float>,
)

@Composable
internal fun Modifier.unifiedNavigationGlass(link: LiquidLink, separateSearch: Boolean): Modifier {
    val dark = AppTheme.colors.isDark
    val blur = LocalBlurStrength.current
    val metal = LocalLiquidFinish.current == LiquidFinish.Metal
    val phase = rememberMetalPhase()
    val style = remember(link, dark, blur, separateSearch, metal, phase) {
        NavigationGlassStyle(link, dark, blur, separateSearch, metal, phase)
    }
    val source = LocalGlassSource.current ?: return this
    return hazeEffect(NavigationGlassFactory, HazeInput.Sources(source), style, HazeSampling.FullResolution)
}

/** Both finishes share the complete Glass optics and geometry. */
private fun DrawScope.populateNavigationFrame(
    style: NavigationGlassStyle,
    bounds: Rect,
    frame: NavigationOpticsFrame,
): Boolean {
    val pill = frame.pill
    val search = frame.search
    val h = bounds.height
    val gap = 8.dp.toPx()
    val pillWidth = if (style.separateSearch) bounds.width - h - gap else bounds.width
    if (pillWidth <= 0f || h <= 0f) return false
    val left = style.link.bodies[LinkSide.Start]
    val right = if (style.separateSearch) style.link.bodies[LinkSide.End] else null
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
    search[3] = if (style.separateSearch) h / 2f * rs else 0f
    fun placeLens(side: LinkSide, body: FloatArray, bodyWidth: Float, scale: Float, target: FloatArray, index: Int) {
        val lens = style.link.lenses[side]
        val rect = lens?.bounds()
        val opacity = lens?.opacity()?.coerceIn(0f, 1f) ?: 0f
        frame.lensPressure[index] = lens?.pressure()?.coerceIn(0f, 1f) ?: 0f
        frame.lensOpacity[index] = if (rect != null && rect.width > 0f) opacity else 0f
        if (rect == null || opacity <= 0f) return
        target[0] = body[0] + (rect.center.x - bodyWidth / 2f) * scale
        target[1] = body[1] + (rect.center.y - h / 2f) * scale
        target[3] = rect.height / 2f * scale
        target[2] = ((rect.width - rect.height) / 2f * scale).coerceAtLeast(0f)
    }
    placeLens(LinkSide.Start, pill, pillWidth, ls, frame.pillLens, 0)
    // A live layout switch may briefly retain the old circle's registration.
    // Clear its lens explicitly so integrated Search has exactly one selection.
    frame.lensOpacity[1] = 0f
    frame.lensPressure[1] = 0f
    if (style.separateSearch) placeLens(LinkSide.End, search, h, rs, frame.searchLens, 1)
    frame.smoothing = if (style.separateSearch) {
        48.dp.toPx() * sqrt(style.link.merge().coerceIn(0f, 1f))
    } else 0f
    frame.density = density
    frame.blurRadiusPx = style.blur.scale(NavigationBackdropBlurDp.dp).toPx()
    frame.dark = style.dark
    frame.metal = style.metal
    frame.phase = if (style.metal) style.phase.value else 0f
    return true
}

private fun DrawScope.drawNavigationFallback(
    style: NavigationGlassStyle,
    bounds: Rect,
    frame: NavigationOpticsFrame,
) {
    val pill = frame.pill
    val search = frame.search
    val h = bounds.height
    val pillWidth = if (style.separateSearch) bounds.width - h - 8.dp.toPx() else bounds.width
    // Shader construction failure must still leave a readable navigation surface.
    val fill = if (style.dark) Color(0xFF252527) else Color(0xFFF0F0F3)
    drawRoundRect(
        fill, Offset(pill[0] - bounds.left - pillWidth / 2f, 0f),
        Size(pillWidth, h), CornerRadius(h / 2f),
    )
    if (style.separateSearch) drawCircle(fill, h / 2f, Offset(search[0] - bounds.left, h / 2f))
    for (index in 0..1) {
        val lens = if (index == 0) frame.pillLens else frame.searchLens
        val opacity = frame.lensOpacity[index]
        if (opacity <= 0f) continue
        val radius = lens[3]
        drawRoundRect(
            (if (style.dark) Color.White else Color.Black).copy(alpha = 0.12f * opacity),
            Offset(lens[0]-lens[2]-radius-bounds.left, lens[1]-radius-bounds.top),
            Size((lens[2]+radius)*2f, radius*2f), CornerRadius(radius),
        )
    }
}

private object NavigationGlassFactory : HazeEffectFactory<NavigationGlassStyle> {
    override fun createRenderer() = NavigationGlassRenderer()
}

private class NavigationGlassRenderer : HazeEffectRenderer<NavigationGlassStyle> {
    private var context: GraphicsContext? = null
    private var input: GraphicsLayer? = null
    private var shader: NavigationShader? = null
    private var attemptedShader = false
    private val frame = NavigationOpticsFrame()

    // A fixed input margin keeps capture geometry independent of the moving contour.
    override fun HazeEffectLayoutScope.calculateLayerBounds(style: NavigationGlassStyle): Rect =
        modifierBounds.inflate(32.dp.toPx())

    override fun HazeEffectDrawScope.draw(style: NavigationGlassStyle) {
        if (!attemptedShader) {
            shader = createNavigationShader()
            attemptedShader = true
        }
        val bounds = modifierBounds
        if (!populateNavigationFrame(style, bounds, frame)) return
        val optical = shader
        if (optical == null) {
            drawNavigationFallback(style, bounds, frame)
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
            // Preserve the recording parent when redirecting Haze's draw scope. Otherwise
            // nested source layers are untracked and may be discarded while still sampled.
            sourceScope.draw(this, layoutDirection, drawContext.canvas, size, graphicsLayer = layer) {
                sourceScope.drawInput()
            }
        }
        layer.renderEffect = optical.effect(frame)
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
 * Original capsule optical model with an optional Search circle. One distance field controls coverage, refraction and
 * lighting, including the connecting waist. No code or constants from the native Apple study.
 */
internal val NavigationGlassProgram = """
    uniform shader backdrop;
    uniform float4 pill;
    uniform float4 search;
    uniform float4 pillLens;
    uniform float4 searchLens;
    uniform float2 lensOpacity;
    uniform float2 lensPressure;
    uniform float smoothing;
    uniform float px;
    uniform float dark;
    uniform float metal;
    uniform float phase;
    $NavigationMetalFunctions
    $NavigationBevelFunctions

    // Distance and its analytic gradient: only two lengths per surface evaluation.
    float3 capsule(float2 p, float4 body) {
        float2 q = p - body.xy;
        q.x -= clamp(q.x, -body.z, body.z);
        float lengthQ = length(q);
        return float3(lengthQ - body.w, q / max(lengthQ, 0.001));
    }
    float3 surface(float2 p) {
        float3 a = capsule(p, pill);
        // The same material also draws the complete three-tab capsule. A zero-radius
        // Search body is absent, not an extra shape or seam inside the bar.
        if (search.w <= 0.0) return a;
        float3 b = capsule(p, search);
        float k = max(smoothing, 0.001);
        float overlap = max(1.0 - abs(a.x-b.x)/k, 0.0);
        float weightA = clamp(0.5 + (b.x-a.x)/(2.0*k), 0.0, 1.0);
        return float3(min(a.x,b.x) - k * overlap * overlap * 0.25,
                      mix(b.yz, a.yz, weightA));
    }
    // The captured page can be transparent at the safe-area edge. Composite that sample
    // onto the theme background before using it as radiance; unpremultiplying zero alpha
    // would otherwise create a black stripe inside the lower bevel.
    float3 pageSample(float2 p) {
        half4 sampleColor = backdrop.eval(p);
        float alpha = clamp(float(sampleColor.a),0.0,1.0);
        return float3(sampleColor.rgb) + float3(1.0-dark)*(1.0-alpha);
    }
    // Same filtered page input for the body, lifted lens and nearby-colour response.
    float3 material(float2 p, float3 geometry, float lifted, float bodyEdge) {
        float2 normal = geometry.yz / max(length(geometry.yz), 0.001);
        float depth = max(-geometry.x, 0.0) / px;
        // Exponential bevel falloff and gradient confidence keep the fused waist smooth.
        float confidence = clamp(length(geometry.yz),0.0,1.0);
        float band = exp(-depth / mix(4.0,5.5,lifted));
        float2 ray = normal * (mix(4.8,8.0,lifted) * px * band * confidence);
        float3 rgb = pageSample(p - ray);
        if (lifted > 0.01 && band > 0.01) {
            float2 fringe = normal * (0.65 * px * lifted * band);
            rgb.r = pageSample(p - ray + fringe).r;
            rgb.b = pageSample(p - ray - fringe).b;
        }
        float luminance = dot(rgb, float3(0.2126,0.7152,0.0722));
        rgb = mix(float3(luminance), rgb, 1.04);
        // A restrained smoked base gives dark glass definition even over black content.
        float3 base = mix(float3(0.98 + lifted*0.02), float3(0.12 + lifted*0.10), dark);
        // Regular glass adapts to the same blurred sample it refracts. A fixed tint lets
        // bright artwork wash out light labels in dark appearance (and vice versa).
        // Preserve colour on compatible backgrounds; add diffusion only where needed.
        // Pressure changes the lens depth, but never removes this legibility response.
        float contrastRisk = mix(1.0-smoothstep(0.18,0.72,luminance),
                                 smoothstep(0.15,0.70,luminance), dark);
        float restingTint = mix(0.46,0.42,dark) - lifted*0.08;
        float protectiveTint = mix(0.72,0.78,dark);
        float tint = mix(restingTint,protectiveTint,contrastRisk*(1.0-lifted*0.15));
        rgb = mix(rgb, base, tint);
        // One nearby-colour sample at the contour; no second blur or spectral tap loop.
        if (depth < 8.0) {
            float3 nearby = pageSample(p + normal * (7.0 * px));
            rgb = mix(rgb, nearby, exp(-depth / 3.0) * 0.10);
        }
        float topLight = max(-normal.y, 0.0);
        float bottomLight = max(normal.y, 0.0) * 0.45;
        float rim = exp(-depth / 0.65);
        float soft = exp(-depth / 4.0);
        // A slightly clearer directional rim keeps dark glass visible on black pages.
        // Keep the light appearance and the raised lens highlight unchanged.
        float restingRim = mix(0.10,0.13,dark);
        float directional = pow(max(dot(normal,normalize(float2(-0.45,-1.0))),0.0),3.0);
        rgb += (directional + bottomLight*0.55) * (rim * mix(restingRim,0.23,lifted) + soft * 0.018);
        // A narrow inner caustic separates thickness from the outer silhouette.
        rgb += exp(-pow((depth-1.7)/0.8,2.0)) * directional * 0.035;
        rgb -= (1.0-abs(normal.y)) * rim * 0.035;
        // Define light glass on a white page without restoring an exterior shadow.
        rgb -= rim * (1.0-dark) * 0.07 * bodyEdge;
        return clamp(rgb, 0.0, 1.0);
    }
    half4 lensLayer(float2 p, float4 body, float opacity, float pressure, half4 behind) {
        if (opacity <= 0.001) return behind;
        float3 geometry = capsule(p, body);
        float aa = max(px * 0.35, 0.65);
        if (geometry.x > 5.0 * px) return behind;
        float coverage = (1.0-smoothstep(-aa,aa,geometry.x)) * opacity;
        float shadow = 0.0;
        if (geometry.x > -aa) {
            float depth = max(capsule(p-float2(0.0,px),body).x / px, 0.0);
            shadow = exp(-depth/1.5) * 0.025 * opacity;
        }
        half4 under = half4(behind.rgb * half(1.0-shadow), behind.a + half(shadow)*(half(1.0)-behind.a));
        if (coverage <= 0.0) return under;
        // A shallow neutral lens keeps the indicator inside the capsule visually.
        // Retain the shared refracted input without the raised-button white rim.
        float3 rgb = material(p,geometry,pressure,0.0);
        float3 selectionTint = mix(float3(0.0),float3(1.0),dark);
        rgb = mix(rgb,selectionTint,mix(0.10,0.12,dark)*(1.0-pressure));
        // Protect the accent ink in the core, including over black/white artwork.
        // These bounds derive from Songlass's own light/dark accent colours. Keep
        // the thin optical contour free to reflect colour and reveal the lens depth.
        float3 bounded;
        if (dark > 0.5) {
            float peak = max(rgb.r,max(rgb.g,rgb.b));
            bounded = rgb * min(1.0,0.18/max(peak,0.001));
        } else {
            float trough = min(rgb.r,min(rgb.g,rgb.b));
            float whiteWeight = clamp((0.89-trough)/max(1.0-trough,0.001),0.0,1.0);
            bounded = mix(rgb,float3(1.0),whiteWeight);
        }
        float core = smoothstep(1.5,5.0,max(-geometry.x,0.0)/px);
        rgb = mix(rgb,bounded,core);
        half4 glass = half4(half3(rgb*coverage),half(coverage)) + under * half(1.0-coverage);
        if (metal > 0.5) {
            float depth = max(-geometry.x,0.0)/px;
            if (depth < 2.65) {
                float alpha = coverage * (1.0-smoothstep(2.0,2.5,depth));
                if (alpha > 0.0) {
                    float2 halfSize = float2(body.z+body.w,body.w);
                    float3 reflected = navigationMetal(p-body.xy+halfSize,halfSize,max(halfSize.x,halfSize.y),phase);
                    return half4(half3(reflected*alpha),half(alpha)) + glass*half(1.0-alpha);
                }
            }
        }
        return glass;
    }
    half4 main(float2 p) {
        float3 geometry = surface(p);
        float d = geometry.x;
        float aa = max(px * 0.35, 0.65);
        float coverage = 1.0-smoothstep(-aa,aa,d);
        // The capsule reads from its illuminated contour rather than a dark halo.
        half4 color = half4(0.0);
        if (coverage > 0.0) {
            float3 rgb = material(p,geometry,0.0,1.0);
            // The approved exterior bevel is only a coating on the original Glass contour.
            float depth = max(-d,0.0)/px;
            if (metal > 0.5 && depth < 3.2) {
                float rim = 1.0-smoothstep(2.4,3.2,depth);
                rgb = mix(rgb,navigationBevel(depth,geometry.yz,dark),rim);
            }
            color = half4(half3(rgb*coverage),half(coverage));
        }
        color = lensLayer(p,pillLens,lensOpacity.x,lensPressure.x,color);
        return lensLayer(p,searchLens,lensOpacity.y,lensPressure.y,color);
    }
""".trimIndent()
