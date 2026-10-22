package dev.cmdrchara.songlass.core.player

import kotlin.test.*

class AudioFocusPolicyTest {
    @Test fun allThreeDuckLevelsRestoreWithoutPausing() {
        for (mode in FocusDucking.entries.filter { it != FocusDucking.Pause }) {
            val policy = AudioFocusPolicy()
            policy.configure(AudioFocusSettings(ducking = mode), true)
            assertEquals(FocusDecision(gain = mode.volume), policy.changed(FocusChange.Duck, true))
            assertEquals(FocusDecision(), policy.changed(FocusChange.Gain, true))
        }
    }
    @Test fun pauseForDuckResumesExactlyOnce() {
        val policy = AudioFocusPolicy()
        policy.configure(AudioFocusSettings(ducking = FocusDucking.Pause), true)
        assertEquals(FocusPlaybackAction.Pause, policy.changed(FocusChange.Duck, true).action)
        assertEquals(FocusPlaybackAction.Resume, policy.changed(FocusChange.Gain, false).action)
        assertEquals(FocusPlaybackAction.None, policy.changed(FocusChange.Gain, false).action)
    }
    @Test fun transientLossNeverResumesAnAlreadyPausedTrack() {
        val policy = AudioFocusPolicy()
        policy.changed(FocusChange.TransientLoss, false)
        assertEquals(FocusPlaybackAction.None, policy.changed(FocusChange.Gain, false).action)
    }
    @Test fun permanentLossInvalidatesAnyTransientResume() {
        val policy = AudioFocusPolicy()
        policy.changed(FocusChange.TransientLoss, true)
        assertEquals(FocusPlaybackAction.Pause, policy.changed(FocusChange.PermanentLoss, false).action)
        assertEquals(FocusPlaybackAction.None, policy.changed(FocusChange.Gain, false).action)
    }
    @Test fun userPauseStopDisconnectOrReplacementInvalidateResume() {
        val policy = AudioFocusPolicy()
        repeat(4) {
            policy.changed(FocusChange.TransientLoss, true)
            policy.cancelResume()
            assertEquals(FocusPlaybackAction.None, policy.changed(FocusChange.Gain, false).action)
        }
    }
    @Test fun interruptedOverlapAndTransportCannotResume() {
        val policy = AudioFocusPolicy()
        policy.changed(FocusChange.TransientLoss, true, canResume = false)
        assertEquals(FocusPlaybackAction.None, policy.changed(FocusChange.Gain, false).action)
        policy.callChanged(true, true, canResume = false)
        assertEquals(FocusPlaybackAction.None, policy.callChanged(false, false).action)
    }
    @Test fun resumeOnFocusGainCanBeDisabled() {
        val policy = AudioFocusPolicy()
        policy.configure(AudioFocusSettings(resumeOnFocusGain = false), true)
        policy.changed(FocusChange.TransientLoss, true)
        assertEquals(FocusPlaybackAction.None, policy.changed(FocusChange.Gain, false).action)
    }
    @Test fun callAndFocusResumeAreIndependentInBothCallbackOrders() {
        for (callFirst in listOf(true, false)) for (resumeCall in listOf(true, false)) {
            val policy = AudioFocusPolicy()
            policy.configure(AudioFocusSettings(resumeOnFocusGain = !resumeCall, resumeAfterCall = resumeCall), true)
            if (callFirst) {
                assertEquals(FocusPlaybackAction.Pause, policy.callChanged(true, true).action)
                policy.changed(FocusChange.TransientLoss, false)
            } else {
                policy.changed(FocusChange.TransientLoss, true)
                policy.callChanged(true, false)
            }
            assertEquals(FocusPlaybackAction.None, policy.changed(FocusChange.Gain, false).action)
            assertEquals(if (resumeCall) FocusPlaybackAction.Resume else FocusPlaybackAction.None,
                policy.callChanged(false, false).action)
            assertEquals(FocusPlaybackAction.None, policy.callChanged(false, false).action)
        }
    }
    @Test fun callEndingBeforeFocusGainWaitsForFocus() {
        val policy = AudioFocusPolicy()
        policy.callChanged(true, true)
        policy.changed(FocusChange.TransientLoss, false)
        assertEquals(FocusPlaybackAction.None, policy.callChanged(false, false).action)
        assertEquals(FocusPlaybackAction.Resume, policy.changed(FocusChange.Gain, false).action)
    }
    @Test fun callWithoutFocusLossStillPausesAndResumes() {
        val policy = AudioFocusPolicy()
        assertEquals(FocusPlaybackAction.Pause, policy.callChanged(true, true).action)
        assertEquals(FocusPlaybackAction.Resume, policy.callChanged(false, false).action)
    }
    @Test fun callOnAlreadyPausedTrackDoesNotStartPlayback() {
        val policy = AudioFocusPolicy()
        policy.callChanged(true, false)
        assertEquals(FocusPlaybackAction.None, policy.callChanged(false, false).action)
    }
    @Test fun callDuringDuckRestoresGainAndUsesCallPolicy() {
        val policy = AudioFocusPolicy()
        policy.configure(AudioFocusSettings(resumeAfterCall = false), true)
        policy.changed(FocusChange.Duck, true)
        assertEquals(FocusDecision(FocusPlaybackAction.Pause, 1f), policy.callChanged(true, true))
        policy.changed(FocusChange.Gain, false)
        assertEquals(FocusPlaybackAction.None, policy.callChanged(false, false).action)
    }
    @Test fun disablingResumeDuringInterruptionCannotBeUndoneByEnablingLater() {
        val policy = AudioFocusPolicy()
        policy.changed(FocusChange.TransientLoss, true)
        policy.configure(AudioFocusSettings(resumeOnFocusGain = false), false)
        policy.configure(AudioFocusSettings(), false)
        assertEquals(FocusPlaybackAction.None, policy.changed(FocusChange.Gain, false).action)
        policy.callChanged(true, true)
        policy.configure(AudioFocusSettings(resumeAfterCall = false), false)
        policy.configure(AudioFocusSettings(), false)
        assertEquals(FocusPlaybackAction.None, policy.callChanged(false, false).action)
    }
    @Test fun switchingDuckLevelIsLiveAndPauseModePreservesInterruptionIntent() {
        val policy = AudioFocusPolicy()
        policy.changed(FocusChange.Duck, true)
        assertEquals(.5f, policy.configure(AudioFocusSettings(ducking = FocusDucking.Soft), true).gain)
        assertEquals(FocusPlaybackAction.Pause, policy.configure(AudioFocusSettings(ducking = FocusDucking.Pause), true).action)
        assertEquals(FocusPlaybackAction.Resume, policy.changed(FocusChange.Gain, false).action)
    }
    @Test fun explicitPlayDuringCallDoesNotScheduleALaterResume() {
        val policy = AudioFocusPolicy()
        policy.callChanged(true, true)
        assertFalse(policy.explicitPlay())
        assertEquals(FocusPlaybackAction.None, policy.callChanged(false, false).action)
        assertTrue(policy.explicitPlay())
    }
    @Test fun repeatedTransientEventsRetainOriginalCandidate() {
        val policy = AudioFocusPolicy()
        policy.changed(FocusChange.TransientLoss, true)
        policy.changed(FocusChange.TransientLoss, false)
        assertEquals(FocusPlaybackAction.Resume, policy.changed(FocusChange.Gain, false).action)
    }
}
