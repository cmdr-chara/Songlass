package dev.cmdrchara.songlass.artwork

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.cmdrchara.songlass.designsystem.foundation.drawGenerativeCover
import java.io.IOException
import androidx.compose.ui.geometry.Size as ComposeSize

/** Cover bitmaps for surfaces drawn outside the app's UI: car screens and home-screen widgets. */
object CoverImages {
    /** The cover embedded in [audioUri], or the generative cover for [seed] when it has none. */
    fun load(context: Context, audioUri: Uri?, sizePx: Int, seed: Int): Bitmap {
        if (audioUri != null) {
            try {
                return context.contentResolver.loadThumbnail(audioUri, Size(sizePx, sizePx), null)
            } catch (_: IOException) {
                // No embedded picture: fall through to the generated one.
            } catch (_: SecurityException) {
            }
        }
        return generative(seed, sizePx)
    }

    /** The same abstract composition the app shows for music without a cover. */
    fun generative(seed: Int, sizePx: Int): Bitmap {
        val image = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), ComposeSize(sizePx.toFloat(), sizePx.toFloat())) {
            drawGenerativeCover(seed)
        }
        return image.asAndroidBitmap()
    }
}
