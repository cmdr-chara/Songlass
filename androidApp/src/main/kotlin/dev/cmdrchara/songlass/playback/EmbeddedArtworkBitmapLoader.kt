package dev.cmdrchara.songlass.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Lets the media notification and lock screen show covers embedded in audio files: the
 * artwork URI is the track's own content URI, read through MediaStore thumbnails.
 */
@UnstableApi
class EmbeddedArtworkBitmapLoader(context: Context) : BitmapLoader {
    private val resolver = context.contentResolver
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Cannot decode artwork")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        resolver.loadThumbnail(uri, Size(SIZE_PX, SIZE_PX), null)
    }

    private companion object {
        /** Large enough for the lock screen, small enough for a notification. */
        const val SIZE_PX = 512
    }
}
