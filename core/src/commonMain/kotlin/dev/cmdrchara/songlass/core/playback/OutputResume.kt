package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.OutputDisconnect

/**
 * Resumes the music when headphones or a car's audio connect again, but only if it stopped
 * because they disconnected (not because the user paused) and not too long ago.
 */
class OutputResume(
    private val clock: () -> Long,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    /** Null preserves delivered-event callers; a supplied source returning null has no token. */
    private val latestDisconnect: (() -> OutputDisconnect?)? = null,
) {
    private var pausedAt: Long? = null
    private var handledOccurrence = 0L

    /** Playback paused because the output went away (headphones unplugged, car switched off). */
    fun onPausedByDisconnect() {
        val source = latestDisconnect
        if (source == null) pausedAt = clock() else observe(source())
    }

    /** Any other start or pause (the user, an alarm…) cancels the pending resume. */
    fun cancel() {
        pausedAt = null
        // The event may still be waiting in the Flow. Remember what already happened natively.
        latestDisconnect?.invoke()?.let { handledOccurrence = maxOf(handledOccurrence, it.occurrenceId) }
    }

    /** Called when headphones or a car connect: whether to play again. Answers true at most once. */
    fun onOutputConnected(enabled: Boolean): Boolean {
        // A connection callback can arrive before the queued disconnect notification.
        latestDisconnect?.let { observe(it()) }
        val at = pausedAt ?: return false
        pausedAt = null
        val now = clock()
        // A wall-clock correction or an overflowing subtraction must never revive an old token.
        return enabled && now >= at && now - at in 0..windowMs
    }

    private fun observe(disconnect: OutputDisconnect?) {
        if (disconnect == null) {
            // The source resumed playback; a conflated state stream may never expose its pause.
            pausedAt = null
            return
        }
        if (disconnect.occurrenceId <= handledOccurrence) return
        handledOccurrence = disconnect.occurrenceId
        pausedAt = disconnect.occurredAtMs
    }

    companion object {
        /** An hour: long enough for a stop at a petrol station, short enough not to surprise the next day. */
        const val DEFAULT_WINDOW_MS = 60 * 60 * 1000L
    }
}
