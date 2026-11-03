package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track

/** Metadata candidates, not an audio fingerprint or permission to delete a file. */
object PossibleDuplicates {
    private val whitespace = Regex("\\s+")
    private fun normalized(value: String) = value.trim().lowercase().replace(whitespace, " ")
    private data class Key(val title: String, val artists: List<String>)

    /**
     * Only distinct physical files with known title, artist and duration are compared.
     * Version labels and punctuation are retained. Every group's full duration spread is
     * at most two seconds: a chain of near matches must not merge unrelated recordings.
     */
    fun find(tracks: List<Track>): List<List<Track>> {
        val candidates = tracks.asSequence()
            .filter { !it.isCueTrack && it.durationMs > 0 && it.uri.isNotBlank() }
            .distinctBy { it.id }.distinctBy { it.uri }
            .mapNotNull { track ->
                val title = normalized(track.title)
                val artists = track.artistNames.map(::normalized).filter(String::isNotEmpty).distinct().sorted()
                if (title.isEmpty() || artists.isEmpty()) null else Key(title, artists) to track
            }.groupBy({ it.first }, { it.second })
        return buildList {
            for (matching in candidates.values) {
                val sorted = matching.sortedWith(compareBy<Track> { it.durationMs }.thenBy { it.path.orEmpty() }.thenBy { it.id })
                var start = 0
                while (start < sorted.size) {
                    var end = start + 1
                    while (end < sorted.size && sorted[end].durationMs - sorted[start].durationMs <= 2_000) end++
                    if (end - start > 1) add(sorted.subList(start, end).toList())
                    start = end
                }
            }
        }.sortedWith(compareBy<List<Track>> { normalized(it.first().title) }.thenBy { it.first().artistNames.joinToString() }.thenBy { it.first().id })
    }
}
