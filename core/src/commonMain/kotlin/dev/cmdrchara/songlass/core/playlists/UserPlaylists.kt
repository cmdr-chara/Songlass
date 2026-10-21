package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaylistInsertion { Start, End }

data class PlaylistAddResult(val added: Int, val alreadyPresent: Int, val playlistFound: Boolean = true)

/** Distinguishes filters from missing files without removing either kind of stored reference. */
data class PlaylistAvailability(
    val tracks: List<Track>, val filteredTrackIds: List<String>, val missingTrackIds: List<String>,
)

/** A unique, ordered list of track references. Unavailable references survive library refresh. */
data class UserPlaylist(val id: String, val name: String, val trackIds: List<String>, val createdMs: Long) {
    fun tracksIn(library: MusicLibrary): List<Track> {
        val byId = library.tracks.associateBy { it.id }
        return trackIds.mapNotNull { byId[it] }
    }

    fun availability(library: MusicLibrary, allTracks: List<Track>): PlaylistAvailability {
        val visible = library.tracks.associateBy { it.id }
        val allIds = allTracks.mapTo(HashSet()) { it.id }
        return PlaylistAvailability(
            trackIds.mapNotNull { visible[it] },
            trackIds.filter { it !in visible && it in allIds },
            trackIds.filter { it !in allIds },
        )
    }
}

/** The user's own playlists, newest first, saved in [store]. */
class UserPlaylists(
    private val store: KeyValueStore = InMemoryKeyValueStore(),
    private val clock: () -> Long = { 0L },
) {
    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    fun get(id: String): UserPlaylist? = _playlists.value.firstOrNull { it.id == id }

    /** Re-reads the playlists from [store], after something else (a backup restore) replaced them. */
    fun reload() {
        _playlists.value = load()
    }

    /** Creates a playlist named [name] (trimmed; a blank name is not allowed) and returns its id. */
    fun create(name: String, trackIds: List<String> = emptyList()): String {
        val created = clock()
        var id = "p$created"
        var n = 1
        while (get(id) != null) id = "p$created-${n++}"
        val playlist = UserPlaylist(id, name.trim().ifEmpty { "Playlist" }, trackIds.distinct(), created)
        save(listOf(playlist) + _playlists.value)
        return id
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        update(id) { it.copy(name = trimmed) }
    }

    fun delete(id: String) = save(_playlists.value.filterNot { it.id == id })

    /** Existing callers append; repeated requests remain idempotent. */
    fun add(id: String, trackIds: List<String>, insertion: PlaylistInsertion = PlaylistInsertion.End): Int =
        addDetailed(id, trackIds, insertion).added

    /** Counts unique requested tracks; existing entries keep their position even for Start. */
    fun addDetailed(id: String, trackIds: List<String>, insertion: PlaylistInsertion = PlaylistInsertion.End): PlaylistAddResult {
        val playlist = get(id) ?: return PlaylistAddResult(0, 0, playlistFound = false)
        val requested = trackIds.distinct()
        val existing = playlist.trackIds.toHashSet()
        val added = requested.filterNot { it in existing }
        if (added.isNotEmpty()) update(id) {
            it.copy(trackIds = when (insertion) {
                PlaylistInsertion.Start -> added + it.trackIds
                PlaylistInsertion.End -> it.trackIds + added
            })
        }
        return PlaylistAddResult(added.size, requested.size - added.size)
    }

    /**
     * Removes only the missing references offered for confirmation, if they are still missing
     * from the complete current scan. A later scan must not expand an approved removal set.
     */
    fun removeMissing(id: String, allTracks: List<Track>, candidateIds: Collection<String>): Int {
        val playlist = get(id) ?: return 0
        if (candidateIds.isEmpty()) return 0
        val candidates = candidateIds.toHashSet()
        val allIds = allTracks.mapTo(HashSet()) { it.id }
        val kept = playlist.trackIds.filter { it !in candidates || it in allIds }
        val removed = playlist.trackIds.size - kept.size
        if (removed > 0) update(id) { it.copy(trackIds = kept) }
        return removed
    }

    fun remove(id: String, trackId: String) = update(id) { it.copy(trackIds = it.trackIds - trackId) }

    fun move(id: String, from: Int, to: Int) = update(id) { playlist ->
        val ids = playlist.trackIds.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return@update playlist
        ids.add(to, ids.removeAt(from))
        playlist.copy(trackIds = ids)
    }

    private fun update(id: String, change: (UserPlaylist) -> UserPlaylist) =
        save(_playlists.value.map { if (it.id == id) change(it) else it })

    private fun save(playlists: List<UserPlaylist>) {
        _playlists.value = playlists
        store.putStringList(INDEX_KEY, playlists.map { it.id })
        for (playlist in playlists) {
            store.putString("$PREFIX${playlist.id}.name", playlist.name)
            store.putString("$PREFIX${playlist.id}.created", playlist.createdMs.toString())
            store.putStringList("$PREFIX${playlist.id}.tracks", playlist.trackIds)
        }
    }

    private fun load(): List<UserPlaylist> = store.getStringList(INDEX_KEY).distinct().mapNotNull { id ->
        val name = store.getString("$PREFIX$id.name") ?: return@mapNotNull null
        UserPlaylist(
            id = id,
            name = name,
            trackIds = store.getStringList("$PREFIX$id.tracks").distinct(),
            createdMs = store.getString("$PREFIX$id.created")?.toLongOrNull() ?: 0L,
        )
    }

    private companion object {
        const val INDEX_KEY = "playlists.index"
        const val PREFIX = "playlists."
    }
}
