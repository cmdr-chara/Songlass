package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.lyrics.ImportedLyricsCodec
import dev.cmdrchara.songlass.core.library.LibraryFacetKind
import dev.cmdrchara.songlass.core.playback.MarkerBinding
import dev.cmdrchara.songlass.core.playback.MarkerRestoreResolution
import dev.cmdrchara.songlass.core.search.SearchHistory
import dev.cmdrchara.songlass.core.playback.TrackMarkersCodec
import dev.cmdrchara.songlass.core.playback.TrackSignature
import dev.cmdrchara.songlass.core.search.SearchTarget

/** How the value stored under a managed key is read, and whether it refers to tracks. */
internal enum class ValueKind {
    /** Opaque text with no track ids (settings, names, equalizer curves, Home layout...). */
    Plain,

    /** Track ids joined by the unit separator, as `putStringList` writes them. */
    IdList,

    /** `id RS count RS lastPlayedMs` records. */
    PlayCounts,

    /** `id RS atMs` records. */
    DatedPlays,

    /** Length-prefixed [SearchTarget]s, as `SearchHistory` writes them. */
    SearchTargets,

    /** URI-only historical documents, retained without an inferred file binding. */
    ImportedLyrics,
    /** Versioned identity records; malformed values are retained inertly for recovery/export. */
    SavedLyrics,
    TrackProgress,
    TrackBookmarks,
}

/**
 * The explicit allowlist of store keys a backup carries. Everything else (the playback queue,
 * AutoEQ download cache, anything a platform or a later feature stores) is neither exported nor
 * touched by a restore. Unknown keys inside a managed namespace are never guessed at: only the
 * shapes below count.
 */
internal object BackupKeys {
    const val LYRICS_PREFIX = ImportedLyricsCodec.LEGACY_PREFIX
    const val FAVORITES = "favorites"
    const val PINS = "library.pins"
    const val PLAYLISTS_INDEX = "playlists.index"
    const val SMART_INDEX = "smart.index"
    const val HISTORY_COUNTS = "history.counts"
    const val HISTORY_DATED = "history.dated_plays"
    const val SEARCH = "search.opened.v1"
    const val EQ_PROFILES = "equalizer.profiles"
    const val EQ_OUTPUTS_INDEX = "equalizer.outputs.index"

    private val exact: Map<String, ValueKind> = mapOf(
        FAVORITES to ValueKind.IdList,
        "history.recent" to ValueKind.IdList,
        HISTORY_COUNTS to ValueKind.PlayCounts,
        HISTORY_DATED to ValueKind.DatedPlays,
        SEARCH to ValueKind.SearchTargets,
        TrackMarkersCodec.PROGRESS_KEY to ValueKind.TrackProgress,
        TrackMarkersCodec.BOOKMARKS_KEY to ValueKind.TrackBookmarks,
        "history.contexts" to ValueKind.Plain,
        "history.tracking_started_ms" to ValueKind.Plain,
        PLAYLISTS_INDEX to ValueKind.Plain,
        SMART_INDEX to ValueKind.Plain,
        "equalizer.enabled" to ValueKind.Plain,
        "equalizer.gains" to ValueKind.Plain,
        "equalizer.bands5" to ValueKind.Plain,
        "equalizer.bands15" to ValueKind.Plain,
        "equalizer.bands31" to ValueKind.Plain,
        "equalizer.profile" to ValueKind.Plain,
        EQ_PROFILES to ValueKind.Plain,
        "equalizer.outputs.enabled" to ValueKind.Plain,
        EQ_OUTPUTS_INDEX to ValueKind.Plain,
    )

    /** Whole namespaces of user preferences (library filters and pins, settings, Home layout). */
    private val plainPrefixes = listOf("settings.", "library.", "home.")

    /** Keys of the form `<prefix><id>.<suffix>`, kept only while `<id>` is listed in [indexKey]. */
    private class Group(val prefix: String, val indexKey: String, val suffixes: Set<String>, val idListSuffix: String? = null) {
        /** The (id, suffix) of a per-item key, or null. Ids may contain dots; suffixes do not. */
        fun match(key: String): Pair<String, String>? {
            if (!key.startsWith(prefix)) return null
            val rest = key.substring(prefix.length)
            val dot = rest.lastIndexOf('.')
            if (dot <= 0) return null
            val suffix = rest.substring(dot + 1)
            return if (suffix in suffixes) rest.substring(0, dot) to suffix else null
        }
    }

