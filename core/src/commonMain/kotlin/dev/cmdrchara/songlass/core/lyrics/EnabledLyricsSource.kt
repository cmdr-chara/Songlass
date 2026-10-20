package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/** Skips a disabled provider and cancels its pending lookup when the setting is turned off. */
class EnabledLyricsSource<T>(
    private val source: LyricsSource,
    private val preferences: StateFlow<T>,
    private val enabled: (T) -> Boolean,
) : LyricsSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun load(track: Track): Lyrics? = preferences
        .map(enabled)
        .distinctUntilChanged()
        .mapLatest { allowed ->
            if (!allowed) null else source.load(track).also { currentCoroutineContext().ensureActive() }
        }
        .buffer(0)
        .first()
}
