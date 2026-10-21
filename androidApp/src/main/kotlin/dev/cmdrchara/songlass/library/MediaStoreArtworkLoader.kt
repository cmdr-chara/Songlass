package dev.cmdrchara.songlass.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.lang.ref.WeakReference
import java.security.MessageDigest
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.core.artwork.ArtworkAvailability
import dev.cmdrchara.songlass.core.artwork.ArtworkKeys
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkImageLoader
import dev.cmdrchara.songlass.designsystem.foundation.CachedArtworkImage
import dev.cmdrchara.songlass.designsystem.foundation.ARTWORK_SIZE_BUCKETS
import dev.cmdrchara.songlass.designsystem.foundation.SMALL_ARTWORK_PX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * One bounded cover cache for Compose, widgets, media browsers and notifications. Track keys
 * include canonical identity; opening a file requires a current successful library snapshot.
 * Verified positive entries remain drawable from memory without reopening an unavailable file.
 */
class MediaStoreArtworkLoader(
    context: Context,
    private val availability: ArtworkAvailability = (context.applicationContext as SonglassApplication).graph.artworkAvailability,
    canReadLibrary: (() -> Boolean)? = null,
) : ArtworkImageLoader {
    private val resolver = context.contentResolver
    private val canReadLibrary: () -> Boolean = canReadLibrary ?: run {
        val app = context.applicationContext as SonglassApplication
        val check: () -> Boolean = {
            val graph = app.graph
            graph.source.hasAccess.value && graph.hasCompleteLibraryScan.value &&
                !graph.isLibraryLoading.value && graph.libraryRefreshFailure.value == null
        }
        check
    }

    override val revision: StateFlow<Long> get() = availability.revision

    /** Current permission for new track IO; a positive memory hit needs no such permission. */
    fun canRead(key: String): Boolean = canReadLibrary() && availability.permitForKey(key) != null

    // Thumbnails (lists, shortcuts) and larger covers have separate budgets, so a few big covers
    // scrolled past on the Home cannot push out the small ones the screen returns to.
    private val smallCache = artworkCache(cacheSizeBytes())
    private val largeCache = artworkCache(cacheSizeBytes())

    private fun cacheFor(sizePx: Int) = if (sizePx <= SMALL_ARTWORK_PX) smallCache else largeCache

    // Only active requests are tracked. Different covers/sizes can decode concurrently;
    // callers of the same key share its cached result instead of allocating duplicate images.
    private class PendingLoad {
        val mutex = Mutex()
        var users = 0
    }
    private val pendingLoads = mutableMapOf<String, PendingLoad>()
    private val decodePermits = Semaphore(DECODE_PARALLELISM)

    // Decoded covers by content, held weakly: they live as long as a cache entry uses them.
    private val sharedCovers = HashMap<String, WeakReference<CachedArtwork>>()

    // Prefetches outlive the composables that start them; the loader lives as long as the app.
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun prefetch(key: String, sizePx: Int): Job = prefetchScope.launch { load(key, sizePx) }

    override fun cached(key: String, sizePx: Int): CachedArtworkImage? =
        cachedEntry(key, sizePx)?.cachedImage

    /** Memory only: safe even when a scan failed or permission has gone away. */
    fun cachedBitmap(key: String, sizePx: Int): Bitmap? = cachedEntry(key, sizePx)?.bitmap

    /**
     * The entry for [key] at [sizePx]; a smaller size missing from the cache is derived from a
     * larger decode of the same cover (one filtered downscale, never upscaled), so views that
     * only showed the large cover tint and draw small ones without waiting for a second decode.
     */
    private fun cachedEntry(key: String, sizePx: Int): CachedArtwork? {
        val cacheKey = "$key@$sizePx"
        freshEntry(cacheKey, sizePx)?.let { return it }
        val larger = ARTWORK_SIZE_BUCKETS.filter { it > sizePx }
            .firstNotNullOfOrNull { freshEntry("$key@$it", it) } ?: return null
        val source = larger.bitmap
        val scale = if (source == null) 1f else minOf(1f, sizePx.toFloat() / maxOf(source.width, source.height))
        val entry = when {
            source == null -> larger
            scale >= 1f -> larger
            else -> CachedArtwork(
                Bitmap.createScaledBitmap(source, maxOf(1, (source.width * scale).toInt()), maxOf(1, (source.height * scale).toInt()), true),
                larger.checkedRevision,
            )
        }
        cacheFor(sizePx).put(cacheKey, entry)
        return entry
    }

    private fun freshEntry(cacheKey: String, sizePx: Int): CachedArtwork? {
        val cache = cacheFor(sizePx)
        val entry = cache.get(cacheKey) ?: return null
        if (entry.bitmap != null || entry.checkedRevision == revision.value) return entry
        // A failed/no-cover read is never a permanent verdict across permission/scan recovery.
        cache.remove(cacheKey)
        return null
    }

    override suspend fun load(key: String, sizePx: Int): ImageBitmap? = loadEntry(key, sizePx)?.image

    suspend fun loadBitmap(key: String, sizePx: Int): Bitmap? = loadEntry(key, sizePx)?.bitmap

    private suspend fun loadEntry(key: String, sizePx: Int): CachedArtwork? {
        val cacheKey = "$key@$sizePx"
        cachedEntry(key, sizePx)?.let { return it }
        val pending = synchronized(pendingLoads) {
            pendingLoads.getOrPut(cacheKey) { PendingLoad() }.also { it.users++ }
        }
        try {
            return pending.mutex.withLock {
                // Another caller may have completed while this one waited.
                cachedEntry(key, sizePx)?.let { return@withLock it }
                // Few decodes at a time, so the rows a fling stops on are not queued behind
                // rows it already passed: a request still waiting here is cancelled freely.
                decodePermits.withPermit {
                    // A decode that has started is finished and cached even if its row left
                    // the screen meanwhile, instead of throwing the work away.
                    withContext(NonCancellable + Dispatchers.IO) {
                        cachedEntry(key, sizePx) ?: decodeAuthorized(key, sizePx)?.also { cacheFor(sizePx).put(cacheKey, it) }
                    }
                }
            }
        } finally {
            // Cancellation while waiting or decoding must release the key too.
            synchronized(pendingLoads) {
                if (--pending.users == 0) pendingLoads.remove(cacheKey)
            }
        }
    }

    private suspend fun decodeAuthorized(key: String, sizePx: Int): CachedArtwork? {
        val permit = availability.permitForKey(key)
        if (permit != null) {
            return availability.readIfCurrent(permit, canReadLibrary) { track ->
                decodeAudio(Uri.parse(track.uri), sizePx, revision.value) {
                    availability.isCurrent(permit) && canReadLibrary()
                }
            }
        }
        val uri = Uri.parse(key).buildUpon().fragment(null).build()
        val isAudio = uri.authority == MediaStore.AUTHORITY && uri.pathSegments.getOrNull(1) == "audio"
        if (ArtworkKeys.isTrackKey(key) || isAudio) return null
        // The tag editor can preview an explicitly picked image URI under its separate picker
        // grant. It is not an alternative route for unverified audio files or invented track keys.
        if (runCatching { resolver.getType(uri) }.getOrNull()?.startsWith("image/") != true) return null
        val bitmap = runCatching {
            val source = android.graphics.ImageDecoder.createSource(resolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = minOf(1f, sizePx.toFloat() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return null
        return CachedArtwork(bitmap, revision.value)
    }

    /** Identical embedded picture bytes at the same size share the existing decoded bitmap. */
    private fun decodeAudio(uri: Uri, sizePx: Int, checkedRevision: Long, stillAllowed: () -> Boolean): CachedArtwork? {
        if (!stillAllowed()) return null
        val picture = dev.cmdrchara.songlass.artwork.EmbeddedCover.frontCover(resolver, uri)
        if (!stillAllowed()) return null
        if (picture != null) {
            val identity = "${digest(picture)}@$sizePx"
            synchronized(sharedCovers) { sharedCovers[identity]?.get() }?.let { return it }
            val bitmap = dev.cmdrchara.songlass.artwork.EmbeddedCover.decode(picture, sizePx)
            if (bitmap != null) {
                val entry = CachedArtwork(bitmap, checkedRevision)
                synchronized(sharedCovers) {
                    if (sharedCovers.size >= SHARED_COVERS_PRUNE_AT) sharedCovers.values.removeAll { it.get() == null }
                    sharedCovers[identity] = WeakReference(entry)
                }
                return entry
            }
        }
        // Thumbnail fallback is another source read, so recheck after the embedded-cover read.
        if (!stillAllowed()) return null
        return CachedArtwork(runCatching { resolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }.getOrNull(), checkedRevision)
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    class CachedArtwork(val bitmap: Bitmap?, val checkedRevision: Long) {
        val cachedImage = CachedArtworkImage(bitmap?.asImageBitmap())
        val image: ImageBitmap? get() = cachedImage.image
    }

    private companion object {
        /**
         * A tenth of the app's heap for each of the two caches (a fifth in all, against the usual
         * eighth for one): bitmap pixels live in native memory since Android 8, and covers are
         * most of what this app keeps in memory.
         */
        fun cacheSizeBytes(): Int = (Runtime.getRuntime().maxMemory() / 10).toInt()

        fun artworkCache(maxBytes: Int) = object : LruCache<String, CachedArtwork>(maxBytes) {
            override fun sizeOf(key: String, value: CachedArtwork): Int = value.bitmap?.allocationByteCount ?: 1
        }

        /** Concurrent decodes: enough to fill a screen of rows quickly, few enough not to starve frames. */
        const val DECODE_PARALLELISM = 5

        /** Cleared references are dropped when the content index reaches this size. */
        const val SHARED_COVERS_PRUNE_AT = 1024
    }
}
