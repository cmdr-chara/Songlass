package dev.cmdrchara.songlass.core.cue

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.M3uLibraryIndex
import dev.cmdrchara.songlass.core.playlists.M3uEntry

/** INDEX 01 boundaries are absolute CD frames (75 per second) in the referenced file. */
data class CueTrack(val file: String, val number: Int, val startFrames: Long,
    val title: String?, val performer: String?)
data class CueSheet(val title: String?, val performer: String?, val tracks: List<CueTrack>)

object CueSheets {
    const val MAX_CHARS = 262_144
    const val MAX_TRACKS = 999
    private val token = Regex("^(\\S+)(?:\\s+(.*))?$")
    private val fileLine = Regex("^(?:\"([^\"]+)\"|(\\S+))\\s+(WAVE|MP3|AIFF|FLAC)$", RegexOption.IGNORE_CASE)
    private val trackLine = Regex("^(\\d{1,3})\\s+AUDIO$", RegexOption.IGNORE_CASE)
    private val indexLine = Regex("^(\\d{2})\\s+(\\d{1,4}):(\\d{2}):(\\d{2})$")
    private fun value(text: String): String? = text.trim().let {
        if (it.startsWith('"')) it.takeIf { v -> v.length >= 2 && v.endsWith('"') }?.drop(1)?.dropLast(1)
        else it.takeIf(String::isNotBlank)
    }

    /** Malformed boundaries reject the complete sheet, never a different partial album. */
    fun parse(text: String): CueSheet? {
        if (text.length > MAX_CHARS || '\u0000' in text) return null
        var album: String? = null
        var artist: String? = null
        var file: String? = null
        var number: Int? = null
        var title: String? = null
        var performer: String? = null
        var start: Long? = null
        val result = mutableListOf<CueTrack>()
        fun finish(): Boolean {
            val n = number ?: return true
            val path = file ?: return false
            val frame = start ?: return false
            if (result.size >= MAX_TRACKS || result.any { it.number == n }) return false
            val previous = result.lastOrNull { it.file == path }
            if (previous != null && frame <= previous.startFrames) return false
            result += CueTrack(path, n, frame, title, performer)
            number = null; title = null; performer = null; start = null
            return true
        }
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = token.matchEntire(line) ?: return null
            val command = parts.groupValues[1].uppercase()
            val body = parts.groupValues[2]
            when (command) {
                "FILE" -> {
                    if (!finish()) return null
                    val match = fileLine.matchEntire(body) ?: return null
                    val path = match.groupValues[1].ifEmpty { match.groupValues[2] }
                    if (path.isBlank() || path.length > 4096 || '\n' in path || '\r' in path) return null
                    file = path
                }
                "TRACK" -> {
                    if (!finish() || file == null) return null
                    number = trackLine.matchEntire(body)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
                }
                "TITLE" -> {
                    val parsed = value(body) ?: return null
                    if (number == null) album = parsed else title = parsed
                }
                "PERFORMER" -> {
                    val parsed = value(body) ?: return null
                    if (number == null) artist = parsed else performer = parsed
                }
                "INDEX" -> {
                    if (number == null) return null
                    val match = indexLine.matchEntire(body) ?: return null
                    val seconds = match.groupValues[3].toInt()
                    val frames = match.groupValues[4].toInt()
                    if (seconds >= 60 || frames >= 75) return null
                    if (match.groupValues[1] == "01") {
                        if (start != null) return null
                        start = match.groupValues[2].toLong() * 60 * 75 + seconds * 75 + frames
                    }
                }
                // CD text and gaps are metadata; INDEX 00 pregaps remain with the preceding track.
                "REM", "FLAGS", "ISRC", "CATALOG", "CDTEXTFILE", "SONGWRITER", "PREGAP", "POSTGAP" -> Unit
                else -> return null
            }
        }
        if (!finish() || result.isEmpty()) return null
        return CueSheet(album, artist, result)
    }

    /** Resolve only existing, unambiguous physical files; the caller reviews before persisting. */
    fun resolve(sheet: CueSheet, library: List<Track>): List<Track>? {
        val physical = library.filterNot { it.isCueTrack }
        val index = M3uLibraryIndex(physical)
        val files = sheet.tracks.map { it.file }.distinct().associateWith { path ->
            val matched = index.match(M3uEntry(path))
            if (matched.issue != null) return null
            matched.track ?: return null
        }
        return sheet.tracks.mapIndexed { index, cue ->
            val source = files.getValue(cue.file)
            val begin = cue.startFrames * 1000 / 75
            val next = sheet.tracks.drop(index + 1).firstOrNull { it.file == cue.file }
            val end = next?.let { it.startFrames * 1000 / 75 } ?: source.durationMs
            if (end <= begin || source.durationMs <= 0 || end > source.durationMs) return null
            source.copy(id = "cue:${source.id}:${cue.startFrames}",
                title = cue.title ?: "${source.title} (${cue.number})",
                artist = cue.performer ?: sheet.performer ?: source.artist,
                artists = (cue.performer ?: sheet.performer)?.let { listOf(it) } ?: source.artists,
                album = sheet.title ?: source.album, albumArtist = sheet.performer ?: source.albumArtist,
                trackNumber = cue.number, durationMs = end - begin,
                clipStartMs = begin, clipEndMs = end, cueSourceId = source.id)
        }
    }
}
