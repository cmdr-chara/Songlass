package dev.cmdrchara.songlass.core.player

import kotlin.test.*

class TransportFaderTest {
    @Test fun pauseRampsBeforeExactlyOneNativePause() {
        val fade = TransportFader()
        assertEquals(TransportFadeStep(1f), fade.begin(TransportAction.Pause, 400, true, 100))
        assertTrue(fade.blocksAdvance)
        assertEquals(TransportFadeStep(.5f), fade.tick(300))
        assertEquals(TransportFadeStep(0f, TransportAction.Pause), fade.tick(500))
        assertEquals(TransportFadeStep(0f), fade.tick(900))
        assertFalse(fade.isRunning)
    }

    @Test fun resumeCancelsPauseAndReversesFromTheAppliedGain() {
        val fade = TransportFader()
        fade.begin(TransportAction.Pause, 400, true, 0)
        fade.tick(200)
        assertEquals(.5f, fade.resume(400, 200).gain)
        assertEquals(.75f, fade.tick(400).gain)
        assertEquals(TransportFadeStep(1f), fade.tick(600))
        assertNull(fade.pendingAction)
    }

    @Test fun resumeAfterAPausedSeekStartsFromSilence() {
        val fade = TransportFader()
        fade.begin(TransportAction.Seek(100), 300, false, 0)
        assertEquals(0f, fade.resume(400, 0, fromSilence = true).gain)
        assertEquals(.5f, fade.tick(200).gain)
        assertEquals(1f, fade.tick(400).gain)
    }

    @Test fun stopReplacesPendingSeekWithoutAnObsoleteSeek() {
        val fade = TransportFader()
        fade.begin(TransportAction.Seek(4_000), 1_000, true, 0)
        fade.tick(250)
        fade.begin(TransportAction.Stop, 200, true, 250)
        assertEquals(.25f, fade.tick(350).gain)
        assertEquals(TransportAction.Stop, fade.tick(450).action)
        assertNull(fade.tick(1_000).action)
    }

    @Test fun seekUsesTwoHalvesAndExcludesBuffering() {
        val fade = TransportFader()
        fade.begin(TransportAction.Seek(5_000), 501, true, 0)
        assertEquals(TransportFadeStep(0f, TransportAction.Seek(5_000)), fade.tick(250))
        assertTrue(fade.awaitingSeek)
        assertEquals(0f, fade.tick(2_000).gain)
        assertNull(fade.tick(2_100).action)
        fade.seekReady(3_000)
        assertEquals(0f, fade.gain)
        assertEquals(1f, fade.tick(3_251).gain)
        assertFalse(fade.isRunning)
    }

    @Test fun oneMillisecondSeekHasAnImmediateSilentSeekAndOneMillisecondRecovery() {
        val fade = TransportFader()
        assertEquals(TransportFadeStep(0f, TransportAction.Seek(123)), fade.begin(TransportAction.Seek(123), 1, true, 0))
        fade.seekReady(100)
        assertEquals(1f, fade.tick(101).gain)
    }

    @Test fun aSecondSeekReplacesTheFirstAndStartsAtCurrentGain() {
        val fade = TransportFader()
        fade.begin(TransportAction.Seek(100), 400, true, 0)
        fade.tick(100)
        assertEquals(.5f, fade.begin(TransportAction.Seek(200), 400, true, 100).gain)
        assertEquals(TransportAction.Seek(200), fade.tick(300).action)
        assertNull(fade.tick(600).action)
    }

    @Test fun stopOrPauseWhileSilentOrBufferingIsImmediate() {
        for (action in listOf(TransportAction.Pause, TransportAction.Stop, TransportAction.Seek(50))) {
            val fade = TransportFader()
            assertEquals(TransportFadeStep(1f, action), fade.begin(action, 3_000, false, 0))
            assertFalse(fade.isRunning)
        }
    }

    @Test fun zeroKeepsImmediateTransport() {
        for (action in listOf(TransportAction.Pause, TransportAction.Stop, TransportAction.Seek(50))) {
            val fade = TransportFader()
            assertEquals(TransportFadeStep(1f, action), fade.begin(action, 0, true, 0))
            assertFalse(fade.blocksAdvance)
        }
    }

    @Test fun interruptionOrLoadCancelsEveryPhaseAndNeverIssuesAnActionOrPlay() {
        for (phase in 0..2) {
            val fade = TransportFader()
            fade.begin(TransportAction.Seek(123), 400, true, 0)
            if (phase > 0) fade.tick(200)
            if (phase > 1) fade.seekReady(300)
            val revision = fade.revision
            assertEquals(TransportFadeStep(1f), fade.cancel())
            assertTrue(fade.revision > revision)
            assertFalse(fade.isRunning)
            assertEquals(TransportFadeStep(1f), fade.tick(10_000))
        }
    }

    @Test fun repeatedPauseDoesNotExtendTheDeadline() {
        val fade = TransportFader()
        fade.begin(TransportAction.Pause, 400, true, 0)
        fade.tick(200)
        fade.begin(TransportAction.Pause, 3_000, true, 200)
        assertEquals(TransportAction.Pause, fade.tick(400).action)
    }

    @Test fun prematureEndFinishesOnlyThePendingUserAction() {
        val fade = TransportFader()
        fade.begin(TransportAction.Stop, 3_000, true, 0)
        assertEquals(TransportFadeStep(1f, TransportAction.Stop), fade.finishNow())
        assertNull(fade.tick(3_000).action)
        assertFalse(fade.isRunning)
    }

    @Test fun missingReadyAndClockRegressionRestoreGainWithoutPlayback() {
        val fade = TransportFader()
        fade.begin(TransportAction.Seek(50), 100, true, 100)
        fade.tick(150)
        assertEquals(TransportFadeStep(1f), fade.tick(150 + TransportFader.SEEK_TIMEOUT_MS))
        fade.begin(TransportAction.Stop, 300, true, 500)
        assertEquals(TransportFadeStep(1f), fade.tick(499))
        assertNull(fade.pendingAction)
    }

    @Test fun invalidDurationsAndNegativeSeekAreRejected() {
        for (duration in listOf(-1L, 3_001L, Long.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { TransportFadeSettings(pauseMs = duration) }
            assertFailsWith<IllegalArgumentException> { TransportFader().begin(TransportAction.Stop, duration, true, 0) }
        }
        assertFailsWith<IllegalArgumentException> { TransportFader().begin(TransportAction.Seek(-1), 1, true, 0) }
    }
}
