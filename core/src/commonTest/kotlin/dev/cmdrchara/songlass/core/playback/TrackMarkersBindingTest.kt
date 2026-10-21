package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.library.TrackIdentityRegistry
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackMarkersBindingTest {
    private val original = Track("7", "Episode", "content://media/audio/7", "Narrator", "Series",
        durationMs = 1_200_000, folder = "Podcasts", fileName = "episode.flac", dateAddedMs = 1_000)

    @Test
    fun preRegistryMarkersRejectARecycledIdOnTheFirstRegistryScanWithoutLosingData() = runTest {
        val store = legacyStore()
        val previous = TrackMarkers(store).state.value
        val replacement = original.copy(title = "Another recording", fileName = "other.flac",
            durationMs = 900_000, dateAddedMs = 2_000)
        val registered = TrackIdentityRegistry(store).canonicalize(listOf(replacement)).single()
        assertEquals(original.id, registered.id) // First-upgrade seeding cannot recover old history.
        val markers = TrackMarkers(store).apply { setLibrary(listOf(registered), stableIds = true) }
        val isolated = markers.state.value
        assertEquals(previous.progress.single().copy(track = previous.progress.single().track.copy(
            binding = MarkerBinding.Unresolved)), isolated.progress.single())
        assertEquals(previous.bookmarks.single().copy(track = previous.bookmarks.single().track.copy(
            binding = MarkerBinding.Unresolved)), isolated.bookmarks.single())
        assertNull(markers.resolve(isolated.bookmarks.single().track))
        assertNull(markers.progressFor(registered, 600_000))
        assertTrue(markers.bookmarksFor(registered).isEmpty())

        // Even a later canonical track with identical old facts cannot revive an isolated record.
        markers.setLibrary(listOf(original), stableIds = true)
        assertNull(markers.resolve(isolated.bookmarks.single().track))
        assertTrue(markers.bookmarksFor(original).isEmpty())
        markers.checkpoint(original, 250_000, 600_000, force = true)
        assertEquals(250_000L, markers.progressFor(original, 600_000))
        assertEquals(2, markers.state.value.progress.size)
        markers.completed(original)
        assertEquals(isolated.progress, markers.state.value.progress)
        assertTrue(markers.rename(isolated.bookmarks.single().id, "Still retained"))
        assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
    }

    @Test
    fun legacyPromotionRequiresKnownRecordingFactsAndTheOriginalLocalLocation() {
        val incompatible = listOf(
            original.copy(folder = "Moved"), // Same id, URI, tags and duration are insufficient.
            original.copy(folder = null, fileName = null),
            original.copy(title = ""), original.copy(artist = null), original.copy(album = "Other"),
            original.copy(durationMs = 0), original.copy(durationMs = original.durationMs + 1_001),
        )
        for (candidate in incompatible) {
            val markers = TrackMarkers(legacyStore()).apply { setLibrary(listOf(candidate), stableIds = true) }
            assertEquals(MarkerBinding.Unresolved, markers.state.value.bookmarks.single().track.binding)
            assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
        }
        val compatible = original.copy(uri = "content://media/audio/42", durationMs = original.durationMs + 1_000)
        val markers = TrackMarkers(legacyStore()).apply { setLibrary(listOf(compatible), stableIds = true) }
        assertEquals(MarkerBinding.Canonical, markers.state.value.bookmarks.single().track.binding)
        assertEquals(compatible, markers.resolve(markers.state.value.bookmarks.single().track))
    }

    @Test
    fun pathlessLegacyIdentityRequiresTheSameUriAndCannotBorrowAMatchingTitle() {
        val pathless = original.copy(folder = null, fileName = null)
        val signature = TrackSignature.of(pathless)
        assertTrue(TrackMarkersCodec.matchesLegacyIdentity(signature, signature))
        assertFalse(TrackMarkersCodec.matchesLegacyIdentity(signature, signature.copy(uri = "other")))
        assertFalse(TrackMarkersCodec.matchesLegacyIdentity(signature, signature.copy(path = "Podcasts/episode.flac")))
        assertFalse(TrackMarkersCodec.matchesLegacyIdentity(signature.copy(durationMs = 0), signature))
        assertFalse(TrackMarkersCodec.matchesLegacyIdentity(signature.copy(title = " "), signature.copy(title = " ")))
    }

    @Test
    fun migrationCommitsBothCollectionsOnceAndCanonicalBindingSurvivesTagEditsAndReindex() {
        val store = ObservedStore(legacyStore())
        val markers = TrackMarkers(store)
        markers.setLibrary(listOf(original), stableIds = true)
        assertEquals(1, store.transactions.size)
        assertEquals(setOf(TrackMarkersCodec.PROGRESS_KEY, TrackMarkersCodec.BOOKMARKS_KEY), store.transactions.single())
        val migrated = markers.state.value
        assertTrue(migrated.progress.all { it.track.binding == MarkerBinding.Canonical })
        assertTrue(migrated.bookmarks.all { it.track.binding == MarkerBinding.Canonical })
        markers.setLibrary(listOf(original), stableIds = true)
        markers.reload()
        assertEquals(1, store.transactions.size)
        val edited = original.copy(uri = "content://media/audio/42", title = "Edited", artist = "New artist",
            album = "New album")
        val reopened = TrackMarkers(store).apply { setLibrary(listOf(edited), stableIds = true) }
        assertEquals(migrated, reopened.state.value)
        assertEquals(edited, reopened.resolve(migrated.bookmarks.single().track))
        assertEquals(145_000L, reopened.progressFor(edited, 600_000))
        assertEquals(1, store.transactions.size)
        assertEquals(MarkerBinding.Canonical, reopened.add(edited, 250_000, "New").track.binding)
    }

    @Test
    fun missingLegacyRecordsWaitWithoutWritesAndAmbiguousOccupiedIdsFailClosed() {
        val store = ObservedStore(legacyStore())
        val markers = TrackMarkers(store)
        val previous = markers.state.value
        markers.setLibrary(emptyList(), stableIds = true)
        assertEquals(previous, markers.state.value)
        assertTrue(store.transactions.isEmpty())
        assertNull(markers.resolve(previous.bookmarks.single().track))
        markers.setLibrary(listOf(original), stableIds = true)
        assertEquals(MarkerBinding.Canonical, markers.state.value.bookmarks.single().track.binding)

        val ambiguous = TrackMarkers(legacyStore()).apply {
            setLibrary(listOf(original, original.copy(uri = "second-uri")), stableIds = true)
        }
        assertEquals(MarkerBinding.Unresolved, ambiguous.state.value.bookmarks.single().track.binding)
        ambiguous.setLibrary(listOf(original), stableIds = true)
        assertNull(ambiguous.resolve(ambiguous.state.value.bookmarks.single().track))
    }

    @Test
    fun failedTwoKeyMigrationRollsBackStorageAndDoesNotPublishLibraryOrMarkerState() {
        val backing = legacyStore()
        val store = FailSecondPutStore(backing)
        val markers = TrackMarkers(store).apply { setLibrary(listOf(original)) }
        val previous = markers.state.value
        val stored = backing.snapshot()
        val revision = markers.libraryRevision.value
        store.failNextTransaction = true
        assertFailsWith<IllegalStateException> { markers.setLibrary(listOf(original), stableIds = true) }
        assertEquals(stored, backing.snapshot())
        assertEquals(previous, markers.state.value)
        assertEquals(revision, markers.libraryRevision.value)
        // The previous path-based library, not the rejected canonical index, remains published.
        assertEquals(original, markers.resolve(TrackSignature.of(original).copy(id = "old-index")))
        markers.setLibrary(listOf(original), stableIds = true)
        assertEquals(MarkerBinding.Canonical, markers.state.value.bookmarks.single().track.binding)
    }

    @Test
    fun reloadMigratesRestoredLegacyDataBeforePublishingEvenWhilePlaybackWritesAreSuspended() {
        val backing = InMemoryKeyValueStore()
        val store = FailSecondPutStore(backing)
        val markers = TrackMarkers(store).apply {
            setLibrary(listOf(original), stableIds = true)
            checkpoint(original, 220_000, 600_000, force = true)
            add(original, 220_000, "Before restore")
        }
        val before = markers.state.value
        val restored = legacyStore().snapshot()
        backing.replace(emptySet(), restored)
        markers.writesSuspended = true
        store.failNextTransaction = true
        assertFailsWith<IllegalStateException> { markers.reload() }
        assertEquals(restored, backing.snapshot())
        assertEquals(before, markers.state.value)
        markers.reload() // No additional scan is required to establish the local trust boundary.
        assertEquals(145_000L, markers.progressFor(original, 600_000))
        assertEquals(MarkerBinding.Canonical, markers.state.value.bookmarks.single().track.binding)
        markers.checkpoint(original, 330_000, 600_000, force = true)
        assertEquals(145_000L, markers.progressFor(original, 600_000))
        assertTrue(markers.writesSuspended)
    }

    @Test
    fun reloadImmediatelyIsolatesRestoredLegacyConflictsAgainstTheExistingCanonicalLibrary() {
        val store = InMemoryKeyValueStore()
        val replacement = original.copy(folder = "Replacement")
        val markers = TrackMarkers(store).apply { setLibrary(listOf(replacement), stableIds = true) }
        store.replace(emptySet(), legacyStore().snapshot())
        markers.reload()
        assertEquals(MarkerBinding.Unresolved, markers.state.value.bookmarks.single().track.binding)
        assertNull(markers.progressFor(replacement, 600_000))
        assertTrue(markers.bookmarksFor(replacement).isEmpty())
    }

    @Test
    fun alreadyRegisteredTracksCanSavePointsWithoutUsingMissingDisplayTagsAsIdentityEvidence() {
        val track = original.copy(title = "", artist = null, album = null)
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply { setLibrary(listOf(track), stableIds = true) }
        markers.checkpoint(track, 145_000, 600_000, force = true)
        val bookmark = markers.add(track, 145_000, null)
        assertEquals(MarkerBinding.Canonical, bookmark.track.binding)
        assertEquals(145_000L, markers.progressFor(track, 600_000))
        assertEquals(listOf(bookmark), markers.bookmarksFor(track))
    }

    @Test
    fun aLoadedFileCanCheckpointAfterAccessLossWithoutDuplicatingOrDowngradingItsCanonicalPoint() {
        val store = InMemoryKeyValueStore()
        val markers = TrackMarkers(store).apply {
            setLibrary(listOf(original), stableIds = true)
            checkpoint(original, 145_000, 600_000, force = true)
        }
        markers.setLibrary(emptyList(), stableIds = true)
        markers.checkpoint(original, 150_000, 600_000, force = true)
        assertEquals(1, markers.state.value.progress.size)
        assertEquals(MarkerBinding.Canonical, markers.state.value.progress.single().track.binding)
        assertNull(markers.progressFor(original, 600_000))
        val edited = original.copy(uri = "content://media/audio/42", title = "Edited while unavailable")
        markers.setLibrary(listOf(edited), stableIds = true)
        assertEquals(150_000L, markers.progressFor(edited, 600_000))
        assertEquals(1, markers.state.value.progress.size)
    }

    @Test
    fun v1FixturesRoundTripExactlyAndV2RejectsUnknownTruncatedAndOversizedFields() {
        val progressV1 = "v1|1:71:u3:p/f1:T1:A1:B7:12000006:120000"
        val bookmarkV1 = "v1|1:b1:71:u3:p/f1:T1:A1:B7:12000006:1200007:Chapter"
        val progress = TrackMarkersCodec.decodeProgress(progressV1)
        val bookmarks = TrackMarkersCodec.decodeBookmarks(bookmarkV1)
        assertEquals(MarkerBinding.Legacy, progress.single().track.binding)
        assertEquals(MarkerBinding.Legacy, bookmarks.single().track.binding)
        assertEquals("Chapter", bookmarks.single().title)
        assertEquals(120_000L, progress.single().positionMs)
        assertEquals(progressV1, TrackMarkersCodec.encodeProgress(progress))
        assertEquals(bookmarkV1, TrackMarkersCodec.encodeBookmarks(bookmarks))
        val bound = bookmarks.single().copy(track = bookmarks.single().track.copy(binding = MarkerBinding.Canonical))
        val v2 = TrackMarkersCodec.encodeBookmarks(listOf(bound))
        assertTrue(v2.startsWith("v2|"))
        assertEquals(listOf(bound), TrackMarkersCodec.decodeBookmarks(v2))
        assertTrue(TrackMarkersCodec.canonicalBookmarks(v2))
        assertFalse(TrackMarkersCodec.canonicalBookmarks(v2.replace("canonical", "arbitrary")))
        assertFalse(TrackMarkersCodec.canonicalBookmarks(v2.replaceFirst("v2|", "v3|")))
        assertFalse(TrackMarkersCodec.canonicalBookmarks(v2.dropLast(1)))
        assertFalse(TrackMarkersCodec.canonicalBookmarks(v2 + "1:x"))
        assertFailsWith<IllegalArgumentException> {
            TrackMarkersCodec.encodeBookmarks(listOf(bound.copy(track = bound.track.copy(title = "x".repeat(4_097)))))
        }
    }

    @Test
    fun backupCodecNeverImportsCanonicalTrustAndConflictsStayInertWithoutSyntheticIds() {
        val canonical = TrackSignature.of(original).copy(binding = MarkerBinding.Canonical)
        val value = TrackMarkersCodec.encodeProgress(listOf(SavedProgress(canonical, 145_000)))
        val missing = TrackMarkersCodec.remapProgress(value, { it }) { MarkerRestoreResolution.Missing }
        assertEquals(canonical.copy(binding = MarkerBinding.Legacy), TrackMarkersCodec.decodeProgress(missing).single().track)
        val conflict = TrackMarkersCodec.remapProgress(value, { null })
        val isolated = canonical.copy(binding = MarkerBinding.Unresolved)
        assertEquals(isolated, TrackMarkersCodec.decodeProgress(conflict).single().track)
        var callbacks = 0
        assertEquals(conflict, TrackMarkersCodec.remapProgress(conflict, { it }) {
            callbacks++
            MarkerRestoreResolution.Matched(original)
        })
        assertEquals(0, callbacks)
        val moved = original.copy(id = "42", uri = "content://media/audio/42", folder = "Other device")
        val matched = TrackMarkersCodec.remapProgress(value, { moved.id }) { MarkerRestoreResolution.Matched(moved) }
        assertEquals(TrackSignature.of(moved).copy(binding = MarkerBinding.Canonical),
            TrackMarkersCodec.decodeProgress(matched).single().track)
        assertEquals(145_000L, TrackMarkersCodec.decodeProgress(matched).single().positionMs)
    }

    private fun legacyStore() = InMemoryKeyValueStore().also { store ->
        TrackMarkers(store).apply {
            setLibrary(listOf(original))
            checkpoint(original, 145_000, 600_000, force = true)
            add(original, 145_000, "Saved chapter")
        }
    }

    private class ObservedStore(private val backing: InMemoryKeyValueStore) : KeyValueStore by backing {
        val transactions = mutableListOf<Set<String>>()
        override fun replace(removing: Set<String>, writing: Map<String, String>) {
            transactions += writing.keys.toSet()
            backing.replace(removing, writing)
        }
    }

    /** Fails once after the first key was written, exercising the store's real rollback contract. */
    private class FailSecondPutStore(private val backing: InMemoryKeyValueStore) : KeyValueStore {
        var failNextTransaction = false
        private var puts = 0
        override fun getString(key: String): String? = backing.getString(key)
        override fun snapshot(): Map<String, String> = backing.snapshot()
        override fun putString(key: String, value: String?) {
            if (failNextTransaction && ++puts == 2) {
                failNextTransaction = false
                puts = 0
                error("Simulated full storage during migration")
            }
            backing.putString(key, value)
        }
    }
}
