package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsImportRequestTest {
    private val song = Track("canonical-a", "Città 😀", "content://media/7", artist = "Artist", durationMs = 10_000)
    private val other = song.copy(id = "canonical-b", title = "Other", uri = "content://media/9")

    private class World(val store: KeyValueStore = InMemoryKeyValueStore()) {
        var tracks = emptyList<Track>()
        var ready = true
        var generation = 0L
        fun repository() = LyricsRepository(store = store, resolveSelection = { requested ->
            tracks.singleOrNull { it.id == requested.id }?.takeIf { ready }
                ?.let { LyricsSelection(it, canonicalId = true, accessGeneration = generation) }
        })
    }

    @Test fun onlyVerifiedCanonicalSelectionsCanCreateRestorableRequests() {
        assertNull(LyricsRepository().prepareImportRequest(song))
        val world = World().apply { tracks = listOf(song) }
        val request = assertNotNull(world.repository().prepareImportRequest(song))
        val restored = assertNotNull(LyricsImportRequest.restore(request.savedState))
        assertEquals(song.title, restored.title)
        assertEquals(song.artist, restored.artist)
        assertEquals(request.savedState, restored.savedState)
        world.ready = false
        assertNull(world.repository().prepareImportRequest(song))
    }

    @Test fun malformedUnverifiedOrTextBearingSavedStatesCannotBecomePickerTargets() {
        for (binding in listOf(LyricsBinding.Observed, LyricsBinding.Unassigned)) {
            val record = ImportedLyricsRecord.bound(LyricsSelection(song), "").copy(binding = binding)
            assertNull(LyricsImportRequest.restore(ImportedLyricsCodec.encode(record)))
        }
        val record = ImportedLyricsRecord.bound(LyricsSelection(song, true), "Unreviewed document")
        assertNull(LyricsImportRequest.restore(ImportedLyricsCodec.encode(record)))
        assertNull(LyricsImportRequest.restore("broken"))
    }

    @Test fun restoredRequestWaitsForScanThenKeepsOriginalTargetEvenIfPlaybackMovedOn() = runTest {
        val world = World().apply { tracks = listOf(song, other) }
        val request = assertNotNull(world.repository().prepareImportRequest(song))
        world.ready = false
        val recreated = world.repository()
        val restored = assertNotNull(LyricsImportRequest.restore(request.savedState))
        assertFalse(recreated.isImportRequestAvailable(restored))
        assertEquals(LyricsImportReview.Rejected(LyricsImportResult.Unavailable), recreated.previewImport(restored, "Words"))
        assertTrue(world.store.snapshot()!!.isEmpty())
        world.ready = true
        world.generation++
        assertTrue(recreated.isImportRequestAvailable(restored))
        val preview = assertIs<LyricsImportReview.Ready>(recreated.previewImport(restored, "Words")).preview
        assertEquals(song.id, preview.track.id)
        assertEquals(LyricsImportResult.Saved, recreated.confirmImport(preview))
        assertIs<LyricsResult.Found>(recreated.load(song))
        assertEquals(LyricsResult.NotFound, recreated.load(other))
    }

    @Test fun reusedUriAfterPickerLaunchNeverReceivesTheOriginalRequest() = runTest {
        val world = World().apply { tracks = listOf(song) }
        val repository = world.repository()
        val request = assertNotNull(repository.prepareImportRequest(song))
        world.tracks = listOf(other.copy(uri = song.uri))
        val before = world.store.snapshot()
        assertEquals(LyricsImportReview.Rejected(LyricsImportResult.Unavailable), repository.previewImport(request, "Words"))
        assertEquals(before, world.store.snapshot())
    }

    @Test fun scanGenerationOrExistingTextChangeRequiresAnotherExplicitPreview() = runTest {
        val world = World().apply { tracks = listOf(song) }
        val repository = world.repository()
        val request = assertNotNull(repository.prepareImportRequest(song))
        val first = assertIs<LyricsImportReview.Ready>(repository.previewImport(request, "Selected words")).preview
        world.generation++
        assertEquals(LyricsImportResult.Changed, repository.confirmImport(first))
        val second = assertIs<LyricsImportReview.Ready>(repository.previewImport(request, "Selected words")).preview
        assertTrue(repository.import(song, "Newer import"))
        assertEquals(LyricsImportResult.Changed, repository.confirmImport(second))
        assertEquals("Newer import", assertIs<LyricsResult.Found>(repository.load(song)).lyrics.lines.single().text)
        val third = assertIs<LyricsImportReview.Ready>(repository.previewImport(request, "Selected words")).preview
        assertTrue(third.replacesExisting)
        assertEquals(LyricsImportResult.Saved, repository.confirmImport(third))
    }

    @Test fun previewAndCancellationLeaveExistingTextAndSelectedDocumentUnchanged() = runTest {
        val world = World().apply { tracks = listOf(song) }
        val repository = world.repository()
        repository.import(song, "Previous")
        val before = world.store.snapshot()
        val request = assertNotNull(repository.prepareImportRequest(song))
        val text = "[00:01.000]Selected 😀\n[00:02.000]Words"
        val preview = assertIs<LyricsImportReview.Ready>(repository.previewImport(request, text)).preview
        assertEquals(text, preview.text)
        assertTrue(preview.replacesExisting)
        // Closing the picker/preview never calls confirmImport; nothing has been persisted.
        assertEquals(before, world.store.snapshot())
    }

    @Test fun invalidTextAndStorageReadFailureAreTypedWithoutDiscardingTheRequest() = runTest {
        val world = World().apply { tracks = listOf(song) }
        val repository = world.repository()
        val request = assertNotNull(repository.prepareImportRequest(song))
        assertEquals(LyricsImportReview.Rejected(LyricsImportResult.Invalid), repository.previewImport(request, "[ar:Metadata only]"))
        assertIs<LyricsImportReview.Ready>(repository.previewImport(request, "Valid words"))
        val broken = object : KeyValueStore by InMemoryKeyValueStore() {
            override fun getString(key: String): String? = error("Injected read failure")
        }
        val failed = World(broken).apply { tracks = listOf(song) }.repository()
        assertEquals(LyricsImportReview.Rejected(LyricsImportResult.Failed), failed.previewImport(request, "Valid words"))
    }

    @Test fun failedConfirmationRetainsOriginalAndCanBeRetriedAfterAnotherPreview() = runTest {
        val backing = InMemoryKeyValueStore()
        var fail = false
        val store = object : KeyValueStore by backing {
            override fun replace(removing: Set<String>, writing: Map<String, String>) {
                if (fail) error("Injected write failure")
                backing.replace(removing, writing)
            }
        }
        val repository = World(store).apply { tracks = listOf(song) }.repository()
        repository.import(song, "Original")
        val request = assertNotNull(repository.prepareImportRequest(song))
        val preview = assertIs<LyricsImportReview.Ready>(repository.previewImport(request, "Selected")).preview
        val before = backing.snapshot()
        fail = true
        assertEquals(LyricsImportResult.Failed, repository.confirmImport(preview))
        assertEquals(before, backing.snapshot())
        fail = false
        val refreshed = assertIs<LyricsImportReview.Ready>(repository.previewImport(request, "Selected")).preview
        assertEquals(LyricsImportResult.Saved, repository.confirmImport(refreshed))
    }

    @Test fun confirmedImportRetainsAnUnassignedTargetCopyAndTheExactSelectedText() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString(ImportedLyricsCodec.trackKey(song.id)!!, "damaged retained original")
        val repository = World(store).apply { tracks = listOf(song) }.repository()
        val request = assertNotNull(repository.prepareImportRequest(song))
        val text = "Selected 😀\nWords"
        val preview = assertIs<LyricsImportReview.Ready>(repository.previewImport(request, text)).preview
        assertEquals(LyricsImportResult.Saved, repository.confirmImport(preview))
        val retained = repository.recoverableImports().single()
        assertFalse(retained.canLink)
        assertEquals("damaged retained original", retained.storedValue)
        assertEquals(text, ImportedLyricsCodec.decode(store.getString(ImportedLyricsCodec.trackKey(song.id)!!)!!)!!.text)
    }
}
