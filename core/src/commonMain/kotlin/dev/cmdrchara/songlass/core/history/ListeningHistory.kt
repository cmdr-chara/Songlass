package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
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
class ListeningHistory(private val store: KeyValueStore, private val clock: () -> Long) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<HistorySnapshot> = _state.asStateFlow()

    init {
        store.putString(STARTED_KEY, _state.value.trackingStartedMs.toString())
    }

    /** Re-reads everything from [store], after something else (a backup restore) replaced it. */
    fun reload() {
        val loaded = load()
        _state.value = loaded
        store.putString(STARTED_KEY, loaded.trackingStartedMs.toString())
    }

    /** Records one counted play of [trackId], started from [context] (null: no specific source). */
    fun recordPlay(trackId: String, context: PlayContext?) {
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
            datedPlays = (listOf(DatedPlay(trackId, now)) + current.datedPlays)
                .filter { it.atMs >= now - RETENTION_MS }.take(MAX_DATED_PLAYS),
            trackingStartedMs = current.trackingStartedMs,
        )
        _state.value = next
        save(next)
    }

    private fun load(): HistorySnapshot {
        val contexts = store.getStringList(CONTEXTS_KEY).mapNotNull { entry ->
            val key = entry.substringBeforeLast(RECORD, "")
            val at = entry.substringAfterLast(RECORD, "").toLongOrNull() ?: return@mapNotNull null
            PlayContext.fromKey(key)?.let { RecentContext(it, at) }
        }
        val counts = mutableMapOf<String, Int>()
        val last = mutableMapOf<String, Long>()
        for (entry in store.getStringList(COUNTS_KEY)) {
            val parts = entry.split(RECORD)
            val id = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: continue
            val count = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: continue
            counts[id] = count
            parts.getOrNull(2)?.toLongOrNull()?.let { last[id] = it }
        }
        val now = clock()
        val dated = store.getStringList(DATED_KEY).take(MAX_DATED_PLAYS).mapNotNull { entry ->
            val id = entry.substringBeforeLast(RECORD, "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val at = entry.substringAfterLast(RECORD, "").toLongOrNull()
                ?.takeIf { it in (now - RETENTION_MS)..now } ?: return@mapNotNull null
            DatedPlay(id, at)
        }.sortedByDescending { it.atMs }
        return HistorySnapshot(contexts, counts, last, store.getStringList(RECENT_KEY), dated,
            store.getString(STARTED_KEY)?.toLongOrNull()?.takeIf { it <= now } ?: now)
    }

    private fun save(snapshot: HistorySnapshot) {
        store.putStringList(CONTEXTS_KEY, snapshot.recentContexts.map { "${it.context.key}$RECORD${it.atMs}" })
        store.putStringList(
            COUNTS_KEY,
            snapshot.playCounts.map { (id, count) -> "$id$RECORD$count$RECORD${snapshot.lastPlayedMs[id] ?: 0}" },
        )
        store.putStringList(RECENT_KEY, snapshot.recentPlays)
        store.putStringList(DATED_KEY, snapshot.datedPlays.map { "${it.trackId}$RECORD${it.atMs}" })
    }

    internal companion object {
        const val DATED_KEY = "history.dated_plays"
        const val STARTED_KEY = "history.tracking_started_ms"
        const val MAX_DATED_PLAYS = 50_000
        const val RETENTION_MS = 731L * 86_400_000
        const val RECORD = '\u001E'
        const val CONTEXTS_KEY = "history.contexts"
        const val COUNTS_KEY = "history.counts"
        const val RECENT_KEY = "history.recent"
        const val MAX_CONTEXTS = 20
        const val MAX_RECENT_PLAYS = 50
    }
}

/**
 * Decides when the current track counts as "played": after 30 seconds, or half of it for short
 * tracks, once per queue entry. Jumping well back (e.g. repeat one, seeking to the start)
 * allows the same entry to count again.
 */
class PlayCounter(private val minPlayMs: Long = 30_000) {
    private var entryId: Long? = null
    private var counted = false
    private var lastPositionMs = 0L

    /** Feed playback progress; returns true exactly when a play should be recorded. */
    fun update(entryId: Long?, positionMs: Long, durationMs: Long): Boolean {
        if (entryId != this.entryId) {
            this.entryId = entryId
            counted = false
        } else if (counted && positionMs + REWIND_MS < lastPositionMs) {
            counted = false
        }
        lastPositionMs = positionMs
        if (entryId == null || counted) return false
        val threshold = if (durationMs > 0) minOf(minPlayMs, durationMs / 2) else minPlayMs
        if (positionMs >= threshold) {
            counted = true
            return true
        }
        return false
    }

    private companion object {
        const val REWIND_MS = 5_000L
    }
}
