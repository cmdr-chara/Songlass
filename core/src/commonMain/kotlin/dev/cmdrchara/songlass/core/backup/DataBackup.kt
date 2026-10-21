package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.lyrics.ImportedLyricsCodec
import dev.cmdrchara.songlass.core.lyrics.ImportedLyricsRecord
import dev.cmdrchara.songlass.core.lyrics.LyricsBinding
import dev.cmdrchara.songlass.core.lyrics.LyricsSelection
import dev.cmdrchara.songlass.core.lyrics.LyricsParser
import dev.cmdrchara.songlass.core.lyrics.LyricsOrigin
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.MarkerBinding
import dev.cmdrchara.songlass.core.playback.MarkerRestoreResolution
import dev.cmdrchara.songlass.core.playback.TrackMarkersCodec
import dev.cmdrchara.songlass.core.playback.TrackSignature
import dev.cmdrchara.songlass.core.storage.KeyValueStore

enum class BackupError {
    /** Not a Songlass backup at all. */
    NotABackup,

    /** A Songlass backup written by a newer (or unknown) format version. */
    UnsupportedVersion,

    /** Bigger than [DataBackup.MAX_BYTES]. */
    TooLarge,

    /** The structure is damaged or incomplete (for example cut short). */
    Malformed,

    /** The contents do not match the checksum written with them. */
    ChecksumMismatch,

    /** The same key or track appears twice. */
    DuplicateEntry,

    /** Data follows the end of the backup. */
    TrailingContent,

    /** A key outside the backup allowlist, or a value that does not have the expected form. */
    InvalidEntry,

    /** Some stored text cannot be written as UTF-8 and read back unchanged. */
    Unrepresentable,

    /** The platform store cannot list its entries. */
    StoreUnsupported,

    /** Writing to the store failed; rollback was attempted by the platform store. */
    WriteFailed,
}

