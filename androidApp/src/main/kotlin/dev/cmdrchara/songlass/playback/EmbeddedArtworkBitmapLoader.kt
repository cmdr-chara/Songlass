package dev.cmdrchara.songlass.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import dev.cmdrchara.songlass.SonglassApplication
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Lets the media notification and lock screen show covers embedded in audio files: the
 * artwork URI is the canonical track cache key. All source reads use the same verified,
 * bounded loader as the app and widgets; unavailable files are never opened from stale metadata.
 */
@UnstableApi
class EmbeddedArtworkBitmapLoader(context: Context) : BitmapLoader {
    private val artwork = (context.applicationContext as SonglassApplication).artworkLoader

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Cannot decode artwork")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        runBlocking(Dispatchers.IO) { artwork.loadBitmap(uri.toString(), SIZE_PX) }
            ?: throw IOException("Artwork is unavailable")
    }

    private companion object {
        // PlaybackService may be recreated; do not strand a new executor thread each time.
        val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
        /** Large enough for the lock screen, small enough for a notification. */
        const val SIZE_PX = 512
    }
}
