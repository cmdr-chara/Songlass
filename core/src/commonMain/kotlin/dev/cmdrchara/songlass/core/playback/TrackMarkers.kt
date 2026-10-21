package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Trust is local to an installation; a backup must validate it against its target library. */
enum class MarkerBinding { Legacy, Canonical, Unresolved }

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
    val binding: MarkerBinding = MarkerBinding.Legacy,
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
 * Different saved points may become the same active signature after remapping or promotion.
 * There is no timestamp that could justify choosing one. Keep every original row, position and
 * file fact inertly instead, including when the original rows differed only in their binding.
 */
private fun preserveAmbiguousProgress(
    original: List<SavedProgress>,
    mapped: List<SavedProgress>,
): List<SavedProgress> {
    require(original.size == mapped.size)
    val counts = HashMap<TrackSignature, Int>()
    mapped.forEach { point ->
        if (point.track.binding != MarkerBinding.Unresolved) {
            counts[point.track] = (counts[point.track] ?: 0) + 1
        }
    }
    if (counts.values.none { it > 1 }) return mapped
    return mapped.mapIndexed { index, point ->
        if ((counts[point.track] ?: 0) <= 1) point else original[index].let {
            it.copy(track = it.track.copy(binding = MarkerBinding.Unresolved))
        }
    }
}

