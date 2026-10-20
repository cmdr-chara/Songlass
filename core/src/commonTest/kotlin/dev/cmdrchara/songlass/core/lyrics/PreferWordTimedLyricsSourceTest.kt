package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PreferWordTimedLyricsSourceTest {
    private val song = track("song")
    private val word = Lyrics(listOf(LyricsLine(0, "Hi", listOf(LyricsWord(0, 2, 0, 100)))) , LyricsOrigin.Amll)
    private val plain = Lyrics(listOf(LyricsLine(null, "Plain")), LyricsOrigin.LrcLib)

    @Test fun validWordsWinAndNothingIsCached() = runTest {
        var calls = 0
        val source = PreferWordTimedLyricsSource(LyricsSource { calls++; word }, LyricsSource { error("Unused") })
        repeat(2) { assertSame(word, source.load(song)) }
        assertEquals(2, calls)
    }

    @Test fun absentOrInvalidWordShapeFallsBackWithoutRestrictingFallback() = runTest {
        val invalid = listOf(null, plain, word.copy(instrumental = true),
            word.copy(lines = listOf(LyricsLine(0, "Hi"))),
            word.copy(lines = listOf(LyricsLine(null, "Hi", word.lines.single().words))))
        val fallbacks = listOf(plain, word.copy(lines = listOf(LyricsLine(0, "Line"))), plain.copy(instrumental = true))
        for (value in invalid) for (fallback in fallbacks) {
            assertSame(fallback, PreferWordTimedLyricsSource(LyricsSource { value }, LyricsSource { fallback }).load(song))
        }
        assertNull(PreferWordTimedLyricsSource(LyricsSource.Empty, LyricsSource.Empty).load(song))
    }

    @Test fun failuresAllowRecoveryButNeverBecomeAbsence() = runTest {
        val failure = IllegalStateException("Offline")
        assertSame(plain, PreferWordTimedLyricsSource(LyricsSource { throw failure }, LyricsSource { plain }).load(song))
        val first = PreferWordTimedLyricsSource(LyricsSource { throw failure }, LyricsSource.Empty)
        assertSame(failure, assertFailsWith<IllegalStateException> { first.load(song) })
        val second = PreferWordTimedLyricsSource(LyricsSource.Empty, LyricsSource { throw failure })
        assertSame(failure, assertFailsWith<IllegalStateException> { second.load(song) })
        assertFailsWith<IllegalStateException> {
            PreferWordTimedLyricsSource(LyricsSource { throw failure }, LyricsSource { throw failure }).load(song)
        }
    }

    @Test fun explicitCancellationPropagatesFromEitherSource() = runTest {
        var fallbackCalls = 0
        assertFailsWith<CancellationException> {
            PreferWordTimedLyricsSource(LyricsSource { throw CancellationException() },
                LyricsSource { fallbackCalls++; plain }).load(song)
        }
        assertEquals(0, fallbackCalls)
        assertFailsWith<CancellationException> {
            PreferWordTimedLyricsSource(LyricsSource.Empty, LyricsSource { throw CancellationException() }).load(song)
        }
    }

    @Test fun swallowedCancellationCannotReturnWordsOrStartFallback() = runTest {
        for (result in listOf(word, null)) {
            var fallbackCalls = 0
            val job = async {
                PreferWordTimedLyricsSource(LyricsSource {
                    currentCoroutineContext().cancel()
                    result
                }, LyricsSource { fallbackCalls++; plain }).load(song)
            }
            assertFailsWith<CancellationException> { job.await() }
            assertEquals(0, fallbackCalls)
        }
    }

    @Test fun swallowedFallbackCancellationCannotReturnAResult() = runTest {
        val job = async {
            PreferWordTimedLyricsSource(LyricsSource.Empty, LyricsSource {
                currentCoroutineContext().cancel()
                plain
            }).load(song)
        }
        assertFailsWith<CancellationException> { job.await() }
    }
}