    private val groups = listOf(
        Group("playlists.", PLAYLISTS_INDEX, setOf("name", "created", "tracks"), idListSuffix = "tracks"),
        Group("smart.", SMART_INDEX, setOf("name", "rules")),
        Group("equalizer.profiles.", EQ_PROFILES, setOf("name", "filters")),
        Group("equalizer.outputs.", EQ_OUTPUTS_INDEX, setOf("name", "enabled", "gains", "profile", "bands5", "bands15", "bands31")),
    )

    /** What a managed key holds; null when the key is not part of a backup. */
    fun kindOf(key: String): ValueKind? {
        exact[key]?.let { return it }
        if (plainPrefixes.any { key.length > it.length && key.startsWith(it) }) return ValueKind.Plain
        if (key.length > LYRICS_PREFIX.length && key.startsWith(LYRICS_PREFIX)) return ValueKind.ImportedLyrics
        if (ImportedLyricsCodec.isKey(key)) return ValueKind.SavedLyrics
        for (group in groups) {
            val (_, suffix) = group.match(key) ?: continue
            return if (suffix == group.idListSuffix) ValueKind.IdList else ValueKind.Plain
        }
        return null
    }

    fun isManaged(key: String): Boolean = kindOf(key) != null

    fun lyricsUri(key: String): String = key.removePrefix(LYRICS_PREFIX)

    /**
     * What a backup of [snapshot] holds: managed keys only, per-item keys only for items still
     * listed in their index (deleting a playlist leaves stale keys behind), values in canonical
     * form, sorted by key so equal state gives equal files.
     */
    fun exportable(snapshot: Map<String, String>): Map<String, String> {
        val listed = groups.associate { group ->
            group.prefix to snapshot[group.indexKey].orEmpty().split(UNIT).filter { it.isNotEmpty() }.toSet()
        }
        val result = LinkedHashMap<String, String>()
        for (key in snapshot.keys.sorted()) {
            val value = snapshot.getValue(key)
            val kind = kindOf(key) ?: continue
            if (key.length > BackupFormat.MAX_KEY_CHARS) continue
            val owner = groups.firstNotNullOfOrNull { group -> group.match(key)?.let { (id, _) -> group.prefix to id } }
            if (owner != null && owner.second !in listed.getValue(owner.first)) continue
            result[key] = ValueCodecs.canonical(kind, value)
        }
        return result
    }

    private const val UNIT = '\u001F'
}

/**
 * Reading, normalising and rewriting values that name tracks. Layouts mirror what the
 * repositories write (`Favorites`, `ListeningHistory`, `UserPlaylists`, `SearchHistory`); the
 * round-trip tests run the real repositories so drift shows up there.
 *
 * Every parser is as forgiving as the repository that loads the value, so [canonical] gives
 * what the app itself would have read back. A value from a backup file is accepted only when
 * it already is canonical.
 */
internal object ValueCodecs {
    private const val UNIT = '\u001F'
    private const val RECORD = '\u001E'

    private class Rec(val id: String, val tail: String)

    fun canonical(kind: ValueKind, value: String): String = when (kind) {
        ValueKind.Plain, ValueKind.ImportedLyrics, ValueKind.SavedLyrics -> value
        ValueKind.TrackProgress -> TrackMarkersCodec.encodeProgress(TrackMarkersCodec.decodeProgress(value))
        ValueKind.TrackBookmarks -> TrackMarkersCodec.encodeBookmarks(TrackMarkersCodec.decodeBookmarks(value))
        ValueKind.SearchTargets -> encodeSearch(decodeSearch(value).distinct().take(SearchHistory.MAX))
        else -> render(records(kind, value))
    }

    fun isCanonical(kind: ValueKind, value: String): Boolean =
        runCatching { canonical(kind, value) == value }.getOrDefault(false)

    fun searchTargetCount(value: String): Int = decodeSearch(value).size

    /** Track ids named by a canonical [value], in order, repeats included. */
    fun references(kind: ValueKind, value: String): List<String> = when (kind) {
        ValueKind.Plain, ValueKind.ImportedLyrics, ValueKind.SavedLyrics -> emptyList()
        ValueKind.TrackProgress -> TrackMarkersCodec.decodeProgress(value)
            .filter { it.track.binding != MarkerBinding.Unresolved }.map { it.track.id }
        ValueKind.TrackBookmarks -> TrackMarkersCodec.decodeBookmarks(value)
            .filter { it.track.binding != MarkerBinding.Unresolved }.map { it.track.id }
        ValueKind.SearchTargets -> decodeSearch(value).filterIsInstance<SearchTarget.Song>().map { it.id }
        else -> records(kind, value).map { it.id }
    }

