package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalWindowInfo
import dev.cmdrchara.songlass.designsystem.foundation.DecorativeFrameCadence
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlin.math.PI

enum class LiquidFinish { Glass, Metal }
val LocalLiquidFinish = staticCompositionLocalOf { LiquidFinish.Glass }

/** A covered scaffold opts out while an overlay owns the visible controls. */
internal val LocalMetalMotionVisible = staticCompositionLocalOf { true }

/** Measured foreground zones opt out of the generic surface's conservative ink footprint. */
internal val LocalMetalInkProtection = staticCompositionLocalOf { true }

/** One clock per theme/window, allocated independently of any platform singleton. */
private class MetalMotionClock {
    var subscribers by mutableIntStateOf(0)
    val phase = mutableFloatStateOf(0f)
}

private val LocalMetalMotionClock = staticCompositionLocalOf<MetalMotionClock?> { null }

/** Composed materials share a draw-only clock. No subscribers means no frame callbacks. */
@Composable
internal fun ProvideLiquidMetalMotion(content: @Composable () -> Unit) {
    val clock = remember { MetalMotionClock() }
    AnimateMetalPhase(clock.phase, clock.subscribers > 0 && metalMotionEnabled())
    CompositionLocalProvider(LocalMetalMotionClock provides clock, content = content)
}

@Composable
private fun metalMotionEnabled(): Boolean =
    LocalLiquidFinish.current == LiquidFinish.Metal &&
        !AppTheme.accessibility.reduceMotion &&
        LocalWindowInfo.current.isWindowFocused

@Composable
private fun AnimateMetalPhase(phase: MutableFloatState, enabled: Boolean) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        // Re-anchor after focus changes rather than integrating time spent in the background.
        val cadence = DecorativeFrameCadence(withFrameNanos { it })
        while (true) {
            // One shared vsync callback; timestamp rounding never drops a 60/120 Hz
            // display to 20/24 material updates per second.
            val elapsed = cadence.elapsedSeconds(withFrameNanos { it }) ?: continue
            phase.floatValue = ((phase.floatValue + elapsed * 0.32f) % (2 * PI)).toFloat()
        }
    }
}

/** Callers keep the State object and read its value only in their draw callback. */
@Composable
internal fun rememberMetalPhase(): State<Float> {
    val clock = LocalMetalMotionClock.current
    val subscribed = LocalLiquidFinish.current == LiquidFinish.Metal &&
        LocalMetalMotionVisible.current
    if (clock != null) {
        DisposableEffect(clock, subscribed) {
            if (subscribed) clock.subscribers++
            onDispose { if (subscribed) clock.subscribers-- }
        }
        // An obscured material must not keep observing the shared phase while a visible
        // sheet has its own subscribers. It receives a stable state until shown again.
        val hiddenPhase = remember { mutableFloatStateOf(0f) }
        return if (subscribed) clock.phase else hiddenPhase
    }
    // Preview or isolated components outside LiquidGlassTheme remain self-contained.
    val phase = remember { mutableFloatStateOf(0f) }
    AnimateMetalPhase(phase, subscribed && metalMotionEnabled())
    return phase
}

internal interface MetalSurfaceShader {
    fun brush(width: Float, height: Float, corners: FloatArray, density: Float, phase: Float): Brush
}
internal expect fun createMetalSurfaceShader(): MetalSurfaceShader?

/** Static alpha feather for measured foreground bounds; it never observes a frame clock. */
internal interface MetalInkShader {
    fun brush(width: Float, height: Float, density: Float, dark: Boolean): Brush
}
internal expect fun createMetalInkShader(): MetalInkShader?


/** Glass keeps its original optics and shadow; metal decorates only its perimeter. */
@Composable
internal fun Modifier.liquidMetal(
    shape: RoundedCornerShape,
    interactionSource: InteractionSource? = null,
    edges: Boolean = true,
    navigationBar: Boolean = false,
): Modifier = (if (!edges) this else if (navigationBar) navigationMetalBevel(shape) else liquidMetalRim(shape))
    .appleGlass(shape, interactionSource, edges, navigationBar, forceGlass = true)

