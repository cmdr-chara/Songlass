package dev.cmdrchara.songlass.core.search

import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Artist
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.library.Playlist
import dev.cmdrchara.songlass.core.library.LibraryFacet
import dev.cmdrchara.songlass.core.library.LibraryFacetKind
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable identities, independent of query text and outdated library snapshots. */
sealed interface SearchTarget {
    data class Song(val id: String) : SearchTarget
    data class AlbumId(val title: String?, val artist: String?) : SearchTarget
    data class ArtistName(val name: String?) : SearchTarget
    data class FolderId(val id: String) : SearchTarget
    data class Facet(val context: PlayContext.Facet) : SearchTarget
}

sealed interface SearchItem {
    val target: SearchTarget
    data class Song(val track: Track) : SearchItem {
        override val target = SearchTarget.Song(track.id)
    }
    data class AlbumItem(val album: Album) : SearchItem {
        override val target = SearchTarget.AlbumId(album.title, album.artist)
    }
    data class ArtistItem(val artist: Artist) : SearchItem {
        override val target = SearchTarget.ArtistName(artist.name)
    }
    data class FolderItem(val playlist: Playlist) : SearchItem {
        override val target = SearchTarget.FolderId(playlist.id)
    }
    data class FacetItem(val facet: LibraryFacet) : SearchItem {
        override val target = SearchTarget.Facet(facet.context)
    }
}

/** Items actually opened from Search; newest first, deduplicated, and local to this device. */
class SearchHistory(
    private val store: KeyValueStore,
    private val mutations: RestoreMutationGate = RestoreMutationGate(),
) {
    private val _targets = MutableStateFlow(decode(store.getString(KEY)).distinct().take(MAX))
    val targets = _targets.asStateFlow()

    /** Re-reads the list from [store], after something else (a backup restore) replaced it. */
    fun reload() = prepareReload().invoke()

    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = decode(store.getString(KEY)).distinct().take(MAX)
        return { _targets.value = loaded }
    }

    fun record(target: SearchTarget) = save((listOf(target) + _targets.value.filterNot { it == target }).take(MAX))
    fun remove(target: SearchTarget) = save(_targets.value - target)
    fun clear() = save(emptyList())

    /** Hidden or missing items stay stored, so a temporary library filter does not erase history. */
    fun resolve(library: MusicLibrary): List<SearchItem> = _targets.value.mapNotNull { target ->
        when (target) {
            is SearchTarget.Song -> library.tracks.firstOrNull { it.id == target.id }?.let(SearchItem::Song)
            is SearchTarget.AlbumId -> library.albums.firstOrNull { it.title == target.title && it.artist == target.artist }
                ?.let(SearchItem::AlbumItem)
            is SearchTarget.ArtistName -> library.artists.firstOrNull { it.name == target.name }?.let(SearchItem::ArtistItem)
            is SearchTarget.FolderId -> library.playlists.firstOrNull { it.id == target.id }?.let(SearchItem::FolderItem)
            is SearchTarget.Facet -> library.facet(target.context)?.let(SearchItem::FacetItem)
        }
    }

    private fun save(targets: List<SearchTarget>) {
        if (!mutations.canMutate) return
        store.putString(KEY, targets.joinToString("") { target ->
            val fields = when (target) {
                is SearchTarget.Song -> listOf("s", target.id)
                is SearchTarget.AlbumId -> listOf("a", target.title, target.artist)
                is SearchTarget.ArtistName -> listOf("r", target.name)
                is SearchTarget.FolderId -> listOf("f", target.id)
                is SearchTarget.Facet -> listOf("m", target.context.kind.storageKey, target.context.value)
            }
            // Length prefixes preserve separators, Unicode, empty strings and missing tags.
            fields.joinToString("") { if (it == null) "-1:" else "${it.length}:$it" }
        }.takeIf { it.isNotEmpty() })
        _targets.value = targets
    }

    companion object {
        const val MAX = 20
        private const val KEY = "search.opened.v1"

        private fun decode(value: String?): List<SearchTarget> {
            if (value == null) return emptyList()
            var offset = 0
            fun field(): String? {
                val colon = value.indexOf(':', offset)
                require(colon >= offset)
                val size = value.substring(offset, colon).toInt()
                offset = colon + 1
                require(size >= -1 && size <= value.length - offset)
                if (size == -1) return null
                return value.substring(offset, offset + size).also { offset += size }
            }
            return buildList {
                // Keep any valid prefix if storage was interrupted or corrupted.
                while (offset < value.length && size < MAX) {
                    val target = runCatching {
                        when (field()) {
                            "s" -> SearchTarget.Song(requireNotNull(field()))
                            "a" -> SearchTarget.AlbumId(field(), field())
                            "r" -> SearchTarget.ArtistName(field())
                            "f" -> SearchTarget.FolderId(requireNotNull(field()))
                            "m" -> {
                                val kind = requireNotNull(LibraryFacetKind.fromStorage(field()))
                                val value = requireNotNull(field()).also { require(it.isNotBlank()) }
                                SearchTarget.Facet(PlayContext.Facet(kind, value))
                            }
                            else -> error("Unknown search target")
                        }
                    }.getOrNull() ?: break
                    add(target)
                }
            }
        }
    }
}

/** A small, stable entry point when there are no visible recent searches. */
data class SearchSuggestions(val artists: List<Artist>, val albums: List<Album>, val songs: List<Track>) {
    companion object {
        fun from(library: MusicLibrary): SearchSuggestions = SearchSuggestions(
            artists = library.artists.filter { it.name != null }
                .sortedByDescending { artist -> artist.tracks.maxOfOrNull { it.dateAddedMs ?: 0L } ?: 0L }.take(6),
            albums = library.albums.sortedByDescending { album -> album.tracks.maxOfOrNull { it.dateAddedMs ?: 0L } ?: 0L }.take(6),
            songs = if (library.albums.isEmpty()) library.tracks.sortedByDescending { it.dateAddedMs ?: 0L }.take(6) else emptyList(),
        )
    }
}
