package dev.cmdrchara.songlass.core.player

import dev.cmdrchara.songlass.core.equalizer.EqOutput
import dev.cmdrchara.songlass.core.equalizer.EqPreset
import dev.cmdrchara.songlass.core.equalizer.Equalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioPipelineInfoTest {
    @Test fun unknownAnonymousAndMultipleRoutesDoNotSelectTheFirstSavedEqualizer() {
        val eq = Equalizer()
        val headphones = AudioOutputDevice("bluetooth:headphones", "Headphones", AudioOutputKind.Bluetooth)
        val speaker = AudioOutputDevice("builtin:2", "Phone", AudioOutputKind.BuiltIn)
        eq.applyPreset(EqPreset.Voice)
        eq.setOutput(AudioOutputRoute(listOf(headphones)).equalizerOutput)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        eq.applyPreset(EqPreset.MoreBass)
        for (route in listOf(
            AudioOutputRoute(),
            AudioOutputRoute(listOf(headphones, speaker)),
            AudioOutputRoute(listOf(headphones.copy(identity = null))),
            AudioOutputRoute(listOf(headphones.copy(identity = ""))),
        )) {
            assertNull(route.equalizerOutput)
            eq.setOutput(route.equalizerOutput)
            assertFalse(eq.state.value.usesOutputProfile)
            assertEquals(EqPreset.Voice, eq.state.value.preset)
        }
        val reconnected = AudioOutputRoute(listOf(headphones))
        eq.setOutput(reconnected.equalizerOutput)
        assertEquals(EqOutput(headphones.identity!!, headphones.name!!), eq.state.value.output)
        assertEquals(EqPreset.MoreBass, eq.state.value.preset)
    }

    @Test fun replayGainInformationReportsTheAppliedAttenuationRatherThanAnUnappliedBoost() {
        val positiveTag = ReplayGainTags(trackGainDb = 6f, trackPeak = 0.4f)
        val capped = ReplayGainApplication.fromTags(positiveTag, ReplayGainMode.Track)
        assertEquals(1f, capped.linearGain)
        assertEquals(0.0, capped.gainDb)
        assertEquals(6f, positiveTag.trackGainDb)
        val peakLimited = ReplayGainApplication.fromTags(ReplayGainTags(trackGainDb = 6f, trackPeak = 2f), ReplayGainMode.Track)
        assertEquals(0.5f, peakLimited.linearGain)
        assertTrue(peakLimited.gainDb!! < -6.0)
        val off = ReplayGainApplication.fromTags(positiveTag, ReplayGainMode.Off)
        assertEquals(1f, off.linearGain)
    }

    @Test fun zeroGainHasNoInfiniteDecibelValueForTheUi() {
        val muted = ReplayGainApplication.fromTags(ReplayGainTags(trackGainDb = -Float.MAX_VALUE), ReplayGainMode.Track)
        assertEquals(0f, muted.linearGain)
        assertNull(muted.gainDb)
    }

    @Test fun sourceChannelCountNeverTurnsIntoAnInferredPcmOrDacDepth() {
        val source = AudioFormatInfo.fromSource("audio/aac", 44_100, 32, 192_000, channelCount = 2)!!
        assertEquals(2, source.channelCount)
        assertNull(source.bitDepth)
        val unknown = AudioFormatInfo.fromSource(null, null, null, null, channelCount = -1)
        assertNull(unknown)
    }
}
