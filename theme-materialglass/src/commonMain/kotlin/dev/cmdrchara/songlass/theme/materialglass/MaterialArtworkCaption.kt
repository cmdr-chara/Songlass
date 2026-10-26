package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val MaterialArtworkCaptionFadeHeight = 12.dp

/**
 * Protect the whole caption, including translucent secondary text, over bright cover art.
 * The fade occupies only the top padding; text below it has a uniform backing. Apply before
 * padding and reserve [MaterialArtworkCaptionFadeHeight] above the first line. At full layer
 * opacity, even 82%-white text has more than 4.5:1 contrast over an otherwise white cover.
 */
internal fun Modifier.materialArtworkCaptionScrim(): Modifier = drawWithCache {
    val fadeHeight = MaterialArtworkCaptionFadeHeight.toPx().coerceAtMost(size.height)
    val scrim = Color.Black.copy(alpha = 0.72f)
    val fade = Brush.verticalGradient(listOf(Color.Transparent, scrim), endY = fadeHeight)
    onDrawBehind {
        if (fadeHeight > 0f) drawRect(fade, size = Size(size.width, fadeHeight))
        if (size.height > fadeHeight) {
            drawRect(scrim, topLeft = Offset(0f, fadeHeight), size = Size(size.width, size.height - fadeHeight))
        }
    }
}
