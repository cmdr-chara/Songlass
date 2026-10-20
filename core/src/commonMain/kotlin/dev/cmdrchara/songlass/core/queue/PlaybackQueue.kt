package dev.cmdrchara.songlass.core.queue

import dev.cmdrchara.songlass.core.model.Track
import kotlin.random.Random

enum class RepeatMode {
    Off, All, One;

    /** Order used by the repeat button: off → all → one → off. */
    fun next(): RepeatMode = when (this) {
        Off -> All
        All -> One
        One -> Off
    }
}

/** One slot in the queue. The same track can appear more than once, so entries have their own id. */
data class QueueEntry(val id: Long, val track: Track)

/**
 * Immutable play queue. Every operation returns a new queue.
 *
 * [entries] is always the current play order. While shuffle is on, the unshuffled order is
 * kept aside so that turning shuffle off restores it with the same current track.
 */
data class PlaybackQueue(
    val entries: List<QueueEntry> = emptyList(),
    val currentIndex: Int = NO_INDEX,
    val repeatMode: RepeatMode = RepeatMode.Off,
    internal val unshuffledEntries: List<QueueEntry>? = null,
    internal val nextEntryId: Long = 0,
) {
    val current: QueueEntry? get() = entries.getOrNull(currentIndex)
    val isShuffled: Boolean get() = unshuffledEntries != null

    /** Entries after the current one, as shown in an "Up Next" list. */
    val upNext: List<QueueEntry> get() = entries.drop(currentIndex + 1)

    fun indexOf(entryId: Long): Int = entries.indexOfFirst { it.id == entryId }

    /**
     * Index to play after the current entry, or `null` when playback should stop.
     *
     * Repeat one only replays the track when it ends by itself ([userInitiated] = false);
     * an explicit skip still moves on, as in Apple Music.
     */
    fun nextIndex(userInitiated: Boolean): Int? {
        if (entries.isEmpty()) return null
        if (repeatMode == RepeatMode.One && !userInitiated) return currentIndex
        val next = currentIndex + 1
        return when {
            next < entries.size -> next
            repeatMode != RepeatMode.Off -> 0
            else -> null
        }
    }

    /** Index of the previous entry, wrapping to the end only when repeat is on. */
    fun previousIndex(): Int? {
        if (entries.isEmpty()) return null
        val previous = currentIndex - 1
        return when {
            previous >= 0 -> previous
            repeatMode != RepeatMode.Off -> entries.lastIndex
            else -> null
        }
    }

    fun withCurrentIndex(index: Int): PlaybackQueue {
        require(index in entries.indices) { "Index $index out of bounds for queue of ${entries.size}" }
        return copy(currentIndex = index)
    }

    fun withRepeatMode(mode: RepeatMode): PlaybackQueue = copy(repeatMode = mode)

    /**
     * Turns shuffle on or off. Shuffling puts the current track first and randomizes all the
     * others; unshuffling restores the original order. The current track never changes.
     */
    fun withShuffle(enabled: Boolean, random: Random = Random): PlaybackQueue {
        if (enabled == isShuffled) return this
        val current = current
        return if (enabled) {
            val others = entries.filter { it.id != current?.id }.shuffled(random)
            copy(
                entries = listOfNotNull(current) + others,
                currentIndex = if (current == null) NO_INDEX else 0,
                unshuffledEntries = entries,
            )
        } else {
            val original = unshuffledEntries.orEmpty()
            copy(
                entries = original,
                currentIndex = current?.let { c -> original.indexOfFirst { it.id == c.id } } ?: NO_INDEX,
                unshuffledEntries = null,
            )
        }
    }

    /** Inserts [tracks] right after the current entry. An empty queue starts at the first one. */
    fun playNext(tracks: List<Track>): PlaybackQueue =
        insert(tracks, playOrderPosition = currentIndex + 1, unshuffledPosition = { list ->
            current?.let { c -> list.indexOfFirst { it.id == c.id } + 1 } ?: list.size
        })

    /** Appends [tracks] at the end. An empty queue starts at the first one. */
    fun addToEnd(tracks: List<Track>): PlaybackQueue =
        insert(tracks, playOrderPosition = entries.size, unshuffledPosition = { it.size })

    /**
     * Removes an entry. Removing the current entry makes the following one current
     * (or the new last one, if the removed entry was last).
     */
    fun remove(entryId: Long): PlaybackQueue {
        val index = indexOf(entryId)
        if (index < 0) return this
        val remaining = entries.filterNot { it.id == entryId }
        val newIndex = when {
            remaining.isEmpty() -> NO_INDEX
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> currentIndex.coerceAtMost(remaining.lastIndex)
            else -> currentIndex
        }
        return copy(
            entries = remaining,
            currentIndex = newIndex,
            unshuffledEntries = unshuffledEntries?.filterNot { it.id == entryId },
        )
    }

    /** Moves an entry within the play order. The current entry stays current. */
    fun move(fromIndex: Int, toIndex: Int): PlaybackQueue {
        require(fromIndex in entries.indices && toIndex in entries.indices) {
            "Cannot move $fromIndex to $toIndex in queue of ${entries.size}"
        }
        if (fromIndex == toIndex) return this
        val reordered = entries.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        val newIndex = when {
            currentIndex == fromIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        return copy(entries = reordered, currentIndex = newIndex)
    }

    /** Refreshes metadata in both orders without changing slots, membership or playback position. */
    fun withUpdatedTracks(tracks: Map<String, Track>): PlaybackQueue {
        fun List<QueueEntry>.updated() = map { entry -> tracks[entry.track.id]?.let { entry.copy(track = it) } ?: entry }
        return copy(entries = entries.updated(), unshuffledEntries = unshuffledEntries?.updated())
    }

    private fun insert(
        tracks: List<Track>,
        playOrderPosition: Int,
        unshuffledPosition: (List<QueueEntry>) -> Int,
    ): PlaybackQueue {
        if (tracks.isEmpty()) return this
        val added = tracks.mapIndexed { i, track -> QueueEntry(nextEntryId + i, track) }
        return copy(
            entries = entries.toMutableList().apply { addAll(playOrderPosition, added) },
            currentIndex = if (currentIndex == NO_INDEX) 0 else currentIndex,
            unshuffledEntries = unshuffledEntries?.let { list ->
                list.toMutableList().apply { addAll(unshuffledPosition(list), added) }
            },
            nextEntryId = nextEntryId + added.size,
        )
    }

    companion object {
        const val NO_INDEX = -1

        /** Builds a queue from [tracks], starting at [startIndex], optionally shuffled. */
        fun of(
            tracks: List<Track>,
            startIndex: Int = 0,
            shuffle: Boolean = false,
            repeatMode: RepeatMode = RepeatMode.Off,
            random: Random = Random,
        ): PlaybackQueue {
            if (tracks.isEmpty()) return PlaybackQueue(repeatMode = repeatMode)
            require(startIndex in tracks.indices) { "Start index $startIndex out of bounds for ${tracks.size} tracks" }
            val queue = PlaybackQueue(
                entries = tracks.mapIndexed { i, track -> QueueEntry(i.toLong(), track) },
                currentIndex = startIndex,
                repeatMode = repeatMode,
                nextEntryId = tracks.size.toLong(),
            )
            return queue.withShuffle(shuffle, random)
        }
    }
}
