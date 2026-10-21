package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Gives scanned files application IDs before any library snapshot is published. A platform row ID
 * is only a location hint: it may change on reindexing or be reused for an unrelated file.
 *
 * Use one instance per store, from a background dispatcher, on complete, permission-checked scans.
 * The first scan retains legacy IDs; later unknown files always get a fresh opaque ID. Missing
 * identities are retained, so a replacement cannot silently inherit their playlists or history.
 * Pre-existing references to files absent from the first scan cannot be reconstructed.
 * A persisted random namespace also separates allocations from other devices' missing backup IDs.
 *
 * [KeyValueStore.replace] must be durable and transactional for this key. Android's native store
 * uses synchronous commit. An asynchronous adapter, including the current iOS UserDefaults store,
 * needs a durable implementation before this registry can provide crash-safe publication there.
 */
class TrackIdentityRegistry(private val store: KeyValueStore, private val random: Random = Random.Default) {
    private val mutex = Mutex()

    /**
     * Returns the input order and metadata, changing only [Track.id]. Nothing is returned when
     * decoding, matching, capacity validation or persistence fails; keep the last published library.
     * A missing row is never deleted from the registry, and an unchanged scan does not write.
     */
    suspend fun canonicalize(rawTracks: List<Track>): List<Track> = mutex.withLock {
        val observed = validateScan(rawTracks)
        val previous = store.getString(STORAGE_KEY)?.let(RegistryCodec::decode)
        val resolution = if (previous == null) {
            // Persist even an empty first scan: later files must not acquire a legacy ID whose
            // older, absent owner could still be referenced by the user's history or playlists.
            Resolution(RegistryState(newNamespace(random), 1, observed), observed.map { it.canonicalId })
        } else {
            resolve(previous, observed)
        }
        if (resolution.state != previous) {
            val encoded = RegistryCodec.encode(resolution.state)
            store.replace(emptySet(), mapOf(STORAGE_KEY to encoded))
        }
        rawTracks.mapIndexed { index, track ->
            val canonicalId = resolution.ids[index]
            if (canonicalId == track.id) track else track.copy(id = canonicalId)
        }
    }

    companion object {
        /** Intentionally outside every settings/library/Home/explicit-backup namespace. */
        const val STORAGE_KEY = "track_identity.registry.v1"

        /** Tombstones count toward these hard bounds; they are never evicted to make room. */
        const val MAX_IDENTITIES = 100_000
        const val MAX_ENCODED_CHARS = 16 * 1024 * 1024
        const val MAX_FIELD_CHARS = 4_096
    }
}

/** A scan must stay unpublished when its IDs cannot be established safely. */
class TrackIdentityRegistryException internal constructor(message: String) : IllegalStateException(message)

private data class SourceKey(val id: String, val uri: String)

private data class Identity(
    val canonicalId: String,
    val sourceId: String,
    val uri: String,
    val path: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val dateAddedMs: Long?,
) {
    val source: SourceKey get() = SourceKey(sourceId, uri)
}

private data class RegistryState(val namespace: String, val nextId: Long, val identities: List<Identity>)
private data class Resolution(val state: RegistryState, val ids: List<String>)

/** A backup may retain missing opaque IDs from another registry; local counters must not bind them. */
private fun newNamespace(random: Random): String = buildString(32) {
    val hex = "0123456789abcdef"
    for (byte in random.nextBytes(16)) {
        val value = byte.toInt() and 0xff
        append(hex[value ushr 4])
        append(hex[value and 0xf])
    }
}

private fun validateScan(tracks: List<Track>): List<Identity> {
    ensure(tracks.size <= TrackIdentityRegistry.MAX_IDENTITIES, "Too many scanned tracks")
    val ids = HashSet<String>(tracks.size)
    val uris = HashSet<String>(tracks.size)
    return tracks.map { track ->
        ensure(validId(track.id) && ids.add(track.id), "Invalid or duplicate scan ID")
        ensure(track.uri.isNotBlank() && uris.add(track.uri), "Invalid or duplicate scan URI")
        val identity = Identity(
            track.id, track.id, track.uri, track.path?.takeIf { it.isNotBlank() },
            track.title, track.artist, track.album, track.durationMs.coerceAtLeast(0),
            track.dateAddedMs?.takeIf { it > 0 },
        )
        validateFields(identity)
        identity
    }
}

