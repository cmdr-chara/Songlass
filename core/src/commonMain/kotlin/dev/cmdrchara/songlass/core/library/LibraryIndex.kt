package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Builds library snapshots away from the caller's UI thread. Refresh and filter changes
 * share one builder; [refresh] returns only after the current inputs have been indexed.
 * Input lists and filters must be immutable snapshots, as with the library itself.
 */
class LibraryIndex(
    private val tracks: StateFlow<List<Track>>,
    private val filter: () -> LibraryFilter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private var indexedTracks: List<Track> = emptyList()
    private var indexedFilter = LibraryFilter()
    private val _state = MutableStateFlow(MusicLibrary(emptyList()))
    val state: StateFlow<MusicLibrary> = _state.asStateFlow()

    suspend fun refresh() = mutex.withLock {
        while (true) {
            val currentTracks = tracks.value
            val currentFilter = filter()
            if (currentTracks == indexedTracks && currentFilter == indexedFilter) return@withLock
            val library = withContext(dispatcher) {
                MusicLibrary(currentFilter.apply(currentTracks))
            }
            // A refresh or exclusion may have changed while construction was dispatched.
            // Discard that obsolete result instead of briefly publishing the wrong library.
            if (currentTracks != tracks.value || currentFilter != filter()) continue
            indexedTracks = currentTracks
            indexedFilter = currentFilter
            _state.value = library
            return@withLock
        }
    }
}
