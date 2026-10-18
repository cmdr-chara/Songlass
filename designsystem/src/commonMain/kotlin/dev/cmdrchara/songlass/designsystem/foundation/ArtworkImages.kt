package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.theme.ArtworkTint

/** Loads cover images; provided by the platform. Implementations should cache. */
fun interface ArtworkImageLoader {
    /** Returns the image for [key] at roughly [sizePx] square, or `null` if there is none. */
    suspend fun load(key: String, sizePx: Int): ImageBitmap?
}

/** `null` where no real artwork is available: placeholders are shown. */
val LocalArtworkImageLoader = staticCompositionLocalOf<ArtworkImageLoader?> { null }

/** Sizes the loader is asked for, so nearby sizes share a cache entry. */
private fun sizeBucket(sizePx: Int): Int = if (sizePx <= SMALL_ARTWORK_PX) SMALL_ARTWORK_PX else LARGE_ARTWORK_PX

const val SMALL_ARTWORK_PX = 256
const val LARGE_ARTWORK_PX = 1024

/** The real image for [artwork], or `null` while loading or when there is none. */
@Composable
fun rememberArtworkImage(artwork: Artwork, sizePx: Int): ImageBitmap? {
    val loader = LocalArtworkImageLoader.current
    val key = artwork.imageKey
    val bucket = sizeBucket(sizePx)
    return produceState<ImageBitmap?>(null, loader, key, bucket) {
        value = if (loader != null && key != null) loader.load(key, bucket) else null
    }.value
}

/**
 * Two representative colors of an image (top-left and bottom-right areas), used to tint
 * backgrounds. Averages a coarse grid of samples, so it is cheap on small images.
 */
fun ImageBitmap.dominantColors(): List<Color> {
    val pixels = toPixelMap()
    fun average(fromX: Float, toX: Float, fromY: Float, toY: Float): Color {
        var r = 0f; var g = 0f; var b = 0f; var n = 0
        val steps = 6
        for (i in 0 until steps) for (j in 0 until steps) {
            val x = ((fromX + (toX - fromX) * (i + 0.5f) / steps) * (width - 1)).toInt()
            val y = ((fromY + (toY - fromY) * (j + 0.5f) / steps) * (height - 1)).toInt()
            val c = pixels[x, y]
            r += c.red; g += c.green; b += c.blue; n++
        }
        return Color(r / n, g / n, b / n)
    }
    return listOf(average(0f, 0.6f, 0f, 0.6f), average(0.4f, 1f, 0.4f, 1f))
}

/** A [grid]×[grid] sample of the image's pixels, enough to judge its colors cheaply. */
fun ImageBitmap.samplePixels(grid: Int = 16): List<Color> {
    val pixels = toPixelMap()
    return buildList(grid * grid) {
        for (i in 0 until grid) for (j in 0 until grid) {
            add(pixels[((i + 0.5f) / grid * (width - 1)).toInt(), ((j + 0.5f) / grid * (height - 1)).toInt()])
        }
    }
}

/**
 * Tonal palette for [artwork] in the given appearance: from the real cover when it has loaded,
 * otherwise from the placeholder gradient (so the screen is tinted immediately).
 * With [followArtwork] (edge-to-edge covers) light/dark follows the cover's bottom edge.
 */
@Composable
fun rememberArtworkTint(artwork: Artwork?, dark: Boolean, followArtwork: Boolean = false): ArtworkTint {
    val image = artwork?.let { rememberArtworkImage(it, SMALL_ARTWORK_PX) }
    val (seed, edge) = remember(image, artwork) {
        val samples = image?.samplePixels(SAMPLE_GRID) ?: artwork?.colors
        val seed = samples?.let { ArtworkTint.seedOf(it) } ?: Color.Gray
        // samplePixels is column-major: the last rows of each column are the bottom edge.
        val edge = when {
            image != null && samples != null -> ArtworkTint.averageOf(
                samples.filterIndexed { index, _ -> index % SAMPLE_GRID >= SAMPLE_GRID * 3 / 4 },
            )
            else -> artwork?.colors?.lastOrNull()
        }
        seed to edge
    }
    return remember(seed, edge, dark, followArtwork) {
        ArtworkTint.from(seed, dark, edge.takeIf { followArtwork })
    }
}

private const val SAMPLE_GRID = 16

/** How covers are presented; a user preference, provided by the app. */
enum class ArtworkPresentation { FullBleed, Square }

val LocalArtworkPresentation = staticCompositionLocalOf { ArtworkPresentation.FullBleed }

/** Header chrome follows the visible cover; playback controls keep the selected appearance. */
@Composable
fun rememberArtworkHeaderIsDark(artwork: Artwork?, fallbackDark: Boolean): Boolean {
    val image = artwork?.let { rememberArtworkImage(it, SMALL_ARTWORK_PX) }
    return remember(image, artwork, fallbackDark) {
        val header = if (image != null) {
            // samplePixels is column-major; use the upper quarter, behind status icons.
            ArtworkTint.averageOf(image.samplePixels(SAMPLE_GRID).filterIndexed { index, _ ->
                index % SAMPLE_GRID < SAMPLE_GRID / 4
            })
        } else artwork?.colors?.firstOrNull()
        header?.let(ArtworkTint::prefersLightContent) ?: fallbackDark
    }
}
