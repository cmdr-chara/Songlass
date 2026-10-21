package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackMarkersAmbiguityTest {
    private val track = Track("7", "Episode", "content://media/audio/7", "Narrator", "Series",
        durationMs = 1_200_000, folder = "Podcasts", fileName = "episode.flac")

    @Test
    fun historicalRestoreAndDuplicatePathCanProduceTwoPointsThatRemainIntactThroughAnotherBackup() {
        // The former backup codec moved id1 to id7 while keeping the source's URI1.
        val oldRestored = SavedProgress(TrackSignature.of(track).copy(uri = "content://media/audio/1"), 145_000)
        val source = InMemoryKeyValueStore().apply {
            putString(TrackMarkersCodec.PROGRESS_KEY, TrackMarkersCodec.encodeProgress(listOf(oldRestored)))
        }
        val markers = TrackMarkers(source).apply {
            // Equal relative paths on two volumes made the legacy path resolver ambiguous.
            setLibrary(listOf(track, track.copy(id = "8", uri = "content://media/audio/8")))
            checkpoint(track, 350_000, 600_000, force = true)
        }
        val original = markers.state.value.progress
        assertEquals(listOf(145_000L, 350_000L), original.map { it.positionMs })
        assertEquals(listOf("content://media/audio/1", track.uri), original.map { it.track.uri })
        markers.setLibrary(listOf(track)) // The duplicate later disappeared; neither point was erased.
        assertNull(markers.progressFor(track, 600_000))

        val text = DataBackup(source).export(listOf(track)).text
        val targetTrack = track.copy(uri = "content://media/audio/42")
        val target = InMemoryKeyValueStore()
        val preview = DataBackup(target).preview(text, listOf(targetTrack))
        assertEquals(2, preview.counts.trackProgress)
        assertEquals(0, preview.droppedReferences)
        DataBackup(target).apply(preview)
        val restored = TrackMarkers(target).apply { setLibrary(listOf(targetTrack), stableIds = true) }
        val expected = original.map { it.copy(track = it.track.copy(binding = MarkerBinding.Unresolved)) }
        assertEquals(expected, restored.state.value.progress)
        assertNull(restored.progressFor(targetTrack, 600_000))
        expected.forEach { assertNull(restored.resolve(it.track)) }

        val exported = DataBackup(target).export(listOf(targetTrack))
        assertEquals(2, exported.counts.trackProgress)
        val againStore = InMemoryKeyValueStore()
        DataBackup(againStore).apply(DataBackup(againStore).preview(exported.text, listOf(track)))
        val again = TrackMarkers(againStore).apply { setLibrary(listOf(track), stableIds = true) }
        assertEquals(expected, again.state.value.progress)
        assertNull(again.progressFor(track, 600_000))
        assertEquals(exported.text, DataBackup(againStore).export(listOf(track)).text)
    }

    @Test
    fun signaturesDifferingOnlyInBindingPreserveBothRowsEvenWhenTheirPositionsAreIdentical() {
        val legacy = SavedProgress(TrackSignature.of(track), 145_000)
        val canonical = legacy.copy(track = legacy.track.copy(binding = MarkerBinding.Canonical))
        val encoded = TrackMarkersCodec.encodeProgress(listOf(legacy, canonical))
        val remapped = TrackMarkersCodec.remapProgress(encoded, { it }) { MarkerRestoreResolution.Matched(track) }
        val isolated = legacy.copy(track = legacy.track.copy(binding = MarkerBinding.Unresolved))
        assertEquals(listOf(isolated, isolated), TrackMarkersCodec.decodeProgress(remapped))
        assertTrue(TrackMarkersCodec.canonicalProgress(remapped))
        assertEquals(remapped, TrackMarkersCodec.remapProgress(remapped, { "other" }) {
            error("An unresolved point must never request another match")
        })
    }

    @Test
    fun ambiguousEndpointsKeepTheirOriginalDurationAndPositionBeforeAnyTargetClamp() {
        val first = SavedProgress(TrackSignature.of(track), track.durationMs)
        val second = SavedProgress(first.track.copy(uri = "content://media/audio/1"), track.durationMs - 100)
        val shorter = track.copy(durationMs = track.durationMs - 1_000)
        val remapped = TrackMarkersCodec.remapProgress(TrackMarkersCodec.encodeProgress(listOf(first, second)), { it }) {
            MarkerRestoreResolution.Matched(shorter)
        }
        val expected = listOf(first, second).map { it.copy(track = it.track.copy(binding = MarkerBinding.Unresolved)) }
        assertEquals(expected, TrackMarkersCodec.decodeProgress(remapped))
        val single = TrackMarkersCodec.remapProgress(TrackMarkersCodec.encodeProgress(listOf(first)), { it }) {
            MarkerRestoreResolution.Matched(shorter)
        }
        assertEquals(shorter.durationMs, TrackMarkersCodec.decodeProgress(single).single().positionMs)
        assertEquals(MarkerBinding.Canonical, TrackMarkersCodec.decodeProgress(single).single().track.binding)
    }

    @Test
    fun onlyUnresolvedV2RowsCanRepeatAndTheExistingCollectionLimitStillApplies() {
        val point = SavedProgress(TrackSignature.of(track), 145_000)
        val legacy = TrackMarkersCodec.encodeProgress(listOf(point))
        assertFailsWith<IllegalArgumentException> {
            TrackMarkersCodec.decodeProgress(legacy + legacy.removePrefix("v1|"))
        }
        val canonical = point.copy(track = point.track.copy(binding = MarkerBinding.Canonical))
        val canonicalText = TrackMarkersCodec.encodeProgress(listOf(canonical))
        assertFailsWith<IllegalArgumentException> {
            TrackMarkersCodec.decodeProgress(canonicalText + canonicalText.removePrefix("v2|"))
        }
        assertFailsWith<IllegalArgumentException> { TrackMarkersCodec.encodeProgress(listOf(canonical, canonical)) }
        val unresolved = point.copy(track = point.track.copy(binding = MarkerBinding.Unresolved))
        val inertText = TrackMarkersCodec.encodeProgress(listOf(unresolved, unresolved))
        assertTrue(inertText.startsWith("v2|"))
        assertEquals(listOf(unresolved, unresolved), TrackMarkersCodec.decodeProgress(inertText))
        assertFailsWith<IllegalArgumentException> {
            TrackMarkersCodec.encodeProgress(List(TrackMarkersCodec.MAX_PROGRESS + 1) { unresolved })
        }
    }

    @Test
    fun localPromotionCollisionPreservesAllRowsAndRollsBackBothKeysOnFailure() {
        val legacy = SavedProgress(TrackSignature.of(track), 145_000)
        val canonical = SavedProgress(legacy.track.copy(binding = MarkerBinding.Canonical), 350_000)
        val bookmark = TrackBookmark("chapter", legacy.track, 145_000, "Keep the bookmark")
        val backing = InMemoryKeyValueStore().apply {
            putString(TrackMarkersCodec.PROGRESS_KEY, TrackMarkersCodec.encodeProgress(listOf(legacy, canonical)))
            putString(TrackMarkersCodec.BOOKMARKS_KEY, TrackMarkersCodec.encodeBookmarks(listOf(bookmark)))
        }
        val store = FailSecondPutStore(backing)
        val markers = TrackMarkers(store).apply { setLibrary(listOf(track)) }
        val before = markers.state.value
        val snapshot = backing.snapshot()
        val revision = markers.libraryRevision.value
        store.failNextTransaction = true
        assertFailsWith<IllegalStateException> { markers.setLibrary(listOf(track), stableIds = true) }
        assertEquals(snapshot, backing.snapshot())
        assertEquals(before, markers.state.value)
        assertEquals(revision, markers.libraryRevision.value)
        markers.setLibrary(listOf(track), stableIds = true)
        val expected = listOf(legacy, canonical).map { it.copy(track = it.track.copy(binding = MarkerBinding.Unresolved)) }
        assertEquals(expected, markers.state.value.progress)
        assertEquals(bookmark.copy(track = bookmark.track.copy(binding = MarkerBinding.Canonical)),
            markers.state.value.bookmarks.single())
        assertNull(markers.progressFor(track, 600_000))
        val puts = store.puts
        markers.setLibrary(listOf(track), stableIds = true)
        markers.reload()
        assertEquals(puts, store.puts)
        assertEquals(expected, markers.state.value.progress)
    }

    @Test
    fun canonicalBookmarksRemainVisibleInTheirMissingTrackDetailWithoutBecomingPlayable() {
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply { setLibrary(listOf(track), stableIds = true) }
        val bookmark = markers.add(track, 145_000, "Chapter")
        markers.setLibrary(emptyList(), stableIds = true)
        assertEquals(listOf(bookmark), markers.bookmarksForDisplay(track))
        assertNull(markers.resolveSelection(track))
        assertNull(markers.resolve(bookmark.track))
    }

    @Test
    fun currentAppSelectionFollowsCanonicalTagEditsAndReindexEvenWithMissingDisplayTags() {
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply { setLibrary(listOf(track), stableIds = true) }
        val bookmark = markers.add(track, 145_000, "Chapter")
        val edited = track.copy(uri = "content://media/audio/42", title = "", artist = null, album = null)
        markers.setLibrary(listOf(edited), stableIds = true)
        assertNull(markers.resolve(TrackSignature.of(track))) // A stored untrusted legacy signature still fails.
        assertEquals(edited, markers.resolveSelection(track))
        assertEquals(listOf(bookmark), markers.bookmarksForDisplay(track))
        assertEquals(listOf(bookmark), markers.bookmarksForDisplay(edited))
        assertEquals(edited, markers.resolve(bookmark.track))
        markers.setLibrary(listOf(edited, edited.copy(uri = "another-uri")), stableIds = true)
        assertNull(markers.resolveSelection(track))
        assertNull(markers.resolve(bookmark.track))
    }

    @Test
    fun unresolvedMarkersAreVisibleOnlyWithTheirOwnFactsAndNeverUnderARecycledIdReplacement() {
        val unresolved = TrackBookmark("chapter", TrackSignature.of(track).copy(binding = MarkerBinding.Unresolved), 145_000, "Old")
        val store = InMemoryKeyValueStore().apply {
            putString(TrackMarkersCodec.BOOKMARKS_KEY, TrackMarkersCodec.encodeBookmarks(listOf(unresolved)))
        }
        val replacement = track.copy(title = "Another recording", fileName = "replacement.flac")
        val markers = TrackMarkers(store).apply { setLibrary(listOf(replacement), stableIds = true) }
        assertTrue(markers.bookmarksForDisplay(replacement).isEmpty())
        assertTrue(markers.bookmarksFor(replacement).isEmpty())
        assertEquals(listOf(unresolved), markers.bookmarksForDisplay(track))
        assertNull(markers.resolve(unresolved.track))
        assertTrue(markers.bookmarksFor(track).isEmpty())
    }

    @Test
    fun legacySelectionUsesItsExistingSafeResolverAndDoesNotPromoteRawIdsForDisplay() {
        val bookmark = TrackBookmark("chapter", TrackSignature.of(track), 145_000, "Chapter")
        val store = InMemoryKeyValueStore().apply {
            putString(TrackMarkersCodec.BOOKMARKS_KEY, TrackMarkersCodec.encodeBookmarks(listOf(bookmark)))
        }
        val reindexed = track.copy(id = "42", uri = "content://media/audio/42")
        val markers = TrackMarkers(store).apply { setLibrary(listOf(reindexed)) }
        assertEquals(reindexed, markers.resolveSelection(track))
        assertEquals(listOf(bookmark), markers.bookmarksForDisplay(reindexed))
        // A canonical flag restored on a source without a registry must not enable raw-id matching.
        store.putString(TrackMarkersCodec.BOOKMARKS_KEY, TrackMarkersCodec.encodeBookmarks(listOf(
            bookmark.copy(track = bookmark.track.copy(binding = MarkerBinding.Canonical)),
        )))
        markers.reload()
        val replacement = track.copy(title = "Replacement", fileName = "replacement.flac")
        markers.setLibrary(listOf(replacement))
        assertTrue(markers.bookmarksForDisplay(replacement).isEmpty())
        assertNull(markers.resolveSelection(track))
    }

    private class FailSecondPutStore(private val backing: InMemoryKeyValueStore) : KeyValueStore {
        var failNextTransaction = false
        var puts = 0
            private set
        private var transactionPuts = 0
        override fun getString(key: String): String? = backing.getString(key)
        override fun snapshot(): Map<String, String> = backing.snapshot()
        override fun putString(key: String, value: String?) {
            puts++
            if (failNextTransaction && ++transactionPuts == 2) {
                failNextTransaction = false
                transactionPuts = 0
                error("Simulated full storage")
            }
            backing.putString(key, value)
        }
    }
}
