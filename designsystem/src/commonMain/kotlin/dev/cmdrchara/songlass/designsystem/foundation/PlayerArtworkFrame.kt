package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp

/** Surface and image share their side edges throughout the player transition. */
data class PlayerArtworkFrame(val surface: Rect, val cover: Rect, val bodyReveal: Float)

fun playerArtworkFrame(thumbnail: Rect, window: Size, progress: Float): PlayerArtworkFrame {
    val t = progress.coerceIn(0f, 1f)
    val body = ((t - 0.6f) / 0.4f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    val left = lerp(thumbnail.left, 0f, t)
    val top = lerp(thumbnail.top, 0f, t)
    val width = lerp(thumbnail.width, window.width, t)
    val heroHeight = window.height * 0.64f
    // Initially a cover card; only the final portion reveals the player controls below it.
    val cardHeight = lerp(thumbnail.height, minOf(window.width, heroHeight), t)
    // Interpolate the total height before splitting image and controls. Growing each part
    // independently pushes the bottom beyond the window during the first downward drag.
    val surfaceHeight = lerp(cardHeight, lerp(thumbnail.height, window.height, t), body)
    val bodyHeight = (window.height - heroHeight) * (width / window.width) * body
    val coverHeight = surfaceHeight - bodyHeight
    val cover = Rect(left, top, left + width, top + coverHeight)
    val surface = Rect(left, top, left + width, top + surfaceHeight)
    return PlayerArtworkFrame(surface, cover, body)
}

/** Same late body reveal as Edge to Edge, ending at the measured inset Square cover. */
fun squarePlayerArtworkFrame(thumbnail: Rect, window: Size, target: Rect, progress: Float): PlayerArtworkFrame {
    val t = progress.coerceIn(0f, 1f)
    val reveal = ((t - 0.6f) / 0.4f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    fun blend(a: Rect, b: Rect, fraction: Float) = Rect(
        lerp(a.left, b.left, fraction), lerp(a.top, b.top, fraction),
        lerp(a.right, b.right, fraction), lerp(a.bottom, b.bottom, fraction),
    )
    val baseCover = blend(thumbnail, target, t)
    val verticalSurface = blend(baseCover, Rect(0f, 0f, window.width, window.height), reveal)
    // Resolve the inset while the body is visible, then shrink on the thumbnail's vertical
    // axis. Blending the whole panel and compensating the cover separately bent the side
    // edges back and forth. Both now share one horizontal anchor and matching late margins.
    val left = lerp(thumbnail.left, target.left, reveal)
    val cover = Rect(left, baseCover.top, left + baseCover.width, baseCover.bottom)
    val surface = Rect(
        left - target.left * reveal, verticalSurface.top,
        cover.right + (window.width - target.right) * reveal, verticalSurface.bottom,
    )
    return PlayerArtworkFrame(surface, cover, reveal)
}