/** Thrown for every failure; the message is only the [error] name, never any user data. */
class BackupException(val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

/** The newly validated plan must be shown and explicitly confirmed before any store change. */
class BackupPreviewChangedException(val preview: BackupPreview) : Exception("Backup preview changed")

/** How much of each kind of data a backup holds. */
data class BackupCounts(
    val favorites: Int = 0,
    val pins: Int = 0,
    val userPlaylists: Int = 0,
    val smartPlaylists: Int = 0,
    /** Tracks with a play count. */
    val playedTracks: Int = 0,
    val datedPlays: Int = 0,
    val searchItems: Int = 0,
    val headphoneProfiles: Int = 0,
    val outputProfiles: Int = 0,
    val importedLyrics: Int = 0,
    /** Settings and library filter entries. */
    val settings: Int = 0,
    val homeEntries: Int = 0,
    val trackProgress: Int = 0,
    val bookmarks: Int = 0,
)

/** Result of [DataBackup.export]. `toString` leaves the text out so it cannot end up in logs. */
class BackupExport(
    val text: String,
    val counts: BackupCounts,
    /** Imported lyrics left out because the backup would have been over the size limit. */
    val omittedLyrics: Int,
    /**
     * Tracks the data mentions that were not in the library given to [DataBackup.export], so the
     * file has no identity for them. Normal for files deleted since; a high number with data you
     * expect to keep means the library was not loaded, and restoring on a device where those ids
     * now name files will drop those references rather than guess.
     */
    val unidentifiedTracks: Int,
) {
    override fun toString(): String =
        "BackupExport(${text.length} chars, omittedLyrics=$omittedLyrics, unidentifiedTracks=$unidentifiedTracks)"
}

/** How the tracks the backup mentions relate to the library on this device. */
data class BackupTrackSummary(
    /** Same id, same file: references stay. */
    val unchanged: Int,
    /** The file has a different id here; references move to it. */
    val remapped: Int,
    /** Not on this device: references are kept as they are, and match again if the file returns. */
    val missing: Int,
    /** The old id now names another file, or the match is ambiguous: references are dropped. */
    val conflicting: Int,
)

enum class UnresolvedKind { Missing, Conflict }

/** A track the backup mentions that could not be matched to a file here. */
data class UnresolvedTrack(
    val id: String,
    /** Identity recorded in the backup; all null when the backup had none for this id. */
    val title: String?,
    val artist: String?,
    val album: String?,
    val path: String?,
    val kind: UnresolvedKind,
)

/**
 * A validated backup, ready to be restored. Holds the rewritten entries; nothing has been
 * written yet. Build a new one if the library changed since.
 */
class BackupPreview internal constructor(
    val createdMs: Long,
    /** What will exist after the restore (references already resolved). */
    val counts: BackupCounts,
    val tracks: BackupTrackSummary,
    val unresolved: List<UnresolvedTrack>,
    /** Included in [counts], but retained without a safe file link for resuming playback. */
    val unavailableTrackProgress: Int,
    /** Included in [counts], but retained without a safe file link for bookmark playback. */
    val unavailableBookmarks: Int,
    /** Imported documents retained without a verified file link, including URI-only legacy text. */
    val unavailableImportedLyrics: Int,
    /** References removed from lists, history and search because their track is [UnresolvedKind.Conflict]. */
    val droppedReferences: Int,
    /** Tracks in the scanned library the backup was matched against; a complete scan may be empty. */
    val libraryTrackCount: Int,
    internal val entries: Map<String, String>,
    /** Parsed source identities bind confirmation to the reviewed backup, independent of ordering. */
    internal val sourceTracks: Map<String, SourceTrack>,
) {
    /** Unrelated library additions may change its size without changing this restore decision. */
    fun sameRestorePlanAs(other: BackupPreview): Boolean =
        createdMs == other.createdMs && sourceTracks == other.sourceTracks && entries == other.entries &&
            counts == other.counts && tracks == other.tracks && unresolved == other.unresolved &&
            unavailableTrackProgress == other.unavailableTrackProgress &&
            unavailableBookmarks == other.unavailableBookmarks &&
            unavailableImportedLyrics == other.unavailableImportedLyrics &&
            droppedReferences == other.droppedReferences

    override fun toString(): String = "BackupPreview(entries=${entries.size}, tracks=$tracks)"
}

data class BackupResult(val written: Int, val removed: Int)

/**
 * Manual backup of everything the app keeps in its [store] (favorites, pins, playlists, smart
 * playlists, history, settings, equalizer, Home layout, search history, imported lyrics), without
 * the music files. Which keys count is an explicit allowlist ([BackupKeys]); the queue, caches and
 * unknown keys are left alone.
 *
 * Restore is two steps so the user can confirm: [preview] reads and validates a file without
 * touching the store, [applyConfirmed] checks that revalidation has not changed the reviewed
 * decision before replacing managed keys through [KeyValueStore.replace].
 * Android uses one synchronous commit; rollback after a write failure is best-effort.
 * After [apply] the caller reloads each repository (`reload()`), which keeps the player and the
 * process as they are.
 *
 * Track ids are not stable (MediaStore can reassign them), so the file also carries a small
 * identity record for each track it mentions; see [TrackMatching] for how ids are kept, moved or
 * dropped on restore.
 */
class DataBackup(private val store: KeyValueStore, private val clock: () -> Long = { 0L }) {

    /**
     * The backup file text. [tracks] is the complete, unfiltered library: it provides the
     * identity record for each track the data mentions.
     */
    fun export(tracks: List<Track>): BackupExport {
        val snapshot = store.snapshot() ?: throw BackupException(BackupError.StoreUnsupported)
        val entries = prepareLyricsForExport(BackupKeys.exportable(snapshot), tracks)
        if (entries.size > BackupFormat.MAX_ENTRIES) throw BackupException(BackupError.TooLarge)
        try {
            val full = encode(entries, tracks)
            return BackupExport(full.text, countsOf(entries), omittedLyrics = 0, unidentifiedTracks = full.unidentified)
        } catch (tooLarge: BackupException) {
            if (tooLarge.error != BackupError.TooLarge) throw tooLarge
        }
        // Lyrics are the only bulky, re-importable part: leave them out rather than fail.
        val lyrics = entries.keys.filterTo(HashSet()) { isLyricsKind(BackupKeys.kindOf(it)) }
        if (lyrics.isEmpty()) throw BackupException(BackupError.TooLarge)
        val trimmed = entries - lyrics
        val encoded = encode(trimmed, tracks)
        return BackupExport(encoded.text, countsOf(trimmed), omittedLyrics = lyrics.size, unidentifiedTracks = encoded.unidentified)
    }

    /**
     * Reads [text] completely and checks it against the allowlist and the [tracks] of this device.
     * Writes nothing. Throws [BackupException] if the file is not a valid, complete backup.
     */
    fun preview(text: String, tracks: List<Track>): BackupPreview {
        val parsed = BackupFormat.read(text)
        val sourcesById = parsed.sources.associateBy { it.id }

        val referenced = LinkedHashSet<String>()
        for ((key, value) in parsed.entries) {
            val kind = BackupKeys.kindOf(key) ?: throw BackupException(BackupError.InvalidEntry)
            if (kind == ValueKind.SavedLyrics) {
                boundLyricsRecord(key, value)?.let { referenced += it.id }
            } else if (kind != ValueKind.ImportedLyrics) {
                if (!ValueCodecs.isCanonical(kind, value)) throw BackupException(BackupError.InvalidEntry)
                referenced += ValueCodecs.references(kind, value)
            }
        }

        val resolution = TrackMatching.resolve(referenced, sourcesById, tracks)
        val currentById = tracks.associateBy { it.id }
        fun idFor(id: String): String? = when (val r = resolution[id]) {
            is Resolution.Moved -> r.to.id
            Resolution.Conflict -> null
            else -> id
        }

        fun resolveMarker(signature: TrackSignature): MarkerRestoreResolution {
            // An unresolved marker is retained for the user, never reattached by a reused ID.
            if (signature.binding == MarkerBinding.Unresolved) return MarkerRestoreResolution.Conflict
            val source = sourcesById[signature.id] ?: return if (signature.id in currentById)
                MarkerRestoreResolution.Conflict else MarkerRestoreResolution.Missing
            // A legacy marker may already have outlived its original MediaStore row before
            // export. Its own facts must agree with the source record before trusting a remap.
            if (signature.binding == MarkerBinding.Legacy && !TrackMarkersCodec.matchesLegacyIdentity(
                    signature, TrackSignature(source.id, source.uri, source.path, source.title,
                        source.artist, source.album, source.durationMs),
                )) return MarkerRestoreResolution.Conflict
            return when (val matched = resolution[signature.id]) {
                Resolution.Same -> currentById[signature.id]?.let { MarkerRestoreResolution.Matched(it) }
                    ?: MarkerRestoreResolution.Missing
                is Resolution.Moved -> MarkerRestoreResolution.Matched(matched.to)
                Resolution.Missing -> MarkerRestoreResolution.Missing
                Resolution.Conflict -> MarkerRestoreResolution.Conflict
                null -> if (signature.id in currentById) MarkerRestoreResolution.Conflict else MarkerRestoreResolution.Missing
            }
        }

        var dropped = 0
        val restored = LinkedHashMap<String, String>()
        // Legacy SourceTrack rows may have been inferred from an already reused URI. They
        // cannot establish ownership of historical text, even if they match today's library.
        for ((key, value) in parsed.entries) {
            val kind = BackupKeys.kindOf(key) ?: throw BackupException(BackupError.InvalidEntry)
            if (kind != ValueKind.SavedLyrics) {
                restored[key] = ValueCodecs.remap(kind, value, ::idFor, resolveMarker = ::resolveMarker) { dropped++ }
            }
        }
        data class LyricsPlan(val oldKey: String, val raw: String, val record: ImportedLyricsRecord?, val targetKey: String?)
        val lyricsPlans = parsed.entries.mapNotNull { (key, value) ->
            if (BackupKeys.kindOf(key) != ValueKind.SavedLyrics) return@mapNotNull null
            val record = ImportedLyricsCodec.decode(value)
            val bound = boundLyricsRecord(key, value)
            val source = bound?.let { sourcesById[it.id] }
            val sourceTrack = source?.let { Track(id = it.id, uri = it.uri, title = it.title,
                artist = it.artist, album = it.album, durationMs = it.durationMs,
                folder = it.path?.substringBeforeLast('/', "")?.takeIf(String::isNotEmpty),
                fileName = it.path?.substringAfterLast('/')) }
            val trustworthy = bound != null && sourceTrack != null &&
                (bound.binding == LyricsBinding.Canonical || bound.matchesObserved(sourceTrack))
            val target = if (!trustworthy) null else when (val matched = resolution[bound.id]) {
                Resolution.Same -> currentById[bound.id]
                is Resolution.Moved -> matched.to
                else -> null
            }
            val targetKey = target?.let { ImportedLyricsCodec.trackKey(it.id) }
            val mapped = if (target != null && targetKey != null && record != null)
                ImportedLyricsRecord.bound(LyricsSelection(target, canonicalId = true), record.text)
            else record?.unassigned()
            LyricsPlan(key, value, mapped, targetKey)
        }
        val destinationCounts = lyricsPlans.mapNotNull { it.targetKey }.groupingBy { it }.eachCount()
        val safeDestinations = destinationCounts.filterValues { it == 1 }.keys
        val reserved = (parsed.entries.keys + safeDestinations).toMutableSet()
        var recoverySequence = 0
        fun recoveryKey(): String {
            while (true) {
                val key = ImportedLyricsCodec.PREFIX + "r." + (recoverySequence++).toString(16)
                if (reserved.add(key)) return key
            }
        }
        // Allocate all proven targets first. An inert record cannot be overwritten by another
        // record remapping into its old key; collisions keep every document separately.
        lyricsPlans.filter { it.targetKey in safeDestinations }.forEach { plan ->
            restored[plan.targetKey!!] = ImportedLyricsCodec.encode(plan.record!!)
        }
        lyricsPlans.filter { it.targetKey !in safeDestinations }.forEach { plan ->
            val key = if (plan.oldKey !in restored) plan.oldKey else recoveryKey()
            restored[key] = plan.record?.unassigned()?.let(ImportedLyricsCodec::encode) ?: plan.raw
        }

        val unresolved = resolution.mapNotNull { (id, r) ->
            val kind = when (r) {
                Resolution.Missing -> UnresolvedKind.Missing
                Resolution.Conflict -> UnresolvedKind.Conflict
                else -> return@mapNotNull null
            }
            val source = sourcesById[id]
            UnresolvedTrack(id, source?.title, source?.artist, source?.album, source?.path, kind)
        }
        val summary = BackupTrackSummary(
            unchanged = resolution.values.count { it == Resolution.Same },
            remapped = resolution.values.count { it is Resolution.Moved },
            missing = unresolved.count { it.kind == UnresolvedKind.Missing },
            conflicting = unresolved.count { it.kind == UnresolvedKind.Conflict },
        )
        return BackupPreview(
            createdMs = parsed.createdMs,
            counts = countsOf(restored),
            tracks = summary,
            unresolved = unresolved,
            // Marker-specific source conflicts and previously unresolved records do not alter
            // valid favorites/history references for the same id. Report the final bindings.
            unavailableTrackProgress = restored[TrackMarkersCodec.PROGRESS_KEY]?.let { value ->
                TrackMarkersCodec.decodeProgress(value).count { it.track.binding != MarkerBinding.Canonical }
            } ?: 0,
            unavailableBookmarks = restored[TrackMarkersCodec.BOOKMARKS_KEY]?.let { value ->
                TrackMarkersCodec.decodeBookmarks(value).count { it.track.binding != MarkerBinding.Canonical }
            } ?: 0,
            unavailableImportedLyrics = restored.count { (key, value) ->
                when (BackupKeys.kindOf(key)) {
                    ValueKind.ImportedLyrics -> true
                    ValueKind.SavedLyrics -> boundLyricsRecord(key, value) == null
                    else -> false
                }
            },
            droppedReferences = dropped,
            libraryTrackCount = tracks.size,
            entries = restored,
            sourceTracks = sourcesById,
        )
    }

    /**
     * Revalidation can change which references are moved or dropped. Never substitute that new
     * plan for the one the user reviewed. A mismatch throws before even reading the target store.
     * The caller keeps the library snapshot stable while validating and applying this decision.
     */
    fun applyConfirmed(reviewed: BackupPreview, current: BackupPreview): BackupResult {
        if (!reviewed.sameRestorePlanAs(current)) throw BackupPreviewChangedException(current)
        return apply(current)
    }

    /**
     * Replaces every managed key by the previewed entries (managed keys the backup lacks are
     * removed, unknown keys stay) in one atomic store operation. Throws
     * [BackupError.WriteFailed] with the store unchanged if it fails. May block on disk I/O.
     */
    fun apply(preview: BackupPreview): BackupResult {
        val current = store.snapshot() ?: throw BackupException(BackupError.StoreUnsupported)
        val stale = current.keys.filterTo(HashSet()) { BackupKeys.isManaged(it) && it !in preview.entries }
        try {
            store.replace(stale, preview.entries)
        } catch (failure: Exception) {
            throw BackupException(BackupError.WriteFailed, failure)
        }
        return BackupResult(written = preview.entries.size, removed = stale.size)
    }

    private fun isLyricsKind(kind: ValueKind?): Boolean =
        kind == ValueKind.ImportedLyrics || kind == ValueKind.SavedLyrics

    private fun boundLyricsRecord(key: String, value: String): ImportedLyricsRecord? =
        ImportedLyricsCodec.decode(value)?.takeIf {
            it.binding != LyricsBinding.Unassigned && ImportedLyricsCodec.trackKey(it.id) == key &&
                LyricsParser.parse(it.text, LyricsOrigin.File) != null
        }

    /** Never lend today's file identity to an old URI-only or mismatched observed document. */
    private fun prepareLyricsForExport(entries: Map<String, String>, tracks: List<Track>): Map<String, String> {
        val byId = tracks.groupBy { it.id }
        return entries.mapValues { (key, value) ->
            if (BackupKeys.kindOf(key) != ValueKind.SavedLyrics) return@mapValues value
            val record = ImportedLyricsCodec.decode(value) ?: return@mapValues value
            val track = byId[record.id]?.singleOrNull()
            val safe = track != null && boundLyricsRecord(key, value) != null &&
                record.matches(LyricsSelection(track, canonicalId = record.binding == LyricsBinding.Canonical))
            if (safe) value else ImportedLyricsCodec.encode(record.unassigned())
        }
    }

    private class Encoded(val text: String, val unidentified: Int)

    private fun encode(entries: Map<String, String>, tracks: List<Track>): Encoded {
        val byId = tracks.associateBy { it.id }
        val referenced = LinkedHashSet<String>()
        for ((key, value) in entries) {
            val kind = BackupKeys.kindOf(key) ?: continue
            if (kind == ValueKind.SavedLyrics) boundLyricsRecord(key, value)?.let { referenced += it.id }
            else referenced += ValueCodecs.references(kind, value)
        }
        val sources = referenced.mapNotNull { byId[it] }
            .filter { it.id.isNotEmpty() && it.uri.isNotEmpty() }
            .distinctBy { it.uri }
            .sortedBy { it.id }
            .map(::sourceTrackOf)
        val text = BackupFormat.write(clock().coerceAtLeast(0), entries, sources)
        if (text.length > MAX_BYTES) throw BackupException(BackupError.TooLarge)
        val bytes = text.encodeToByteArray()
        if (bytes.size > MAX_BYTES) throw BackupException(BackupError.TooLarge)
        // A lone surrogate would come back as U+FFFD from the file and fail its checksum.
        if (bytes.decodeToString() != text) throw BackupException(BackupError.Unrepresentable)
        return Encoded(text, unidentified = referenced.count { it !in byId })
    }

    private fun countsOf(entries: Map<String, String>): BackupCounts {
        fun items(key: String) = entries[key]?.split('\u001F')?.count { it.isNotEmpty() } ?: 0
        return BackupCounts(
            favorites = items(BackupKeys.FAVORITES),
            pins = items(BackupKeys.PINS),
            userPlaylists = items(BackupKeys.PLAYLISTS_INDEX),
            smartPlaylists = items(BackupKeys.SMART_INDEX),
            playedTracks = items(BackupKeys.HISTORY_COUNTS),
            datedPlays = items(BackupKeys.HISTORY_DATED),
            searchItems = entries[BackupKeys.SEARCH]?.let(ValueCodecs::searchTargetCount) ?: 0,
            headphoneProfiles = items(BackupKeys.EQ_PROFILES),
            outputProfiles = items(BackupKeys.EQ_OUTPUTS_INDEX),
            importedLyrics = entries.keys.count { isLyricsKind(BackupKeys.kindOf(it)) },
            settings = entries.keys.count { it.startsWith("settings.") || (it.startsWith("library.") && it != BackupKeys.PINS) },
            homeEntries = entries.keys.count { it.startsWith("home.") },
            trackProgress = entries[TrackMarkersCodec.PROGRESS_KEY]?.let { TrackMarkersCodec.decodeProgress(it).size } ?: 0,
            bookmarks = entries[TrackMarkersCodec.BOOKMARKS_KEY]?.let { TrackMarkersCodec.decodeBookmarks(it).size } ?: 0,
        )
    }

    companion object {
        /** Largest backup, in UTF-8 bytes, that is written or read. */
        const val MAX_BYTES = 16 * 1024 * 1024
    }
}
