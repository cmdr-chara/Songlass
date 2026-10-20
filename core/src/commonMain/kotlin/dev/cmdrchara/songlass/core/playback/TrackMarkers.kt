package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * File identity accompanies every saved point. A recycled MediaStore id is never enough to
 * associate private listening data with a file. No tags or audio bytes are modified.
 */
data class TrackSignature(
    val id: String,
    val uri: String,
    val path: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
) {
    companion object {
        fun of(track: Track) = TrackSignature(track.id, track.uri, track.path, track.title,
            track.artist, track.album, track.durationMs.coerceAtLeast(0))
    }
}

data class SavedProgress(val track: TrackSignature, val positionMs: Long)
data class TrackBookmark(val id: String, val track: TrackSignature, val positionMs: Long, val title: String?)
data class TrackMarkersState(
    val progress: List<SavedProgress> = emptyList(),
    val bookmarks: List<TrackBookmark> = emptyList(),
)

/**
 * A bounded, versioned, length-prefixed wire format. Unknown/corrupt records are not silently
 * interpreted as positions, and backup validation can reject a damaged value before writing.
 * New keys are explicitly allowlisted by the backup; its original v1 container stays readable.
 */
object TrackMarkersCodec {
    const val PROGRESS_KEY = "playback.progress.v1"
    const val BOOKMARKS_KEY = "playback.bookmarks.v1"
    const val MAX_PROGRESS = 2_500
    const val MAX_BOOKMARKS = 10_000
    private const val HEADER = "v1|"
    private const val MAX_TEXT = 16 * 1024 * 1024
    private const val MAX_FIELD = 4_096

    private fun encodeField(value: String?): String = if (value == null) "-1:" else
        value.length.toString() + ":" + value

    private fun identityFields(id: TrackSignature): List<String?> = listOf(
        id.id, id.uri, id.path, id.title, id.artist, id.album, id.durationMs.toString(),
    )

    private fun identity(fields: List<String?>, offset: Int): TrackSignature {
        val duration = fields[offset + 6]?.toLongOrNull()
        require(duration != null && duration >= 0 && duration.toString() == fields[offset + 6])
        val id = requireNotNull(fields[offset]).also { require(it.isNotEmpty()) }
        val uri = requireNotNull(fields[offset + 1]).also { require(it.isNotEmpty()) }
        return TrackSignature(id, uri, fields[offset + 2], requireNotNull(fields[offset + 3]),
            fields[offset + 4], fields[offset + 5], duration)
    }

    private fun readRows(text: String, width: Int, maximum: Int): List<List<String?>> {
        require(text.length <= MAX_TEXT && text.startsWith(HEADER))
        val rows = ArrayList<List<String?>>()
        var cursor = HEADER.length
        while (cursor < text.length) {
            require(rows.size < maximum)
            val fields = ArrayList<String?>(width)
            repeat(width) {
                val colon = text.indexOf(':', cursor)
                require(colon in (cursor + 1)..(cursor + 8))
                val lengthText = text.substring(cursor, colon)
                val length = lengthText.toIntOrNull()
                require(length != null && length in -1..MAX_FIELD && length.toString() == lengthText)
                cursor = colon + 1
                if (length == -1) fields.add(null) else {
                    require(length <= text.length - cursor)
                    fields.add(text.substring(cursor, cursor + length))
                    cursor += length
                }
            }
            rows.add(fields)
        }
        return rows
    }

    private fun writeRows(rows: List<List<String?>>): String = buildString {
        append(HEADER)
        rows.forEach { row -> row.forEach { append(encodeField(it)) } }
    }

    fun decodeProgress(value: String?): List<SavedProgress> {
        if (value == null) return emptyList()
        val result = readRows(value, 8, MAX_PROGRESS).map { fields ->
            val position = fields[7]?.toLongOrNull()
            require(position != null && position > 0 && position.toString() == fields[7])
            SavedProgress(identity(fields, 0), position)
        }
        require(result.distinctBy { it.track }.size == result.size)
        return result
    }

