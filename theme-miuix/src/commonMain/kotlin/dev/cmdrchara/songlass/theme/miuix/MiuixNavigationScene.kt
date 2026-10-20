package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.highlight.*
import top.yukonga.miuix.kmp.blur.sensor.DeviceTilt
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import kotlin.math.abs
import kotlin.math.roundToInt

/** Paint an ordered scene through Miuix's public Backdrop contract. */
internal class NavigationScene(private val planes: List<Backdrop>) : Backdrop {
    override val isCoordinatesDependent get() = planes.any { it.isCoordinatesDependent }
    override val offsetResidualX get() = planes.firstOrNull()?.offsetResidualX ?: 0f
    override val offsetResidualY get() = planes.firstOrNull()?.offsetResidualY ?: 0f
    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Int) {
        val destination = this
        planes.forEach { plane -> with(plane) { destination.drawBackdrop(density, coordinates, layerBlock, downscaleFactor) } }
    }
}

/** Quantized light coordinates keep small sensor noise from invalidating the rim every frame. */
@Composable
internal fun rememberNavigationLighting(active: Boolean): State<Highlight> {
    val sensor = if (active) rememberDeviceTilt(smoothing = 0.12f) else remember { mutableStateOf(DeviceTilt.Zero) }
    return remember(sensor) {
        derivedStateOf {
            val value = sensor.value
            val x = (value.gravityX * 12f).roundToInt() / 12f
            val y = (value.gravityY * 12f).roundToInt() / 12f
            val dx = if (abs(x) + abs(y) < 0.15f) -0.65f else x
            val dy = if (abs(x) + abs(y) < 0.15f) 0.75f else y
            Highlight(width = 0.9.dp, style = BloomStroke(
                color = Color.White.copy(alpha = 0.10f), innerBlurRadius = 2.4.dp, dualPeak = true,
                primaryLight = LightSource(LightPosition(0.5f + dx * 0.8f, 0.55f - dy * 0.8f, -0.2f), intensity = 0.85f),
                secondaryLight = LightSource(LightPosition(0.5f - dx * 0.5f, 0.55f + dy * 0.5f, -0.45f), intensity = 0.30f),
            ))
        }
    }
}
