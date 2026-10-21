package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.theme.ArtworkTint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/** Loads cover images; provided by the platform. Implementations should cache. */
fun interface ArtworkImageLoader {
    /** Returns the image for [key] at roughly [sizePx] square, or `null` if there is none. */
    suspend fun load(key: String, sizePx: Int): ImageBitmap?

    /** Retry unresolved images after access/library recovery, even when their key is unchanged. */
    val revision: StateFlow<Long>? get() = null

    /**
     * What [load] would return from memory, without suspending: `null` when not loaded yet.
     * Lets a cover already in the cache be drawn on the first frame instead of the placeholder.
     */
    fun cached(key: String, sizePx: Int): CachedArtworkImage? = null

    /**
     * Starts [load] in the loader's own scope, so it outlives the element that asked (a card
     * that navigates away); the returned job cancels it. `null` where prefetching is unsupported.
     */
    fun prefetch(key: String, sizePx: Int): Job? = null
}

/** A loader's cached result; [image] is `null` for a file known to have no cover. */
class CachedArtworkImage(val image: ImageBitmap?) {
    /** Shared by all views of this immutable decode; released with the loader's cache entry. */
    val colors: ArtworkColors? by lazy { image?.let { ArtworkColors(it.samplePixels(SAMPLE_GRID)) } }
}

/** Original color samples and calculations, evaluated once per decoded cover as needed. */
class ArtworkColors internal constructor(private val samples: List<Color>) {
    val seed: Color by lazy { ArtworkTint.seedOf(samples) }
    val bottom: Color by lazy {
        ArtworkTint.averageOf(samples.filterIndexed { index, _ -> index % SAMPLE_GRID >= SAMPLE_GRID * 3 / 4 })
    }
    private val headerSamples: List<Color> by lazy {
        samples.filterIndexed { index, _ -> index % SAMPLE_GRID < SAMPLE_GRID / 4 }
    }
    val header: Color by lazy { ArtworkTint.averageOf(headerSamples) }
    val headerScrimAlpha: Float by lazy { ArtworkTint.headerScrimAlpha(headerSamples) }
}

/** `null` where no real artwork is available: placeholders are shown. */
val LocalArtworkImageLoader = staticCompositionLocalOf<ArtworkImageLoader?> { null }

/** Sizes the loader is asked for, so nearby sizes share a cache entry. */
internal fun sizeBucket(sizePx: Int): Int = ARTWORK_SIZE_BUCKETS.firstOrNull { sizePx <= it } ?: LARGE_ARTWORK_PX

const val SMALL_ARTWORK_PX = 256
/** Mid-size views (carousels, cards): never decoded below the size they are drawn at. */
const val MEDIUM_ARTWORK_PX = 512
const val LARGE_ARTWORK_PX = 1024

/** Every decoded size, smallest first; a view gets the first one at least as large as itself. */
val ARTWORK_SIZE_BUCKETS = listOf(SMALL_ARTWORK_PX, MEDIUM_ARTWORK_PX, LARGE_ARTWORK_PX)

/**
 * The real image for [artwork], or `null` while loading or when there is none.
 * A cover already in memory is returned on the first composition; while one decodes, a
 * cached stand-in is drawn (its small size, else the [Artwork.fallbackImageKey] cover),
 * so neither flashes the placeholder.
 */
@Composable
fun rememberArtworkImage(artwork: Artwork, sizePx: Int): ImageBitmap? {
    val loader = LocalArtworkImageLoader.current
    val key = artwork.imageKey
    val bucket = sizeBucket(sizePx)
    val revision = loader?.revision?.collectAsState()?.value ?: 0L
    val request = if (loader != null && key != null) ArtworkRequest(loader, key, bucket, revision) else null
    val cached = remember(request) { request?.let { it.loader.cached(it.key, it.bucket) } }
    val fallbackKey = artwork.fallbackImageKey
    val standIn = remember(request, cached, fallbackKey) {
        if (request == null || cached != null) return@remember null
        // The requested size, else the largest smaller size already decoded.
        fun cachedImage(key: String) = ARTWORK_SIZE_BUCKETS.filter { it <= request.bucket }.asReversed()
            .firstNotNullOfOrNull { request.loader.cached(key, it)?.image }
        cachedImage(request.key) ?: fallbackKey?.takeIf { it != request.key }?.let(::cachedImage)
    }
    var loaded by remember { mutableStateOf<LoadedArtwork?>(null) }
    LaunchedEffect(request) {
        if (request != null) loaded = LoadedArtwork(request, cached?.image ?: request.loader.load(request.key, request.bucket))
    }
    return artworkToShow(
        hasRequest = request != null,
        loadedForRequest = loaded?.request == request,
        loaded = loaded?.image,
        cachedKnown = cached != null,
        cached = cached?.image,
        standIn = standIn,
    )
}

