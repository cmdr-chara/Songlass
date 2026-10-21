package dev.cmdrchara.songlass.core.queue

import dev.cmdrchara.songlass.core.model.TrackAvailability
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpcomingQueueTest {
    @Test
    fun summaryExcludesCurrentCountsOccurrencesAndSeparatesUnknownFromUnavailable() {
        val known = track("known", durationMs = 90_000)
        val queue = PlaybackQueue(
            entries = listOf(
                QueueEntry(0, track("current", durationMs = 600_000)),
                QueueEntry(1, known, isTemporary = true),
                QueueEntry(2, known, isTemporary = true),
                QueueEntry(3, track("zero", durationMs = 0)),
                QueueEntry(4, track("negative", durationMs = -5)),
                QueueEntry(5, track("missing", durationMs = 900_000), isAvailable = false),
            ),
            currentIndex = 0,
        )

        val upcoming = UpcomingQueue.from(queue)

        assertEquals(UpcomingQueueSummary(5, 4, 180_000, 2, 1, false), upcoming.summary)
        assertEquals(2, upcoming.summary.knownDurationCount)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), upcoming.entries.map { it.id })
        assertEquals(listOf(true, false), upcoming.sections.map { it.isTemporary })
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L, 5L)),
            upcoming.sections.map { section -> section.entries.map { it.id } })
    }

    @Test
    fun sectionsNeverSortOrMergeAcrossAnInterveningSegment() {
        val queue = PlaybackQueue(entries = listOf(
            QueueEntry(0, track("current")),
            QueueEntry(1, track("a"), isTemporary = true),
            QueueEntry(2, track("b")),
            QueueEntry(3, track("c"), isTemporary = true),
        ), currentIndex = 0)

        val upcoming = UpcomingQueue.from(queue)

        assertEquals(listOf(true, false, true), upcoming.sections.map { it.isTemporary })
        assertEquals(queue.upNext, upcoming.sections.flatMap { it.entries })
    }

    @Test
    fun emptyOrFinishedUpcomingListHasZeroCountsAndNoSections() {
        for (queue in listOf(PlaybackQueue(), PlaybackQueue.of(listOf(track("current"))))) {
            val upcoming = UpcomingQueue.from(queue)
            assertEquals(UpcomingQueueSummary(0, 0, 0, 0, 0, false), upcoming.summary)
            assertTrue(upcoming.entries.isEmpty())
            assertTrue(upcoming.sections.isEmpty())
            assertTrue(QueuePlaylistSnapshot.capture(queue).tracks.isEmpty())
        }
    }

    @Test
    fun durationOverflowSaturatesAndDoesNotWrapToAnApparentlyShortDuration() {
        val queue = PlaybackQueue.of(listOf(track("current"),
            track("very-long", durationMs = Long.MAX_VALUE - 1), track("extra", durationMs = 3)))

        val summary = UpcomingQueue.from(queue).summary

        assertEquals(Long.MAX_VALUE, summary.knownDurationMs)
        assertTrue(summary.durationOverflow)
        assertEquals(2, summary.knownDurationCount)
    }

    @Test
    fun anExactMaximumSumIsNotMarkedAsOverflow() {
        val queue = PlaybackQueue.of(listOf(track("current"),
            track("very-long", durationMs = Long.MAX_VALUE - 1), track("extra", durationMs = 1)))

        val summary = UpcomingQueue.from(queue).summary

        assertEquals(Long.MAX_VALUE, summary.knownDurationMs)
        assertFalse(summary.durationOverflow)
    }

    @Test
    fun playlistSnapshotKeepsFirstAvailableOccurrenceOrderAndReportsOmissions() {
        val first = track("a")
        val second = track("b")
        val queue = PlaybackQueue(entries = listOf(
            QueueEntry(0, track("current")),
            QueueEntry(1, first, isTemporary = true, isAvailable = false),
            QueueEntry(2, second, isTemporary = true),
            QueueEntry(3, first, isTemporary = true),
            QueueEntry(4, second),
            QueueEntry(5, first),
            QueueEntry(6, track("missing"), isAvailable = false),
        ), currentIndex = 0)
        val original = queue.copy(entries = queue.entries.toList())

        val snapshot = QueuePlaylistSnapshot.capture(queue)

        assertEquals(listOf(second, first), snapshot.tracks)
        assertEquals(2, snapshot.duplicateCount)
        assertEquals(2, snapshot.unavailableCount)
        assertEquals(original, queue)
        assertEquals(listOf("b", "a"), snapshot.currentTrackIds(TrackAvailability(listOf(first, second))))
    }

    @Test
    fun snapshotAndProjectionDoNotFollowChangesToACallersMutableList() {
        val before = track("before")
        val entries = mutableListOf(QueueEntry(0, track("current")), QueueEntry(1, before))
        val queue = PlaybackQueue(entries = entries, currentIndex = 0)
        val snapshot = QueuePlaylistSnapshot.capture(queue)
        val upcoming = UpcomingQueue.from(queue)

        entries[1] = QueueEntry(2, track("after"))

        assertEquals(listOf(before), snapshot.tracks)
        assertEquals(listOf(before), upcoming.entries.map { it.track })
        assertEquals(listOf(before), upcoming.sections.single().entries.map { it.track })
    }

    @Test
    fun missingCapturedSongRejectsTheWholeSelectionInsteadOfDroppingIt() {
        val one = track("one")
        val two = track("two")
        val snapshot = QueuePlaylistSnapshot.capture(PlaybackQueue.of(listOf(track("current"), one, two)))

        assertNull(snapshot.currentTrackIds(TrackAvailability(listOf(one))))
        assertEquals(listOf(one, two), snapshot.tracks)
    }

    @Test
    fun reusedCanonicalIdentityStillRequiresTheCapturedFile() {
        val original = track("one").copy(folder = "Music/Album", fileName = "one.flac", dateAddedMs = 1_000)
        val replacement = original.copy(folder = "Music/Other", fileName = "another.flac", dateAddedMs = 2_000)
        val snapshot = QueuePlaylistSnapshot.capture(PlaybackQueue.of(listOf(track("current"), original)))

        assertNull(snapshot.currentTrackIds(TrackAvailability(listOf(replacement), stableIds = true)))
    }

    @Test
    fun ambiguousCapturedIdentityFailsEvenWhenBothCandidatesHaveEqualMetadata() {
        val original = track("one")
        val snapshot = QueuePlaylistSnapshot.capture(PlaybackQueue.of(listOf(track("current"), original)))

        assertNull(snapshot.currentTrackIds(TrackAvailability(listOf(original, original))))
        assertNull(snapshot.currentTrackIds(TrackAvailability(listOf(original, original), stableIds = true)))
    }

    @Test
    fun reindexingRequiresReviewEvenWhenTheAvailabilityResolverCanRecoverThePath() {
        val original = track("one").copy(folder = "Music/Album", fileName = "one.flac")
        val reindexed = original.copy(id = "new", uri = "file:///music/new.flac")
        val availability = TrackAvailability(listOf(reindexed))
        val snapshot = QueuePlaylistSnapshot.capture(PlaybackQueue.of(listOf(track("current"), original)))

        assertEquals(reindexed, availability.resolve(original))
        assertNull(snapshot.currentTrackIds(availability))
    }

    @Test
    fun tagAndArtworkEditsOnTheSameFileDoNotSubstituteTheCapturedSong() {
        val original = track("one").copy(folder = "Music/Album", fileName = "one.flac", dateAddedMs = 1_000)
        val edited = original.copy(title = "Corrected title", artist = "Corrected artist", artworkRevision = 2)
        val snapshot = QueuePlaylistSnapshot.capture(PlaybackQueue.of(listOf(track("current"), original)))

        assertEquals(listOf(original.id), snapshot.currentTrackIds(TrackAvailability(listOf(edited))))
    }
}
