package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track

/** The display label is kept because an artist or title may itself contain " - ". */
data class M3uEntry(
    val path: String,
    val title: String? = null,
    val artist: String? = null,
    val durationSec: Int? = null,
    val displayLabel: String? = null,
)

enum class M3uIssueKind { Missing, Ambiguous, Conflict, Unsupported, Malformed }
data class M3uIssue(val entry: M3uEntry, val kind: M3uIssueKind)
enum class M3uFileFailure { TooLarge, TooManyEntries }
data class M3uDocument(
    val entries: List<M3uEntry>,
    val issues: List<M3uIssue> = emptyList(),
    val failure: M3uFileFailure? = null,
)

/** Unique matched tracks in file order. An uncertain entry never selects a recording. */
data class M3uImport(
    val tracks: List<Track>,
    val issues: List<M3uIssue>,
    val duplicates: Int = 0,
    val failure: M3uFileFailure? = null,
) {
    val missing: List<M3uEntry> get() = issues.filter { it.kind == M3uIssueKind.Missing }.map { it.entry }
    fun count(kind: M3uIssueKind): Int = issues.count { it.kind == kind }
}

data class M3uExport(val text: String, val exported: Int, val skipped: Int)
data class M3uPlaylistExport(val output: M3uExport, val missing: Int)

/**
 * Extended M3U for local files. Matching uses a complete library snapshot, independent of UI
 * filters. A unique path is accepted unless known recording facts conflict; relocation by tags
 * needs a unique title, artist and positive compatible duration. This is not a content hash.
 */
object M3u {
    const val MAX_FILE_BYTES = 1024 * 1024
    const val MAX_ENTRIES = 20_000
    internal const val MAX_PATH_LENGTH = 4096

    fun write(tracks: List<Track>): String = writeDetailed(tracks).text

    /** Filters never remove stored playlist entries from an export. Missing references stay stored. */
    fun writePlaylist(playlist: UserPlaylist, allTracks: List<Track>): M3uPlaylistExport {
        val byId = allTracks.groupBy { it.id }
        val tracks = playlist.trackIds.mapNotNull { byId[it]?.singleOrNull() }
        return M3uPlaylistExport(writeDetailed(tracks), playlist.trackIds.size - tracks.size)
    }

