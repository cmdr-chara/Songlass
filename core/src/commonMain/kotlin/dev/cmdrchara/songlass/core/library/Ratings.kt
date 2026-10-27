package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's local star ratings: whole stars from 1 to [MAX_STARS], no rating when absent.
 *
 * A rating belongs to one application track id, the opaque identity assigned by
 * [TrackIdentityRegistry]. The platform row id a file happens to have is only a location hint
 * and can be reused for unrelated music, so a rating is never keyed on it alone: a replacement
 * file receives a new application id and cannot inherit the previous file's rating, while the
 * original file keeps its rating when it comes back. Callers resolve ratings through the
 * current library snapshot, so a rating whose track is missing is retained but never shown.
 *
 * [KEY] sits in the backup's `library.` namespace: it is exported and restored with the other
 * user data. The value is a list of ids, not one of the backup's remapped track-id formats, so
 * a restore keeps it verbatim (see `docs/design/rating-e-cambio-categoria-2026-10-27.md`).
 */
class Ratings(
    private val store: KeyValueStore = InMemoryKeyValueStore(),
    private val mutations: RestoreMutationGate = RestoreMutationGate(),
) {
    private val _values = MutableStateFlow(read(store))

    /** Stored ratings by application track id, in the order they were first rated. */
    val values: StateFlow<Map<String, Int>> = _values.asStateFlow()

    /** The stored rating for [trackId], or 0 when it has none. */
    fun rating(trackId: String): Int = _values.value[trackId] ?: 0

    /**
     * Stores [stars] for [trackId]; 0 removes the rating. Returns the rating now stored, which
     * is the previous one while a backup restore owns these keys or when the request is invalid.
     */
    fun set(trackId: String, stars: Int): Int {
        val current = rating(trackId)
        if (stars !in 0..MAX_STARS || !validId(trackId) || !mutations.canMutate) return current
        val values = _values.value
        val updated = when {
            stars == 0 -> if (trackId in values) values - trackId else return 0
            values[trackId] == stars -> return stars
            trackId !in values && values.size >= MAX_RATINGS -> return current
            else -> values + (trackId to stars)
        }
        store.putString(KEY, encode(updated))
        _values.value = updated
        return stars
    }

    /** Re-reads the ratings from [store], after something else (a backup restore) replaced them. */
    fun reload() = prepareReload().invoke()

    /** Decode off the owner thread; invoke the returned publication only after commit. */
    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = read(store)
        return { _values.value = loaded }
    }

    companion object {
        const val MAX_STARS = 5

        /** Same bound as the identity registry: ratings cannot outgrow the tracks they describe. */
        const val MAX_RATINGS = TrackIdentityRegistry.MAX_IDENTITIES

        /** Inside the backup's `library.` namespace; see `BackupKeys`. */
        const val KEY = "library.ratings"
    }
}

private const val UNIT = '\u001F'
private const val RECORD = '\u001E'

/**
 * Tolerant reader for values written by older versions or an interrupted write: unknown and
 * malformed rows are skipped, and the first valid row for an id wins, as in `Favorites`.
 */
private fun read(store: KeyValueStore): Map<String, Int> {
    val text = store.getString(Ratings.KEY) ?: return emptyMap()
    val values = LinkedHashMap<String, Int>()
    for (entry in text.split(UNIT)) {
        if (entry.isEmpty()) continue
        val separator = entry.indexOf(RECORD)
        if (separator <= 0 || separator != entry.lastIndexOf(RECORD)) continue
        val id = entry.substring(0, separator)
        val stars = entry.substring(separator + 1).toIntOrNull()
        if (!validId(id) || stars == null || stars !in 1..Ratings.MAX_STARS || id in values) continue
        if (values.size >= Ratings.MAX_RATINGS) break
        values[id] = stars
    }
    return values
}

private fun encode(values: Map<String, Int>): String =
    values.entries.joinToString(UNIT.toString()) { (id, stars) -> "$id$RECORD$stars" }

private fun validId(id: String): Boolean = id.isNotBlank() && UNIT !in id && RECORD !in id
