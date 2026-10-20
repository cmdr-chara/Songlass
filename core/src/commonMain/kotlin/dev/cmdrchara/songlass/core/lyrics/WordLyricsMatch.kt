package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track

data class WordLyricsCandidate(
    val id: String,
    val titles: List<String>,
    val artists: List<String>,
    val albums: List<String>,
    val recordingKeys: Set<String> = emptySet(),
    val createdAtMs: Long = 0,
)

/** Conservative metadata matching: distinct recordings must never merge by guesswork. */
object WordLyricsMatch {
    fun select(track: Track, candidates: List<WordLyricsCandidate>, truncated: Boolean = false): WordLyricsCandidate? {
        if (truncated) return null
        val title = normalize(track.title)
        val artists = track.artistNames.map(::normalize).toSet()
        if (title.isEmpty() || artists.isEmpty() || "" in artists) return null
        val album = track.album?.let(::normalize).orEmpty()
        val matching = candidates.filter { candidate ->
            candidate.titles.any { normalize(it) == title } &&
                candidate.artists.map(::normalize).toSet() == artists &&
                (album.isEmpty() || candidate.albums.any { normalize(it) == album })
        }
        if (matching.size == 1) return matching.single()
        if (matching.isEmpty()) return null
        val sharedKeys = matching.drop(1).fold(matching.first().recordingKeys.filter { it.isNotBlank() }.toSet()) {
                shared, candidate -> shared intersect candidate.recordingKeys
        }
        if (sharedKeys.isEmpty()) return null
        return matching.sortedWith(compareByDescending<WordLyricsCandidate> { it.createdAtMs }.thenBy { it.id }).first()
    }

    private fun normalize(value: String): String = buildString {
        var pendingSpace = false
        for (character in value.lowercase()) {
            if (character.isWhitespace()) {
                pendingSpace = isNotEmpty()
            } else {
                if (pendingSpace) append(' ')
                append(character)
                pendingSpace = false
            }
        }
    }
}
