package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.queue.QueueEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Only widget-relevant state: position ticks do not cause new renders or source reads. */
data class WidgetPlaybackSnapshot(
    val track: Track?,
    val canReadArtwork: Boolean,
    val playing: Boolean,
) {
    companion object {
        fun from(entry: QueueEntry?, player: PlayerState, artworkAvailable: Boolean) = WidgetPlaybackSnapshot(
            track = entry?.track,
            canReadArtwork = entry?.isAvailable == true && artworkAvailable,
            playing = player.isPlaying,
        )
    }
}

/**
 * A single publication lane shared by observed playback changes and manual widget refreshes.
 * Calls and [publish] run on the same serial dispatcher (Android Main). A cancelled decoder
 * may finish its work, but it can never overwrite the newer title, artwork or transport action.
 * Cancellation never waits for that IO before starting the next widget render.
 */
class LatestWidgetRenderer<C, R>(
    private val scope: CoroutineScope,
    private val render: suspend (C) -> R,
    private val publish: (C, R) -> Unit,
) {
    private var generation = 0L
    private var running: Job? = null

    fun submit(content: C) {
        val submitted = ++generation
        running?.cancel()
        running = scope.launch {
            val rendered = render(content)
            if (submitted == generation) publish(content, rendered)
        }
    }
}
