package dev.cmdrchara.songlass.core.queue

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.isSameFileAs
import kotlin.math.ln
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

/** Where a fresh set of additions starts: right after the current song, or once the collection has finished. */
enum class TemporaryPlacement { AfterCurrent, AfterCollection }

/**
 * A random order. With a [weight] it is a weighted shuffle without replacement (each item's
 * key is ln(u)/weight, the Efraimidis–Spirakis method): heavier items tend to come first, but
 * any order is still possible. Weights must be positive.
 */
fun <T> List<T>.shuffledBy(random: Random, weight: ((T) -> Double)?): List<T> =
    if (weight == null) shuffled(random)
    else map { it to ln(1.0 - random.nextDouble()) / weight(it).coerceAtLeast(MIN_WEIGHT) }
        .sortedByDescending { it.second }.map { it.first }

private const val MIN_WEIGHT = 1e-9

/** One slot in the queue. The same track can appear more than once, so entries have their own id. */
data class QueueEntry(
    val id: Long, val track: Track, val isTemporary: Boolean = false,
    /** The slot remains visible when a full library scan no longer resolves its file safely. */
    val isAvailable: Boolean = true,
)

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
    /** Collection slot after which the temporary segment is played. Never a track/media id. */
    internal val temporaryAnchorId: Long? = null,
    /** The temporary segment follows the whole collection, not the current song; it stays there as playback moves. */
    internal val temporaryAtEnd: Boolean = false,
) {
    val current: QueueEntry? get() = entries.getOrNull(currentIndex)
    val isShuffled: Boolean get() = unshuffledEntries != null
    val temporaryEntries: List<QueueEntry> get() = entries.filter { it.isTemporary }
    val pendingTemporaryCount: Int get() = entries.count { it.isTemporary && it.id != current?.id }
    val hasSource: Boolean get() = entries.any { !it.isTemporary }

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
        if (current == null) return entries.indexOfFirst { it.isAvailable }.takeIf { it >= 0 }
        if (repeatMode == RepeatMode.One && !userInitiated && current?.isAvailable == true) return currentIndex
        val next = entries.indices.firstOrNull { it > currentIndex && entries[it].isAvailable }
        return when {
            next != null -> next
            // Temporary additions are consumed once, including in Repeat all. Repeat one
            // above deliberately keeps the active addition until the listener skips it.
            repeatMode != RepeatMode.Off -> entries.indexOfFirst { !it.isTemporary && it.isAvailable }.takeIf { it >= 0 }
            else -> null
        }
    }

    /** Index of the previous entry, wrapping to the end only when repeat is on. */
    fun previousIndex(): Int? {
        if (entries.isEmpty()) return null
        val previous = entries.indices.lastOrNull { it < currentIndex && entries[it].isAvailable }
        return when {
            previous != null -> previous
            repeatMode != RepeatMode.Off -> entries.indexOfLast { !it.isTemporary && it.isAvailable }.takeIf { it >= 0 }
            else -> null
        }
    }

    fun withCurrentIndex(index: Int): PlaybackQueue {
        require(index in entries.indices) { "Index $index out of bounds for queue of ${entries.size}" }
        val selected = entries[index]
        if (selected.id == current?.id || entries.none { it.isTemporary }) return copy(currentIndex = index)
        val source = entries.filterNot { it.isTemporary }
        val temporary = temporaryEntries
        val remaining = if (selected.isTemporary) {
            // Selecting a later addition deliberately skips the earlier additions.
            temporary.dropWhile { it.id != selected.id }
        } else temporary.filterNot { it.id == current?.id }
        return withSegment(source, remaining, if (selected.isTemporary) temporaryAnchorId else selected.id, selected.id, temporaryAtEnd)
    }

    fun withRepeatMode(mode: RepeatMode): PlaybackQueue = copy(repeatMode = mode)

    /**
     * Turns shuffle on or off. Shuffling puts the current track first and randomizes all the
     * others; unshuffling restores the original order. The current track never changes.
     */
    fun withShuffle(enabled: Boolean, random: Random = Random, weight: ((Track) -> Double)? = null): PlaybackQueue {
        if (enabled == isShuffled) return this
        if (entries.any { it.isTemporary }) {
            val source = entries.filterNot { it.isTemporary }
            val anchor = if (current?.isTemporary == true) temporaryAnchorId else current?.id
            val ordered = if (enabled) {
                val first = source.firstOrNull { it.id == anchor }
                listOfNotNull(first) + source.filterNot { it.id == anchor }.shuffledBy(random) { weight?.invoke(it.track) ?: 1.0 }
            } else unshuffledEntries.orEmpty().filterNot { it.isTemporary }
            // Additions queued "after the collection" stay after it, whatever the new order is.
            return withSegment(ordered, temporaryEntries, anchor, current?.id, temporaryAtEnd).copy(
                unshuffledEntries = if (enabled) source else null,
            )
        }
        val current = current
        return if (enabled) {
            val others = entries.filter { it.id != current?.id }.shuffledBy(random) { weight?.invoke(it.track) ?: 1.0 }
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
     * Adds a one-shot segment before the collection continues. Play next inserts immediately
     * after the active entry; ordinary additions append to the pending segment. The source
     * order (including its hidden unshuffled order) is never modified by this operation.
     */
    fun enqueueTemporary(
        tracks: List<Track>,
        playNext: Boolean = false,
        placement: TemporaryPlacement = TemporaryPlacement.AfterCurrent,
        shuffleAdded: Boolean = false,
        random: Random = Random,
    ): PlaybackQueue {
        if (tracks.isEmpty()) return this
        val added = tracks.mapIndexed { i, track -> QueueEntry(nextEntryId + i, track, isTemporary = true) }
        val temporary = temporaryEntries
        val active = current?.takeIf { it.isTemporary }
        val pending = temporary.filterNot { it.id == active?.id }
        val ordered = listOfNotNull(active) + when {
            playNext -> added + pending
            // Each addition lands at a random place among the ones still waiting.
            shuffleAdded -> pending.toMutableList().also { list -> added.forEach { list.add(random.nextInt(list.size + 1), it) } }
            else -> pending + added
        }
        val source = entries.filterNot { it.isTemporary }
        // Play next is always right after the current song; additions to waiting ones keep where those wait.
        val atEnd = when {
            active != null -> temporaryAtEnd
            playNext -> false
            pending.isNotEmpty() -> temporaryAtEnd
            else -> placement == TemporaryPlacement.AfterCollection
        }
        val anchor = if (active != null) temporaryAnchorId else current?.id
        return withSegment(source, ordered, anchor, current?.id ?: ordered.first().id, atEnd)
            .copy(nextEntryId = nextEntryId + added.size)
    }

    /** Clears pending additions without interrupting the one already playing. */
    fun clearTemporary(): PlaybackQueue = withSegment(
        entries.filterNot { it.isTemporary }, listOfNotNull(current?.takeIf { it.isTemporary }),
        temporaryAnchorId, current?.id, temporaryAtEnd,
    )

    /**
     * Leaves the temporary segment and continues after its source anchor. At the end of a
     * non-repeating collection it stops, keeping the source order available for selection.
     */
    fun returnToSource(): PlaybackQueue {
        val source = entries.filterNot { it.isTemporary }
        val selected = if (current?.isTemporary != true) current?.id else {
            val after = source.indexOfFirst { it.id == temporaryAnchorId } + 1
            source.drop(after).firstOrNull { it.isAvailable }?.id
                ?: source.firstOrNull { it.isAvailable }?.takeIf { repeatMode != RepeatMode.Off }?.id
        }
        return withSegment(source, emptyList(), null, selected)
    }

    /**
     * Removes an entry. Removing the current entry makes the following one current
     * (or the new last one, if the removed entry was last).
     */
    fun remove(entryId: Long): PlaybackQueue {
        val index = indexOf(entryId)
        if (index < 0) return this
        if (entries.any { it.isTemporary }) {
            val source = entries.filterNot { it.isTemporary || it.id == entryId }
            val temporary = temporaryEntries.filterNot { it.id == entryId }
            val selected = if (current?.id != entryId) current?.id else
                entries.drop(index + 1).firstOrNull { it.isAvailable }?.id ?: if (current?.isTemporary == true) {
                    source.firstOrNull { it.isAvailable }?.takeIf { repeatMode != RepeatMode.Off }?.id
                } else source.lastOrNull()?.id ?: temporary.firstOrNull()?.id
            val anchor = when {
                source.any { it.id == selected } -> selected
                temporaryAnchorId == entryId -> source.lastOrNull { indexOf(it.id) < index }?.id
                else -> temporaryAnchorId
            }
            return withSegment(source, temporary, anchor, selected, temporaryAtEnd).copy(
                unshuffledEntries = unshuffledEntries?.filterNot { it.id == entryId },
            )
        }
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
        if (entries.any { it.isTemporary }) {
            val moving = entries[fromIndex]
            val target = entries[toIndex]
            // Keep the collection and the one-shot segment distinct. The active addition
            // is not draggable: moving it behind pending entries would replay consumed work.
            if (moving.isTemporary != target.isTemporary ||
                (moving.isTemporary && (moving.id == current?.id || target.id == current?.id))) return this
            val segment = entries.filter { it.isTemporary == moving.isTemporary }.toMutableList()
            val from = segment.indexOfFirst { it.id == moving.id }
            val to = segment.indexOfFirst { it.id == target.id }
            segment.add(to, segment.removeAt(from))
            return if (moving.isTemporary) withSegment(entries.filterNot { it.isTemporary }, segment, temporaryAnchorId, current?.id, temporaryAtEnd)
            else withSegment(segment, temporaryEntries, temporaryAnchorId, current?.id, temporaryAtEnd)
        }
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
        fun List<QueueEntry>.updated() = map { entry ->
            tracks[entry.track.id]?.takeIf { entry.track.isSameFileAs(it) }
                ?.let { entry.copy(track = it) } ?: entry
        }
        return copy(entries = entries.updated(), unshuffledEntries = unshuffledEntries?.updated())
    }

    /** A full scan changes availability, not membership, slot ids or original collection order. */
    fun withResolvedTracks(resolve: (Track) -> Track?): PlaybackQueue {
        fun List<QueueEntry>.resolved() = map { entry ->
            val available = resolve(entry.track)
            if (available == null) entry.copy(isAvailable = false)
            else entry.copy(track = available, isAvailable = true)
        }
        return copy(entries = entries.resolved(), unshuffledEntries = unshuffledEntries?.resolved())
    }

    private fun withSegment(
        source: List<QueueEntry>, temporary: List<QueueEntry>, anchorId: Long?, selectedId: Long?, atEnd: Boolean = false,
    ): PlaybackQueue {
        val anchor = (if (atEnd) source.lastOrNull()?.id else anchorId)?.takeIf { id -> source.any { it.id == id } }
        val at = source.indexOfFirst { it.id == anchor } + 1
        val ordered = if (temporary.isEmpty()) source else source.toMutableList().apply { addAll(at, temporary) }
        return copy(
            entries = ordered,
            currentIndex = ordered.indexOfFirst { it.id == selectedId },
            temporaryAnchorId = anchor.takeIf { temporary.isNotEmpty() },
            temporaryAtEnd = atEnd && temporary.isNotEmpty(),
        )
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

        /**
         * Builds a queue from [tracks], starting at [startIndex], optionally shuffled.
         * A controller passes its previous [nextEntryId] as [firstEntryId], including after
         * clearing the queue: a delayed row action must never refer to a different slot.
         */
        fun of(
            tracks: List<Track>,
            startIndex: Int = 0,
            shuffle: Boolean = false,
            repeatMode: RepeatMode = RepeatMode.Off,
            random: Random = Random,
            firstEntryId: Long = 0,
            weight: ((Track) -> Double)? = null,
        ): PlaybackQueue {
            if (tracks.isEmpty()) return PlaybackQueue(repeatMode = repeatMode, nextEntryId = firstEntryId)
            require(startIndex in tracks.indices) { "Start index $startIndex out of bounds for ${tracks.size} tracks" }
            val queue = PlaybackQueue(
                entries = tracks.mapIndexed { i, track -> QueueEntry(firstEntryId + i, track) },
                currentIndex = startIndex,
                repeatMode = repeatMode,
                nextEntryId = firstEntryId + tracks.size,
            )
            return queue.withShuffle(shuffle, random, weight)
        }
    }
}
