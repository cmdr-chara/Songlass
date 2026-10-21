package dev.cmdrchara.songlass.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import dev.cmdrchara.songlass.core.playback.PlaybackController

/** One/two/three headset presses toggle, skip forward or go back within the active queue. */
internal class HeadsetButtons(
    private val controller: PlaybackController,
    private val scheduler: HeadsetButtonScheduler = MainThreadHeadsetScheduler(),
) {
    private var presses = 0
    private var intentRevision = 0L
    private var closed = false
    private val resolve = Runnable {
        val count = presses
        presses = 0
        // Clearing the queue while a gesture waits is a newer intent. Do not resurrect it.
        if (!closed && controller.queue.value.current != null &&
            intentRevision == controller.transportIntentRevision) when (count) {
            1 -> controller.togglePlayPause()
            2 -> controller.skipToNext()
            in 3..Int.MAX_VALUE -> controller.skipToPrevious()
        }
    }

    fun handle(intent: Intent): Boolean {
        if (closed) return false
        val event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
        if (event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK && event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            cancelPending()
            return false
        }
        if (controller.queue.value.current == null) {
            cancelPending()
            // MediaSession can call onPlaybackResumption. Consuming this event locally
            // would turn Play after process recreation into a silent no-op.
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (presses > 0 && intentRevision != controller.transportIntentRevision) cancelPending()
            if (presses == 0) intentRevision = controller.transportIntentRevision
            presses++
            scheduler.remove(resolve)
            scheduler.post(resolve, TAP_WINDOW_MS)
        }
        return true
    }

    fun release() {
        closed = true
        cancelPending()
    }

    private fun cancelPending() {
        scheduler.remove(resolve)
        presses = 0
    }

    private companion object {
        const val TAP_WINDOW_MS = 400L
    }
}

/** The debug probe supplies a deterministic queue while production uses the main looper. */
internal interface HeadsetButtonScheduler {
    fun post(callback: Runnable, delayMs: Long)
    fun remove(callback: Runnable)
}

private class MainThreadHeadsetScheduler : HeadsetButtonScheduler {
    private val handler = Handler(Looper.getMainLooper())
    override fun post(callback: Runnable, delayMs: Long) { handler.postDelayed(callback, delayMs) }
    override fun remove(callback: Runnable) { handler.removeCallbacks(callback) }
}
