package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.blur.highlight.Highlight

/** Panel optics and pressed lens optics are distinct; the lens receives the composed ink scene. */
@Composable
internal fun miuixNavigationGlass(
    modifier: Modifier, shape: RoundedCornerShape, bubble: Boolean = false,
    source: Backdrop? = null, pressure: () -> Float = { 0f }, rim: () -> Highlight? = { null },
    castShadow: Boolean = true,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val colors = AppTheme.colors
    val shadowRadius = with(LocalDensity.current) { 6.dp.toPx() }
    val shadowOffset = with(LocalDensity.current) { 2.dp.toPx() }
    val backdrop = if (AppTheme.accessibility.reduceTransparency) null else source ?: LocalMiuixBackdrop.current
    val fill = if (bubble) colors.fill else colors.secondaryBackground
    val panelSheen = remember(colors.isDark) {
        Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (colors.isDark) 0.045f else 0.16f),
            Color.Transparent,
            Color.Black.copy(alpha = if (colors.isDark) 0.05f else 0.025f),
        ))
    }
    val layer: GraphicsLayerScope.() -> Unit = {
        layerBlock?.invoke(this)
        this.shape = shape
        // Clip the optical recording itself: its padded samples must not leak below the bar.
        clip = true
    }
    if (backdrop == null) {
        return modifier.graphicsLayer(layer).clip(shape).background(fill)
            .border(0.8.dp, colors.label.copy(alpha = if (colors.isDark) 0.14f else 0.08f), shape)
    }
    // Light surfaces use an outer shadow. In dark mode the rim defines depth without a second silhouette.
    // The moving lens gets depth internally.
    val panel = if (castShadow && !bubble && !colors.isDark) modifier.dropShadow(shape, Shadow(
        radius = 10.dp, offset = androidx.compose.ui.unit.DpOffset(0.dp, 3.dp),
        color = Color.Black.copy(alpha = 0.10f),
    )) else modifier
    val optics = panel.drawBackdrop(
        backdrop = backdrop, shape = { shape }, layerBlock = layer,
        effects = {
            padding = maxOf(padding, 22.dp.toPx())
            // Music covers contain much stronger edges than the reference's settings pages.
            // Diffuse those edges in the base; preserve sharp glyph refraction inside the lens.
            if (!bubble) { blur(16.dp.toPx()); colorControls(contrast = 0.90f, saturation = 1.10f) }
            // Only the lens bends with pressure. The panels must not read it here: every read
            // re-runs this block, which rebuilds their blur chain on each animation frame.
            val p = if (bubble) pressure().coerceIn(0f, 1f) else 0f
            val scale = downscaleFactor.toFloat().coerceAtLeast(1f)
            val boxWidth = size.width / scale
            val boxHeight = size.height / scale
            val pad = padding / scale
            val band = (if (bubble) 11.dp else 6.dp).toPx() / scale
            // Keep the rim sampling monotonic, including the chromatic fringe. A larger
            // bend folds the ink scene back onto itself and repeats labels at the edge.
            val bend = band * 0.35f * (if (bubble) p else 1f)
            val dispersion = if (bubble) 0.8.dp.toPx() * p / scale else 0f
            runtimeShaderEffect("SonglassNavOpticsV2", CapsuleRefraction, "page") {
                setFloatUniform("bounds", boxWidth, boxHeight)
                setFloatUniform("padding", pad)
                setFloatUniform("bend", bend)
                setFloatUniform("band", band)
                setFloatUniform("dispersion", dispersion)
                setFloatUniform("zoom", if (bubble) p * 0.025f else 0f)
            }
        },
        highlight = { rim()?.copy(alpha = if (bubble) pressure().coerceIn(0f, 1f) else 0.65f) },
        onDrawSurface = {
            val p = pressure().coerceIn(0f, 1f)
            if (bubble) {
                drawRect(colors.label.copy(alpha = 0.065f * (1f - p)))
                drawRect(Color.Black.copy(alpha = 0.025f * p))
            } else {
                drawRect(fill.copy(alpha = (if (colors.isDark) 0.68f else 0.74f) - 0.08f * p))
                // Shape-bound light gives the resting panel depth without another blur layer.
                drawRect(panelSheen)
            }
        },
    )
    return if (!bubble) optics else optics.innerShadow(shape) {
        val p = pressure().coerceIn(0f, 1f)
        radius = shadowRadius * p
        offset = Offset(0f, shadowOffset * p)
        color = Color.Black.copy(alpha = 0.14f * p)
        alpha = p
    }
}

// Original Songlass distance-field lens. The red/blue split adds a small edge fringe while held.
private const val CapsuleRefraction = """
uniform shader page;
uniform float2 bounds;
uniform float padding;
uniform float bend;
uniform float band;
uniform float dispersion;
uniform float zoom;
half4 main(float2 coord) {
    if (bend < 0.001 && zoom < 0.001) return page.eval(coord);
    float2 center = bounds * 0.5 + float2(padding);
    float2 p = coord - center;
    float2 halfSize = bounds * 0.5;
    float radius = min(halfSize.x, halfSize.y);
    float2 q = abs(p) - (halfSize - float2(radius));
    float2 outside = max(q, float2(0.0));
    float distanceToRim = length(outside) + min(max(q.x, q.y), 0.0) - radius;
    float edge = smoothstep(-max(band, 0.001), 0.0, distanceToRim);
    float2 normal = sign(p) * outside / max(length(outside), 0.001);
    float2 uv = coord - p * zoom - normal * bend * edge * edge;
    half4 color = page.eval(uv);
    float2 fringe = normal * dispersion * edge;
    if (dispersion > 0.001) {
        color.r = page.eval(uv + fringe).r;
        color.b = page.eval(uv - fringe).b;
    }
    return color;
}
"""

/**
 * The panel's light under the moving lens, drawn just above the panel surface and below its
 * content. It has its own layer: following the lens every frame must not redraw the blurred panel.
 */
@Composable
internal fun NavigationGlow(pressure: () -> Float, spot: () -> Float, modifier: Modifier) {
    Box(modifier.graphicsLayer {}.drawBehind {
        val p = pressure().coerceIn(0f, 1f)
        if (p > 0f) {
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.13f * p), Color.Transparent),
                center = Offset(size.width * spot().coerceIn(0f, 1f), size.height * 0.45f), radius = size.height * 1.4f))
        }
    })
}
