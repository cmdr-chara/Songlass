package dev.cmdrchara.songlass.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecorativeFrameCadenceTest {
    @Test
    fun truncatedDisplayTimestampsKeepThirtyUpdatesOnCommonRefreshRates() {
        // Deliberately truncate each vsync interval: without tolerance, 60 and 120 Hz
        // miss the target boundary and degrade to 20 and 24 updates per second.
        for (refreshRate in listOf(60, 90, 120)) {
            val cadence = DecorativeFrameCadence(0L)
            val interval = 1_000_000_000L / refreshRate
            val elapsed = (1..refreshRate * 10).mapNotNull { cadence.elapsedSeconds(it * interval) }
            assertEquals(300, elapsed.size, "$refreshRate Hz")
            assertEquals(10f, elapsed.sum(), absoluteTolerance = 0.0001f, message = "$refreshRate Hz")
        }
    }

    @Test
    fun intermediateDisplayFramesDoNotInvalidateTheMaterial() {
        val cadence = DecorativeFrameCadence(0L)
        assertNull(cadence.elapsedSeconds(8_333_333L))
        assertNull(cadence.elapsedSeconds(16_666_666L))
        assertNull(cadence.elapsedSeconds(24_999_999L))
        assertEquals(0.033333332f, cadence.elapsedSeconds(33_333_332L))
        assertNull(cadence.elapsedSeconds(41_666_665L))
    }

    @Test
    fun toleranceIsOnlyOneMicrosecondAndDoesNotAdmitAnEarlyFrame() {
        val cadence = DecorativeFrameCadence(0L)
        assertNull(cadence.elapsedSeconds(33_332_332L))
        assertTrue(requireNotNull(cadence.elapsedSeconds(33_332_333L)) > 0f)
    }

    @Test
    fun resumedClockStartsAtItsNewAnchorInsteadOfIntegratingPausedTime() {
        val resumed = DecorativeFrameCadence(60_000_000_000L)
        assertNull(resumed.elapsedSeconds(60_016_666_666L))
        assertEquals(0.033333332f, resumed.elapsedSeconds(60_033_333_332L))
    }

    @Test
    fun LongStallClampsMotionAndNeverQueuesCatchUpFrames() {
        val cadence = DecorativeFrameCadence(0L)
        assertEquals(0.1f, cadence.elapsedSeconds(10_000_000_000L))
        assertNull(cadence.elapsedSeconds(10_016_666_666L))
        assertEquals(0.033333332f, cadence.elapsedSeconds(10_033_333_332L))
    }

    @Test
    fun clockResetReanchorsWithoutNegativeElapsedTime() {
        val cadence = DecorativeFrameCadence(1_000_000_000L)
        assertNull(cadence.elapsedSeconds(0L))
        assertEquals(0.033333332f, cadence.elapsedSeconds(33_333_332L))
    }
}
