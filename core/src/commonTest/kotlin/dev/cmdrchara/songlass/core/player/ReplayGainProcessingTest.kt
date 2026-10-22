package dev.cmdrchara.songlass.core.player

import kotlin.math.abs
import kotlin.test.*

class ReplayGainProcessingTest {
    @Test fun upgradeKeepsTheExactLegacyGainUntilOptIn() {
        for (tags in listOf(null, ReplayGainTags(trackGainDb = 6f), ReplayGainTags(trackGainDb = -6f),
            ReplayGainTags(trackGainDb = 6f, trackPeak = 2f))) for (mode in ReplayGainMode.entries) {
            val plan = ReplayGainPlan.from(tags, mode, ReplayGainSettings(preampDb = 20, missingPreampDb = 20))
            assertEquals(ReplayGain.volumeFor(tags, mode).toDouble(), plan.gain)
            assertFalse(plan.limiter)
        }
    }
    @Test fun offBypassesEvenEnabledBoostAndLimiter() {
        assertEquals(ReplayGainPlan(ReplayGainMode.Off, 1.0), ReplayGainPlan.from(ReplayGainTags(trackGainDb = 20f),
            ReplayGainMode.Off, ReplayGainSettings(true, 20, 20, true)))
    }
    @Test fun enabledProcessingAllowsPositiveGainAndSeparatePreamps() {
        val settings = ReplayGainSettings(true, 6, -6, false)
        val tagged = ReplayGainPlan.from(ReplayGainTags(trackGainDb = 6f), ReplayGainMode.Track, settings)
        assertEquals(3.9810717, tagged.gain, .00001)
        assertEquals(6, tagged.preampDb)
        val missing = ReplayGainPlan.from(null, ReplayGainMode.Track, settings)
        assertEquals(.5011872, missing.gain, .00001)
        assertEquals(-6, missing.preampDb)
    }
    @Test fun albumFallbackUsesMatchingGainAndPeak() {
        val settings = ReplayGainSettings(enabled = true)
        val fallback = ReplayGainPlan.from(ReplayGainTags(trackGainDb = 6f, trackPeak = 2f), ReplayGainMode.Album, settings)
        assertEquals(.5, fallback.gain)
        val album = ReplayGainPlan.from(ReplayGainTags(trackGainDb = 6f, trackPeak = 2f, albumGainDb = -6f, albumPeak = .5f), ReplayGainMode.Album, settings)
        assertEquals(.5011872, album.gain, .00001)
    }
    @Test fun knownPeakConstrainsTheRequestedGainOnlyWhenLimiterIsEnabled() {
        val tags = ReplayGainTags(trackGainDb = 6f, trackPeak = .4f)
        assertEquals(2.5, ReplayGainPlan.from(tags, ReplayGainMode.Track, ReplayGainSettings(true, 6)).gain, .000001)
        assertTrue(ReplayGainPlan.from(tags, ReplayGainMode.Track, ReplayGainSettings(true, 6, limiter = false)).gain > 3.9)
    }
    @Test fun limiterLinksChannelsAndPreservesStereoRatio() {
        val dsp = ReplayGainDsp(48_000)
        val frame = doubleArrayOf(.9, .45)
        dsp.processFrame(frame, ReplayGainPlan(ReplayGainMode.Track, 2.0, limiter = true))
        assertEquals(1.0, frame[0], .000001)
        assertEquals(.5, frame[1], .000001)
        assertEquals(1.0 / 1.8, dsp.reduction, .000001)
    }
    @Test fun releaseIsGradualAcrossBuffersAndFlushClearsItsMemory() {
        val dsp = ReplayGainDsp(48_000)
        val plan = ReplayGainPlan(ReplayGainMode.Track, 2.0, limiter = true)
        dsp.processFrame(doubleArrayOf(1.0, -1.0), plan)
        val initial = dsp.reduction
        repeat(2_400) { dsp.processFrame(doubleArrayOf(.1, -.1), plan) }
        assertTrue(dsp.reduction > initial && dsp.reduction < 1)
        assertEquals(.81606, dsp.reduction, .0001)
        dsp.reset()
        assertEquals(1.0, dsp.reduction)
    }
    @Test fun everyLimitedSampleStaysFiniteAndInRangeAtBothPolarities() {
        val dsp = ReplayGainDsp(44_100)
        val plan = ReplayGainPlan(ReplayGainMode.Album, 100.0, limiter = true)
        val samples = doubleArrayOf(-1.0, -.9, -.2, 0.0, .2, .9, 1.0, Double.NaN, Double.POSITIVE_INFINITY, 1e308)
        repeat(30) { for (sample in samples) {
            val frame = doubleArrayOf(sample, -sample)
            dsp.processFrame(frame, plan)
            assertTrue(frame.all { it.isFinite() && abs(it) <= 1.0 })
        } }
    }
    @Test fun disabledLimiterLeavesOverloadsForTheExplicitFinalPcmClamp() {
        val frame = doubleArrayOf(.9, -.9)
        ReplayGainDsp(48_000).processFrame(frame, ReplayGainPlan(ReplayGainMode.Track, 2.0))
        assertEquals(1.8, frame[0]); assertEquals(-1.8, frame[1])
    }
    @Test fun invalidMetadataAndSettingsCannotCreateUnboundedGain() {
        val settings = ReplayGainSettings(enabled = true)
        for (db in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE, -Float.MAX_VALUE)) {
            val plan = ReplayGainPlan.from(ReplayGainTags(trackGainDb = db, trackPeak = Float.NaN), ReplayGainMode.Track, settings)
            assertTrue(plan.gain.isFinite() && plan.gain in 0.0..100.0)
        }
        assertFailsWith<IllegalArgumentException> { ReplayGainSettings(preampDb = 21) }
        assertFailsWith<IllegalArgumentException> { ReplayGainSettings(missingPreampDb = -21) }
    }
    @Test fun sliderIncludesExactBoundsZeroAndOneDecibelSteps() {
        for (db in -20..20) assertEquals(db, ReplayGainSettings.preampFromSlider((db + 20) / 40f))
        assertFailsWith<IllegalArgumentException> { ReplayGainSettings.preampFromSlider(Float.NaN) }
    }
    @Test fun applicationCanDescribeBoostWithoutClaimingDynamicReduction() {
        val plan = ReplayGainPlan(ReplayGainMode.Track, 2.0, 6, true)
        assertEquals(2f, plan.application.linearGain)
        assertEquals(6, plan.application.preampDb)
        assertTrue(plan.application.limiterEnabled)
        assertEquals(6.0206, plan.application.gainDb!!, .0001)
    }
}
