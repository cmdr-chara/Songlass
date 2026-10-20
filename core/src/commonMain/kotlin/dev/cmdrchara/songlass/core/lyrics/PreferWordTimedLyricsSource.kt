package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Remote-only preference; the repository still owns local priority and caching. */
class PreferWordTimedLyricsSource(
    private val words: LyricsSource,
    private val fallback: LyricsSource,
) : LyricsSource {
    override suspend fun load(track: Track): Lyrics? {
        var failure: Exception? = null
        currentCoroutineContext().ensureActive()
        try {
            val result = words.load(track)
            currentCoroutineContext().ensureActive()
            if (result != null && result.isSynced && result.hasWordTiming && !result.instrumental) return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error
        }
        currentCoroutineContext().ensureActive()
        try {
            val result = fallback.load(track)
            currentCoroutineContext().ensureActive()
            if (result != null) return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error
        }
        currentCoroutineContext().ensureActive()
        failure?.let { throw it }
        return null
    }
}
