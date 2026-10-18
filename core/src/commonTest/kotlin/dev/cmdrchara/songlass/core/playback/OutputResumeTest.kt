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
}