private fun resolve(previous: RegistryState, observed: List<Identity>): Resolution {
    val bySource = previous.identities.groupBy { it.source }
    val byPath = uniquePaths(previous.identities)
    val observedPathCounts = HashMap<String, Int>()
    observed.forEach { identity -> identity.path?.let { observedPathCounts[it] = (observedPathCounts[it] ?: 0) + 1 } }
    val selected = arrayOfNulls<String>(observed.size)
    val claimed = HashSet<String>(observed.size)

    // Exact source locations have priority. Path and file hints still have to agree: source IDs
    // alone cannot establish identity. Multiple compatible historical owners are an unsafe scan.
    observed.forEachIndexed { index, identity ->
        var match: Identity? = null
        for (candidate in bySource[identity.source].orEmpty()) {
            if (!sameKnownLocation(candidate, identity)) continue
            ensure(match == null, "Ambiguous historical source identity")
            match = candidate
        }
        if (match != null) {
            ensure(claimed.add(match.canonicalId), "Duplicate canonical scan identity")
            selected[index] = match.canonicalId
        }
    }

    // Reindexing may change both row ID and URI. A unique exact path plus unchanged metadata and
    // a known matching duration is the fallback; never pick the first member of a duplicate path.
    observed.forEachIndexed { index, identity ->
        if (selected[index] != null) return@forEachIndexed
        val path = identity.path ?: return@forEachIndexed
        if (observedPathCounts[path] != 1) return@forEachIndexed
        val candidate = byPath[path] ?: return@forEachIndexed
        if (candidate.source == identity.source || candidate.canonicalId in claimed) return@forEachIndexed
        if (!sameMetadata(candidate, identity) || candidate.durationMs <= 0 || identity.durationMs <= 0) return@forEachIndexed
        if (!compatibleDuration(candidate.durationMs, identity.durationMs)) return@forEachIndexed
        claimed += candidate.canonicalId
        selected[index] = candidate.canonicalId
    }

    val reserved = previous.identities.mapTo(HashSet()) { it.canonicalId }
    // A generated ID cannot collide with a raw ID in the current scan, either.
    observed.mapTo(reserved) { it.sourceId }
    var nextId = previous.nextId
    val next = previous.identities.toMutableList()
    val positions = previous.identities.withIndex().associate { it.value.canonicalId to it.index }
    val mapped = observed.mapIndexed { index, identity ->
        val canonicalId = selected[index] ?: run {
            ensure(next.size < TrackIdentityRegistry.MAX_IDENTITIES, "Track identity registry is full")
            var fresh: String
            do {
                ensure(nextId < Long.MAX_VALUE, "Track identity sequence is exhausted")
                fresh = "songlass-track-${previous.namespace}-${nextId++}"
            } while (!reserved.add(fresh))
            fresh
        }
        val updated = identity.copy(canonicalId = canonicalId)
        val position = positions[canonicalId]
        if (position == null) next += updated else next[position] = updated
        canonicalId
    }
    return Resolution(RegistryState(previous.namespace, nextId, next), mapped)
}

/** Null values represent ambiguous paths; tombstones participate in that ambiguity. */
private fun uniquePaths(identities: List<Identity>): Map<String, Identity?> {
    val result = HashMap<String, Identity?>()
    for (identity in identities) {
        val path = identity.path ?: continue
        result[path] = if (result.containsKey(path)) null else identity
    }
    return result
}

private fun sameKnownLocation(previous: Identity, current: Identity): Boolean {
    if (previous.path != current.path) return false
    if (previous.dateAddedMs != null && current.dateAddedMs != null && previous.dateAddedMs != current.dateAddedMs) return false
    if (!compatibleDuration(previous.durationMs, current.durationMs)) return false
    // Actual tag edits may alter all display metadata. A known path and unchanged arrival time
    // permit them; without that extra evidence only unchanged metadata is accepted.
    val stableArrival = previous.dateAddedMs != null && previous.dateAddedMs == current.dateAddedMs
    return sameMetadata(previous, current) || (previous.path != null && stableArrival)
}

private fun sameMetadata(first: Identity, second: Identity): Boolean =
    first.title == second.title && first.artist == second.artist && first.album == second.album

