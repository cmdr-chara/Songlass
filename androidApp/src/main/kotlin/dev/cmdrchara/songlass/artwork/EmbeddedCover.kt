package dev.cmdrchara.songlass.artwork

import android.content.ContentResolver
import androidx.core.graphics.scale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.kyant.taglib.TagLib

/** Prefer the explicit front cover: MediaStore may choose an embedded back cover instead. */
object EmbeddedCover {
    private val native = TagLib

    fun load(resolver: ContentResolver, uri: Uri, sizePx: Int): Bitmap? =
        frontCover(resolver, uri)?.let { decode(it, sizePx) }

    /** The encoded front cover embedded in the audio file, or `null` if there is none. */
    fun frontCover(resolver: ContentResolver, uri: Uri): ByteArray? = runCatching {
        val source = uri.buildUpon().fragment(null).build()
        resolver.openFileDescriptor(source, "r")?.let { native.getFrontCover(it.detachFd()) }?.data
    }.getOrNull()

    /** Decodes an encoded cover to at most [sizePx] on its longer side. */
    fun decode(picture: ByteArray, sizePx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, options) ?: return null
        val scale = minOf(1f, sizePx.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (scale < 1f) bitmap.scale( maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()), true)
        else bitmap
    }.getOrNull()
}
