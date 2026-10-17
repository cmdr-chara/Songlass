package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The user's favorite tracks, by track id, in the order they were added.
 *
 * In memory for now: persistence comes with the settings storage choice (see CLAUDE.md).
 */
class Favorites(initial: List<String> = emptyList()) {
    private val _ids = MutableStateFlow(initial.distinct())

    /** Favorite track ids, oldest first. */
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    fun isFavorite(trackId: String): Boolean = trackId in _ids.value

    /** Adds the track if missing, removes it otherwise. Returns the new state. */
    fun toggle(trackId: String): Boolean {
        var nowFavorite = false
        _ids.update { ids ->
            if (trackId in ids) {
                ids - trackId
            } else {
                nowFavorite = true
                ids + trackId
            }
        }
        return nowFavorite
    }

    /** Favorite tracks found in [library], most recently added first (as in Apple Music). */
    fun tracksIn(library: MusicLibrary): List<Track> {
        val byId = library.tracks.associateBy { it.id }
        return _ids.value.asReversed().mapNotNull { byId[it] }
    }
}
