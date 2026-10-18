package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track

/** One entry of an M3U playlist. */
data class M3uEntry(val path: String, val title: String? = null, val artist: String? = null, val durationSec: Int? = null)

/** Result of matching an M3U file against the library. */
data class M3uImport(val tracks: List<Track>, val missing: List<M3uEntry>)

/**
 * Extended M3U (`#EXTM3U` / `#EXTINF`), as written by most players. Paths are written relative to
 * the storage root (`Music/Chill/song.flac`); on import, entries are matched by the longest common
 * tail of their path (so absolute, relative and Windows paths from other apps work), then by
 * title and artist.
 */
object M3u {
    fun write(tracks: List<Track>): String = buildString {
        append("#EXTM3U\n")
        for (track in tracks) {
            val path = track.path ?: continue
            val seconds = if (track.durationMs > 0) track.durationMs / 1000 else -1
            val label = listOfNotNull(track.artist, track.title).joinToString(" - ")
            append("#EXTINF:").append(seconds).append(',').append(label.replace('\n', ' ')).append('\n')
            append(path).append('\n')
        }
    }

    fun parse(text: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var pending: Triple<Int?, String?, String?>? = null
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.substringAfter(':')
                    val seconds = body.substringBefore(',').trim().substringBefore(' ').toIntOrNull()?.takeIf { it >= 0 }
                    val label = body.substringAfter(',', "").trim()
                    val artist = label.substringBefore(" - ", "").trim().ifEmpty { null }
                    val title = (if (artist != null) label.substringAfter(" - ") else label).trim().ifEmpty { null }
                    pending = Triple(seconds, title, artist)
                }
                line.startsWith("#") -> Unit
                // Streams are not local files.
                line.contains("://") && !line.startsWith("file://", ignoreCase = true) -> pending = null
                else -> {
                    entries += M3uEntry(line.removePrefix("file://"), pending?.second, pending?.third, pending?.first)
                    pending = null
                }
            }
        }
        return entries
    }

    fun resolve(entries: List<M3uEntry>, library: MusicLibrary): M3uImport {
        val byName = library.tracks.filter { it.fileName != null }.groupBy { it.fileName!!.lowercase() }
        val tracks = mutableListOf<Track>()
        val missing = mutableListOf<M3uEntry>()
        for (entry in entries) {
            val segments = entry.path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            val candidates = byName[segments.lastOrNull()?.lowercase()].orEmpty()
            val match = candidates.maxByOrNull { commonTail(segments, it.path!!.split('/')) }
                ?: library.tracks.firstOrNull { track ->
                    entry.title != null && track.title.equals(entry.title, ignoreCase = true) &&
                        (entry.artist == null || track.artist.equals(entry.artist, ignoreCase = true))
                }
            if (match != null) tracks += match else missing += entry
        }
        return M3uImport(tracks, missing)
    }

    private fun commonTail(a: List<String>, b: List<String>): Int {
        var n = 0
        while (n < a.size && n < b.size && a[a.size - 1 - n].equals(b[b.size - 1 - n], ignoreCase = true)) n++
        return n
    }

    /** The playlist name an M3U file suggests: its file name without extension. */
    fun nameFromFile(fileName: String?): String? =
        fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.trim()?.ifEmpty { null }
}
