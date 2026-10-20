package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsRepositoryTest {
    private val song = track("song")
    private val embedded = Lyrics(listOf(LyricsLine(null, "Local words")), LyricsOrigin.Embedded)
    private val online = Lyrics(listOf(LyricsLine(0, "Online words")), LyricsOrigin.LrcLib)

    private class RecordingSource(private val block: suspend (Track) -> Lyrics?) : LyricsSource {
        val calls = mutableListOf<Track>()
        override suspend fun load(track: Track): Lyrics? {
            calls += track
            return block(track)
        }
    }

    @Test
    fun constructionDoesNoWorkAndLocalLyricsPreventOnlineLookup() = runTest {
        val local = RecordingSource { embedded }
        val remote = RecordingSource { online }
        val repository = LyricsRepository(local, remote)
        assertTrue(local.calls.isEmpty())
        assertTrue(remote.calls.isEmpty())
        assertEquals(LyricsResult.Found(embedded), repository.load(song))
        assertEquals(listOf(song), local.calls)
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun onlineLookupRunsOnlyAfterLocalAbsenceAndCachesSuccessfulResult() = runTest {
        val local = RecordingSource { null }
        val remote = RecordingSource { online }
        val repository = LyricsRepository(local, remote)
        repeat(2) { assertEquals(LyricsResult.Found(online), repository.load(song)) }
        assertEquals(1, local.calls.size)
        assertEquals(1, remote.calls.size)
        assertEquals(LyricsResult.Found(online), repository.load(song, refresh = true))
        assertEquals(2, remote.calls.size)
    }

    @Test
    fun absentResultsAreCachedButRefreshRetriesBothSources() = runTest {
        val local = RecordingSource { null }
        val remote = RecordingSource { null }
        val repository = LyricsRepository(local, remote)
        repeat(2) { assertEquals(LyricsResult.NotFound, repository.load(song)) }
        assertEquals(1, remote.calls.size)
        assertEquals(LyricsResult.NotFound, repository.load(song, refresh = true))
        assertEquals(2, local.calls.size)
        assertEquals(2, remote.calls.size)
    }

    @Test
    fun sourceFailuresAreNotCachedOrMisreportedAsAbsence() = runTest {
        val local = RecordingSource { error("Unreadable local metadata") }
        val remote = RecordingSource { null }
        val repository = LyricsRepository(local, remote)
        repeat(2) { assertEquals(LyricsResult.Failed, repository.load(song)) }
        assertEquals(2, local.calls.size)
        assertEquals(2, remote.calls.size)
        val recovered = LyricsRepository(local, RecordingSource { online })
        assertEquals(LyricsResult.Found(online), recovered.load(song))
        val unavailableOnline = LyricsRepository(remote = RecordingSource { error("Offline") })
        repeat(2) { assertEquals(LyricsResult.Failed, unavailableOnline.load(song)) }
    }

    @Test
    fun cancellationPropagatesAndDoesNotStartFallbackOrCacheFailure() = runTest {
        var cancel = true
        val local = RecordingSource {
            if (cancel) throw CancellationException("Track changed")
            null
        }
        val remote = RecordingSource { online }
        val repository = LyricsRepository(local, remote)
        assertFailsWith<CancellationException> { repository.load(song) }
        assertTrue(remote.calls.isEmpty())
        cancel = false
        assertEquals(LyricsResult.Found(online), repository.load(song))
        assertEquals(2, local.calls.size)
        val remoteCancellation = LyricsRepository(remote = RecordingSource { throw CancellationException() })
        assertFailsWith<CancellationException> { remoteCancellation.load(song) }
    }

    @Test
    fun importedFileWinsPersistsAndIsScopedByUriRatherThanReusedId() = runTest {
        val store = InMemoryKeyValueStore()
        val local = RecordingSource { embedded }
        val remote = RecordingSource { online }
        val repository = LyricsRepository(local, remote, store)
        assertTrue(repository.import(song, "[00:01]Imported words"))
        val expected = LyricsResult.Found(Lyrics(listOf(LyricsLine(1_000, "Imported words")), LyricsOrigin.File))
        assertEquals(expected, repository.load(song, refresh = true))
        assertTrue(local.calls.isEmpty())
        assertTrue(remote.calls.isEmpty())
        val restored = LyricsRepository(local, remote, store)
        assertEquals(expected, restored.load(song.copy(id = "reindexed")))
        assertEquals(LyricsResult.Found(embedded), restored.load(song.copy(uri = "file:///another.flac")))
    }

    @Test
    fun ttmlImportPersistsOriginalWordTimesAndInvalidXmlCannotReplaceIt() = runTest {
        val store = InMemoryKeyValueStore()
        val remote = RecordingSource { online }
        val repository = LyricsRepository(remote = remote, store = store)
        val document = """<tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="1s" end="3s"><span begin="0s" end="1s">Neon</span> <span begin="1s" end="2s">skies</span></p></body></tt>"""
        assertTrue(repository.import(song, document))
        val expected = LyricsResult.Found(LyricsParser.parse(document, LyricsOrigin.File)!!)
        assertEquals(expected, repository.load(song))
        assertFalse(repository.import(song, "<tt><body><p>Broken</body></tt>"))
        assertEquals(expected, repository.load(song, refresh = true))
        val restored = LyricsRepository(remote = remote, store = store)
        assertEquals(expected, restored.load(song.copy(id = "new-media-id")))
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun invalidImportPreservesPreviousLyricsAndReplacesCachedAbsence() = runTest {
        val repository = LyricsRepository()
        assertEquals(LyricsResult.NotFound, repository.load(song))
        assertFalse(repository.import(song, "[ar:Metadata only]"))
        assertTrue(repository.import(song, "Imported words"))
        assertFalse(repository.import(song, " "))
        val found = repository.load(song) as LyricsResult.Found
        assertEquals("Imported words", found.lyrics.lines.single().text)
    }

    @Test
    fun importDuringOnlineLookupCannotBeOverwrittenByItsLateResult() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val remote = RecordingSource {
            started.complete(Unit)
            finish.await()
            online
        }
        val repository = LyricsRepository(remote = remote)
        val lookup = async { repository.load(song) }
        started.await()
        assertTrue(repository.import(song, "New imported words"))
        finish.complete(Unit)
        val result = lookup.await() as LyricsResult.Found
        assertEquals(LyricsOrigin.File, result.lyrics.source)
        assertEquals("New imported words", result.lyrics.lines.single().text)
        assertEquals(result, repository.load(song))
    }

    @Test
    fun importDuringLocalLookupAvoidsUnnecessaryOnlineRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val local = RecordingSource {
            started.complete(Unit)
            finish.await()
            null
        }
        val remote = RecordingSource { online }
        val repository = LyricsRepository(local, remote)
        val lookup = async { repository.load(song) }
        started.await()
        assertTrue(repository.import(song, "Imported words"))
        finish.complete(Unit)
        val found = lookup.await() as LyricsResult.Found
        assertEquals(LyricsOrigin.File, found.lyrics.source)
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun cacheKeyIncludesMetadataAndEvictsLeastRecentlyUsedBeyondThirtyTwoSongs() = runTest {
        val remote = RecordingSource { online }
        val repository = LyricsRepository(remote = remote)
        val songs = (0..32).map { track(it.toString()) }
        songs.take(32).forEach { repository.load(it) }
        repository.load(songs[0]) // Protect this old entry; song 1 should be evicted instead.
        repository.load(songs[32])
        repository.load(songs[0])
        assertEquals(33, remote.calls.size)
        repository.load(songs[1])
        assertEquals(34, remote.calls.size)
        repository.load(songs[1].copy(title = "Corrected title"))
        assertEquals(35, remote.calls.size)
    }

    @Test
    fun storageFailuresAreReportedWithoutDiscardingAvailableLocalLyrics() = runTest {
        val broken = object : KeyValueStore {
            override fun getString(key: String): String? = error("Cannot read")
            override fun putString(key: String, value: String?) = error("Cannot write")
        }
        val repository = LyricsRepository(store = broken)
        assertEquals(LyricsResult.Failed, repository.load(song))
        assertFalse(repository.import(song, "Words"))
        val withLocal = LyricsRepository(local = RecordingSource { embedded }, store = broken)
        assertEquals(LyricsResult.Found(embedded), withLocal.load(song))
    }
}
