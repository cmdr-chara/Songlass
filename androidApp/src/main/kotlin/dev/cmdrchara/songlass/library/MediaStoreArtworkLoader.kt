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
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkImageLoader
import dev.cmdrchara.songlass.designsystem.foundation.CachedArtworkImage
import dev.cmdrchara.songlass.designsystem.foundation.ARTWORK_SIZE_BUCKETS
import dev.cmdrchara.songlass.designsystem.foundation.SMALL_ARTWORK_PX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Loads the cover embedded in an audio file through MediaStore thumbnails. Keys are the
 * tracks' content URIs. Results, including "no cover", are kept in a memory-bounded cache.
 */
class MediaStoreArtworkLoader(context: Context) : ArtworkImageLoader {
    private val resolver = context.contentResolver

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

    /**
     * The entry for [key] at [sizePx]; a smaller size missing from the cache is derived from a
     * larger decode of the same cover (one filtered downscale, never upscaled), so views that
     * only showed the large cover tint and draw small ones without waiting for a second decode.
     */
    private fun cachedEntry(key: String, sizePx: Int): CachedArtwork? {
        val cacheKey = "$key@$sizePx"
        cacheFor(sizePx).get(cacheKey)?.let { return it }
        val larger = ARTWORK_SIZE_BUCKETS.filter { it > sizePx }
            .firstNotNullOfOrNull { cacheFor(it).get("$key@$it") } ?: return null
        val source = larger.bitmap
        val scale = if (source == null) 1f else minOf(1f, sizePx.toFloat() / maxOf(source.width, source.height))
        val entry = when {
            source == null -> CachedArtwork(null)
            scale >= 1f -> larger
            else -> CachedArtwork(
                Bitmap.createScaledBitmap(source, maxOf(1, (source.width * scale).toInt()), maxOf(1, (source.height * scale).toInt()), true),
            )
        }
        cacheFor(sizePx).put(cacheKey, entry)
        return entry
    }

    override suspend fun load(key: String, sizePx: Int): ImageBitmap? {
        val cacheKey = "$key@$sizePx"
        cachedEntry(key, sizePx)?.let { return it.image }
        val pending = synchronized(pendingLoads) {
            pendingLoads.getOrPut(cacheKey) { PendingLoad() }.also { it.users++ }
        }
        try {
            return pending.mutex.withLock {
                // Another caller may have completed while this one waited.
                cachedEntry(key, sizePx)?.let { return@withLock it.image }
                // Few decodes at a time, so the rows a fling stops on are not queued behind
                // rows it already passed: a request still waiting here is cancelled freely.
                decodePermits.withPermit {
                    // A decode that has started is finished and cached even if its row left
                    // the screen meanwhile, instead of throwing the work away.
                    withContext(NonCancellable + Dispatchers.IO) {
                        cachedEntry(key, sizePx) ?: decodeEntry(key, sizePx).also { cacheFor(sizePx).put(cacheKey, it) }
                    }
                }.image
            }
        } finally {
            // Cancellation while waiting or decoding must release the key too.
            synchronized(pendingLoads) {
                if (--pending.users == 0) pendingLoads.remove(cacheKey)
            }
        }
    }

    /**
     * Files without embedded art (or unreadable ones) simply have no cover. Tracks embedding the
     * same picture (usually a whole album) share one decoded bitmap: identical bytes, same size.
     */
    private fun decodeEntry(key: String, sizePx: Int): CachedArtwork {
        val uri = Uri.parse(key).buildUpon().fragment(null).build()
        // Track keys are MediaStore audio items; asking their type would be one more IPC per cover.
        val isAudio = uri.authority == MediaStore.AUTHORITY && uri.pathSegments.getOrNull(1) == "audio"
        if (!isAudio && runCatching { resolver.getType(uri) }.getOrNull()?.startsWith("image/") == true) {
            return CachedArtwork(runCatching {
                val source = android.graphics.ImageDecoder.createSource(resolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val scale = minOf(1f, sizePx.toFloat() / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull())
        }
        val picture = dev.cmdrchara.songlass.artwork.EmbeddedCover.frontCover(resolver, uri)
        if (picture != null) {
            val identity = "${digest(picture)}@$sizePx"
            synchronized(sharedCovers) { sharedCovers[identity]?.get() }?.let { return it }
            val bitmap = dev.cmdrchara.songlass.artwork.EmbeddedCover.decode(picture, sizePx)
            if (bitmap != null) {
                val entry = CachedArtwork(bitmap)
                synchronized(sharedCovers) {
                    if (sharedCovers.size >= SHARED_COVERS_PRUNE_AT) sharedCovers.values.removeAll { it.get() == null }
                    sharedCovers[identity] = WeakReference(entry)
                }
                return entry
            }
        }
        return CachedArtwork(runCatching { resolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }.getOrNull())
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    class CachedArtwork(val bitmap: Bitmap?) {
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