    fun decodeBookmarks(value: String?): List<TrackBookmark> {
        if (value == null) return emptyList()
        val result = readRows(value, 10, MAX_BOOKMARKS).map { fields ->
            val id = requireNotNull(fields[0])
            require(id.isNotBlank() && id.length <= 80)
            val position = fields[8]?.toLongOrNull()
            require(position != null && position >= 0 && position.toString() == fields[8])
            val title = fields[9]
            require(title == null || (title.isNotBlank() && title.length <= 200 && title.trim() == title))
            TrackBookmark(id, identity(fields, 1), position, title)
        }
        require(result.distinctBy { it.id }.size == result.size)
        return result
    }

    fun encodeProgress(values: List<SavedProgress>): String {
        require(values.size <= MAX_PROGRESS)
        return writeRows(values.map { identityFields(it.track) + it.positionMs.toString() })
    }

    fun encodeBookmarks(values: List<TrackBookmark>): String {
        require(values.size <= MAX_BOOKMARKS)
        return writeRows(values.map { listOf(it.id) + identityFields(it.track) +
            listOf(it.positionMs.toString(), it.title) })
    }

    fun canonicalProgress(value: String): Boolean =
        runCatching { encodeProgress(decodeProgress(value)) == value }.getOrDefault(false)

    fun canonicalBookmarks(value: String): Boolean =
        runCatching { encodeBookmarks(decodeBookmarks(value)) == value }.getOrDefault(false)

    /**
     * Backup remaps only id references that have a safe match. The original path, metadata and
     * duration stay attached even after remapping; a mismatched candidate cannot be opened.
     * A missing/conflicting record is retained as unavailable, never attached by id alone.
     */
    fun remapProgress(value: String, map: (String) -> String?): String =
        encodeProgress(decodeProgress(value).map { it.copy(track = it.track.copy(id = map(it.track.id) ?: it.track.id)) })

    fun remapBookmarks(value: String, map: (String) -> String?): String =
        encodeBookmarks(decodeBookmarks(value).map { it.copy(track = it.track.copy(id = map(it.track.id) ?: it.track.id)) })
}

