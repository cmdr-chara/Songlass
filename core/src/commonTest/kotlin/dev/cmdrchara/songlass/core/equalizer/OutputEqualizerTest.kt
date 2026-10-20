package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.*

class OutputEqualizerTest {
    @Test fun outputEditsNeverReplaceManualSoundAndSurviveRestart() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        val car = EqOutput("bluetooth:car", "Hyundai")
        val phones = EqOutput("bluetooth:phones", "Headphones")
        eq.applyPreset(EqPreset.Voice)
        eq.setEnabled(true)
        eq.setOutput(car)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        eq.applyPreset(EqPreset.MoreBass)
        eq.setOutput(phones)
        assertEquals(EqPreset.Voice, eq.state.value.preset)
        eq.saveForCurrentOutput()
        eq.applyPreset(EqPreset.LessTreble)
        eq.setEnabled(false)
        eq.setOutput(car)
        assertEquals(EqPreset.MoreBass, eq.state.value.preset)
        assertTrue(eq.state.value.enabled)
        eq.setAutomaticOutputs(false)
        assertEquals(EqPreset.Voice, eq.state.value.preset)
        eq.setAutomaticOutputs(true)
        val restored = Equalizer(store)
        restored.setOutput(phones)
        assertEquals(EqPreset.LessTreble, restored.state.value.preset)
        assertFalse(restored.state.value.enabled)
        restored.setOutput(car)
        assertEquals(EqPreset.MoreBass, restored.state.value.preset)
        restored.removeOutputProfile(car.id)
        assertEquals(EqPreset.Voice, restored.state.value.preset)
        restored.setOutput(null)
        assertEquals(EqPreset.Voice, restored.state.value.preset)
    }

    @Test fun deletingHeadphoneCorrectionClearsEveryOutputReference() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        val profile = eq.importProfile("Phones.txt", "Filter 1: ON PK Fc 100 Hz Gain -2 dB Q 1")!!
        val output = EqOutput("wired:headphones", "Headphones")
        eq.setOutput(output); eq.saveForCurrentOutput(); eq.setAutomaticOutputs(true)
        eq.deleteProfile(profile.id)
        assertNull(eq.state.value.profileId)
        val restored = Equalizer(store)
        restored.setOutput(output)
        assertNull(restored.state.value.profileId)
        assertNull(restored.state.value.outputProfile?.profileId)
    }
}
