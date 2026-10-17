package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's favorite tracks, by track id, in the order they were added; saved in [store]. */
class Favorites(private val store: KeyValueStore = InMemoryKeyValueStore()) {
    private val _ids = MutableStateFlow(store.getStringList(KEY).distinct())

    /** Favorite track ids, oldest first. */
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    fun isFavorite(trackId: String): Boolean = trackId in _ids.value

    /** Adds the track if missing, removes it otherwise. Returns the new state. */
    fun toggle(trackId: String): Boolean {
        val current = _ids.value
        val nowFavorite = trackId !in current
        val updated = if (nowFavorite) current + trackId else current - trackId
        _ids.value = updated
        store.putStringList(KEY, updated)
        return nowFavorite
    }

    /** Favorite tracks found in [library], most recently added first (as in Apple Music). */
    fun tracksIn(library: MusicLibrary): List<Track> {
        val byId = library.tracks.associateBy { it.id }
        return _ids.value.asReversed().mapNotNull { byId[it] }
    }

    private companion object {
        const val KEY = "favorites"
    }
}
