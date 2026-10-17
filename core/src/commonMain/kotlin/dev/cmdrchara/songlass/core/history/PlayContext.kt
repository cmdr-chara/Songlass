package dev.cmdrchara.songlass.core.history

/** What a listening session was started from, for "Recently played". */
sealed interface PlayContext {
    /** Stable text form, used for storage and de-duplication. */
    val key: String

    data class Album(val title: String, val artist: String?) : PlayContext {
        override val key: String get() = "album$FIELD$title$FIELD${artist.orEmpty()}"
    }

    data class Playlist(val id: String) : PlayContext {
        override val key: String get() = "playlist$FIELD$id"
    }

    data class Artist(val name: String?) : PlayContext {
        override val key: String get() = "artist$FIELD${name.orEmpty()}"
    }

    data object Favorites : PlayContext {
        override val key: String get() = "favorites"
    }

    companion object {
        internal const val FIELD = '\u001D'

        /** Parses [key]; null for unknown or malformed values (e.g. from a future version). */
        fun fromKey(key: String): PlayContext? {
            val parts = key.split(FIELD)
            return when (parts.firstOrNull()) {
                "album" -> parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { Album(it, parts.getOrNull(2)?.ifEmpty { null }) }
                "playlist" -> parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { Playlist(it) }
                "artist" -> if (parts.size >= 2) Artist(parts[1].ifEmpty { null }) else null
                "favorites" -> Favorites
                else -> null
            }
        }
    }
}
