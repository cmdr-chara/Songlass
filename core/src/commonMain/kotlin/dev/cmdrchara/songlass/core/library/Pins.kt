package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something pinned at the top of the Library, resolved against the current library. */
sealed interface PinnedItem {
    val context: PlayContext

    data class AlbumPin(val album: Album, override val context: PlayContext) : PinnedItem
    data class FolderPin(val folder: Folder, override val context: PlayContext) : PinnedItem
    data class ArtistPin(val artist: Artist, override val context: PlayContext) : PinnedItem
    data class FacetPin(val facet: LibraryFacet, override val context: PlayContext) : PinnedItem
}

/**
 * Albums, folders and artists the user pinned to the top of the Library (as in iOS 26 Apple
 * Music), newest first, at most [MAX]. Saved in [store] by [PlayContext.key], so pins survive
 * restarts; ones whose music is gone are skipped when resolved, not deleted.
 */
class Pins(
    private val store: KeyValueStore = InMemoryKeyValueStore(),
    private val mutations: RestoreMutationGate = RestoreMutationGate(),
) {
    private val _contexts = MutableStateFlow(store.getStringList(KEY).mapNotNull(PlayContext::fromKey).distinct())

    val contexts: StateFlow<List<PlayContext>> = _contexts.asStateFlow()

    fun isPinned(context: PlayContext): Boolean = context in _contexts.value

    /** Re-reads the pins from [store], after something else (a backup restore) replaced them. */
    fun reload() = prepareReload().invoke()

    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = store.getStringList(KEY).mapNotNull(PlayContext::fromKey).distinct()
        return { _contexts.value = loaded }
    }

    /** Pins [context] at the front, or unpins it. Returns whether it is now pinned. */
    fun toggle(context: PlayContext): Boolean {
        if (!mutations.canMutate) return isPinned(context)
        val current = _contexts.value
        val nowPinned = context !in current
        val updated = if (nowPinned) (listOf(context) + current).take(MAX) else current - context
        store.putStringList(KEY, updated.map { it.key })
        _contexts.value = updated
        return nowPinned
    }

    fun resolve(library: MusicLibrary): List<PinnedItem> = _contexts.value.mapNotNull { context ->
        when (context) {
            is PlayContext.Album -> library.albums.firstOrNull { it.title == context.title && it.artist == context.artist }
                ?.let { PinnedItem.AlbumPin(it, context) }
            is PlayContext.Playlist -> library.folder(context.id)?.let { PinnedItem.FolderPin(it, context) }
            is PlayContext.Artist -> library.artists.firstOrNull { it.name == context.name }?.let { PinnedItem.ArtistPin(it, context) }
            is PlayContext.Facet -> library.facet(context)?.let { PinnedItem.FacetPin(it, context) }
            PlayContext.Favorites, is PlayContext.UserPlaylist, is PlayContext.SmartPlaylist -> null
        }
    }

    companion object {
        const val MAX = 8
        private const val KEY = "library.pins"
    }
}
