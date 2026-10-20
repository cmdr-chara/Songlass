package dev.cmdrchara.songlass.designsystem.foundation

import dev.cmdrchara.songlass.designsystem.Artwork
import kotlin.test.Test
import kotlin.test.assertEquals

class PrefetchOrderTest {
    @Test
    fun scrollingDownLoadsTheNextRowsFirstThenAFewAbove() {
        assertEquals(listOf(11, 12, 13, 9, 8), prefetchOrder(10, 10, 100, ahead = 3, behind = 2, forward = true))
    }

    @Test
    fun scrollingUpLoadsTheRowsAboveFirst() {
        assertEquals(listOf(9, 8, 7, 11, 12), prefetchOrder(10, 10, 100, ahead = 3, behind = 2, forward = false))
    }

    @Test
    fun staysInsideTheList() {
        assertEquals(listOf(4), prefetchOrder(0, 3, 5, ahead = 3, behind = 2, forward = true))
        assertEquals(emptyList(), prefetchOrder(0, 4, 5, ahead = 3, behind = 2, forward = true))
        assertEquals(emptyList(), prefetchOrder(0, 0, 0, ahead = 3, behind = 2, forward = true))
    }

    @Test
    fun windowGrowsWithScrollSpeedWithinBounds() {
        assertEquals(10, prefetchAhead(0f))
        assertEquals(30, prefetchAhead(40f))
        assertEquals(60, prefetchAhead(1000f))
        assertEquals(15, prefetchAhead(20f))

    }

    @Test
    fun albumStandInsComeBeforeTheRowsOwnCovers() {
        val rows = listOf(
            Artwork(emptyList(), imageKey = "t2", fallbackImageKey = "a1"),
            Artwork(emptyList(), imageKey = "t3", fallbackImageKey = "a1"),
            Artwork(emptyList(), imageKey = "b1"),
            Artwork(emptyList(), imageKey = "c2", fallbackImageKey = "c1"),
        )
        assertEquals(listOf("a1", "c1", "t2", "t3", "b1", "c2"), prefetchKeys(rows))
    }
}
