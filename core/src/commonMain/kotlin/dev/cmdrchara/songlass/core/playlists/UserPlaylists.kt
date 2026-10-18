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

/** A playlist the user made, by track id; songs no longer on the device are skipped when shown. */
data class UserPlaylist(val id: String, val name: String, val trackIds: List<String>, val createdMs: Long) {
    fun tracksIn(library: MusicLibrary): List<Track> {
        val byId = library.tracks.associateBy { it.id }
        return trackIds.mapNotNull { byId[it] }
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

    /** Appends tracks not already in the playlist. Returns how many were added. */
    fun add(id: String, trackIds: List<String>): Int {
        var added = 0
        update(id) { playlist ->
            val new = trackIds.distinct().filterNot { it in playlist.trackIds }
            added = new.size
            playlist.copy(trackIds = playlist.trackIds + new)
        }
        return added
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
            trackIds = store.getStringList("$PREFIX$id.tracks"),
            createdMs = store.getString("$PREFIX$id.created")?.toLongOrNull() ?: 0L,
        )
    }

    private companion object {
        const val INDEX_KEY = "playlists.index"
        const val PREFIX = "playlists."
    }
}
