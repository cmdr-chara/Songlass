package dev.cmdrchara.songlass.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/** Native, synthetic layer-lifetime check. No library, preferences or playback are touched. */
class NavigationLayerProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val graphics = LocalGraphicsContext.current
            LaunchedEffect(graphics) {
                try {
                    suspend fun retainedPixels(keepParent: Boolean): Boolean {
                        val source = graphics.createGraphicsLayer()
                        val target = graphics.createGraphicsLayer()
                        var releasedSource = false
                        try {
                            source.record(Density(1f), LayoutDirection.Ltr, IntSize(32, 32)) {
                                drawRect(Color.Red, size = Size(32f, 16f))
                                drawRect(Color.Cyan, topLeft = Offset(0f, 16f), size = Size(32f, 16f))
                            }
                            val external = CanvasDrawScope()
                            target.record(Density(1f), LayoutDirection.Ltr, IntSize(32, 32)) {
                                external.draw(this, layoutDirection, drawContext.canvas, size,
                                    graphicsLayer = if (keepParent) target else null) {
                                    drawLayer(source)
                                }
                            }
                            // Haze also releases its source when the Activity stops. A recorded
                            // consumer must keep that child alive until it replaces its input.
                            graphics.releaseGraphicsLayer(source)
                            releasedSource = true
                            val pixels = target.toImageBitmap().toPixelMap()
                            val top = pixels[16, 8]
                            val bottom = pixels[16, 24]
                            val retained = top.red > .9f && top.alpha > .9f &&
                                bottom.green > .9f && bottom.blue > .9f && bottom.alpha > .9f
                            Log.i("NavigationLayerProbe", "parent=$keepParent top=$top bottom=$bottom retained=$retained")
                            return retained
                        } finally {
                            if (!releasedSource) graphics.releaseGraphicsLayer(source)
                            graphics.releaseGraphicsLayer(target)
                        }
                    }
                    val old = retainedPixels(false)
                    val fixed = retainedPixels(true)
                    check(!old && fixed) { "Expected old=false/fixed=true, got old=$old fixed=$fixed" }
                    Log.i("NavigationLayerProbe", "PASS: explicit recording parent retains both source halves")
                } catch (failure: Exception) {
                    Log.e("NavigationLayerProbe", "FAIL", failure)
                } finally {
                    finish()
                }
            }
        }
    }
}
