package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DatedPlay(val trackId: String, val atMs: Long)

data class RecentContext(val context: PlayContext, val atMs: Long)

/** What has been listened to, as of the latest recorded play. */
data class HistorySnapshot(
    /** Where listening started from, newest first, without duplicates. */
    val recentContexts: List<RecentContext> = emptyList(),
    /** Track id → number of counted plays. */
    val playCounts: Map<String, Int> = emptyMap(),
    /** Track id → time of its latest counted play (epoch ms). */
    val lastPlayedMs: Map<String, Long> = emptyMap(),
    /** Ids of the latest counted plays, newest first (repeats included). */
    val recentPlays: List<String> = emptyList(),
    /** Newest first; at most two years / 50,000 plays. Older totals remain in playCounts. */
    val datedPlays: List<DatedPlay> = emptyList(),
    /** When dated tracking began. Existing undated totals are never assigned invented dates. */
    val trackingStartedMs: Long? = null,
) {
    val totalPlays: Int get() = playCounts.values.sum()
}

/**
 * Local listening history, saved in [store]: it never leaves the device. Feeds the Home
 * sections (recently played, most and least played).
 */
class ListeningHistory(
    private val store: KeyValueStore,
    private val mutations: RestoreMutationGate = RestoreMutationGate(),
    private val clock: () -> Long,
) {
    constructor(store: KeyValueStore, clock: () -> Long) : this(store, RestoreMutationGate(), clock)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HistorySnapshot> = _state.asStateFlow()

    init {
        store.putString(STARTED_KEY, _state.value.trackingStartedMs.toString())
    }

    /** Re-reads everything from [store], after something else (a backup restore) replaced it. */
    fun reload() = prepareReload().invoke()

    /** Normalization writes only to the supplied staging store during backup preparation. */
    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = load(store)
        store.putString(STARTED_KEY, loaded.trackingStartedMs.toString())
        return { _state.value = loaded }
    }

    /** Records one counted play of [trackId], started from [context] (null: no specific source). */
    fun recordPlay(trackId: String, context: PlayContext?) {
        if (!mutations.canMutate) return
        val now = clock()
        val current = _state.value
        val contexts = if (context == null) current.recentContexts else {
            (listOf(RecentContext(context, now)) + current.recentContexts.filter { it.context.key != context.key })
                .take(MAX_CONTEXTS)
        }
        val next = HistorySnapshot(
            recentContexts = contexts,
            playCounts = current.playCounts + (trackId to (current.playCounts[trackId] ?: 0) + 1),
            lastPlayedMs = current.lastPlayedMs + (trackId to now),
            recentPlays = (listOf(trackId) + current.recentPlays).take(MAX_RECENT_PLAYS),
            datedPlays = buildList(minOf(MAX_DATED_PLAYS, current.datedPlays.size + 1)) {
                add(DatedPlay(trackId, now))
                val cutoff = now - RETENTION_MS
                for (play in current.datedPlays) {
                    if (size == MAX_DATED_PLAYS) break
                    if (play.atMs >= cutoff) add(play)
                }
            },
            trackingStartedMs = current.trackingStartedMs,
        )
        save(next)
        _state.value = next
    }

    private fun load(store: KeyValueStore = this.store): HistorySnapshot {
        // Native stores expose a coherent snapshot. The fallback supports older/custom
        // stores, but cannot provide a multi-key concurrent-read guarantee on its own.
        val values = store.snapshot()
        fun value(key: String): String? = if (values != null) values[key] else store.getString(key)
        fun list(key: String): List<String> = value(key)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
        val contexts = list(CONTEXTS_KEY).mapNotNull { entry ->
            val key = entry.substringBeforeLast(RECORD, "")
            val at = entry.substringAfterLast(RECORD, "").toLongOrNull() ?: return@mapNotNull null
            PlayContext.fromKey(key)?.let { RecentContext(it, at) }
        }
        val counts = mutableMapOf<String, Int>()
        val last = mutableMapOf<String, Long>()
        for (entry in list(COUNTS_KEY)) {
            val parts = entry.split(RECORD)
            val id = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: continue
            val count = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: continue
            counts[id] = count
            parts.getOrNull(2)?.toLongOrNull()?.let { last[id] = it }
        }
        val now = clock()
        val dated = list(DATED_KEY).take(MAX_DATED_PLAYS).mapNotNull { entry ->
            val id = entry.substringBeforeLast(RECORD, "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val at = entry.substringAfterLast(RECORD, "").toLongOrNull()
                ?.takeIf { it in (now - RETENTION_MS)..now } ?: return@mapNotNull null
            DatedPlay(id, at)
        }.sortedByDescending { it.atMs }
        return HistorySnapshot(contexts, counts, last, list(RECENT_KEY), dated,
            value(STARTED_KEY)?.toLongOrNull()?.takeIf { it <= now } ?: now)
    }

    private fun save(snapshot: HistorySnapshot) {
        // Preserve the existing backup wire format without allocating a temporary String
        // for every retained play. Android applies these related keys with one editor, so
        // a concurrent backup snapshot cannot see new totals with the previous dated list.
        val contexts = buildString {
            snapshot.recentContexts.forEachIndexed { index, recent ->
                if (index != 0) append(SEPARATOR)
                append(recent.context.key).append(RECORD).append(recent.atMs)
            }
        }
        val counts = buildString {
            snapshot.playCounts.entries.forEachIndexed { index, (id, count) ->
                if (index != 0) append(SEPARATOR)
                append(id).append(RECORD).append(count).append(RECORD).append(snapshot.lastPlayedMs[id] ?: 0)
            }
        }
        val dated = buildString {
            snapshot.datedPlays.forEachIndexed { index, play ->
                if (index != 0) append(SEPARATOR)
                append(play.trackId).append(RECORD).append(play.atMs)
            }
        }
        store.putStrings(mapOf(
            CONTEXTS_KEY to contexts,
            COUNTS_KEY to counts,
            RECENT_KEY to snapshot.recentPlays.joinToString(SEPARATOR.toString()),
            DATED_KEY to dated,
        ))
    }

    internal companion object {
        const val DATED_KEY = "history.dated_plays"
        const val STARTED_KEY = "history.tracking_started_ms"
        const val MAX_DATED_PLAYS = 50_000
        const val RETENTION_MS = 731L * 86_400_000
        const val RECORD = '\u001E'
        private const val SEPARATOR = '\u001F'
        const val CONTEXTS_KEY = "history.contexts"
        const val COUNTS_KEY = "history.counts"
        const val RECENT_KEY = "history.recent"
        const val MAX_CONTEXTS = 20
        const val MAX_RECENT_PLAYS = 50
    }
}
