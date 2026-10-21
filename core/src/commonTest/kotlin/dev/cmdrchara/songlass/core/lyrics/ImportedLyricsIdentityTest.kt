package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.library.TrackIdentityRegistry
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportedLyricsIdentityTest {
    private fun raw(id: String = "7", name: String = "one.flac", title: String = "One", date: Long = 1_000) =
        Track(id, title, "content://media/$id", artist = "Artist", album = "Album", durationMs = 90_000,
            folder = "Music", fileName = name, dateAddedMs = date)

    private class World {
        val store = InMemoryKeyValueStore()
        val registry = TrackIdentityRegistry(store)
        var tracks = emptyList<Track>()
        var ready = true
        var generation = 0L
        val repository = LyricsRepository(store = store, resolveSelection = { requested ->
            tracks.singleOrNull { it.id == requested.id }?.takeIf { ready }
                ?.let { LyricsSelection(it, canonicalId = true, accessGeneration = generation) }
        })
        suspend fun scan(vararg raw: Track): List<Track> {
            tracks = registry.canonicalize(raw.toList())
            repository.selectionChanged()
            return tracks
        }
    }

    @Test fun reusedMediaStoreIdAndUriNeverInheritAnotherFilesText() = runTest {
        val world = World()
        val original = world.scan(raw()).single()
        assertTrue(world.repository.import(original, "Original words"))
        val replacement = world.scan(raw(name = "other.flac", title = "Other", date = 2_000)).single()
        assertNotEquals(original.id, replacement.id)
        assertEquals(original.uri, replacement.uri)
        assertEquals(LyricsResult.NotFound, world.repository.load(replacement))
        assertEquals(LyricsUnavailable, world.repository.load(original))
        assertEquals("Original words", world.repository.recoverableImports().single().text)
    }

    @Test fun verifiedReindexAndTagEditRetainCanonicalImportWithoutWritingItsDocumentAgain() = runTest {
        val world = World()
        val original = world.scan(raw()).single()
        val text = "[00:01.12]Neon 😀\n[00:02.00]Skies"
        assertTrue(world.repository.import(original, text))
        val before = world.store.getString(ImportedLyricsCodec.trackKey(original.id)!!)
        val reindexed = world.scan(raw(id = "42", date = 3_000)).single()
        assertEquals(original.id, reindexed.id)
        assertNotEquals(original.uri, reindexed.uri)
        val edited = world.scan(raw(id = "42", title = "Corrected title", date = 3_000)).single()
        assertEquals(original.id, edited.id)
        assertEquals(LyricsParser.parse(text, LyricsOrigin.File), assertIs<LyricsResult.Found>(world.repository.load(edited)).lyrics)
        assertEquals(before, world.store.getString(ImportedLyricsCodec.trackKey(original.id)!!))
        assertTrue(world.repository.recoverableImports().isEmpty())
    }

    @Test fun legacyUriTextIsRetainedButNeverAutomaticallyLoadedEvenAfterRecreation() = runTest {
        val song = raw()
        val store = InMemoryKeyValueStore()
        val key = ImportedLyricsCodec.LEGACY_PREFIX + song.uri
        val text = "[00:01]Old import\n\n[00:02]More"
        store.putString(key, text)
        repeat(2) {
            val repository = LyricsRepository(store = store)
            assertEquals(LyricsResult.NotFound, repository.load(song))
            val recovery = repository.recoverableImports().single()
            assertEquals(text, recovery.text)
            assertTrue(recovery.canLink)
            assertEquals(text, store.getString(key))
        }
    }

    @Test fun explicitRelinkPreservesExactDocumentAndCancellationMakesNoStoreChange() = runTest {
        val world = World()
        val song = world.scan(raw()).single()
        val key = ImportedLyricsCodec.LEGACY_PREFIX + song.uri
        val text = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><p begin=\"1s\" end=\"2s\">Nuvole 😀</p></body></tt>"
        world.store.putString(key, text)
        val before = world.store.snapshot()
        val entry = world.repository.recoverableImports().single()
        val cancelled = assertNotNull(world.repository.previewRelink(song, entry))
        assertFalse(cancelled.replacesExisting)
        assertEquals(before, world.store.snapshot()) // Dismiss/back never calls relink.
        assertEquals(LyricsImportResult.Saved, world.repository.relink(cancelled))
        assertNull(world.store.getString(key))
        val saved = ImportedLyricsCodec.decode(world.store.getString(ImportedLyricsCodec.trackKey(song.id)!!)!!)!!
        assertEquals(text, saved.text)
        assertEquals(LyricsBinding.Canonical, saved.binding)
        assertEquals(LyricsParser.parse(text, LyricsOrigin.File), assertIs<LyricsResult.Found>(world.repository.load(song)).lyrics)
    }

    @Test fun relinkRequiresFreshConfirmationWhenTargetImportOrRecoveryTextChanges() = runTest {
        val song = raw()
        val store = InMemoryKeyValueStore()
        val repository = LyricsRepository(store = store)
        val key = ImportedLyricsCodec.LEGACY_PREFIX + song.uri
        store.putString(key, "Retained")
        repository.import(song, "Existing")
        val entry = repository.recoverableImports().single()
        val first = assertNotNull(repository.previewRelink(song, entry))
        assertTrue(first.replacesExisting)
        repository.import(song, "Newer")
        assertEquals(LyricsImportResult.Changed, repository.relink(first))
        assertEquals("Retained", store.getString(key))
        val second = assertNotNull(repository.previewRelink(song, entry))
        store.putString(key, "Edited retained text")
        assertEquals(LyricsImportResult.Changed, repository.relink(second))
        assertEquals("Newer", assertIs<LyricsResult.Found>(repository.load(song)).lyrics.lines.single().text)
        assertEquals("Edited retained text", store.getString(key))
    }

    @Test fun failedRelinkTransactionRetainsBothExistingAndRecoveryCopies() = runTest {
        val backing = InMemoryKeyValueStore()
        val song = raw()
        backing.putString(ImportedLyricsCodec.LEGACY_PREFIX + song.uri, "Retained")
        val store = object : KeyValueStore by backing {
            override fun replace(removing: Set<String>, writing: Map<String, String>) { error("Injected commit failure") }
        }
        val repository = LyricsRepository(store = store)
        val before = backing.snapshot()
        val preview = assertNotNull(repository.previewRelink(song, repository.recoverableImports().single()))
        assertEquals(LyricsImportResult.Failed, repository.relink(preview))
        assertEquals(before, backing.snapshot())
    }

    @Test fun libraryReplacementDuringConfirmationCannotAttachToRecycledUri() = runTest {
        val world = World()
        val song = world.scan(raw()).single()
        val key = ImportedLyricsCodec.LEGACY_PREFIX + song.uri
        world.store.putString(key, "Retained")
        val preview = assertNotNull(world.repository.previewRelink(song, world.repository.recoverableImports().single()))
        world.scan(raw(name = "replacement.flac", title = "Replacement", date = 3_000))
        val before = world.store.snapshot()
        assertEquals(LyricsImportResult.Unavailable, world.repository.relink(preview))
        assertEquals(before, world.store.snapshot())
        assertFalse(world.repository.import(song, "Stale picker result"))
    }

    @Test fun unavailableSelectionStartsNeitherLocalFileReadNorRemoteRequest() = runTest {
        var reads = 0
        val source = LyricsSource { reads++; null }
        val repository = LyricsRepository(source, source, resolveSelection = { null })
        assertEquals(LyricsUnavailable, repository.load(raw()))
        assertEquals(0, reads)
        assertEquals(LyricsImportResult.Unavailable, repository.importText(raw(), "Words"))
    }

    @Test fun replacementDuringLocalReadRejectsResultAndDoesNotStartRemoteFallback() = runTest {
        val original = raw()
        var selected: Track? = original
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var remoteCalls = 0
        val repository = LyricsRepository(
            local = LyricsSource { started.complete(Unit); finish.await(); null },
            remote = LyricsSource { remoteCalls++; LyricsParser.parse("Wrong words", LyricsOrigin.LrcLib) },
            resolveSelection = { requested -> selected?.takeIf { it.id == requested.id }?.let { LyricsSelection(it, true) } },
        )
        val loading = async { repository.load(original) }
        started.await()
        selected = original.copy(id = "canonical-replacement", title = "Replacement")
        repository.selectionChanged()
        finish.complete(Unit)
        assertEquals(LyricsUnavailable, loading.await())
        assertEquals(0, remoteCalls)
    }

    @Test fun accessGenerationChangeRejectsAnOnlineResultEvenWhenSameTrackReturns() = runTest {
        val song = raw()
        var generation = 1L
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val repository = LyricsRepository(remote = LyricsSource {
            calls++
            if (calls == 1) { started.complete(Unit); finish.await() }
            LyricsParser.parse("Fresh words", LyricsOrigin.LrcLib)
        }, resolveSelection = { LyricsSelection(song, true, generation) })
        val loading = async { repository.load(song) }
        started.await()
        generation++
        repository.selectionChanged()
        finish.complete(Unit)
        assertEquals(LyricsUnavailable, loading.await())
        assertIs<LyricsResult.Found>(repository.load(song))
        assertEquals(2, calls)
    }

    @Test fun newImportArchivesMalformedOrUnassignedTargetInsteadOfDestroyingIt() = runTest {
        val song = raw()
        val store = InMemoryKeyValueStore()
        val key = ImportedLyricsCodec.trackKey(song.id)!!
        store.putString(key, "SONGLASS-LYRICS-1\ntruncated private original")
        val repository = LyricsRepository(store = store)
        assertTrue(repository.import(song, "New words"))
        val recovery = repository.recoverableImports().single()
        assertFalse(recovery.canLink)
        assertEquals("SONGLASS-LYRICS-1\ntruncated private original", recovery.storedValue)
        assertEquals("New words", assertIs<LyricsResult.Found>(repository.load(song)).lyrics.lines.single().text)
    }

    @Test fun anImportCommittedDuringStoredDocumentReadWinsOverTheOlderSnapshot() = runTest {
        val song = raw()
        val backing = InMemoryKeyValueStore()
        var afterRead: (() -> Unit)? = null
        val store = object : KeyValueStore by backing {
            override fun getString(key: String): String? {
                val observed = backing.getString(key)
                val callback = afterRead
                afterRead = null
                callback?.invoke()
                return observed
            }
        }
        val repository = LyricsRepository(store = store)
        assertTrue(repository.import(song, "Old words"))
        // Deterministic interleaving: a new import commits after this load read its value,
        // but before that older document is parsed/published. No source lookup is involved.
        afterRead = { assertTrue(repository.import(song, "New words")) }
        assertEquals("New words", assertIs<LyricsResult.Found>(repository.load(song)).lyrics.lines.single().text)
    }

    @Test fun ordinaryLoadsNeverEnumerateOrWriteTheStoreAndRestoreLockRejectsSyncWrites() = runTest {
        val backing = InMemoryKeyValueStore()
        var snapshots = 0
        var writes = 0
        val store = object : KeyValueStore by backing {
            override fun snapshot(): Map<String, String> { snapshots++; return backing.snapshot() }
            override fun replace(removing: Set<String>, writing: Map<String, String>) { writes++; backing.replace(removing, writing) }
        }
        val repository = LyricsRepository(store = store)
        val song = raw()
        assertTrue(repository.import(song, "Words"))
        repeat(100) { assertIs<LyricsResult.Found>(repository.load(song)) }
        assertEquals(0, snapshots)
        assertEquals(1, writes)
        repository.withImportWritesPaused { assertFalse(repository.import(song, "Cannot race restore")) }
        assertEquals(1, writes)
    }
}
