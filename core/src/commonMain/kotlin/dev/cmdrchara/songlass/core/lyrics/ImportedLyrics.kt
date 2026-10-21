package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track

/** A currently verified library selection. Only a registry-backed source may set [canonicalId]. */
data class LyricsSelection(val track: Track, val canonicalId: Boolean = false, val accessGeneration: Long = 0)

/** File access is unavailable or the selection changed while a source was being read. */
data object LyricsUnavailable : LyricsResult

enum class LyricsImportResult { Saved, Invalid, Unavailable, Changed, Failed }

/** Retained text is never attached to a song merely because its old URI is occupied again. */
class LyricsRecoveryEntry internal constructor(
    val key: String,
    val title: String?,
    val excerpt: String,
    val canLink: Boolean,
    internal val storedValue: String,
    internal val text: String?,
) {
    override fun toString(): String = "LyricsRecoveryEntry(linkable=$canLink)"
}

class LyricsRelinkPreview internal constructor(
    val entry: LyricsRecoveryEntry,
    val track: Track,
    val lyrics: Lyrics,
    val replacesExisting: Boolean,
    internal val selection: LyricsSelection,
    internal val previousTarget: String?,
) {
    override fun toString(): String = "LyricsRelinkPreview(replacesExisting=$replacesExisting)"
}

internal enum class LyricsBinding { Canonical, Observed, Unassigned }

/** The original document remains byte-for-character intact; this codec does not parse or rewrite it. */
internal data class ImportedLyricsRecord(
    val binding: LyricsBinding,
    val id: String,
    val uri: String,
    val path: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val dateAddedMs: Long?,
    val text: String,
) {
    override fun toString(): String = "ImportedLyricsRecord(binding=$binding, chars=${text.length})"

    fun matches(selection: LyricsSelection): Boolean = when {
        binding == LyricsBinding.Unassigned || id != selection.track.id -> false
        binding == LyricsBinding.Canonical && selection.canonicalId -> true
        else -> matchesObserved(selection.track)
    }

    /** A non-registry source must retain all observed recording/location facts. */
    fun matchesObserved(track: Track): Boolean = id == track.id && uri == track.uri && path == track.path &&
        title == track.title && artist == track.artist && album == track.album && durationMs == track.durationMs &&
        (dateAddedMs == null || track.dateAddedMs == null || dateAddedMs == track.dateAddedMs)

    fun asTrack(): Track = Track(id = id, uri = uri, title = title, artist = artist, album = album,
        durationMs = durationMs, dateAddedMs = dateAddedMs,
        folder = path?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() },
        fileName = path?.substringAfterLast('/'))

    fun unassigned(): ImportedLyricsRecord = copy(binding = LyricsBinding.Unassigned)

    companion object {
        fun bound(selection: LyricsSelection, text: String): ImportedLyricsRecord = selection.track.let {
            ImportedLyricsRecord(if (selection.canonicalId) LyricsBinding.Canonical else LyricsBinding.Observed,
                it.id, it.uri, it.path, it.title, it.artist, it.album, it.durationMs, it.dateAddedMs, text)
        }
    }
}

/** Strict, independently versioned, bounded fields. Unknown or damaged records remain inert. */
internal object ImportedLyricsCodec {
    const val LEGACY_PREFIX = "lyrics.import."
    const val PREFIX = "lyrics.saved.v1."
    private const val HEADER = "SONGLASS-LYRICS-1\n"
    private const val MAX_FIELD = 4_096
    private const val MAX_ID = 240
    private const val MAX_RECORD = LyricsParser.MAX_CHARACTERS + 7 * MAX_FIELD

    // A reversible key has no hash collision; record contents still have to validate its identity.
    fun trackKey(id: String): String? = id.takeIf { it.isNotEmpty() && it.length <= MAX_ID }?.let {
        PREFIX + "t." + it.map { char -> char.code.toString(16).padStart(4, '0') }.joinToString("")
    }

    fun isKey(key: String): Boolean {
        if (!key.startsWith(PREFIX) || key.length > 1_024) return false
        val suffix = key.removePrefix(PREFIX)
        return when {
            suffix.startsWith("t.") -> suffix.length > 2 && (suffix.length - 2) % 4 == 0 &&
                suffix.drop(2).all { it in '0'..'9' || it in 'a'..'f' }
            suffix.startsWith("r.") -> suffix.length in 3..82 &&
                suffix.drop(2).all { it in '0'..'9' || it in 'a'..'f' }
            else -> false
        }
    }

    fun encode(record: ImportedLyricsRecord): String {
        require(record.id.isNotEmpty() && record.id.length <= MAX_ID && record.uri.isNotEmpty())
        require(record.text.length <= LyricsParser.MAX_CHARACTERS && record.durationMs >= 0)
        val fields = listOf(record.binding.name, record.id, record.uri, record.path, record.title,
            record.artist, record.album, record.durationMs.toString(), record.dateAddedMs?.toString(), record.text)
        require(fields.dropLast(1).all { it == null || it.length <= MAX_FIELD })
        return HEADER + fields.joinToString("") { if (it == null) "-1:" else "${it.length}:$it" }
    }

    fun decode(value: String): ImportedLyricsRecord? {
        if (value.length > MAX_RECORD || !value.startsWith(HEADER)) return null
        return runCatching {
            var offset = HEADER.length
            fun field(limit: Int = MAX_FIELD): String? {
                val colon = value.indexOf(':', offset)
                require(colon in offset..(offset + 8).coerceAtMost(value.lastIndex))
                val lengthText = value.substring(offset, colon)
                val length = lengthText.toInt()
                require(length.toString() == lengthText && length in -1..limit)
                offset = colon + 1
                if (length == -1) return null
                require(length <= value.length - offset)
                return value.substring(offset, offset + length).also { offset += length }
            }
            val binding = LyricsBinding.valueOf(requireNotNull(field()))
            val id = requireNotNull(field(MAX_ID)).also { require(it.isNotEmpty()) }
            val uri = requireNotNull(field()).also { require(it.isNotEmpty()) }
            val path = field()
            val title = requireNotNull(field())
            val artist = field()
            val album = field()
            val durationText = requireNotNull(field())
            val duration = durationText.toLong().also { require(it >= 0 && it.toString() == durationText) }
            val dateText = field()
            val date = dateText?.toLong()?.also { require(it.toString() == dateText) }
            val text = requireNotNull(field(LyricsParser.MAX_CHARACTERS))
            require(offset == value.length)
            ImportedLyricsRecord(binding, id, uri, path, title, artist, album, duration, date, text)
        }.getOrNull()
    }
}