/** The backup matcher distinguishes a missing file from an occupied or ambiguous identity. */
sealed interface MarkerRestoreResolution {
    data class Matched(val track: Track) : MarkerRestoreResolution
    data object Missing : MarkerRestoreResolution
    data object Conflict : MarkerRestoreResolution
}

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
    private const val HEADER_V1 = "v1|"
    private const val HEADER_V2 = "v2|"
    private const val MAX_TEXT = 16 * 1024 * 1024
    private const val MAX_FIELD = 4_096

    private fun encodeField(value: String?): String = if (value == null) "-1:" else
        value.length.toString() + ":" + value

    private fun identityFields(id: TrackSignature): List<String?> = listOf(
        id.id, id.uri, id.path, id.title, id.artist, id.album, id.durationMs.toString(),
    )

    private fun identity(fields: List<String?>, offset: Int, binding: MarkerBinding): TrackSignature {
        val duration = fields[offset + 6]?.toLongOrNull()
        require(duration != null && duration >= 0 && duration.toString() == fields[offset + 6])
        val id = requireNotNull(fields[offset]).also { require(it.isNotEmpty()) }
        val uri = requireNotNull(fields[offset + 1]).also { require(it.isNotEmpty()) }
        return TrackSignature(id, uri, fields[offset + 2], requireNotNull(fields[offset + 3]),
            fields[offset + 4], fields[offset + 5], duration, binding)
    }

    private fun hasBinding(text: String): Boolean {
        require(text.length <= MAX_TEXT)
        return when {
            text.startsWith(HEADER_V1) -> false
            text.startsWith(HEADER_V2) -> true
            else -> error("Unknown marker version")
        }
    }

    private fun binding(value: String?): MarkerBinding = when (value) {
        "legacy" -> MarkerBinding.Legacy
        "canonical" -> MarkerBinding.Canonical
        "unresolved" -> MarkerBinding.Unresolved
        else -> error("Unknown marker binding")
    }

    private fun bindingField(binding: MarkerBinding): String = when (binding) {
        MarkerBinding.Legacy -> "legacy"
        MarkerBinding.Canonical -> "canonical"
        MarkerBinding.Unresolved -> "unresolved"
    }

    private fun readRows(text: String, width: Int, maximum: Int): List<List<String?>> {
        val rows = ArrayList<List<String?>>()
        var cursor = HEADER_V1.length
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

    private fun writeRows(rows: List<List<String?>>, hasBinding: Boolean): String {
        // Reject before constructing or persisting a value our own reader cannot accept.
        val header = if (hasBinding) HEADER_V2 else HEADER_V1
        var size = header.length
        rows.forEach { row -> row.forEach { field ->
            require(field == null || field.length <= MAX_FIELD)
            val encodedSize = if (field == null) 3 else field.length.toString().length + 1 + field.length
            require(encodedSize <= MAX_TEXT - size)
            size += encodedSize
        } }
        return buildString(size) {
            append(header)
            rows.forEach { row -> row.forEach { append(encodeField(it)) } }
        }
    }

    private fun validateIdentity(track: TrackSignature) {
        require(track.id.isNotEmpty() && track.uri.isNotEmpty() && track.durationMs >= 0)
    }

    private fun validateProgress(progress: SavedProgress) {
        validateIdentity(progress.track)
        require(progress.positionMs > 0 &&
            (progress.track.durationMs == 0L || progress.positionMs <= progress.track.durationMs))
    }

    private fun requireUniqueActiveProgress(values: List<SavedProgress>) {
        val seen = HashSet<TrackSignature>()
        require(values.all { it.track.binding == MarkerBinding.Unresolved || seen.add(it.track) })
    }

    private fun validateBookmark(bookmark: TrackBookmark) {
        validateIdentity(bookmark.track)
        require(bookmark.id.isNotBlank() && bookmark.id.length <= 80)
        require(bookmark.positionMs >= 0 &&
            (bookmark.track.durationMs == 0L || bookmark.positionMs <= bookmark.track.durationMs))
        require(bookmark.title == null || (bookmark.title.isNotBlank() && bookmark.title.length <= 200 &&
            bookmark.title.trim() == bookmark.title))
    }

    fun decodeProgress(value: String?): List<SavedProgress> {
        if (value == null) return emptyList()
        val versionTwo = hasBinding(value)
        val result = readRows(value, if (versionTwo) 9 else 8, MAX_PROGRESS).map { fields ->
            val position = fields[7]?.toLongOrNull()
            require(position != null && position > 0 && position.toString() == fields[7])
            val binding = if (versionTwo) binding(fields[8]) else MarkerBinding.Legacy
            SavedProgress(identity(fields, 0, binding), position).also(::validateProgress)
        }
        requireUniqueActiveProgress(result)
        return result
    }

    fun decodeBookmarks(value: String?): List<TrackBookmark> {
        if (value == null) return emptyList()
        val versionTwo = hasBinding(value)
        val result = readRows(value, if (versionTwo) 11 else 10, MAX_BOOKMARKS).map { fields ->
            val id = requireNotNull(fields[0])
            require(id.isNotBlank() && id.length <= 80)
            val position = fields[8]?.toLongOrNull()
            require(position != null && position >= 0 && position.toString() == fields[8])
            val title = fields[9]
            require(title == null || (title.isNotBlank() && title.length <= 200 && title.trim() == title))
            val binding = if (versionTwo) binding(fields[10]) else MarkerBinding.Legacy
            TrackBookmark(id, identity(fields, 1, binding), position, title).also(::validateBookmark)
        }
        require(result.distinctBy { it.id }.size == result.size)
        return result
    }

    fun encodeProgress(values: List<SavedProgress>): String {
        require(values.size <= MAX_PROGRESS)
        requireUniqueActiveProgress(values)
        values.forEach(::validateProgress)
        val versionTwo = values.any { it.track.binding != MarkerBinding.Legacy }
        return writeRows(values.map { identityFields(it.track) + it.positionMs.toString() +
            (if (versionTwo) listOf(bindingField(it.track.binding)) else emptyList()) }, versionTwo)
    }

    fun encodeBookmarks(values: List<TrackBookmark>): String {
        require(values.size <= MAX_BOOKMARKS)
        require(values.distinctBy { it.id }.size == values.size)
        values.forEach(::validateBookmark)
        val versionTwo = values.any { it.track.binding != MarkerBinding.Legacy }
        return writeRows(values.map { listOf(it.id) + identityFields(it.track) +
            listOf(it.positionMs.toString(), it.title) +
            (if (versionTwo) listOf(bindingField(it.track.binding)) else emptyList()) }, versionTwo)
    }

    fun canonicalProgress(value: String): Boolean =
        runCatching { encodeProgress(decodeProgress(value)) == value }.getOrDefault(false)

    fun canonicalBookmarks(value: String): Boolean =
        runCatching { encodeBookmarks(decodeBookmarks(value)) == value }.getOrDefault(false)

    /** Known local location and recording facts are both required to trust a pre-registry record. */
    fun matchesLegacyIdentity(signature: TrackSignature, sourceSignature: TrackSignature): Boolean {
        val sameLocation = if (signature.path != null || sourceSignature.path != null) {
            !signature.path.isNullOrBlank() && signature.path == sourceSignature.path
        } else signature.uri == sourceSignature.uri
        if (!sameLocation || signature.title.isBlank() || signature.title != sourceSignature.title ||
            signature.artist != sourceSignature.artist || signature.album != sourceSignature.album ||
            signature.durationMs <= 0 || sourceSignature.durationMs <= 0) return false
        val difference = if (signature.durationMs >= sourceSignature.durationMs) {
            signature.durationMs - sourceSignature.durationMs
        } else sourceSignature.durationMs - signature.durationMs
        return difference <= 1_000
    }

    /**
     * Source trust never crosses an installation boundary. Matched records bind to the validated
     * target facts in the restore transaction itself, without a second commit during reload.
     * Missing records keep their original identity without trust. Conflicts keep all source
     * fields but are permanently inert, including after re-export.
     */
    private fun remapIdentity(
        signature: TrackSignature,
        map: (String) -> String?,
        resolveMarker: ((TrackSignature) -> MarkerRestoreResolution)?,
    ): TrackSignature {
        if (signature.binding == MarkerBinding.Unresolved) return signature
        if (resolveMarker == null) {
            val id = map(signature.id) ?: return signature.copy(binding = MarkerBinding.Unresolved)
            return signature.copy(id = id, binding = MarkerBinding.Legacy)
        }
        return when (val resolution = resolveMarker(signature)) {
            is MarkerRestoreResolution.Matched -> TrackSignature.of(resolution.track)
                .copy(binding = MarkerBinding.Canonical)
            MarkerRestoreResolution.Missing -> signature.copy(binding = MarkerBinding.Legacy)
            MarkerRestoreResolution.Conflict -> signature.copy(binding = MarkerBinding.Unresolved)
        }
    }

    fun remapProgress(
        value: String,
        map: (String) -> String?,
        resolveMarker: ((TrackSignature) -> MarkerRestoreResolution)? = null,
    ): String {
        val original = decodeProgress(value)
        val mapped = original.map {
            val track = remapIdentity(it.track, map, resolveMarker)
            it.copy(track = track, positionMs = boundedPosition(it.positionMs, track))
        }
        return encodeProgress(preserveAmbiguousProgress(original, mapped))
    }

    fun remapBookmarks(
        value: String,
        map: (String) -> String?,
        resolveMarker: ((TrackSignature) -> MarkerRestoreResolution)? = null,
    ): String = encodeBookmarks(decodeBookmarks(value).map {
        val track = remapIdentity(it.track, map, resolveMarker)
        it.copy(track = track, positionMs = boundedPosition(it.positionMs, track))
    })

    // A validated match permits up to 1 s of duration variance between media scanners. A point
    // at the old endpoint must not make the entire backup invalid on a slightly shorter target.
    private fun boundedPosition(positionMs: Long, track: TrackSignature): Long =
        if (track.durationMs > 0) positionMs.coerceAtMost(track.durationMs) else positionMs
}