private fun compatibleDuration(first: Long, second: Long): Boolean =
    first <= 0 || second <= 0 || (if (first >= second) first - second else second - first) <= 1_000

private fun validId(id: String): Boolean =
    id.isNotBlank() && '\u001e' !in id && '\u001f' !in id

private fun validateFields(identity: Identity) {
    ensure(validId(identity.canonicalId) && validId(identity.sourceId), "Invalid track identity ID")
    ensure(identity.uri.isNotBlank(), "Invalid track identity URI")
    for (field in listOf(identity.canonicalId, identity.sourceId, identity.uri, identity.path, identity.title, identity.artist, identity.album)) {
        ensure(field == null || field.length <= TrackIdentityRegistry.MAX_FIELD_CHARS, "Track identity field is too long")
    }
}

/** Strict, bounded, length-prefixed fields; no delimiter escaping or permissive partial recovery. */
private object RegistryCodec {
    private const val HEADER = "v1|"

    fun encode(state: RegistryState): String = buildString {
        append(HEADER)
        fun field(value: String?) {
            val addition = if (value == null) 3 else value.length.toString().length + 1 + value.length
            ensure(length <= TrackIdentityRegistry.MAX_ENCODED_CHARS - addition, "Track identity registry is too large")
            if (value == null) append("-1:") else { append(value.length); append(':'); append(value) }
        }
        field(state.namespace)
        field(state.nextId.toString())
        field(state.identities.size.toString())
        for (identity in state.identities) {
            field(identity.canonicalId)
            field(identity.sourceId)
            field(identity.uri)
            field(identity.path)
            field(identity.title)
            field(identity.artist)
            field(identity.album)
            field(identity.durationMs.toString())
            field(identity.dateAddedMs?.toString())
        }
    }

    fun decode(encoded: String): RegistryState {
        ensure(encoded.length <= TrackIdentityRegistry.MAX_ENCODED_CHARS && encoded.startsWith(HEADER), "Invalid track identity registry version or size")
        val reader = Reader(encoded, HEADER.length)
        val namespace = reader.required()
        ensure(namespace.length == 32 && namespace.all { it in '0'..'9' || it in 'a'..'f' }, "Invalid track identity namespace")
        val nextId = number(reader.field())
        ensure(nextId > 0, "Invalid track identity sequence")
        val count = number(reader.field())
        ensure(count in 0..TrackIdentityRegistry.MAX_IDENTITIES.toLong(), "Invalid track identity count")
        val ids = HashSet<String>(count.toInt())
        val identities = List(count.toInt()) {
            val identity = Identity(
                reader.required(), reader.required(), reader.required(), reader.field(),
                reader.required(), reader.field(), reader.field(), number(reader.field()),
                reader.field()?.let(::number),
            )
            validateFields(identity)
            ensure(ids.add(identity.canonicalId), "Duplicate canonical registry identity")
            ensure(identity.path == null || identity.path.isNotBlank(), "Invalid track identity path")
            ensure(identity.durationMs >= 0 && (identity.dateAddedMs == null || identity.dateAddedMs > 0), "Invalid track identity time")
            identity
        }
        ensure(reader.finished(), "Trailing track identity registry data")
        return RegistryState(namespace, nextId, identities)
    }

    private fun number(value: String?): Long {
        val result = value?.toLongOrNull()
        ensure(result != null && result.toString() == value, "Invalid track identity number")
        return result!!
    }

    private class Reader(private val value: String, private var offset: Int) {
        fun required(): String = field() ?: throw TrackIdentityRegistryException("Missing track identity field")

        fun field(): String? {
            val separator = value.indexOf(':', offset)
            ensure(separator >= offset && separator - offset <= 10, "Invalid track identity field length")
            val sizeText = value.substring(offset, separator)
            val size = sizeText.toIntOrNull()
            ensure(size != null && size.toString() == sizeText && size in -1..TrackIdentityRegistry.MAX_FIELD_CHARS, "Invalid track identity field length")
            offset = separator + 1
            if (size == -1) return null
            ensure(size!! <= value.length - offset, "Truncated track identity field")
            return value.substring(offset, offset + size).also { offset += size }
        }

        fun finished(): Boolean = offset == value.length
    }
}

private fun ensure(condition: Boolean, message: String) {
    if (!condition) throw TrackIdentityRegistryException(message)
}
