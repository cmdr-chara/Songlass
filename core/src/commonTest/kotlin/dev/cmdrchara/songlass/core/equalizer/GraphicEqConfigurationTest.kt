package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.SettingsArea
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.math.abs
import kotlin.test.*

class GraphicEqConfigurationTest {
    private val car = EqOutput("bluetooth:car.test", "Car")
    private val phones = EqOutput("wired:phones", "Phones")
    private class Fixture(val store: InMemoryKeyValueStore = InMemoryKeyValueStore()) {
        val settings = AppSettings(store)
        val eq = Equalizer(store)
        fun layout(layout: GraphicEqLayout) { settings.setEqualizerLayout(layout); eq.setTuning(settings.state.value.equalizerTuning) }
        fun tones(bass: Double, treble: Double) {
            settings.setEqualizerBass(bass); settings.setEqualizerTreble(treble); eq.setTuning(settings.state.value.equalizerTuning)
        }
    }
    @Test fun defaultLayoutKeepsExactLegacyFiltersAndCurve() {
        val f = Fixture(); f.eq.applyPreset(EqPreset.Voice, enable = true)
        assertEquals(GraphicEqLayout.Ten, f.eq.state.value.tuning.layout)
        assertEquals(EqPreset.Voice.gains, f.eq.state.value.gains)
        val expected = GraphicEq.frequencies.zip(EqPreset.Voice.gains) { hz, db -> EqFilter(FilterType.Peaking, hz, db, 1.41) }
        assertEquals(EqChain.withHeadroom(expected), f.eq.state.value.chain)
        assertEquals(listOf(31.0,62.0,125.0,250.0,500.0,1000.0,2000.0,4000.0,8000.0,16000.0), GraphicEq.frequencies)
    }
    @Test fun everyLayoutHasIndependentOrderedSupportedFiltersAndStableCoefficients() {
        assertEquals(listOf(5,10,15,31), GraphicEqLayout.entries.map { it.count })
        for (layout in GraphicEqLayout.entries) {
            assertTrue(layout.frequencies.zipWithNext().all { (a,b) -> b > a })
            val gains = List(layout.count) { if (it % 2 == 0) 12.0 else -12.0 }
            val filters = GraphicEq.filters(gains, layout)
            assertEquals(layout.count, filters.size)
            assertTrue(filters.all { it.isSupported })
            for (rate in listOf(8_000,44_100,48_000,96_000)) for (filter in filters) {
                val c = filter.coefficients(rate)
                assertTrue(listOf(c.b0,c.b1,c.b2,c.a1,c.a2).all(Double::isFinite))
                assertTrue(c.responseDb(1000.0, rate).isFinite())
            }
            assertTrue(EqChain.withHeadroom(filters).curve(256).all { it.isFinite() && it < .2 })
        }
    }
    @Test fun toneShelvesWorkInOppositeEndsAndFollowTheMasterSwitch() {
        val f=Fixture(); f.tones(6.0,-6.0)
        assertEquals(EqChain.Flat, f.eq.state.value.chain)
        f.eq.setEnabled(true)
        val chain=f.eq.state.value.chain
        assertEquals(setOf(FilterType.LowShelf, FilterType.HighShelf),chain.filters.map { it.type }.toSet())
        assertTrue(chain.responseDb(20.0) - chain.responseDb(18_000.0) > 10.0)
        assertTrue(chain.preampDb < -5.0)
        f.eq.setEnabled(false)
        assertEquals(EqChain.Flat,f.eq.state.value.chain)
    }
    @Test fun choosingLayoutsNeverWritesOrDestroysTheTenBandCurve() {
        val f=Fixture();f.eq.applyPreset(EqPreset.Voice)
        val legacy=f.store.getString("equalizer.gains")
        for (layout in GraphicEqLayout.entries) {
            f.layout(layout)
            assertEquals(layout.count, f.eq.state.value.gains.size)
            assertEquals(EqPreset.Voice.gainsFor(layout), f.eq.state.value.gains)
            assertEquals(legacy,f.store.getString("equalizer.gains"))
            if (layout!=GraphicEqLayout.Ten) assertNull(f.store.getString("equalizer.bands${layout.count}"))
        }
    }
    @Test fun editedManualCurvesStayIndependentAcrossAllLayoutsAndReload() {
        val f=Fixture();f.eq.applyPreset(EqPreset.Voice)
        val original=f.eq.state.value.gains
        for ((i,layout) in GraphicEqLayout.entries.filter { it!=GraphicEqLayout.Ten }.withIndex()) {
            f.layout(layout);f.eq.setGain(layout.count-1, -4.0-i)
        }
        f.layout(GraphicEqLayout.Ten);assertEquals(original,f.eq.state.value.gains)
        for ((i,layout) in GraphicEqLayout.entries.filter { it!=GraphicEqLayout.Ten }.withIndex()) {
            f.layout(layout)
            assertEquals(-4.0-i, f.eq.state.value.gains.last())
            assertEquals(f.eq.state.value,Equalizer(f.store).state.value)
        }
    }
    @Test fun outputBindingsKeepSeparateLayoutBanksAndManualFallback() {
        val f=Fixture();f.eq.applyPreset(EqPreset.Voice,enable=true)
        f.layout(GraphicEqLayout.ThirtyOne);f.eq.setGain(30,5.0)
        val manual=f.eq.state.value.manualCurve
        f.eq.setOutput(car);f.eq.saveForCurrentOutput();f.eq.setAutomaticOutputs(true);f.eq.setGain(30,-7.0)
        f.layout(GraphicEqLayout.Five);f.eq.setGain(0,4.0)
        f.eq.setOutput(phones);f.eq.saveForCurrentOutput();f.eq.setGain(0,-8.0)
        f.eq.setOutput(car);assertEquals(4.0,f.eq.state.value.gains.first())
        f.layout(GraphicEqLayout.ThirtyOne);assertEquals(-7.0,f.eq.state.value.gains.last())
        f.eq.setOutput(null);assertEquals(5.0,f.eq.state.value.gains.last())
        assertEquals(manual,f.eq.state.value.manualCurve)
        val reloaded=Equalizer(f.store);reloaded.setOutput(car)
        f.eq.setOutput(car);assertEquals(f.eq.state.value,reloaded.state.value)
    }
    @Test fun presetsApplyOnlyToSelectedLayoutAndRemainRecognizable() {
        val f=Fixture();f.eq.applyPreset(EqPreset.Voice)
        for (layout in GraphicEqLayout.entries.filter { it!=GraphicEqLayout.Ten }) {
            f.layout(layout);f.eq.applyPreset(EqPreset.MoreBass,enable=true)
            assertEquals(EqPreset.MoreBass,f.eq.state.value.preset)
            assertEquals(EqPreset.MoreBass.gainsFor(layout),f.eq.state.value.gains)
        }
        f.layout(GraphicEqLayout.Ten);assertEquals(EqPreset.Voice,f.eq.state.value.preset)
    }
    @Test fun correctionImportPreservesAlternateCurvesAndLegacyShape() {
        val f=Fixture();f.eq.applyPreset(EqPreset.Voice);f.layout(GraphicEqLayout.Fifteen);f.eq.setGain(14,-8.0)
        val banks=f.eq.state.value.manualCurve
        val profile=assertNotNull(f.eq.importProfile("Studio.txt","Filter 1: ON LSC Fc 100 Hz Gain 3 dB Q 0.7"))
        assertEquals(banks.gains,f.eq.state.value.manualCurve.gains)
        assertEquals(banks.extraGains,f.eq.state.value.manualCurve.extraGains)
        assertEquals(10,f.store.getString("equalizer.gains")!!.split(',').size)
        assertEquals(profile.id,f.eq.state.value.profileId)
        assertEquals(f.eq.state.value,Equalizer(f.store).state.value)
        f.eq.setOutput(car);f.eq.saveForCurrentOutput();f.eq.setAutomaticOutputs(true)
        val binding=f.eq.state.value.outputProfile!!
        assertNotNull(f.eq.importProfile("Second.txt","Filter 1: ON HSC Fc 8000 Hz Gain -2 dB Q 0.7"))
        assertEquals(binding.extraGains,f.eq.state.value.outputProfile!!.extraGains)
        assertEquals(10,f.store.getString("equalizer.outputs.${car.id}.gains")!!.split(',').size)
    }
    @Test fun catalogApplyWhileBoundPreservesBanksAndOtherOutputs() {
        val f=Fixture();f.layout(GraphicEqLayout.Fifteen);f.eq.setGain(14,5.0)
        f.eq.setOutput(phones);f.eq.saveForCurrentOutput()
        val other=f.eq.state.value.outputProfile!!
        f.eq.setOutput(car)
        val correction=AutoEqCorrection(AutoEqEntry("Studio","Source","Source/over-ear/Studio","7ae0f56d53074872b028649617a22bbb4232feb7"),
            listOf(EqFilter(FilterType.Peaking,100.0,-2.0,1.0)))
        assertTrue(f.eq.applyCatalogProfile(correction,car))
        assertEquals(other,f.eq.state.value.outputProfiles.first { it.output==phones })
        assertEquals(5.0,f.eq.state.value.gains.last())
        assertEquals(f.eq.state.value,Equalizer(f.store).also { it.setOutput(car) }.state.value)
    }
    @Test fun layoutChangeInvalidatesAlreadyReviewedCorrectionImport() {
        val f=Fixture();val prepared=assertNotNull(f.eq.prepareProfileImport("Studio.txt","Filter 1: ON PK Fc 500 Hz Gain 2 dB Q 1"))
        f.layout(GraphicEqLayout.ThirtyOne)
        assertEquals(EqualizerProfileImportResult.Changed,f.eq.confirmProfileImport(prepared))
    }
    @Test fun deletingBindingRemovesEveryBankWithoutErasingManualCurves() {
        val f=Fixture();f.layout(GraphicEqLayout.ThirtyOne);f.eq.setGain(30,7.0)
        f.eq.setOutput(car);f.eq.saveForCurrentOutput();val manual=f.eq.state.value.manualCurve
        f.eq.removeOutputProfile(car.id)
        assertFalse(f.store.snapshot().keys.any { it.startsWith("equalizer.outputs.${car.id}.") })
        assertEquals(manual,f.eq.state.value.manualCurve)
        assertNotNull(f.store.getString("equalizer.bands31"))
    }
    @Test fun malformedAlternateBankFallsBackAsAWholeWithoutMovingBands() {
        val f=Fixture();f.eq.applyPreset(EqPreset.Voice);f.layout(GraphicEqLayout.Fifteen)
        for (text in listOf("1,2,3",List(15) { if(it==4) "NaN" else "1" }.joinToString(","),"1".repeat(15*64+1))) {
            f.store.putString("equalizer.bands15",text)
            val loaded=Equalizer(f.store).state.value
            assertEquals(EqPreset.Voice.gainsFor(GraphicEqLayout.Fifteen),loaded.gains)
            assertEquals(EqPreset.Voice.gains,loaded.manualCurve.gains)
        }
    }
    @Test fun failedBankSaveCannotPublishUnsavedCurve() {
        val backing=InMemoryKeyValueStore();val settings=AppSettings(backing);settings.setEqualizerLayout(GraphicEqLayout.ThirtyOne)
        val store=object:KeyValueStore by backing {
            override fun updateStrings(removing:Set<String>,writing:Map<String,String>) { error("disk failure") }
        }
        val eq=Equalizer(store);val before=eq.state.value;val disk=backing.snapshot()
        assertFailsWith<IllegalStateException> { eq.setGain(30,8.0,enable=true) }
        assertEquals(before,eq.state.value);assertEquals(disk,backing.snapshot())
    }
    @Test fun backupRoundTripCarriesManualAndOutputBanksAndLegacyRestoreRemovesNewData() {
        val f=Fixture();f.eq.applyPreset(EqPreset.Voice);f.tones(5.5,-3.0)
        for (layout in GraphicEqLayout.entries) { f.layout(layout);f.eq.setGain(layout.count-1,4.0) }
        f.eq.setOutput(car);f.eq.saveForCurrentOutput()
        val target=InMemoryKeyValueStore();val backup=DataBackup(target)
        backup.apply(backup.preview(DataBackup(f.store).export(emptyList()).text,emptyList()))
        assertEquals(f.store.snapshot(),target.snapshot())
        assertEquals(f.eq.state.value,Equalizer(target).also { it.setOutput(car) }.state.value)
        backup.apply(backup.preview(DataBackup(InMemoryKeyValueStore()).export(emptyList()).text,emptyList()))
        assertEquals(EqualizerTuning(),AppSettings(target).state.value.equalizerTuning)
        assertTrue(target.snapshot().keys.none { it.contains("bands31") || it.contains("bands15") || it.contains("bands5") })
    }
    @Test fun soundResetReturnsToTenBandsAndZeroToneButPreservesEverySavedCurve() {
        val f=Fixture();f.eq.applyPreset(EqPreset.Voice);f.layout(GraphicEqLayout.ThirtyOne);f.eq.setGain(30,4.5);f.tones(8.0,-6.0)
        val saved=f.eq.state.value.manualCurve
        f.settings.resetToDefaults(SettingsArea.Sound);f.eq.setTuning(f.settings.state.value.equalizerTuning)
        assertEquals(EqualizerTuning(),f.eq.state.value.tuning);assertEquals(saved,f.eq.state.value.manualCurve)
        assertEquals(EqPreset.Voice.gains,f.eq.state.value.gains)
        f.layout(GraphicEqLayout.ThirtyOne);assertEquals(4.5,f.eq.state.value.gains.last())
    }
}
