package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.QueueEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRenderingTest {
    private val entry = QueueEntry(1, Track("a", "A", "content://media/1"))
    private val playing = PlayerState(PlayerStatus.Ready, playWhenReady = true)

    @Test
    fun endedAndErrorOfferPlayEvenIfTheNativePlayIntentRemainsTrue() {
        assertTrue(WidgetPlaybackSnapshot.from(entry, playing, true).playing)
        for (status in listOf(PlayerStatus.Ended, PlayerStatus.Error, PlayerStatus.Idle)) {
            assertFalse(WidgetPlaybackSnapshot.from(entry, playing.copy(status = status), true).playing)
        }
        assertTrue(WidgetPlaybackSnapshot.from(entry, playing.copy(status = PlayerStatus.Buffering), true).playing)
        assertFalse(WidgetPlaybackSnapshot.from(entry, playing.copy(playWhenReady = false), true).playing)
    }

    @Test
    fun availabilityOnlyChangesInvalidateRenderingWithoutChangingTheDisplayedTrack() {
        val ready = WidgetPlaybackSnapshot.from(entry, playing, true)
        val revoked = WidgetPlaybackSnapshot.from(entry, playing, false)
        val missing = WidgetPlaybackSnapshot.from(entry.copy(isAvailable = false), playing, true)
        assertEquals(ready.track, revoked.track)
        assertFalse(revoked.canReadArtwork)
        assertEquals(revoked, missing)
        assertNotEquals(ready, revoked)
        assertEquals(ready, WidgetPlaybackSnapshot.from(entry, playing.copy(positionMs = 40_000), true), "ticks do not redraw")
    }

    @Test
    fun lateUncancellableRefreshCannotOverwriteNewObservedContent() = runTest {
        val a = CompletableDeferred<String>()
        val b = CompletableDeferred<String>()
        val decoded = mutableListOf<String>()
        val published = mutableListOf<Pair<String, String>>()
        val renderer = LatestWidgetRenderer<String, String>(backgroundScope, render = { content ->
            withContext(NonCancellable) {
                (if (content == "A") a else b).await().also { decoded += content }
            }
        }, publish = { content, cover -> published += content to cover })
        renderer.submit("A") // Manual widget refresh began before the next track.
        runCurrent()
        renderer.submit("B") // Observed playback uses the same lane.
        runCurrent()
        b.complete("cover B")
        runCurrent()
        assertEquals(listOf("B" to "cover B"), published)
        a.complete("cover A")
        runCurrent()
        assertEquals(listOf("B", "A"), decoded, "the old decoder really completed after the new one")
        assertEquals(listOf("B" to "cover B"), published)
    }

    @Test
    fun identicalManualRefreshStillRendersForNewWidgetOrConfiguration() = runTest {
        var renders = 0
        val published = mutableListOf<Int>()
        val renderer = LatestWidgetRenderer<String, Int>(backgroundScope,
            render = { ++renders }, publish = { _, rendered -> published += rendered })
        renderer.submit("A")
        runCurrent()
        renderer.submit("A")
        runCurrent()
        assertEquals(listOf(1, 2), published)
    }
}