    fun writeDetailed(tracks: List<Track>): M3uExport {
        var exported = 0
        val text = buildString {
            append("#EXTM3U\n")
            for (track in tracks) {
                val path = track.path?.takeIf(::exportablePath) ?: continue
                val seconds = if (track.durationMs > 0) track.durationMs / 1000 else -1
                val label = listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, track.title)
                    .joinToString(" - ").singleLine()
                append("#EXTINF:").append(seconds).append(',').append(label).append('\n')
                // A legal file name must not become an M3U comment or a URI scheme.
                val leading = path.trimStart()
                val scheme = leading.substringBefore(':', "")
                val escape = leading.startsWith('#') || scheme.isNotEmpty() && scheme.all { it.isLetterOrDigit() || it in "+-." }
                append(if (escape) "./$path" else path).append('\n')
                exported++
            }
        }
        return M3uExport(text, exported, tracks.size - exported)
    }

    private fun exportablePath(path: String): Boolean = path.isNotBlank() &&
        path.length <= MAX_PATH_LENGTH && path.none { it == '\u0000' || it == '\r' || it == '\n' || it == '\\' }

    fun parse(text: String): List<M3uEntry> = parseDetailed(text).entries

    fun parseDetailed(text: String): M3uDocument {
        if (text.length > MAX_FILE_BYTES) return M3uDocument(emptyList(), failure = M3uFileFailure.TooLarge)
        val entries = mutableListOf<M3uEntry>()
        val issues = mutableListOf<M3uIssue>()
        var pending: M3uEntry? = null
        var invalidMetadata = false
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            // Do not trim a file name: leading/trailing spaces can be real file-name bytes.
            val directive = raw.trim()
            if (directive.isEmpty()) continue
            if (directive.startsWith("#EXTINF:", ignoreCase = true)) {
                if (pending != null || invalidMetadata) {
                    issues += M3uIssue(pending ?: M3uEntry(""), M3uIssueKind.Malformed)
                }
                val body = directive.substringAfter(':')
                val durationText = body.substringBefore(',').trim().substringBefore(' ')
                val duration = durationText.toIntOrNull()
                val label = body.substringAfter(',', "").trim()
                val artist = label.substringBefore(" - ", "").trim().ifEmpty { null }
                val title = (if (artist != null) label.substringAfter(" - ") else label).trim().ifEmpty { null }
                pending = M3uEntry("", title, artist, duration?.takeIf { it > 0 }, label.ifEmpty { null })
                invalidMetadata = !body.contains(',') || duration == null || duration < -1 || label.contains('\u0000')
            } else if (!directive.startsWith('#')) {
                val parsed = localM3uPath(raw)
                val entry = (pending ?: M3uEntry("")).copy(path = parsed.path ?: raw.take(MAX_PATH_LENGTH))
                val issue = if (invalidMetadata) M3uIssueKind.Malformed else parsed.issue
                if (issue == null) entries += entry else issues += M3uIssue(entry, issue)
                pending = null
                invalidMetadata = false
            }
            if (entries.size + issues.size > MAX_ENTRIES) {
                // The caller cannot accidentally import a truncated prefix as a complete document.
                return M3uDocument(emptyList(), failure = M3uFileFailure.TooManyEntries)
            }
        }
        if (pending != null) issues += M3uIssue(pending, M3uIssueKind.Malformed)
        if (entries.size + issues.size > MAX_ENTRIES) return M3uDocument(emptyList(), failure = M3uFileFailure.TooManyEntries)
        return M3uDocument(entries, issues)
    }

    fun resolve(entries: List<M3uEntry>, library: MusicLibrary): M3uImport =
        resolve(M3uDocument(entries), library.tracks)

    fun resolve(document: M3uDocument, allTracks: List<Track>): M3uImport {
        if (document.failure != null) return M3uImport(emptyList(), document.issues, failure = document.failure)
        if (document.entries.size + document.issues.size > MAX_ENTRIES) {
            return M3uImport(emptyList(), emptyList(), failure = M3uFileFailure.TooManyEntries)
        }
        val index = M3uLibraryIndex(allTracks)
        val tracks = mutableListOf<Track>()
        val seen = mutableSetOf<String>()
        val issues = document.issues.toMutableList()
        val results = mutableMapOf<M3uEntry, M3uMatch>()
        var duplicates = 0
        for (entry in document.entries) {
            val match = results.getOrPut(entry) { index.match(entry) }
            if (match.track != null) {
                if (seen.add(match.track.id)) tracks += match.track else duplicates++
            } else issues += M3uIssue(entry, match.issue ?: M3uIssueKind.Missing)
        }
        return M3uImport(tracks, issues, duplicates)
    }

    /** UTF-8 is mandatory for .m3u8; legacy .m3u may use ISO-8859-1. Never replace bad bytes. */
    fun decodeText(bytes: ByteArray, fileName: String?): String? {
        if (bytes.size > MAX_FILE_BYTES || bytes.take(2).let {
                it == listOf(0xff.toByte(), 0xfe.toByte()) || it == listOf(0xfe.toByte(), 0xff.toByte())
            }) return null
        val utf8 = try { bytes.decodeToString(throwOnInvalidSequence = true) } catch (_: CharacterCodingException) { null }
        return utf8 ?: if (fileName?.endsWith(".m3u", ignoreCase = true) == true) {
            buildString(bytes.size) { bytes.forEach { append((it.toInt() and 0xff).toChar()) } }
        } else null
    }

    /** Suggest a filename, not a path or a document-provider command. */
    fun exportFileName(name: String): String = name.substringBeforeLast('.', name)
        .map { if (it == '/' || it == '\\' || it.code < 32 || it == '\u007f') '_' else it }
        .joinToString("").trim().trim('.').take(120).ifEmpty { "Playlist" } + ".m3u8"

    fun nameFromFile(fileName: String?): String? = fileName?.replace('\\', '/')
        ?.substringAfterLast('/')?.substringBeforeLast('.')?.singleLine()?.trim()?.ifEmpty { null }
}

internal fun String.singleLine(): String = replace('\r', ' ').replace('\n', ' ').replace('\u0000', ' ')