class TrackMarkers(private val store: KeyValueStore, private val clock: () -> Long = { 0L }) {
    private fun read(): TrackMarkersState = TrackMarkersState(
        progress = runCatching { TrackMarkersCodec.decodeProgress(store.getString(TrackMarkersCodec.PROGRESS_KEY)) }.getOrDefault(emptyList()),
        bookmarks = runCatching { TrackMarkersCodec.decodeBookmarks(store.getString(TrackMarkersCodec.BOOKMARKS_KEY)) }.getOrDefault(emptyList()),
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<TrackMarkersState> = _state.asStateFlow()
    private var byPath: Map<String, List<Track>> = emptyMap()
    private var byUriId: Map<Pair<String, String>, List<Track>> = emptyMap()
    /** Backup replacement owns these keys until repository reload has finished. */
    var writesSuspended: Boolean = false

    fun reload() { _state.value = read() }

    fun setLibrary(tracks: List<Track>) {
        // Build once per MediaStore refresh. Bookmark lists never scan the full library per row.
        byPath = tracks.mapNotNull { track -> track.path?.let { it to track } }.groupBy({ it.first }, { it.second })
        byUriId = tracks.groupBy { it.id to it.uri }
    }

    /** Identity must be unique in the unfiltered library. Similar tags alone never match. */
    fun resolve(signature: TrackSignature): Track? {
        val candidates = if (signature.path != null) byPath[signature.path] else byUriId[signature.id to signature.uri]
        return candidates?.singleOrNull()?.takeIf { candidate ->
            candidate.title == signature.title && candidate.artist == signature.artist &&
                candidate.album == signature.album &&
                abs(candidate.durationMs.coerceAtLeast(0) - signature.durationMs) <= 1_000
        }
    }

    fun bookmarksFor(track: Track): List<TrackBookmark> =
        state.value.bookmarks.filter { it.track == TrackSignature.of(track) || resolve(it.track) == track }

    fun progressFor(track: Track, minimumDurationMs: Long): Long? {
        if (track.durationMs < minimumDurationMs || track.durationMs <= 0 || resolve(TrackSignature.of(track)) != track) return null
        val entries = state.value.progress.filter { it.track == TrackSignature.of(track) || resolve(it.track) == track }
        if (entries.size != 1) return null
        return entries.single().positionMs.takeIf { it >= MIN_POSITION_MS && it < track.durationMs - END_MARGIN_MS }
    }

    /** Call only on pauses, seeks, explicit exits, and spaced position milestones. */
    fun checkpoint(track: Track, positionMs: Long, thresholdMs: Long, force: Boolean = false) {
        if (writesSuspended || track.durationMs < thresholdMs || track.durationMs <= 0) return
        val signature = TrackSignature.of(track)
        val existing = state.value.progress.filter { it.track == signature || resolve(it.track) == track }
        // Loading or crossing the first seconds must not destroy a previously saved point.
        // Only an explicit restart clears it, or reaching the end margin/completion.
        if (positionMs < MIN_POSITION_MS) return
        if (positionMs >= track.durationMs - END_MARGIN_MS) {
            if (existing.isNotEmpty()) writeProgress(state.value.progress - existing.toSet())
            return
        }
        val normalized = positionMs.coerceIn(0, track.durationMs)
        if (existing.size == 1 && existing.single().track == signature &&
            existing.single().positionMs == normalized) return
        if (!force && existing.size == 1 && abs(existing.single().positionMs - normalized) < CHECKPOINT_MS) return
        val remaining = state.value.progress - existing.toSet()
        val updated = (remaining + SavedProgress(signature, normalized)).takeLast(TrackMarkersCodec.MAX_PROGRESS)
        writeProgress(updated)
    }

    fun completed(track: Track) {
        if (writesSuspended) return
        val signature = TrackSignature.of(track)
        val matches = state.value.progress.filter { it.track == signature || resolve(it.track) == track }
        if (matches.isNotEmpty()) writeProgress(state.value.progress - matches.toSet())
    }

    /** Always treat an explicit seek to zero as a fresh start, not an invitation to resume. */
    fun restart(track: Track) = completed(track)

    fun add(track: Track, positionMs: Long, title: String?): TrackBookmark {
        require(!writesSuspended)
        val normalizedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedTitle == null || normalizedTitle.length <= 200)
        require(positionMs >= 0 && (track.durationMs <= 0 || positionMs <= track.durationMs))
        val existing = state.value.bookmarks
        require(existing.size < TrackMarkersCodec.MAX_BOOKMARKS)
        val base = clock().coerceAtLeast(0).toString()
        var sequence = 0
        var id = base
        while (existing.any { it.id == id }) { sequence++; id = base + "-" + sequence }
        val marker = TrackBookmark(id, TrackSignature.of(track), positionMs, normalizedTitle)
        writeBookmarks(existing + marker)
        return marker
    }

    fun rename(id: String, title: String?): Boolean {
        if (writesSuspended) return false
        val normalized = title?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null && normalized.length > 200) return false
        if (state.value.bookmarks.none { it.id == id }) return false
        writeBookmarks(state.value.bookmarks.map { if (it.id == id) it.copy(title = normalized) else it })
        return true
    }

    fun delete(id: String): Boolean {
        if (writesSuspended) return false
        val next = state.value.bookmarks.filterNot { it.id == id }
        if (next.size == state.value.bookmarks.size) return false
        writeBookmarks(next)
        return true
    }

    private fun writeProgress(values: List<SavedProgress>) {
        store.putString(TrackMarkersCodec.PROGRESS_KEY, TrackMarkersCodec.encodeProgress(values))
        _state.value = state.value.copy(progress = values)
    }

    private fun writeBookmarks(values: List<TrackBookmark>) {
        store.putString(TrackMarkersCodec.BOOKMARKS_KEY, TrackMarkersCodec.encodeBookmarks(values))
        _state.value = state.value.copy(bookmarks = values)
    }

    companion object {
        const val MIN_POSITION_MS = 5_000L
        const val END_MARGIN_MS = 5_000L
        const val CHECKPOINT_MS = 60_000L
    }
}
