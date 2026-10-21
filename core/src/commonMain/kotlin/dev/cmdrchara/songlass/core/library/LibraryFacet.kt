package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.model.Track

/** Metadata-based collections use the same identity in navigation, history, pins and browsers. */
enum class LibraryFacetKind(val storageKey: String) {
    AlbumArtist("album_artist"), Year("year"), Composer("composer");

    companion object {
        fun fromStorage(value: String?): LibraryFacetKind? = entries.firstOrNull { it.storageKey == value }
    }
}

data class LibraryFacet(val kind: LibraryFacetKind, val value: String, val tracks: List<Track>) {
    val context: PlayContext.Facet get() = PlayContext.Facet(kind, value.lowercase())
}
