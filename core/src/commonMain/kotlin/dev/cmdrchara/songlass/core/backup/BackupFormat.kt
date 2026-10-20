package dev.cmdrchara.songlass.core.backup

/** Identity of a track as it was on the device that made the backup. */
internal data class SourceTrack(
    val id: String,
    val uri: String,
    /** `folder/fileName` relative to the storage root, when known. */
    val path: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
)

internal class ParsedBackup(
    val createdMs: Long,
    /** Store key → value, every key unique. */
    val entries: Map<String, String>,
    val sources: List<SourceTrack>,
)

/**
 * Text container for a backup. Version 1:
 *
 * ```
 * SONGLASS-BACKUP 1\n
 * H <createdMs> <entryCount> <sourceTrackCount>\n
 * K <key> <value>\n          × entryCount
 * T <id> <uri> <path|null> <title> <artist|null> <album|null> <durationMs>\n   × sourceTrackCount
 * E <checksum of everything above>\n
 * ```
 *
 * Every field is `<length>:<characters>` (length in UTF-16 units, `-1:` for null) with no other
 * separator, so values may hold any character, newlines and control characters included. The
 * checksum is FNV-1a (64 bit) over the preceding text: it catches truncation and accidental
 * damage, it is not a signature. Anything after the final newline is rejected.
 */
internal object BackupFormat {
    const val MAGIC = "SONGLASS-BACKUP"
    const val VERSION = 1
    const val MAX_ENTRIES = 250_000
    const val MAX_SOURCE_TRACKS = 250_000
    const val MAX_KEY_CHARS = 1_024
    /** U+FEFF, written numerically so it stays visible in the source. */
    private val BYTE_ORDER_MARK = Char(0xFEFF)
    private const val MAX_HEADER_CHARS = 64
    private const val MAX_LENGTH_DIGITS = 9

    fun write(createdMs: Long, entries: Map<String, String>, sources: List<SourceTrack>): String {
        val out = StringBuilder()
        out.append(MAGIC).append(' ').append(VERSION).append('\n')
        record(out, "H", createdMs.toString(), entries.size.toString(), sources.size.toString())
        for ((key, value) in entries) record(out, "K", key, value)
        for (s in sources) record(out, "T", s.id, s.uri, s.path, s.title, s.artist, s.album, s.durationMs.toString())
        record(out, "E", checksum(out, out.length))
        return out.toString()
    }

    /** Validates the whole container; throws [BackupException] before any caller can act on it. */
    fun read(text: String): ParsedBackup {
        if (text.length > DataBackup.MAX_BYTES) throw BackupException(BackupError.TooLarge)
        // Some editors prepend a byte order mark; the checksum is over what follows it.
        val body = text.removePrefix(BYTE_ORDER_MARK.toString())
        val newline = body.indexOf('\n').takeIf { it in 0..MAX_HEADER_CHARS } ?: throw BackupException(BackupError.NotABackup)
        val header = body.substring(0, newline)
        if (!header.startsWith("$MAGIC ")) throw BackupException(BackupError.NotABackup)
        val version = header.substring(MAGIC.length + 1).toIntOrNull() ?: throw BackupException(BackupError.Malformed)
        if (version != VERSION) throw BackupException(BackupError.UnsupportedVersion)

        val reader = Reader(body, newline + 1)
        reader.expectKind("H")
        val createdMs = reader.long()
        val entryCount = reader.int(MAX_ENTRIES)
        val sourceCount = reader.int(MAX_SOURCE_TRACKS)
        reader.endRecord()

        val entries = LinkedHashMap<String, String>()
        repeat(entryCount) {
            reader.expectKind("K")
            val key = reader.string()
            val value = reader.string()
            reader.endRecord()
            if (key.isEmpty() || key.length > MAX_KEY_CHARS) throw BackupException(BackupError.InvalidEntry)
            if (entries.put(key, value) != null) throw BackupException(BackupError.DuplicateEntry)
        }
        val sources = LinkedHashMap<String, SourceTrack>()
        val uris = HashSet<String>()
        repeat(sourceCount) {
            reader.expectKind("T")
            val source = SourceTrack(
                id = reader.string(),
                uri = reader.string(),
                path = reader.nullableString(),
                title = reader.string(),
                artist = reader.nullableString(),
                album = reader.nullableString(),
                durationMs = reader.long(),
            )
            reader.endRecord()
            if (source.id.isEmpty() || source.uri.isEmpty() || source.durationMs < 0) throw BackupException(BackupError.InvalidEntry)
            if (sources.put(source.id, source) != null || !uris.add(source.uri)) throw BackupException(BackupError.DuplicateEntry)
        }
        val checkedUpTo = reader.position
        reader.expectKind("E")
        val stored = reader.string()
        reader.endRecord()
        if (!reader.atEnd) throw BackupException(BackupError.TrailingContent)
        if (stored != checksum(body, checkedUpTo)) throw BackupException(BackupError.ChecksumMismatch)
        return ParsedBackup(createdMs, entries, sources.values.toList())
    }

    private fun record(out: StringBuilder, vararg fields: String?) {
        for (field in fields) {
            if (field == null) out.append("-1:") else out.append(field.length).append(':').append(field)
        }
        out.append('\n')
    }

    /** FNV-1a over the UTF-16 units of the first [length] characters, as 16 hex digits. */
    internal fun checksum(text: CharSequence, length: Int): String {
        var hash = -3750763034362895579L // 0xcbf29ce484222325
        for (i in 0 until length) {
            val unit = text[i].code
            hash = (hash xor (unit and 0xFF).toLong()) * 0x100000001b3L
            hash = (hash xor (unit ushr 8).toLong()) * 0x100000001b3L
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }

    private class Reader(private val text: String, var position: Int) {
        val atEnd: Boolean get() = position >= text.length

        fun expectKind(kind: String) {
            if (string() != kind) throw BackupException(BackupError.Malformed)
        }

        fun endRecord() {
            if (position >= text.length || text[position] != '\n') throw BackupException(BackupError.Malformed)
            position++
        }

        fun string(): String = field(allowNull = false) ?: throw BackupException(BackupError.Malformed)

        fun nullableString(): String? = field(allowNull = true)

        /** A canonical decimal number carried in a text field. */
        fun long(): Long = string().let { it.toLongOrNull()?.takeIf { n -> n >= 0 && n.toString() == it } }
            ?: throw BackupException(BackupError.Malformed)

        fun int(max: Int): Int = long().takeIf { it <= max }?.toInt() ?: throw BackupException(BackupError.Malformed)

        private fun field(allowNull: Boolean): String? {
            var i = position
            if (i < text.length && text[i] == '-') i++
            while (i < text.length && text[i] in '0'..'9' && i - position < MAX_LENGTH_DIGITS) i++
            if (i >= text.length || text[i] != ':') throw BackupException(BackupError.Malformed)
            val digits = text.substring(position, i)
            val length = digits.toIntOrNull()?.takeIf { it >= -1 && it.toString() == digits }
                ?: throw BackupException(BackupError.Malformed)
            position = i + 1
            if (length == -1) {
                if (!allowNull) throw BackupException(BackupError.Malformed)
                return null
            }
            if (length > text.length - position) throw BackupException(BackupError.Malformed)
            return text.substring(position, position + length).also { position += length }
        }
    }
}