/** Starts loading [artwork] at [sizePx] (e.g. the playing cover, before the player opens). */
@Composable
fun PrefetchArtwork(artwork: Artwork?, sizePx: Int) {
    val loader = LocalArtworkImageLoader.current
    val key = artwork?.imageKey
    val bucket = sizeBucket(sizePx)
    val revision = loader?.revision?.collectAsState()?.value ?: 0L
    LaunchedEffect(loader, key, bucket, revision) {
        if (loader != null && key != null && loader.cached(key, bucket) == null) loader.load(key, bucket)
    }
}

private data class ArtworkRequest(val loader: ArtworkImageLoader, val key: String, val bucket: Int, val revision: Long)

private class LoadedArtwork(val request: ArtworkRequest, val image: ImageBitmap?)

/**
 * The image to draw now. In order: the result loaded for this request, the synchronously
 * cached one, a cached stand-in (smaller size or fallback cover), otherwise the last loaded image
 * (a different cover keeps showing until its replacement decodes, as before).
 */
internal fun <T : Any> artworkToShow(
    hasRequest: Boolean,
    loadedForRequest: Boolean,
    loaded: T?,
    cachedKnown: Boolean,
    cached: T?,
    standIn: T?,
): T? = when {
    !hasRequest -> null
    loadedForRequest -> loaded
    cachedKnown -> cached
    standIn != null -> standIn
    else -> loaded
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
    val colors = rememberArtworkColors(artwork, image)
    val (seed, edge) = remember(colors, artwork) {
        val seed = colors?.seed ?: artwork?.colors?.let(ArtworkTint::seedOf) ?: Color.Gray
        seed to (colors?.bottom ?: artwork?.colors?.lastOrNull())
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
    val colors = rememberArtworkColors(artwork, image)
    return remember(colors, artwork, fallbackDark) {
        val header = colors?.header ?: artwork?.colors?.firstOrNull()
        header?.let(ArtworkTint::prefersLightContent) ?: fallbackDark
    }
}

/** Black protection for white status icons, sampled independently of the app appearance. */
@Composable
fun rememberArtworkHeaderScrimAlpha(artwork: Artwork?): Float {
    val image = artwork?.let { rememberArtworkImage(it, SMALL_ARTWORK_PX) }
    val colors = rememberArtworkColors(artwork, image)
    return remember(colors, artwork) {
        colors?.headerScrimAlpha ?: ArtworkTint.headerScrimAlpha(artwork?.colors.orEmpty())
    }
}

@Composable
private fun rememberArtworkColors(artwork: Artwork?, image: ImageBitmap?): ArtworkColors? {
    val loader = LocalArtworkImageLoader.current
    return remember(loader, image, artwork?.imageKey, artwork?.fallbackImageKey) {
        if (image == null) return@remember null
        // A loading view can show the previous cover or an album fallback. Only share an
        // analysis when it belongs to the exact bitmap being drawn, never merely its URI.
        val cached = artwork?.imageKey?.let { loader?.cached(it, SMALL_ARTWORK_PX) }
            ?.takeIf { it.image === image }
            ?: artwork?.fallbackImageKey?.let { loader?.cached(it, SMALL_ARTWORK_PX) }
                ?.takeIf { it.image === image }
        (cached ?: CachedArtworkImage(image)).colors
    }
}
