package dev.cmdrchara.songlass.debug

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.cmdrchara.songlass.playback.PlaybackService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.launch

/**
 * Debug builds only. `adb shell am broadcast -a dev.cmdrchara.songlass.debug.BROWSE_PROBE
 * [--es query jojo] [--ez play true]` connects a MediaBrowser to the library as Android Auto
 * would, logs the tabs, a few children of each, a search and a cover read (tag BrowseProbe),
 * and optionally plays the first song found.
 */
class BrowseProbeReceiver : BroadcastReceiver() {
    override fun onReceive(receiverContext: Context, intent: Intent) {
        // A receiver's own context may not bind to services.
        val context = receiverContext.applicationContext
        val pending = goAsync()
        val query = intent.getStringExtra("query") ?: "a"
        val play = intent.getBooleanExtra("play", false)
        MainScope().launch {
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val browser = MediaBrowser.Builder(context, token).buildAsync().await()
                val root = browser.getLibraryRoot(null).await().value!!
                log("root ${root.mediaId}")
                val tabs = browser.getChildren(root.mediaId, 0, 50, null).await().value.orEmpty()
                for (tab in tabs) {
                    val children = browser.getChildren(tab.mediaId, 0, 4, null).await().value.orEmpty()
                    log("tab ${tab.mediaMetadata.title}: ${children.joinToString { it.describe() }}")
                    children.firstOrNull { it.mediaMetadata.isBrowsable == true }?.let { first ->
                        val inner = browser.getChildren(first.mediaId, 0, 3, null).await().value.orEmpty()
                        log("  ${first.mediaMetadata.title} → ${inner.joinToString { it.describe() }}")
                    }
                }
                browser.search(query, null).await()
                val results = browser.getSearchResult(query, 0, 10, null).await().value.orEmpty()
                log("search '$query': ${results.joinToString { it.describe() }}")
                results.firstOrNull()?.mediaMetadata?.artworkUri?.let { uri ->
                    val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                    log("cover $uri → ${bitmap?.width}x${bitmap?.height}")
                }
                val song = results.firstOrNull { it.mediaMetadata.isPlayable == true && it.mediaMetadata.isBrowsable != true }
                if (play && song != null) {
                    browser.setMediaItem(song)
                    browser.prepare()
                    browser.play()
                    delay(1500)
                    log("playing=${browser.isPlaying} title=${browser.mediaMetadata.title}")
                }
                browser.release()
            } catch (error: Exception) {
                Log.e(TAG, "probe failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private fun MediaItem.describe() = "${mediaMetadata.title}" + (if (mediaMetadata.isBrowsable == true) "/" else "") + (if (mediaMetadata.isPlayable == true) "▶" else "")

    private fun log(message: String) = Log.i(TAG, message)

    private companion object {
        const val TAG = "BrowseProbe"
    }
}

/** Waits for a future without kotlinx-coroutines-guava (not a dependency of the app). */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener({
        try {
            continuation.resume(get())
        } catch (error: ExecutionException) {
            continuation.resumeWithException(error.cause ?: error)
        }
    }, MoreExecutors.directExecutor())
}
