package dev.cmdrchara.songlass.core.history

import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        return HistorySnapshot(contexts, counts, last, store.getStringList(RECENT_KEY))
    }

    private fun save(snapshot: HistorySnapshot) {
        store.putStringList(CONTEXTS_KEY, snapshot.recentContexts.map { "${it.context.key}$RECORD${it.atMs}" })
        store.putStringList(
            COUNTS_KEY,
            snapshot.playCounts.map { (id, count) -> "$id$RECORD$count$RECORD${snapshot.lastPlayedMs[id] ?: 0}" },
        )
        store.putStringList(RECENT_KEY, snapshot.recentPlays)
    }

    private companion object {
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
