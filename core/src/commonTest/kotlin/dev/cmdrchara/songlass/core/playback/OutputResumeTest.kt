package dev.cmdrchara.songlass.core.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputResumeTest {
    private var now = 0L
    private val resume = OutputResume({ now }, windowMs = 1_000)

    @Test
    fun resumesOnceAfterADisconnectWithinTheWindow() {
        resume.onPausedByDisconnect()
        now = 900
        assertTrue(resume.onOutputConnected(enabled = true))
        assertFalse(resume.onOutputConnected(enabled = true))
    }

    @Test
    fun doesNothingWhenOffTooLateOrPausedByTheUser() {
        resume.onPausedByDisconnect()
        assertFalse(resume.onOutputConnected(enabled = false))
        resume.onPausedByDisconnect()
        now = 5_000
        assertFalse(resume.onOutputConnected(enabled = true))
        resume.onPausedByDisconnect()
        resume.cancel()
        assertFalse(resume.onOutputConnected(enabled = true))
        assertFalse(OutputResume({ 0 }).onOutputConnected(enabled = true))
    }

    @Test
    fun aBackwardsClockConsumesTheTokenWithoutResuming() {
        now = 5_000
        resume.onPausedByDisconnect()
        now = 4_000
        assertFalse(resume.onOutputConnected(enabled = true))
        now = 5_100
        assertFalse(resume.onOutputConnected(enabled = true))
    }

    @Test
    fun overflowingElapsedTimeCannotResume() {
        now = Long.MIN_VALUE
        resume.onPausedByDisconnect()
        now = Long.MAX_VALUE
        assertFalse(resume.onOutputConnected(enabled = true))
    }

    @Test
    fun cancellationWhileAlreadyPausedSurvivesLaterReconnects() {
        resume.onPausedByDisconnect()
        resume.cancel()
        now = 100
        assertFalse(resume.onOutputConnected(enabled = true))
        resume.onPausedByDisconnect()
        now = 200
        assertTrue(resume.onOutputConnected(enabled = true))
    }
}
