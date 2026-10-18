package dev.cmdrchara.songlass.core.playback

/**
 * Resumes the music when headphones or a car's audio connect again, but only if it stopped
 * because they disconnected (not because the user paused) and not too long ago.
 */
class OutputResume(private val clock: () -> Long, private val windowMs: Long = DEFAULT_WINDOW_MS) {
    private var pausedAt: Long? = null

    /** Playback paused because the output went away (headphones unplugged, car switched off). */
    fun onPausedByDisconnect() {
        pausedAt = clock()
    }

    /** Any other start or pause (the user, an alarm…) cancels the pending resume. */
    fun cancel() {
        pausedAt = null
    }

    /** Called when headphones or a car connect: whether to play again. Answers true at most once. */
    fun onOutputConnected(enabled: Boolean): Boolean {
        val at = pausedAt ?: return false
        pausedAt = null
        return enabled && clock() - at <= windowMs
    }

    companion object {
        /** An hour: long enough for a stop at a petrol station, short enough not to surprise the next day. */
        const val DEFAULT_WINDOW_MS = 60 * 60 * 1000L
    }
}