    /**
     * Replaces list/history ids by [resolve], reporting removed references to [onDrop]. Markers
     * instead retain conflicts inertly and use [resolveMarker] to validate cross-device trust.
     */
    fun remap(
        kind: ValueKind,
        value: String,
        resolve: (String) -> String?,
        resolveMarker: ((TrackSignature) -> MarkerRestoreResolution)? = null,
        onDrop: () -> Unit,
    ): String = when (kind) {
        ValueKind.Plain, ValueKind.ImportedLyrics, ValueKind.SavedLyrics -> value
        ValueKind.TrackProgress -> TrackMarkersCodec.remapProgress(value, resolve, resolveMarker)
        ValueKind.TrackBookmarks -> TrackMarkersCodec.remapBookmarks(value, resolve, resolveMarker)
        ValueKind.SearchTargets -> encodeSearch(
            decodeSearch(value).mapNotNull { target ->
                if (target !is SearchTarget.Song) return@mapNotNull target
                resolve(target.id)?.let { SearchTarget.Song(it) } ?: run { onDrop(); null }
            }.distinct(),
        )
        else -> render(
            records(kind, value).mapNotNull { rec ->
                resolve(rec.id)?.let { Rec(it, rec.tail) } ?: run { onDrop(); null }
            },
        )
    }

    private fun render(records: List<Rec>): String = records.joinToString(UNIT.toString()) { it.id + it.tail }

    private fun records(kind: ValueKind, value: String): List<Rec> {
        val items = value.split(UNIT)
        return when (kind) {
            ValueKind.IdList -> items.filter { it.isNotEmpty() }.map { Rec(it, "") }
            ValueKind.PlayCounts -> items.mapNotNull(::playCount).distinctBy { it.id }
            ValueKind.DatedPlays -> items.mapNotNull(::datedPlay)
            else -> error("Not a record list: $kind")
        }
    }

    private fun playCount(entry: String): Rec? {
        val parts = entry.split(RECORD)
        val id = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return null
        val count = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val last = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        return Rec(id, "$RECORD$count$RECORD$last")
    }

    private fun datedPlay(entry: String): Rec? {
        val parts = entry.split(RECORD)
        if (parts.size != 2 || parts[0].isEmpty()) return null
        val at = parts[1].toLongOrNull() ?: return null
        return Rec(parts[0], "$RECORD$at")
    }

    // Same grammar as SearchHistory: length-prefixed fields, `-1:` for a missing tag.

    private fun encodeSearch(targets: List<SearchTarget>): String = targets.joinToString("") { target ->
        val fields: List<String?> = when (target) {
            is SearchTarget.Song -> listOf("s", target.id)
            is SearchTarget.AlbumId -> listOf("a", target.title, target.artist)
            is SearchTarget.ArtistName -> listOf("r", target.name)
            is SearchTarget.FolderId -> listOf("f", target.id)
            is SearchTarget.Facet -> listOf("m", target.context.kind.storageKey, target.context.value)
        }
        fields.joinToString("") { if (it == null) "-1:" else "${it.length}:$it" }
    }

    /** The valid prefix of [value], like SearchHistory keeps after interrupted writes. */
    private fun decodeSearch(value: String): List<SearchTarget> {
        var offset = 0
        fun field(): String? {
            val colon = value.indexOf(':', offset)
            require(colon >= offset)
            val size = value.substring(offset, colon).toInt()
            offset = colon + 1
            require(size >= -1 && size <= value.length - offset)
            if (size == -1) return null
            return value.substring(offset, offset + size).also { offset += size }
        }
        return buildList {
            while (offset < value.length && size < SearchHistory.MAX) {
                val target = runCatching {
                    when (field()) {
                        "s" -> SearchTarget.Song(requireNotNull(field()))
                        "a" -> SearchTarget.AlbumId(field(), field())
                        "r" -> SearchTarget.ArtistName(field())
                        "f" -> SearchTarget.FolderId(requireNotNull(field()))
                        "m" -> {
                            val kind = requireNotNull(LibraryFacetKind.fromStorage(field()))
                            val value = requireNotNull(field()).also { require(it.isNotBlank()) }
                            SearchTarget.Facet(PlayContext.Facet(kind, value))
                        }
                        else -> error("Unknown search target")
                    }
                }.getOrNull() ?: break
                add(target)
            }
        }
    }
}
