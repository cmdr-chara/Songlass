package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Artwork addresses bind a decode to the canonical file as well as its metadata revision. */
object ArtworkKeys {
    private const val FRAGMENT = "#songlass:"

    fun forTrack(track: Track): String {
        // IDs are opaque and may contain URI punctuation. Hex encoding is unambiguous on every
        // platform; only the cache fragment changes, never Track.uri or the player's data source.
        val bytes = track.id.encodeToByteArray()
        val identity = buildString(bytes.size * 2) {
            val digits = "0123456789abcdef"
            for (byte in bytes) {
                val value = byte.toInt() and 0xff
                append(digits[value ushr 4]).append(digits[value and 15])
            }
        }
        return "${track.uri.substringBefore('#')}$FRAGMENT${track.artworkRevision}:$identity"
    }

    fun isTrackKey(key: String): Boolean = key.substringAfter('#', "").startsWith("songlass:")
}

/**
 * Current authorization for opening library artwork. The last displayed library is not an
 * authorization: a failed scan may retain its titles while a platform URI now names another file.
 *
 * Build [Index] away from rendering/Main, then [publish] only after a complete, successful scan.
 * Invalidate at scan start, access loss and source-identity failures. Mutations share the graph's
 * serial context; immutable snapshots and StateFlow make permits safe to inspect from IO workers.
 */
class ArtworkAvailability {
    class Index private constructor(
        internal val byKey: Map<String, Track>,
        internal val byId: Map<String, Track>,
    ) {
        companion object {
            fun build(tracks: List<Track>): Index {
                val idCounts = tracks.groupingBy { it.id }.eachCount()
                val allowedUris = tracks.groupBy { it.uri }.filterValues { group ->
                    val physical = group.filterNot { it.isCueTrack }.singleOrNull()
                    physical != null && group.all { candidate ->
                        candidate === physical || candidate.isCueTrack && candidate.cueSourceId == physical.id &&
                            candidate.path == physical.path && candidate.dateAddedMs == physical.dateAddedMs &&
                            candidate.artworkRevision == physical.artworkRevision && candidate.clipEndMs!! <= physical.durationMs
                    }
                }.keys
                val unique = tracks.filter {
                    it.id.isNotBlank() && it.uri.isNotBlank() && idCounts[it.id] == 1 && it.uri in allowedUris
                }
                return Index(unique.associateBy { it.artworkKey }, unique.associateBy { it.id })
            }
        }
    }

    internal class Snapshot(val revision: Long, val index: Index?)
    class Permit internal constructor(internal val snapshot: Snapshot, val track: Track) {
        val key: String = track.artworkKey
    }

    private val snapshot = MutableStateFlow(Snapshot(0, null))
    private val _revision = MutableStateFlow(0L)
    /** Changes also after recovery with identical track metadata, allowing failed images to retry. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun invalidate() = replace(null)
    fun publish(index: Index) = replace(index)

    private fun replace(index: Index?) {
        val next = Snapshot(snapshot.value.revision + 1, index)
        snapshot.value = next
        _revision.value = next.revision
    }

    fun permitForKey(key: String): Permit? {
        val current = snapshot.value
        return current.index?.byKey?.get(key)?.let { Permit(current, it) }
    }

    fun permitForId(id: String): Permit? {
        val current = snapshot.value
        return current.index?.byId?.get(id)?.let { Permit(current, it) }
    }

    fun isCurrent(permit: Permit): Boolean = snapshot.value === permit.snapshot

    /**
     * [canRead] checks the platform/graph's current readiness directly, not a delayed observer.
     * A started decode may finish after cancellation, permission loss or a new scan; its result
     * must not be cached or published unless the same permit is still current afterwards.
     */
    suspend fun <T> readIfCurrent(
        permit: Permit,
        canRead: () -> Boolean,
        read: suspend (Track) -> T,
    ): T? {
        if (!isCurrent(permit) || !canRead()) return null
        val result = read(permit.track)
        return result.takeIf { isCurrent(permit) && canRead() }
    }
}
