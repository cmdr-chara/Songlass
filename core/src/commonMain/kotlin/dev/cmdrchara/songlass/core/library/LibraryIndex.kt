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
 * Input lists, filters and vote maps must be immutable snapshots, as with the library itself.
 *
 * The filter's [LibraryFilter.minimumRating] is the only part of the filter that needs the
 * ratings, so [ratings] is read only when that threshold is active and is kept out of the
 * "inputs unchanged" comparison otherwise: voting on a song does not rebuild a library that
 * shows every rating.
 */
class LibraryIndex(
    private val tracks: StateFlow<List<Track>>,
    private val filter: () -> LibraryFilter,
    private val ratings: () -> Map<String, Int> = { emptyMap() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private var indexedTracks: List<Track> = emptyList()
    private var indexedFilter = LibraryFilter()
    /** The vote snapshot the last publication used; null when this filter does not read votes. */
    private var indexedRatings: Map<String, Int>? = null
    private val _state = MutableStateFlow(MusicLibrary(emptyList()))
    val state: StateFlow<MusicLibrary> = _state.asStateFlow()

    suspend fun refresh() = mutex.withLock {
        while (true) {
            val currentTracks = tracks.value
            val currentFilter = filter()
            val currentRatings = ratingSnapshot(currentFilter)
            val inputsUnchanged = currentTracks == indexedTracks && currentFilter == indexedFilter &&
                currentRatings == indexedRatings
            if (inputsUnchanged) return@withLock
            val library = withContext(dispatcher) {
                MusicLibrary(currentFilter.apply(visibleTracks(currentTracks, currentFilter, currentRatings)))
            }
            // A refresh, exclusion or vote may have changed while construction was dispatched.
            // Discard that obsolete result instead of briefly publishing the wrong library.
            if (currentTracks != tracks.value || currentFilter != filter() ||
                currentRatings != ratingSnapshot(currentFilter)) continue
            indexedTracks = currentTracks
            indexedFilter = currentFilter
            indexedRatings = currentRatings
            _state.value = library
            return@withLock
        }
    }

    /** The votes can only change this view when the filter asks for a minimum rating. */
    private fun ratingSnapshot(filter: LibraryFilter): Map<String, Int>? =
        if (filter.minimumRating > 0) ratings() else null

    private fun visibleTracks(
        tracks: List<Track>,
        filter: LibraryFilter,
        ratings: Map<String, Int>?,
    ): List<Track> {
        // An unrated track counts as 0 stars, so any positive threshold hides it.
        if (ratings == null) return tracks
        return tracks.filter { (ratings[it.id] ?: 0) >= filter.minimumRating }
    }
}
