package dev.cmdrchara.songlass.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the cover embedded in an audio file through MediaStore thumbnails. Keys are the
 * tracks' content URIs. Results, including "no cover", are kept in a memory-bounded cache.
 */
class MediaStoreArtworkLoader(context: Context) : ArtworkImageLoader {
    private val resolver = context.contentResolver

    private val cache = object : LruCache<String, CachedArtwork>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: CachedArtwork): Int = value.bitmap?.allocationByteCount ?: 1
    }

    override suspend fun load(key: String, sizePx: Int): ImageBitmap? {
        val cacheKey = "$key@$sizePx"
        cache.get(cacheKey)?.let { return it.image }
        val bitmap = withContext(Dispatchers.IO) {
            // Files without embedded art (or unreadable ones) simply have no cover.
            runCatching { resolver.loadThumbnail(Uri.parse(key), Size(sizePx, sizePx), null) }.getOrNull()
        }
        val entry = CachedArtwork(bitmap)
        cache.put(cacheKey, entry)
        return entry.image
    }

    class CachedArtwork(val bitmap: Bitmap?) {
        val image: ImageBitmap? = bitmap?.asImageBitmap()
    }

    private companion object {
        /** An eighth of the app's heap, the usual budget for an image memory cache. */
        fun cacheSizeBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).toInt()
    }
}
