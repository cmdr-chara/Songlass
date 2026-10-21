package dev.cmdrchara.songlass.core.browse

import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.queue.QueueEntry

/**
 * An external Play can arrive with an empty native timeline after Stop, while core still owns
 * the collection. Resume that queue first; history is only a fallback after the queue was lost
 * or explicitly cleared. An unavailable retained entry must not silently select other music.
 * Call on the controller's serial owner thread after checking library readiness.
 */
fun PlaybackController.resumeForExternalPlayback(history: () -> PlayRequest?): QueueEntry {
    val retained = queue.value.current
    if (retained != null) {
        // A paused decoder may still be open after this entry became unavailable. Reject
        // before resume can set play intent; the postcheck alone would be too late.
        check(retained.isAvailable) { "No available music to resume" }
        resume()
    } else play(checkNotNull(history()) { "No music to resume" })
    return checkNotNull(queue.value.current?.takeIf { it.isAvailable }) { "No available music to resume" }
}
