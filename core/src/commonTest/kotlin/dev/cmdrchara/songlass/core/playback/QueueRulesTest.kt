package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import dev.cmdrchara.songlass.core.queue.TemporaryPlacement
import dev.cmdrchara.songlass.core.queue.shuffledBy
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.QueueAddOrder
import dev.cmdrchara.songlass.core.settings.QueueEnd
import dev.cmdrchara.songlass.core.settings.QueueStart
import dev.cmdrchara.songlass.core.settings.ShuffleBias
import dev.cmdrchara.songlass.core.track
import dev.cmdrchara.songlass.core.tracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QueueRulesTest {
    private fun PlaybackController.ids() = queue.value.entries.map { it.track.id }
    private fun PlaybackController.current() = queue.value.current?.track?.id

    @Test
    fun defaultRulesKeepTheExistingBehaviour() = runTest(UnconfinedTestDispatcher()) {
        val controller = PlaybackController(FakeAudioPlayer(), backgroundScope)
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x"), track("y")))
        assertEquals(listOf("1", "x", "y", "2", "3"), controller.ids())
        controller.playNext(listOf(track("z")))
        assertEquals(listOf("1", "z", "x", "y", "2", "3"), controller.ids())
    }

    @Test
    fun additionsCanWaitForTheEndOfTheCollection() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, preferences = { AppPreferences(queueStart = QueueStart.AfterCollection) })
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x")))
        controller.addToQueue(listOf(track("y")))
        assertEquals(listOf("1", "2", "3", "x", "y"), controller.ids())
        val seen = mutableListOf<String?>(controller.current())
        repeat(3) { player.finishTrack(); seen += controller.current() }
        assertEquals(listOf<String?>("1", "2", "3", "x"), seen, "the additions follow the collection")
        player.finishTrack()
        assertEquals("y", controller.current())
    }

    @Test
    fun playNextAlwaysGoesRightAfterTheCurrentSongAndBringsTheWaitingAdditionsWithIt() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, preferences = { AppPreferences(queueStart = QueueStart.AfterCollection) })
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x"), track("y")))
        // The queue is one segment with one anchor, so "next" cannot leave the others at the end.
        controller.playNext(listOf(track("n")))
        assertEquals(listOf("1", "n", "x", "y", "2", "3"), controller.ids())
        repeat(3) { player.finishTrack() }
        assertEquals("y", controller.current())
    }

    @Test
    fun afterCollectionStaysAtTheEndWhenShuffleIsToggled() = runTest(UnconfinedTestDispatcher()) {
        val controller = PlaybackController(FakeAudioPlayer(), backgroundScope, random = Random(5),
            preferences = { AppPreferences(queueStart = QueueStart.AfterCollection) })
        controller.playTracks(tracks(6))
        controller.addToQueue(listOf(track("x")))
        controller.setShuffle(true)
        assertEquals("x", controller.ids().last())
        controller.setShuffle(false)
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "x"), controller.ids())
    }

    @Test
    fun immediatelyStartsTheAdditionsAndKeepsTheCollectionWaiting() = runTest(UnconfinedTestDispatcher()) {
        val controller = PlaybackController(FakeAudioPlayer(), backgroundScope, preferences = { AppPreferences(queueStart = QueueStart.Immediately) })
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x"), track("y")))
        assertEquals("x", controller.current())
        assertEquals(listOf("1", "x", "y", "2", "3"), controller.ids())
        // While the additions run, a further one just joins them.
        controller.addToQueue(listOf(track("z")))
        assertEquals("x", controller.current())
        assertEquals(listOf("1", "x", "y", "z", "2", "3"), controller.ids())
    }

    @Test
    fun anAdditionToAnEmptyQueueAlwaysPlays() = runTest(UnconfinedTestDispatcher()) {
        for (start in QueueStart.entries) {
            val controller = PlaybackController(FakeAudioPlayer(), backgroundScope, preferences = { AppPreferences(queueStart = start) })
            controller.addToQueue(listOf(track("x")))
            assertEquals("x", controller.current(), start.name)
        }
    }

    @Test
    fun replacingDropsOnlyTheAdditionsStillWaiting() = runTest(UnconfinedTestDispatcher()) {
        val controller = PlaybackController(FakeAudioPlayer(), backgroundScope, preferences = { AppPreferences(queueReplaces = true) })
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x")))
        controller.addToQueue(listOf(track("y")))
        assertEquals(listOf("1", "y", "2", "3"), controller.ids())
        controller.skipTo(controller.queue.value.entries[1].id)
        controller.addToQueue(listOf(track("z")))
        // The addition that is playing is not interrupted or removed.
        assertEquals("y", controller.current())
        assertEquals(listOf("1", "y", "z", "2", "3"), controller.ids())
    }

    @Test
    fun shuffledAdditionsJoinTheWaitingOnesInAnyOrderWithoutLosingAny() {
        val seen = mutableSetOf<List<String>>()
        for (seed in 0 until 40) {
            val queue = PlaybackQueue.of(tracks(2)).enqueueTemporary(listOf(track("a"), track("b"), track("c")), shuffleAdded = true, random = Random(seed))
            val order = queue.upNext.map { it.track.id }
            assertEquals(listOf("2"), order.filter { it == "2" })
            assertEquals(setOf("a", "b", "c"), order.filter { it != "2" }.toSet())
            assertEquals("2", order.last(), "the collection still follows the additions")
            seen += order
        }
        assertTrue(seen.size > 1, "different seeds give different orders")
    }

    @Test
    fun stoppingAtTheEndLeavesTheCollectionReadyButPaused() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, preferences = { AppPreferences(queueEnd = QueueEnd.Stop) })
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x")))
        player.finishTrack()
        assertEquals("x", controller.current())
        assertNull(player.nextTrack, "nothing is prepared to follow the last addition")
        player.finishTrack()
        assertEquals("2", controller.current())
        assertFalse(player.state.value.playWhenReady)
    }

    @Test
    fun byDefaultTheCollectionContinuesAfterTheAdditions() = runTest(UnconfinedTestDispatcher()) {
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope)
        controller.playTracks(tracks(3))
        controller.addToQueue(listOf(track("x")))
        player.finishTrack()
        assertEquals("2", player.nextTrack?.id)
        player.finishTrack()
        assertEquals("2", controller.current())
        assertTrue(player.state.value.playWhenReady)
    }

    @Test
    fun pickingASongEndsShuffleUnlessItIsKept() = runTest(UnconfinedTestDispatcher()) {
        val keep = PlaybackController(FakeAudioPlayer(), backgroundScope, random = Random(1),
            preferences = { AppPreferences(keepShuffleOnSelection = true) })
        keep.playTracks(tracks(10))
        keep.setShuffle(true)
        keep.playTracks(tracks(10), 4)
        assertTrue(keep.queue.value.isShuffled)
        assertEquals("5", keep.current())
        assertEquals("5", keep.ids().first())

        val plain = PlaybackController(FakeAudioPlayer(), backgroundScope, random = Random(1))
        plain.playTracks(tracks(10))
        plain.setShuffle(true)
        plain.playTracks(tracks(10), 4)
        assertFalse(plain.queue.value.isShuffled)
        // Keeping shuffle never turns it on by itself.
        val off = PlaybackController(FakeAudioPlayer(), backgroundScope, preferences = { AppPreferences(keepShuffleOnSelection = true) })
        off.playTracks(tracks(10), 4)
        assertFalse(off.queue.value.isShuffled)
    }

    @Test
    fun theLeastPlayedBiasFavoursUnplayedSongsButStaysRandom() {
        val heavy = (1..30).map { track("t$it") }
        val firsts = (0 until 300).map { seed ->
            heavy.shuffledBy(Random(seed)) { if (it.id == "t1") 1.0 else 1.0 / 1000 }.first().id
        }
        assertTrue(firsts.count { it == "t1" } > 270, "the unplayed song nearly always leads")
        assertTrue(firsts.any { it != "t1" }, "it is still a shuffle, not a sort")
        val plain = (0 until 300).map { seed -> heavy.shuffledBy(Random(seed), null).first().id }
        assertTrue(plain.count { it == "t1" } < 40, "without a bias nothing is preferred")
    }

    @Test
    fun shufflingWithTheBiasUsesTheHistoryAndKeepsEverySong() = runTest(UnconfinedTestDispatcher()) {
        val library = tracks(12)
        val controller = PlaybackController(FakeAudioPlayer(), backgroundScope, random = Random(3),
            preferences = { AppPreferences(shuffleBias = ShuffleBias.LeastPlayed) },
            playCountOf = { if (it.id == "7") 0 else 500 })
        var leads = 0
        repeat(40) {
            controller.shuffleTracks(library)
            assertEquals(library.map { it.id }.toSet(), controller.ids().toSet())
            if (controller.current() == "7") leads++
        }
        assertTrue(leads > 30, "song 7 was started in $leads of 40 shuffles")
    }

    @Test
    fun movingAroundTheCollectionReanchorsOnlyTheDefaultPlacement() {
        fun ids(q: PlaybackQueue) = q.entries.map { it.track.id }
        val afterCurrent = PlaybackQueue.of(tracks(4), startIndex = 2).enqueueTemporary(listOf(track("x")))
        assertEquals(listOf("1", "2", "3", "x", "4"), ids(afterCurrent))
        assertEquals(listOf("1", "x", "2", "3", "4"), ids(afterCurrent.withCurrentIndex(0)), "follows the song that is playing")
        val afterCollection = PlaybackQueue.of(tracks(4), startIndex = 2)
            .enqueueTemporary(listOf(track("x")), placement = TemporaryPlacement.AfterCollection)
        assertEquals(listOf("1", "2", "3", "4", "x"), ids(afterCollection))
        assertEquals(listOf("1", "2", "3", "4", "x"), ids(afterCollection.withCurrentIndex(0)), "stays after the whole collection")
        // Consuming the addition ends the placement: the next addition starts fresh.
        val consumed = afterCollection.withCurrentIndex(4).returnToSource()
        assertEquals(0, consumed.pendingTemporaryCount)
        assertEquals(listOf("1", "2", "3", "4"), ids(consumed))
    }
}
