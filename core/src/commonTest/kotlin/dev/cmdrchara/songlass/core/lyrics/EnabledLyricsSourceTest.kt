package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnabledLyricsSourceTest {
    private val song = track("song")
    private val words = Lyrics(listOf(LyricsLine(0, "Hi", listOf(LyricsWord(0, 2, 0, 100)))), LyricsOrigin.Amll)
    private val fallback = Lyrics(listOf(LyricsLine(0, "Fallback")), LyricsOrigin.LrcLib)

    @Test
    fun togglesTakeEffectWithoutRestartAndCannotReuseTheOtherProviderCache() = runTest {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        var amllCalls = 0
        var fallbackCalls = 0
        val source = PreferWordTimedLyricsSource(
            EnabledLyricsSource(LyricsSource { amllCalls++; words }, settings.state) { it.amllEnabled },
            LyricsSource { fallbackCalls++; fallback },
        )
        val repository = LyricsRepository(remote = source, cacheVersion = { if (settings.state.value.amllEnabled) 1 else 0 })
        repeat(2) { assertEquals(LyricsResult.Found(words), repository.load(song)) }
        assertEquals(1, amllCalls)
        assertEquals(0, fallbackCalls)
        settings.setAmllEnabled(false)
        repeat(2) { assertEquals(LyricsResult.Found(fallback), repository.load(song)) }
        assertEquals(1, amllCalls)
        assertEquals(1, fallbackCalls)
        settings.setAmllEnabled(true)
        assertEquals(LyricsResult.Found(words), repository.load(song))
        assertEquals(2, amllCalls)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun persistedOffSkipsAmllAndLocalImportsKeepPriorityInEitherMode() = runTest {
        val store = InMemoryKeyValueStore()
        AppSettings(store).setAmllEnabled(false)
        val settings = AppSettings(store)
        var fallbackCalls = 0
        val source = PreferWordTimedLyricsSource(
            EnabledLyricsSource(LyricsSource { error("Disabled provider called") }, settings.state) { it.amllEnabled },
            LyricsSource { fallbackCalls++; fallback },
        )
        val repository = LyricsRepository(remote = source, store = store,
            cacheVersion = { if (settings.state.value.amllEnabled) 1 else 0 })
        assertEquals(LyricsResult.Found(fallback), repository.load(song))
        assertTrue(repository.import(song, "Imported words"))
        for (enabled in listOf(false, true)) {
            settings.setAmllEnabled(enabled)
            val result = repository.load(song) as LyricsResult.Found
            assertEquals(LyricsOrigin.File, result.lyrics.source)
        }
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun disablingCancelsPendingRequestAndFallsBackEvenIfProviderSwallowsCancellation() = runTest {
        val settings = AppSettings(InMemoryKeyValueStore())
        val entered = CompletableDeferred<Unit>()
        var cancelled = false
        val source = PreferWordTimedLyricsSource(
            EnabledLyricsSource(LyricsSource {
                entered.complete(Unit)
                try { awaitCancellation() } catch (_: CancellationException) { cancelled = true }
                words
            }, settings.state) { it.amllEnabled },
            LyricsSource { fallback },
        )
        val result = async { source.load(song) }
        entered.await()
        settings.setAmllEnabled(false)
        assertEquals(fallback, result.await())
        assertTrue(cancelled)
    }

    @Test
    fun cancellingCallerDoesNotStartFallback() = runTest {
        val settings = AppSettings(InMemoryKeyValueStore())
        val entered = CompletableDeferred<Unit>()
        var fallbackCalled = false
        val source = PreferWordTimedLyricsSource(
            EnabledLyricsSource(LyricsSource { entered.complete(Unit); awaitCancellation() }, settings.state) { it.amllEnabled },
            LyricsSource { fallbackCalled = true; fallback },
        )
        val job = async { source.load(song) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(fallbackCalled)
    }

    @Test
    fun switchingWhileLoadingCannotCacheAnOldResultUnderTheNewConfiguration() = runTest {
        var version = 0
        var calls = 0
        val repository = LyricsRepository(remote = LyricsSource {
            calls++
            if (version == 0) { version = 1; words } else fallback
        }, cacheVersion = { version })
        repeat(2) { assertEquals(LyricsResult.Found(fallback), repository.load(song)) }
        assertEquals(2, calls)
    }
}
