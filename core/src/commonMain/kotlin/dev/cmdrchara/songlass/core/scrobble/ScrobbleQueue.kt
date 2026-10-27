package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.storage.KeyValueStore

/** One queued listen plus its retry bookkeeping. */
internal data class ScrobbleQueueEntry(
    val record: ScrobbleRecord,
    /** Completed send attempts; zero for a listen that has never been tried. */
    val attempts: Int = 0,
    /** Epoch milliseconds before which this entry must not be sent again. */
    val nextAttemptMs: Long = 0,
) {
    val key: String get() = record.dedupeKey
}

/** Retry schedule for a queue that must survive restarts without hammering Last.fm. */
internal object ScrobbleBackoff {
    /** 30 s, 2 min, 10 min, 30 min, 2 h, 6 h: the last delay repeats. */
    val delaysMs: List<Long> = listOf(30_000L, 120_000L, 600_000L, 1_800_000L, 7_200_000L, 21_600_000L)

    /** After this many retryable failures an entry is dropped instead of retried forever. */
    const val MAX_ATTEMPTS: Int = 8

    /** Delay after [failures] completed failures (1 = first failure). */
    fun delayMs(failures: Int): Long = delaysMs[(failures - 1).coerceIn(0, delaysMs.lastIndex)]
}

/**
 * The durable scrobble queue.
 *
 * Listen that could not be sent (no session yet, offline, rate limited, session expired) wait here
 * and survive a restart. Entries are removed **before** a batch is sent: a crash while the request is
 * in flight can lose those listens, but it can never submit the same occurrence twice. At most
 * [MAX_ENTRIES] listens are kept; a longer backlog drops the oldest, because Last.fm rejects very old
 * timestamps anyway.
 *
 * The queue owns no coroutine and no network: the scrobbler decides when to flush.
 */
internal class ScrobbleQueue(private val store: KeyValueStore) {
    private val entries = mutableListOf<ScrobbleQueueEntry>()

    init {
        reload()
    }

    /** Every queued listen, oldest first. */
    fun snapshot(): List<ScrobbleQueueEntry> = entries.toList()

    val size: Int get() = entries.size

    /** Lists whose retry time has come at [nowMs], oldest first. */
    fun due(nowMs: Long): List<ScrobbleQueueEntry> = entries.filter { it.nextAttemptMs <= nowMs }

    /**
     * Adds one listen. Duplicates of an occurrence already queued are dropped, and a full queue
     * forgets its oldest pending listen to make room for the new one.
     */
    fun enqueue(record: ScrobbleRecord): Boolean {
        if (entries.any { it.key == record.dedupeKey }) return false
        while (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries += ScrobbleQueueEntry(record)
        persist()
        return true
    }

    /** Removes the entries identified by [keys], for example because their batch was accepted. */
    fun remove(keys: Set<String>) {
        if (keys.isEmpty()) return
        val removed = entries.removeAll { it.key in keys }
        if (removed) persist()
    }

    /** Puts a failed batch back with its updated attempts and retry time; duplicates are merged. */
    fun requeue(failed: List<ScrobbleQueueEntry>) {
        if (failed.isEmpty()) return
        for (entry in failed) {
            val index = entries.indexOfFirst { it.key == entry.key }
            if (index >= 0) {
                entries[index] = entry
            } else {
                while (entries.size >= MAX_ENTRIES) entries.removeAt(0)
                entries += entry
            }
        }
        entries.sortBy { it.record.timestampSeconds }
        persist()
    }

    /** Makes every queued listen due immediately, for a manual retry after reconnecting. */
    fun clearBackoff() {
        if (entries.isEmpty()) return
        var changed = false
        for (index in entries.indices) {
            val entry = entries[index]
            if (entry.attempts != 0 || entry.nextAttemptMs != 0L) {
                entries[index] = entry.copy(attempts = 0, nextAttemptMs = 0)
                changed = true
            }
        }
        if (changed) persist()
    }

    /** Forgets everything: used by logout and by "restore defaults". */
    fun clear() {
        if (entries.isEmpty()) return
        entries.clear()
        persist()
    }

    /** Re-reads the stored queue, after something else replaced the key-value store. */
    fun reload() {
        entries.clear()
        val stored = store.getString(QUEUE_KEY) ?: return
        for (chunk in stored.split(RECORD_SEPARATOR)) {
            if (chunk.isEmpty()) continue
            val entry = decodeEntry(chunk) ?: continue
            if (entries.none { it.key == entry.key }) entries += entry
        }
        entries.sortBy { it.record.timestampSeconds }
    }

    private fun persist() {
        store.putString(QUEUE_KEY, entries.joinToString(RECORD_SEPARATOR.toString()) { encodeEntry(it) })
    }

    private fun encodeEntry(entry: ScrobbleQueueEntry): String = listOf(
        encodeText(entry.record.track.artist),
        encodeText(entry.record.track.title),
        encodeText(entry.record.track.album.orEmpty()),
        entry.record.timestampSeconds.toString(),
        entry.record.durationSeconds.toString(),
        entry.attempts.toString(),
        entry.nextAttemptMs.toString(),
    ).joinToString(FIELD_SEPARATOR.toString())

    private fun decodeEntry(chunk: String): ScrobbleQueueEntry? {
        val fields = chunk.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) return null
        val artist = decodeText(fields[0])
        val title = decodeText(fields[1])
        if (artist.isBlank() || title.isBlank()) return null
        val timestamp = fields[3].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val duration = fields[4].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val attempts = fields[5].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val nextAttempt = fields[6].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val record = ScrobbleRecord(
            track = ScrobbleTrack(
                artist = artist,
                title = title,
                album = decodeText(fields[2]).takeIf(String::isNotEmpty),
                durationMs = duration * 1000,
            ),
            timestampSeconds = timestamp,
            durationSeconds = duration,
        )
        return ScrobbleQueueEntry(record, attempts, nextAttempt)
    }

    internal companion object {
        const val QUEUE_KEY: String = LastFmStorageKeys.QUEUE

        /** A backlog this long would be years of listening; older entries are worthless to send. */
        const val MAX_ENTRIES: Int = 200

        private const val RECORD_SEPARATOR = '\u001E'
        private const val FIELD_SEPARATOR = '\u001F'
        private const val FIELD_COUNT = 7

        /**
         * Music tags may contain any character, so the two separators and `%` itself are escaped.
         * A plain `%25` in a title therefore survives a round trip unchanged.
         */
        internal fun encodeText(value: String): String = buildString {
            for (character in value) {
                when (character) {
                    '%' -> append("%25")
                    RECORD_SEPARATOR -> append("%1E")
                    FIELD_SEPARATOR -> append("%1F")
                    else -> append(character)
                }
            }
        }

        internal fun decodeText(value: String): String {
            if ('%' !in value) return value
            val output = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val character = value[index]
                if (character == '%' && index + 2 < value.length) {
                    val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
                    if (decoded != null) {
                        output.append(decoded.toChar())
                        index += 3
                        continue
                    }
                }
                output.append(character)
                index++
            }
            return output.toString()
        }
    }
}