/** Transparent draw overlay: preserve the base Glass centre, shadow and foreground ink. */
@Composable
internal fun Modifier.liquidMetalRim(shape: RoundedCornerShape): Modifier {
    val shader = remember { createMetalSurfaceShader() }
    val phase = if (shader != null) rememberMetalPhase() else null
    return drawWithCache {
        val width = size.width
        val height = size.height
        // Resolve relative/asymmetric corners with the same density and RTL as Glass.
        val outline = shape.createOutline(size, layoutDirection, this)
        val roundRect = (outline as? Outline.Rounded)?.roundRect
        val corners = floatArrayOf(
            roundRect?.topLeftCornerRadius?.x ?: 0f,
            roundRect?.topRightCornerRadius?.x ?: 0f,
            roundRect?.bottomRightCornerRadius?.x ?: 0f,
            roundRect?.bottomLeftCornerRadius?.x ?: 0f,
        )
        val fallbackClip = Path().apply {
            when (outline) {
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val fallbackRim = Brush.linearGradient(
            listOf(Color(0xFF25262A), Color(0xFFF5F6FA), Color(0xFF767980), Color(0xFFE5E7EE), Color(0xFF303237)),
            start = Offset.Zero,
            end = Offset(width, height),
        )
        // A centred stroke clipped only during this overlay leaves 2.25 dp inside.
        val fallbackStroke = Stroke(4.5f * density)
        onDrawWithContent {
            drawContent()
            val material = shader?.brush(width, height, corners, density, phase?.value ?: 0f)
            if (material != null) drawRect(material)
            else clipPath(fallbackClip) { drawOutline(outline, fallbackRim, style = fallbackStroke) }
        }
    }
}

/** Premultiplied Paper reflection only in the inner 2.25 dp rim; no opaque face or ink mask. */
internal val MetalSurfaceProgram = """
    uniform float2 size;
    uniform float4 corners;
    uniform float px;
    uniform float phase;
    $NavigationMetalFunctions
    half4 main(float2 point) {
        float2 halfSize = size * 0.5;
        float2 local = point - halfSize;
        float radius = local.x < 0.0
            ? (local.y < 0.0 ? corners.x : corners.w)
            : (local.y < 0.0 ? corners.y : corners.z);
        radius = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
        float2 q = abs(local) - halfSize + radius;
        float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
        float depth = -d;
        float ring = 2.25 * px;
        // Outside and the Glass centre cost no reflection/noise evaluation.
        if (depth <= 0.0 || depth >= ring + 0.5) return half4(0.0);
        float alpha = smoothstep(0.0, 1.0, depth)
                    * (1.0 - smoothstep(ring - 0.5, ring + 0.5, depth));
        if (alpha <= 0.0) return half4(0.0);
        float3 reflected = navigationMetal(point, halfSize, max(halfSize.x, halfSize.y), phase);
        return half4(half3(reflected * alpha), half(alpha));
    }
""".trimIndent()


/** One continuous premultiplied mask, avoiding joins between separate edge/corner fills. */
internal val MetalInkProgram = """
    uniform float2 size;
    uniform float px;
    uniform float dark;
    half4 main(float2 point) {
        float2 halfSize = size*0.5;
        float2 q = abs(point-halfSize)-halfSize-float2(0.5*px);
        float edgeDistance = length(max(q,float2(0.0)));
        float coverage = 1.0-smoothstep(0.0,8.0*px,edgeDistance);
        float alpha = mix(0.96,0.94,dark)*coverage;
        float3 ink = mix(float3(0.94),float3(0.12),dark);
        return half4(half3(ink*alpha),half(alpha));
    }
""".trimIndent()
