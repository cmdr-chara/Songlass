package dev.cmdrchara.songlass.core.player

import kotlin.math.abs
import kotlin.test.*

class AudioEffectsTest {
    @Test fun neutralSettingsPassFramesExactly() {
        for (channels in listOf(1, 2, 6)) {
            val dsp = AudioEffectsDsp(48_000, channels)
            val frame = DoubleArray(channels) { (it - 2) * .123456789 }
            val before = frame.copyOf()
            dsp.processFrame(frame)
            assertContentEquals(before, frame)
            assertTrue(dsp.isBypassed)
        }
    }
    @Test fun balanceAttenuatesOnlyTheOppositeStereoChannel() {
        for ((pan, expected) in listOf(-100 to doubleArrayOf(.8, 0.0), 100 to doubleArrayOf(0.0, -.4), 50 to doubleArrayOf(.4, -.4))) {
            val frame = doubleArrayOf(.8, -.4)
            AudioEffectsDsp(48_000, 2, AudioEffectsProcessing(balancePercent = pan)).processFrame(frame)
            expected.zip(frame).forEach { (e, actual) -> assertEquals(e, actual, 1e-12) }
        }
    }
    @Test fun expansionPreservesSideAndAttenuatesCenterWithHeadroom() {
        val dsp = AudioEffectsDsp(48_000, 2, AudioEffectsProcessing(stereoExpandPercent = 100))
        val center = doubleArrayOf(1.0, 1.0); dsp.processFrame(center)
        assertContentEquals(doubleArrayOf(.5, .5), center)
        val sides = doubleArrayOf(1.0, -1.0); dsp.processFrame(sides)
        assertContentEquals(doubleArrayOf(1.0, -1.0), sides)
        val left = doubleArrayOf(1.0, 0.0); dsp.processFrame(left)
        assertContentEquals(doubleArrayOf(.75, -.25), left)
        for (l in -10..10) for (r in -10..10) {
            val frame = doubleArrayOf(l / 10.0, r / 10.0); dsp.processFrame(frame)
            assertTrue(frame.all { abs(it) <= 1.0 })
        }
    }
    @Test fun layoutPolicyDoesNotGuessStereoChannelsInSurround() {
        val settings = AudioEffectsSettings(100, 100, 200, 100)
        assertEquals(AudioEffectsProcessing(100, 100, 100), settings.pcmForChannels(2))
        assertEquals(AudioEffectsProcessing(reverbPercent = 100), settings.pcmForChannels(1))
        assertEquals(AudioEffectsProcessing(), settings.pcmForChannels(6))
        val frame = doubleArrayOf(.1, .2, .3, .4, .5, .6)
        AudioEffectsDsp(48_000, 6, settings.pcmForChannels(6)).processFrame(frame)
        assertContentEquals(doubleArrayOf(.1, .2, .3, .4, .5, .6), frame)
    }
    @Test fun balanceAndExpansionIgnoreMono() {
        val frame = doubleArrayOf(.73)
        val dsp = AudioEffectsDsp(48_000, 1, AudioEffectsSettings(100, 100).pcmForChannels(1))
        dsp.processFrame(frame)
        assertEquals(.73, frame.single())
    }
    @Test fun roomProducesATailAndItsEnergyDecays() {
        for (rate in listOf(8_000, 44_100, 48_000, 96_000)) {
            val dsp = AudioEffectsDsp(rate, 1, AudioEffectsProcessing(reverbPercent = 100))
            val frame = doubleArrayOf(1.0); dsp.processFrame(frame)
            assertEquals(.5, frame.single(), 1e-12)
            var first = 0.0; var last = 0.0
            for (i in 1..rate * 3) {
                frame[0] = 0.0; dsp.processFrame(frame)
                assertTrue(frame[0].isFinite() && abs(frame[0]) < 1.0)
                if (i < rate) first += frame[0] * frame[0]
                if (i > rate * 2) last += frame[0] * frame[0]
            }
            assertTrue(first > .00001, "No room tail at $rate")
            assertTrue(last < first * .00001, "Tail failed to decay at $rate: $first -> $last")
        }
    }
    @Test fun stereoRoomDiffusesChannelsIndependentlyAndFlushClearsTheTail() {
        val dsp = AudioEffectsDsp(48_000, 2, AudioEffectsProcessing(reverbPercent = 100))
        var different = false
        val frame = doubleArrayOf(1.0, 1.0); dsp.processFrame(frame)
        repeat(12_000) {
            frame.fill(0.0); dsp.processFrame(frame)
            if (frame[0] != frame[1]) different = true
        }
        assertTrue(different)
        dsp.reset()
        repeat(12_000) {
            frame.fill(0.0); dsp.processFrame(frame)
            assertContentEquals(doubleArrayOf(0.0, 0.0), frame)
        }
    }
    @Test fun balanceFollowsReverbSoAnExtremePanAlsoMutesTheRoom() {
        val dsp = AudioEffectsDsp(48_000, 2, AudioEffectsProcessing(100, 0, 100))
        val frame = doubleArrayOf(1.0, 1.0)
        repeat(10_000) { i ->
            frame.fill(if (i == 0) 1.0 else 0.0); dsp.processFrame(frame)
            assertEquals(0.0, frame[0], 1e-12)
        }
    }
    @Test fun liveGainChangesAreSmoothedAndSettleToExactNeutral() {
        val dsp = AudioEffectsDsp(48_000, 2)
        val frame = doubleArrayOf(1.0, 1.0)
        dsp.setParameters(AudioEffectsProcessing(balancePercent = 100)); dsp.processFrame(frame)
        assertTrue(frame[0] in .99..1.0)
        repeat(24_000) { frame.fill(1.0); dsp.processFrame(frame) }
        assertEquals(0.0, frame[0], 1e-12)
        dsp.setParameters(AudioEffectsProcessing())
        repeat(24_000) { frame.fill(.5); dsp.processFrame(frame) }
        assertTrue(dsp.isBypassed)
        assertContentEquals(doubleArrayOf(.5, .5), frame)
    }
    @Test fun turningRoomOffCannotResurrectItsPreviousTail() {
        val dsp = AudioEffectsDsp(48_000, 1, AudioEffectsProcessing(reverbPercent = 100))
        val frame = doubleArrayOf(1.0); dsp.processFrame(frame)
        dsp.setParameters(AudioEffectsProcessing())
        repeat(24_000) { frame.fill(0.0); dsp.processFrame(frame) }
        assertTrue(dsp.isBypassed)
        dsp.setParameters(AudioEffectsProcessing(reverbPercent = 100))
        repeat(24_000) { frame.fill(0.0); dsp.processFrame(frame); assertEquals(0.0, frame[0], 1e-12) }
    }
    @Test fun nativePcmNonFiniteInputCannotPolluteTheRoom() {
        val dsp = AudioEffectsDsp(48_000, 2, AudioEffectsProcessing(10, 50, 100))
        val frame = doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY); dsp.processFrame(frame)
        assertContentEquals(doubleArrayOf(0.0, 0.0), frame)
        repeat(12_000) { frame.fill(0.0); dsp.processFrame(frame); assertTrue(frame.all(Double::isFinite)) }
    }
    @Test fun eachSliderRoundTripsEveryAllowedIntegerAndRejectsNonFinite() {
        for (effect in AudioEffect.entries) {
            for (value in effect.minimum..effect.maximum) assertEquals(value, effect.fromSlider(effect.sliderPosition(value)))
            assertEquals(effect.minimum, effect.fromSlider(-1f))
            assertEquals(effect.maximum, effect.fromSlider(2f))
            assertFailsWith<IllegalArgumentException> { effect.fromSlider(Float.NaN) }
            assertFailsWith<IllegalArgumentException> { effect.changing(AudioEffectsSettings(), effect.maximum + 1) }
        }
        assertFailsWith<IllegalArgumentException> { AudioEffectsSettings(tempoPercent = 0) }
        assertFailsWith<IllegalArgumentException> { AudioEffectsDsp(0, 2) }
        assertEquals(.5f, AudioEffectsSettings(tempoPercent = 50).speed)
        assertEquals(2f, AudioEffectsSettings(tempoPercent = 200).speed)
    }
    @Test fun tempoKeepsRequestedCrossfadeWallTimeAndSourceCoordinates() {
        for (speed in listOf(.5f, 1f, 1.01f, 1.5f, 2f)) {
            val plan = Crossfade.plan(1_000, 20_000, 20_000, playbackSpeed = speed)!!
            assertEquals(1_000L, plan.wallTimeMs(plan.durationMs, speed))
            assertEquals(20_000L, plan.endMs)
            assertEquals(1_000L, plan.wallTimeMs(plan.msUntilStart(plan.startMs - plan.durationMs), speed))
            val manual = Crossfade.manualPlan(1_000, 20_000, 20_000, 5_000, playbackSpeed = speed)!!
            assertEquals(5_000L, manual.startMs)
            assertEquals(1_000L, manual.wallTimeMs(manual.durationMs, speed))
            assertNotNull(Crossfade.plan(250, 20_000, 20_000, playbackSpeed = speed), "speed=$speed")
            assertNull(Crossfade.plan(249, 20_000, 20_000, playbackSpeed = speed))
        }
    }
    @Test fun tempoStillBoundsCrossfadeByRealRemainingAudioAndBookmark() {
        val decision = Crossfade.decide(CrossfadeKind.Manual, 2_000, 20_000, 20_000, 19_000,
            incomingPositionMs = 19_000, playbackSpeed = 2f)
        val plan = assertNotNull(decision.plan)
        assertEquals(500L, plan.durationMs)
        assertEquals(250L, plan.wallTimeMs(plan.durationMs, 2f))
        assertNull(Crossfade.manualPlan(2_000, 20_000, 20_000, 19_800, playbackSpeed = 2f))
        assertEquals(CrossfadeSkipReason.SameTrack, Crossfade.decide(CrossfadeKind.Automatic, 2_000, 20_000, 20_000,
            sameTrack = true, playbackSpeed = .5f).skipReason)
    }
}
