package dev.cmdrchara.songlass.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossfadeTest {
    private val fourMinutes = 240_000L

    @Test
    fun offAndTinyDurationsNeverOverlap() {
        assertNull(Crossfade.plan(0, fourMinutes, fourMinutes))
        assertNull(Crossfade.plan(-5_000, fourMinutes, fourMinutes))
        assertNull(Crossfade.plan(Crossfade.MIN_EFFECTIVE_MS - 1, fourMinutes, fourMinutes))
        assertNotNull(Crossfade.plan(Crossfade.MIN_EFFECTIVE_MS, fourMinutes, fourMinutes))
    }

    @Test
    fun overlapStartsBeforeTheEndAndEndsWithTheTrack() {
        val plan = Crossfade.plan(5_000, fourMinutes, fourMinutes)!!
        assertEquals(fourMinutes - 5_000, plan.startMs)
        assertEquals(5_000, plan.durationMs)
        assertEquals(fourMinutes, plan.endMs)
    }

    @Test
    fun configuredDurationIsClampedToTheMaximum() {
        val plan = Crossfade.plan(60_000, fourMinutes, fourMinutes)!!
        assertEquals(Crossfade.MAX_DURATION_MS, plan.durationMs)
        assertEquals(Crossfade.MAX_DURATION_MS, Crossfade.normalize(Long.MAX_VALUE))
        assertEquals(0, Crossfade.normalize(-1))
    }

    @Test
    fun unknownDurationsFallBackToGapless() {
        assertNull(Crossfade.plan(5_000, 0, fourMinutes))
        assertNull(Crossfade.plan(5_000, fourMinutes, 0))
        assertNull(Crossfade.plan(5_000, -1, -1))
    }

    @Test
    fun repeatOneLoopStaysGapless() {
        assertNull(Crossfade.plan(5_000, fourMinutes, fourMinutes, sameTrack = true))
    }

    @Test
    fun shortTracksNeverOverlapMoreThanHalfOfTheShorterOne() {
        // 8 s jingle into a long track with a 12 s setting: at most 4 s, starting at 4 s.
        val outgoingShort = Crossfade.plan(12_000, 8_000, fourMinutes)!!
        assertEquals(4_000, outgoingShort.durationMs)
        assertEquals(4_000, outgoingShort.startMs)
        // Long track into a 10 s one: bounded by the incoming track, so it cannot end during the overlap.
        val incomingShort = Crossfade.plan(12_000, fourMinutes, 10_000)!!
        assertEquals(5_000, incomingShort.durationMs)
        assertTrue(incomingShort.durationMs <= 10_000 / 2)
    }

    @Test
    fun tracksTooShortForAnyUsefulOverlapAreJoinedGaplessly() {
        // Half of a 400 ms clip is below the minimum.
        assertNull(Crossfade.plan(12_000, 400, fourMinutes))
        assertNull(Crossfade.plan(12_000, fourMinutes, 400))
    }

    @Test
    fun seekingIntoTheWindowShrinksTheOverlapToWhatIsLeft() {
        val plan = Crossfade.plan(8_000, fourMinutes, fourMinutes, positionMs = fourMinutes - 3_000)!!
        assertEquals(fourMinutes - 3_000, plan.startMs)
        assertEquals(3_000, plan.durationMs)
        // Too little left: no overlap, the track simply hands over.
        assertNull(Crossfade.plan(8_000, fourMinutes, fourMinutes, positionMs = fourMinutes - 100))
        assertNull(Crossfade.plan(8_000, fourMinutes, fourMinutes, positionMs = fourMinutes + 1_000))
    }

    @Test
    fun seekingBeforeTheWindowKeepsTheFullOverlap() {
        val plan = Crossfade.plan(8_000, fourMinutes, fourMinutes, positionMs = 10_000)!!
        assertEquals(8_000, plan.durationMs)
        assertEquals(fourMinutes - 8_000, plan.startMs)
    }

    @Test
    fun manualChangeStartsAtTheCurrentPositionInsteadOfWaitingForTheEnd() {
        val manual = Crossfade.manualPlan(1_250, fourMinutes, fourMinutes, 10_000)!!
        assertEquals(CrossfadePlan(10_000, 1_250), manual)
        assertEquals(fourMinutes - 1_250, Crossfade.plan(1_250, fourMinutes, fourMinutes, 10_000)!!.startMs)
    }

    @Test
    fun manualPlanUsesTheRemainingAudioAndTheShorterTrackLimit() {
        assertEquals(CrossfadePlan(fourMinutes - 800, 800),
            Crossfade.manualPlan(1_250, fourMinutes, fourMinutes, fourMinutes - 800))
        assertEquals(CrossfadePlan(1_000, 500), Crossfade.manualPlan(12_000, fourMinutes, 1_000, 1_000))
        assertNull(Crossfade.manualPlan(1_250, fourMinutes, fourMinutes, fourMinutes - 249))
        assertNull(Crossfade.manualPlan(1_250, fourMinutes, fourMinutes, Long.MAX_VALUE))
        assertEquals(0, Crossfade.manualPlan(1_250, fourMinutes, fourMinutes, Long.MIN_VALUE)!!.startMs)
    }

    @Test
    fun manualPlanKeepsTheExistingOffUnknownAndRepeatOneGuards() {
        assertNull(Crossfade.manualPlan(0, fourMinutes, fourMinutes))
        assertNull(Crossfade.manualPlan(249, fourMinutes, fourMinutes))
        assertNull(Crossfade.manualPlan(1_250, 0, fourMinutes))
        assertNull(Crossfade.manualPlan(1_250, fourMinutes, 0))
        assertNull(Crossfade.manualPlan(1_250, fourMinutes, fourMinutes, sameTrack = true))
        assertEquals(12_000, Crossfade.manualPlan(Long.MAX_VALUE, fourMinutes, fourMinutes)!!.durationMs)
    }

    @Test
    fun manualResumeUsesTheRemainingIncomingAudio() {
        val plan = Crossfade.manualPlan(12_000, fourMinutes, 30_000, 10_000, incomingPositionMs = 29_000)
        assertEquals(CrossfadePlan(10_000, 500), plan)
        assertNull(Crossfade.manualPlan(12_000, fourMinutes, 30_000, 10_000, incomingPositionMs = 29_900))
    }

    @Test
    fun aKnownBookmarkAtOrBeyondTheEndIsTooShortRatherThanUnknown() {
        for (position in listOf(29_900L, 30_000L, Long.MAX_VALUE)) {
            val decision = Crossfade.decide(CrossfadeKind.Manual, 12_000, fourMinutes, 30_000,
                positionMs = 10_000, incomingPositionMs = position)
            assertNull(decision.plan)
            assertEquals(CrossfadeSkipReason.TooShort, decision.skipReason)
        }
    }

    @Test
    fun negativeIncomingPositionsAreClampedAndUnknownDurationStaysUnknown() {
        assertEquals(
            Crossfade.manualPlan(1_250, fourMinutes, fourMinutes, 10_000),
            Crossfade.manualPlan(1_250, fourMinutes, fourMinutes, 10_000, incomingPositionMs = Long.MIN_VALUE),
        )
        val unknown = Crossfade.decide(CrossfadeKind.Manual, 1_250, fourMinutes, 0,
            incomingPositionMs = 1_500)
        assertEquals(CrossfadeSkipReason.UnknownDuration, unknown.skipReason)
    }

    @Test
    fun automaticJoinsStillStartTheirIncomingTrackAtZero() {
        val baseline = Crossfade.decide(CrossfadeKind.Automatic, 4_000, fourMinutes, 30_000)
        assertEquals(baseline, Crossfade.decide(CrossfadeKind.Automatic, 4_000, fourMinutes, 30_000,
            incomingPositionMs = 29_900))
    }

    @Test
    fun eligibilityExplainsWhyATransitionCannotRun() {
        fun reason(ms: Long = 1_250, out: Long = fourMinutes, incoming: Long = fourMinutes,
            same: Boolean = false, supported: Boolean = true, playing: Boolean = true) =
            Crossfade.decide(CrossfadeKind.Manual, ms, out, incoming,
                sameTrack = same, supported = supported, playing = playing).skipReason
        assertEquals(CrossfadeSkipReason.Disabled, reason(ms = 0))
        assertEquals(CrossfadeSkipReason.Unsupported, reason(supported = false))
        assertEquals(CrossfadeSkipReason.NotPlaying, reason(playing = false))
        assertEquals(CrossfadeSkipReason.SameTrack, reason(same = true))
        assertEquals(CrossfadeSkipReason.UnknownDuration, reason(out = 0))
        assertEquals(CrossfadeSkipReason.UnknownDuration, reason(incoming = 0))
        assertEquals(CrossfadeSkipReason.TooShort, reason(incoming = 400))
        assertNull(reason())
    }

    @Test
    fun automaticAndManualDecisionsKeepTheirOwnTiming() {
        val automatic = Crossfade.decide(CrossfadeKind.Automatic, 4_000, fourMinutes, fourMinutes, 20_000)
        val manual = Crossfade.decide(CrossfadeKind.Manual, 750, fourMinutes, fourMinutes, 20_000)
        assertEquals(CrossfadePlan(fourMinutes - 4_000, 4_000), automatic.plan)
        assertEquals(CrossfadePlan(20_000, 750), manual.plan)
        assertNull(automatic.skipReason)
        assertNull(manual.skipReason)
    }

    @Test
    fun progressFollowsThePlaybackPosition() {
        val plan = CrossfadePlan(startMs = 10_000, durationMs = 4_000)
        assertEquals(0f, plan.progressAt(0))
        assertEquals(0f, plan.progressAt(10_000))
        assertEquals(0.5f, plan.progressAt(12_000))
        assertEquals(1f, plan.progressAt(14_000))
        assertEquals(1f, plan.progressAt(99_000))
        assertTrue(!plan.hasStarted(9_999) && plan.hasStarted(10_000))
        assertTrue(!plan.isComplete(13_999) && plan.isComplete(14_000))
        assertEquals(2_500, plan.msUntilStart(7_500))
        assertEquals(0, plan.msUntilStart(11_000))
    }

    @Test
    fun gainsAreComplementaryAtEveryStepSoCorrelatedSignalsCannotClip() {
        for (step in 0..1_000) {
            val gains = Crossfade.gains(step / 1_000f)
            assertEquals(1f, gains.outgoing + gains.incoming, 1e-5f)
            assertTrue(gains.outgoing in 0f..1f && gains.incoming in 0f..1f)
            // Two identical full-scale samples through both gains never exceed full scale.
            assertTrue(gains.outgoing * 1f + gains.incoming * 1f <= 1f + 1e-5f)
        }
    }

    @Test
    fun gainsStartAndEndCleanlyAndAreMonotonic() {
        assertEquals(CrossfadeGains(1f, 0f), Crossfade.gains(0f))
        val end = Crossfade.gains(1f)
        assertEquals(0f, end.outgoing, 1e-6f)
        assertEquals(1f, end.incoming, 1e-6f)
        assertEquals(0.5f, Crossfade.gains(0.5f).outgoing, 1e-6f)
        var previous = Crossfade.gains(0f)
        for (step in 1..200) {
            val gains = Crossfade.gains(step / 200f)
            assertTrue(gains.incoming >= previous.incoming)
            assertTrue(gains.outgoing <= previous.outgoing)
            previous = gains
        }
        // Out-of-range progress is clamped instead of producing a louder or inverted signal.
        assertEquals(Crossfade.gains(0f), Crossfade.gains(-3f))
        assertEquals(Crossfade.gains(1f), Crossfade.gains(7f))
    }

    @Test
    fun gainsAreSmoothAtBothEnds() {
        // No audible corner when the overlap starts or stops: the first steps change very little.
        assertTrue(Crossfade.gains(0.02f).incoming < 0.005f)
        assertTrue(Crossfade.gains(0.98f).outgoing < 0.005f)
    }

    @Test
    fun seekWhileArmedReplansButDuringTheOverlapTheUserTakesControl() {
        assertEquals(CrossfadeResolution.Replan, Crossfade.resolve(CrossfadePhase.Armed, CrossfadeInterruption.Seek))
        assertEquals(CrossfadeResolution.Finish, Crossfade.resolve(CrossfadePhase.Fading, CrossfadeInterruption.Seek))
    }

    @Test
    fun manualLoadStopOrChangedQueueDisarmsButNeverLeavesTwoTracksPlaying() {
        for (interruption in listOf(CrossfadeInterruption.Load, CrossfadeInterruption.Stop, CrossfadeInterruption.NextChanged)) {
            assertEquals(CrossfadeResolution.Disarm, Crossfade.resolve(CrossfadePhase.Armed, interruption), "$interruption")
        }
        // A running overlap is ended at once on load or stop; a changed next track concerns the one after.
        assertEquals(CrossfadeResolution.Finish, Crossfade.resolve(CrossfadePhase.Fading, CrossfadeInterruption.Load))
        assertEquals(CrossfadeResolution.Finish, Crossfade.resolve(CrossfadePhase.Fading, CrossfadeInterruption.Stop))
        assertEquals(CrossfadeResolution.Keep, Crossfade.resolve(CrossfadePhase.Fading, CrossfadeInterruption.NextChanged))
    }

    @Test
    fun failedStandbyFallsBackToGaplessOnlyBeforeTheOverlap() {
        assertEquals(CrossfadeResolution.Disarm, Crossfade.resolve(CrossfadePhase.Armed, CrossfadeInterruption.StandbyFailed))
        assertEquals(CrossfadeResolution.Keep, Crossfade.resolve(CrossfadePhase.Fading, CrossfadeInterruption.StandbyFailed))
    }

    @Test
    fun outgoingTrackEndingEarlyHandsOverInBothPhases() {
        assertEquals(CrossfadeResolution.Finish, Crossfade.resolve(CrossfadePhase.Armed, CrossfadeInterruption.OutgoingEnded))
        assertEquals(CrossfadeResolution.Finish, Crossfade.resolve(CrossfadePhase.Fading, CrossfadeInterruption.OutgoingEnded))
    }

    @Test
    fun pausingAndResumingNeverChangeThePlan() {
        for (phase in CrossfadePhase.entries) {
            assertEquals(CrossfadeResolution.Keep, Crossfade.resolve(phase, CrossfadeInterruption.Pause))
            assertEquals(CrossfadeResolution.Keep, Crossfade.resolve(phase, CrossfadeInterruption.Resume))
        }
    }

    @Test
    fun everyInterruptionHasAResolutionInEveryPhase() {
        for (phase in CrossfadePhase.entries) {
            for (interruption in CrossfadeInterruption.entries) {
                Crossfade.resolve(phase, interruption)
            }
        }
    }

    @Test
    fun fakePlayerIgnoresCrossfadeSetting() {
        // iOS and demo players keep playing gaplessly: the default implementation does nothing.
        val player = FakeAudioPlayer()
        player.setCrossfade(8_000)
        player.setManualCrossfade(1_250)
        assertTrue(!player.supportsCrossfade)
        assertEquals(PlayerStatus.Idle, player.state.value.status)
    }
}
