package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
internal actual fun KeepLyricsScreenAwake(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) LyricsIdleTimer.acquire()
        onDispose { if (enabled) LyricsIdleTimer.release() }
    }
}

/** UIKit's timer is process-wide; one scene must not release another scene's request. Main thread only. */
private object LyricsIdleTimer {
    private var requests = 0
    private var previous = false

    fun acquire() {
        if (requests == 0) {
            previous = UIApplication.sharedApplication.idleTimerDisabled
            UIApplication.sharedApplication.idleTimerDisabled = true
        }
        requests++
    }

    fun release() {
        check(requests > 0)
        requests--
        if (requests == 0) UIApplication.sharedApplication.idleTimerDisabled = previous
    }
}
