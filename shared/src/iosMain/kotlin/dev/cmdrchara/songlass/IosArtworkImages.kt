@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.cmdrchara.songlass.core.artwork.ArtworkAvailability
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkImageLoader
import dev.cmdrchara.songlass.designsystem.foundation.CachedArtworkImage
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.getBytes
import platform.UIKit.UIImagePNGRepresentation

/** The same authorized Music cover feeds Compose and the system Now Playing presentation. */
internal class IosArtworkImages(
    private val source: IosMusicSource,
    private val availability: ArtworkAvailability,
) : ArtworkImageLoader {
    override val revision = availability.revision
    private val cache = linkedMapOf<Pair<String, Int>, CachedArtworkImage>()
    private var cacheRevision = -1L

    override fun cached(key: String, sizePx: Int): CachedArtworkImage? {
        if (cacheRevision != revision.value) {
            cache.clear()
            cacheRevision = revision.value
        }
        return if (source.hasAccess.value && availability.permitForKey(key) != null) cache[key to sizePx] else null
    }

    override suspend fun load(key: String, sizePx: Int): ImageBitmap? {
        cached(key, sizePx)?.let { return it.image }
        val permit = availability.permitForKey(key) ?: return null
        val image = withContext(Dispatchers.Default) {
            availability.readIfCurrent(permit, { source.hasAccess.value }) { track ->
                val native = source.artwork(track)?.imageWithSize(CGSizeMake(sizePx.toDouble(), sizePx.toDouble()))
                    ?: return@readIfCurrent null
                val data = UIImagePNGRepresentation(native) ?: return@readIfCurrent null
                if (data.length > Int.MAX_VALUE.toULong() || data.length == 0uL) return@readIfCurrent null
                val bytes = ByteArray(data.length.toInt())
                bytes.usePinned { data.getBytes(it.addressOf(0), data.length) }
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }
        }
        if (!source.hasAccess.value || !availability.isCurrent(permit)) return null
        cached(key, sizePx) // Drop entries from an older library generation before inserting.
        cache[key to sizePx] = CachedArtworkImage(image)
        while (cache.size > 12) cache.remove(cache.keys.first())
        return image
    }
}
