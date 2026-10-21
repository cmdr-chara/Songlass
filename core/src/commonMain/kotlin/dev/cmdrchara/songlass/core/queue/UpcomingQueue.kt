package dev.cmdrchara.songlass.core.queue

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.TrackAvailability
import dev.cmdrchara.songlass.core.model.isSameFileAs

/** Durations count every listed, available occurrence, not a playback completion time. */
data class UpcomingQueueSummary(
    val entryCount: Int,
    val availableCount: Int,
    val knownDurationMs: Long,
    val unknownDurationCount: Int,
    val unavailableCount: Int,
    /** The sum is saturated rather than allowed to wrap for invalid or extremely large metadata. */
    val durationOverflow: Boolean,
) {
    val knownDurationCount: Int get() = availableCount - unknownDurationCount
}

/** A contiguous section preserves playback order even if a caller supplies several boundaries. */
data class UpcomingQueueSection(val isTemporary: Boolean, val entries: List<QueueEntry>)

/** A presentation snapshot; constructing it never consumes, sorts or otherwise mutates the queue. */
class UpcomingQueue private constructor(
    val entries: List<QueueEntry>,
    val sections: List<UpcomingQueueSection>,
    val summary: UpcomingQueueSummary,
) {
    companion object {
        fun from(queue: PlaybackQueue): UpcomingQueue {
            val entries = queue.upNext
            var available = 0
            var unknown = 0
            var duration = 0L
            var overflow = false
            val sections = mutableListOf<UpcomingQueueSection>()
            var sectionStart = 0
            for ((index, entry) in entries.withIndex()) {
                if (index > 0 && entry.isTemporary != entries[index - 1].isTemporary) {
                    sections += UpcomingQueueSection(entries[sectionStart].isTemporary,
                        entries.subList(sectionStart, index).toList())
                    sectionStart = index
                }
                if (!entry.isAvailable) continue
                available++
                val length = entry.track.durationMs
                if (length <= 0) unknown++
                else if (duration > Long.MAX_VALUE - length) {
                    duration = Long.MAX_VALUE
                    overflow = true
                } else duration += length
            }
            if (entries.isNotEmpty()) sections += UpcomingQueueSection(entries[sectionStart].isTemporary,
                entries.subList(sectionStart, entries.size).toList())
            return UpcomingQueue(entries, sections.toList(), UpcomingQueueSummary(
                entryCount = entries.size,
                availableCount = available,
                knownDurationMs = duration,
                unknownDurationCount = unknown,
                unavailableCount = entries.size - available,
                durationOverflow = overflow,
            ))
        }
    }
}

/**
 * The exact selection offered for a new playlist. Playlists contain each song once, whereas
 * playback slots may repeat it. The first available occurrence supplies the saved order.
 */
class QueuePlaylistSnapshot private constructor(
    val tracks: List<Track>,
    val duplicateCount: Int,
    val unavailableCount: Int,
) {
    /**
     * A stale selection fails as a whole. Even a conservative path-based reindex must be reviewed
     * again: no captured file is silently replaced and no additional duplicate is silently lost.
     * Tag/artwork updates on the same file retain the existing identity contract.
     */
    fun currentTrackIds(availability: TrackAvailability): List<String>? {
        val ids = ArrayList<String>(tracks.size)
        val unique = HashSet<String>(tracks.size)
        for (track in tracks) {
            val current = availability.resolve(track) ?: return null
            if (!track.isSameFileAs(current) || !unique.add(current.id)) return null
            ids += current.id
        }
        return ids
    }

    companion object {
        fun capture(queue: PlaybackQueue): QueuePlaylistSnapshot {
            val tracks = mutableListOf<Track>()
            val ids = HashSet<String>()
            var duplicates = 0
            var unavailable = 0
            for (entry in queue.upNext) {
                if (!entry.isAvailable) unavailable++
                else if (!ids.add(entry.track.id)) duplicates++
                else tracks += entry.track
            }
            return QueuePlaylistSnapshot(tracks.toList(), duplicates, unavailable)
        }
    }
}