class TrackMarkers(private val store: KeyValueStore, private val clock: () -> Long = { 0L }) {
    private fun read(store: KeyValueStore = this.store): TrackMarkersState = TrackMarkersState(
        progress = runCatching { TrackMarkersCodec.decodeProgress(store.getString(TrackMarkersCodec.PROGRESS_KEY)) }.getOrDefault(emptyList()),
        bookmarks = runCatching { TrackMarkersCodec.decodeBookmarks(store.getString(TrackMarkersCodec.BOOKMARKS_KEY)) }.getOrDefault(emptyList()),
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<TrackMarkersState> = _state.asStateFlow()
    private var stableIds = false
    private var byId: Map<String, List<Track>> = emptyMap()
    private var byPath: Map<String, List<Track>> = emptyMap()
    private var byUriId: Map<Pair<String, String>, List<Track>> = emptyMap()
    private val _libraryRevision = MutableStateFlow(0L)
    /** Availability can change without editing any bookmark or moving the player. */
    val libraryRevision: StateFlow<Long> = _libraryRevision.asStateFlow()
    /** Backup replacement owns these keys until repository reload has finished. */
    var writesSuspended: Boolean = false

    fun reload() = reloadPreparation()(store).invoke()

    /**
     * Capture indexes on the owner thread. The returned preparation decodes/migrates only the
     * supplied staging store on a worker; its final publication is a single owner-thread assignment.
     * Availability remains the current live index, including a permission revocation meanwhile.
     */
    fun reloadPreparation(): (KeyValueStore) -> (() -> Unit) {
        val stable = stableIds
        val index = byId
        return { source ->
            val loaded = read(source)
            val next = if (stable) migrateLegacy(loaded, index, source) else loaded
            val publish: () -> Unit = { _state.value = next }
            publish
        }
    }

    /**
     * Validate/persist old bindings before a new displayed scan is published, without opening
     * its file addresses yet. Availability stays invalid until the caller finishes indexing
     * and invokes [setLibrary]. A write failure leaves both state and displayed scan unchanged.
     */
    fun reconcileStoredIdentities(tracks: List<Track>, stableIds: Boolean) {
        if (!stableIds || (state.value.progress.none { it.track.binding == MarkerBinding.Legacy } &&
            state.value.bookmarks.none { it.track.binding == MarkerBinding.Legacy })) return
        _state.value = migrateLegacy(state.value, tracks.groupBy { it.id })
    }

    /** [stableIds] is only for scans already validated by the persistent identity registry. */
    fun setLibrary(tracks: List<Track>, stableIds: Boolean = false) {
        // Build once per MediaStore refresh. Bookmark lists never scan the full library per row.
        val nextById = if (stableIds) tracks.groupBy { it.id } else emptyMap()
        val nextByPath = if (stableIds) emptyMap() else tracks.mapNotNull { track ->
            track.path?.let { it to track }
        }.groupBy({ it.first }, { it.second })
        val nextByUriId = if (stableIds) emptyMap() else tracks.groupBy { it.id to it.uri }
        val nextState = if (stableIds) migrateLegacy(state.value, nextById) else state.value
        // A failed transaction leaves both the published library and marker state unchanged.
        this.stableIds = stableIds
        byId = nextById
        byPath = nextByPath
        byUriId = nextByUriId
        _state.value = nextState
        _libraryRevision.value++
    }

    private fun migrateLegacy(
        previous: TrackMarkersState,
        library: Map<String, List<Track>>,
        store: KeyValueStore = this.store,
    ): TrackMarkersState {
        if (previous.progress.none { it.track.binding == MarkerBinding.Legacy } &&
            previous.bookmarks.none { it.track.binding == MarkerBinding.Legacy }) return previous
        fun migrate(signature: TrackSignature): TrackSignature {
            if (signature.binding != MarkerBinding.Legacy) return signature
            val candidates = library[signature.id] ?: return signature // Missing can return later.
            val candidate = candidates.singleOrNull()
            val binding = if (candidate != null &&
                TrackMarkersCodec.matchesLegacyIdentity(signature, TrackSignature.of(candidate))) {
                MarkerBinding.Canonical
            } else MarkerBinding.Unresolved
            return signature.copy(binding = binding)
        }
        val next = TrackMarkersState(
            preserveAmbiguousProgress(previous.progress, previous.progress.map { it.copy(track = migrate(it.track)) }),
            previous.bookmarks.map { it.copy(track = migrate(it.track)) },
        )
        if (next == previous) return previous
        val changed = buildMap {
            if (next.progress != previous.progress) put(TrackMarkersCodec.PROGRESS_KEY,
                TrackMarkersCodec.encodeProgress(next.progress))
            if (next.bookmarks != previous.bookmarks) put(TrackMarkersCodec.BOOKMARKS_KEY,
                TrackMarkersCodec.encodeBookmarks(next.bookmarks))
        }
        // Both collections are committed together, with no state publication on failure.
        // The caller serializes repository access; no background mutation races with playback.
        store.replace(emptySet(), changed)
        return next
    }

    /** Identity must be unique in the unfiltered library. Similar tags alone never match. */
    fun resolve(signature: TrackSignature): Track? {
        if (signature.binding == MarkerBinding.Unresolved) return null
        if (stableIds) return byId[signature.id]?.singleOrNull()?.takeIf { candidate ->
            signature.binding == MarkerBinding.Canonical ||
                TrackMarkersCodec.matchesLegacyIdentity(signature, TrackSignature.of(candidate))
        }
        val candidates = if (signature.path != null) byPath[signature.path] else byUriId[signature.id to signature.uri]
        return candidates?.singleOrNull()?.takeIf { candidate ->
            candidate.title == signature.title && candidate.artist == signature.artist &&
                candidate.album == signature.album &&
                abs(candidate.durationMs.coerceAtLeast(0) - signature.durationMs) <= 1_000
        }
    }

    /** A Track selected inside this app is distinct from an untrusted stored legacy signature. */
    fun resolveSelection(track: Track): Track? =
        if (stableIds) byId[track.id]?.singleOrNull() else resolve(TrackSignature.of(track))

    private fun signatureFor(track: Track): TrackSignature = TrackSignature.of(track).let { signature ->
        if (stableIds && byId[track.id]?.singleOrNull() != null) {
            signature.copy(binding = MarkerBinding.Canonical)
        } else signature
    }

    private fun sameObservedIdentity(signature: TrackSignature, observed: TrackSignature): Boolean =
        signature.id == observed.id && signature.uri == observed.uri &&
            signature.path == observed.path && signature.title == observed.title &&
            signature.artist == observed.artist && signature.album == observed.album &&
            signature.durationMs == observed.durationMs

    private fun belongsTo(signature: TrackSignature, track: Track, observed: TrackSignature): Boolean {
        if (signature.binding == MarkerBinding.Unresolved) return false
        // A loaded decoder can still checkpoint its exact file after access is revoked. Keep
        // that identity match without allowing an unresolved signature to bypass the resolver.
        return sameObservedIdentity(signature, observed) ||
            (if (stableIds) resolve(signature)?.id == track.id else resolve(signature) == track)
    }

    fun bookmarksFor(track: Track): List<TrackBookmark> {
        val observed = TrackSignature.of(track)
        return state.value.bookmarks.filter { belongsTo(it.track, track, observed) }
    }

    /** Display also retains unavailable records; actions must still call [resolve] on the marker. */
    fun bookmarksForDisplay(track: Track): List<TrackBookmark> {
        val observed = TrackSignature.of(track)
        val selection = resolveSelection(track)
        return state.value.bookmarks.filter { marker ->
            (stableIds && marker.track.binding == MarkerBinding.Canonical && marker.track.id == track.id) ||
                sameObservedIdentity(marker.track, observed) ||
                (selection != null && resolve(marker.track) == selection)
        }
    }

    fun progressFor(track: Track, minimumDurationMs: Long): Long? {
        if (track.durationMs < minimumDurationMs || track.durationMs <= 0 || resolve(signatureFor(track)) != track) return null
        val observed = TrackSignature.of(track)
        val entries = state.value.progress.filter { belongsTo(it.track, track, observed) }
        if (entries.size != 1) return null
        return entries.single().positionMs.takeIf { it >= MIN_POSITION_MS && it < track.durationMs - END_MARGIN_MS }
    }

    /** Call only on pauses, seeks, explicit exits, and spaced position milestones. */
    fun checkpoint(track: Track, positionMs: Long, thresholdMs: Long, force: Boolean = false) {
        if (writesSuspended || track.durationMs < thresholdMs || track.durationMs <= 0) return
        val observed = TrackSignature.of(track)
        val existing = state.value.progress.filter { belongsTo(it.track, track, observed) }
        val signature = if (stableIds && existing.any { it.track.binding == MarkerBinding.Canonical }) {
            observed.copy(binding = MarkerBinding.Canonical)
        } else signatureFor(track)
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
        val matches = state.value.progress.filter { belongsTo(it.track, track, signature) }
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
        val marker = TrackBookmark(id, signatureFor(track), positionMs, normalizedTitle)
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
