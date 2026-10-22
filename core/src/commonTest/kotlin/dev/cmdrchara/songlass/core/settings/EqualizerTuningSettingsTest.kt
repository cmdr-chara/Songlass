package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.equalizer.*
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EqualizerTuningSettingsTest {
    @Test fun allOptionsPersistAndResetOnlyWithSound() {
        val store=InMemoryKeyValueStore();val settings=AppSettings(store)
        settings.setEqualizerBass(5.5);settings.setEqualizerTreble(-4.0);settings.setEqualizerLayout(GraphicEqLayout.Fifteen)
        assertEquals(EqualizerTuning(5.5,-4.0,GraphicEqLayout.Fifteen),AppSettings(store).state.value.equalizerTuning)
        assertEquals(setOf("settings.eq_bass_db","settings.eq_treble_db","settings.eq_band_count"),store.snapshot().keys)
        settings.resetToDefaults(SettingsArea.Appearance)
        assertEquals(5.5,settings.state.value.equalizerTuning.bassDb)
        settings.resetToDefaults(SettingsArea.Sound)
        assertEquals(EqualizerTuning(),settings.state.value.equalizerTuning)
        assertTrue(store.snapshot().isEmpty())
    }
    @Test fun malformedValuesFallBackAndInvalidSettersCannotMutate() {
        val store=InMemoryKeyValueStore()
        store.putString(EqualizerTuning.BASS_KEY,"NaN");store.putString(EqualizerTuning.TREBLE_KEY,"13");store.putString(EqualizerTuning.LAYOUT_KEY,"30")
        val settings=AppSettings(store);assertEquals(EqualizerTuning(),settings.state.value.equalizerTuning)
        val before=store.snapshot()
        for (value in listOf(Double.NaN,Double.POSITIVE_INFINITY,12.5,-12.5,1.2)) {
            assertFailsWith<IllegalArgumentException> { settings.setEqualizerBass(value) }
            assertFailsWith<IllegalArgumentException> { settings.setEqualizerTreble(value) }
        }
        assertEquals(before,store.snapshot())
    }
    @Test fun everyHalfDecibelCanBeSelectedAndSliderRejectsNonFinite() {
        for (half in -24..24) assertEquals(half/2.0,EqualizerTuning.gainFromSlider((half+24)/48f))
        assertFailsWith<IllegalArgumentException> { EqualizerTuning.gainFromSlider(Float.NaN) }
        assertEquals(-12.0,EqualizerTuning.gainFromSlider(-1f));assertEquals(12.0,EqualizerTuning.gainFromSlider(2f))
    }
    @Test fun restoreGateRejectsEveryNewSetting() = runTest {
        val store=InMemoryKeyValueStore();val gate=RestoreMutationGate();val settings=AppSettings(store,gate)
        gate.restoring { settings.setEqualizerBass(6.0);settings.setEqualizerTreble(-6.0);settings.setEqualizerLayout(GraphicEqLayout.ThirtyOne) }
        assertEquals(EqualizerTuning(),settings.state.value.equalizerTuning);assertTrue(store.snapshot().isEmpty())
    }
    @Test fun italianEnglishTargetsAreSearchableAndHiddenWithoutNativeSupport() {
        val index=SettingsSearchIndex(SettingsTarget.entries.map { SettingsSearchDocument(it,it.name,"Settings") })
        for ((target,queries) in listOf(
            SettingsTarget.EqualizerBass to listOf("bassi","bass","shelf basso","low shelf"),
            SettingsTarget.EqualizerTreble to listOf("alti","treble","shelf alto","high shelf"),
            SettingsTarget.EqualizerBands to listOf("numero di bande EQ","number of EQ bands","bande configurabili","configurable bands"),
        )) for (query in queries) {
            assertEquals(target,index.search(query,true).first(),query)
            assertTrue(target !in index.search(query,true,supportsAudioEffects=false),query)
        }
    }
}
