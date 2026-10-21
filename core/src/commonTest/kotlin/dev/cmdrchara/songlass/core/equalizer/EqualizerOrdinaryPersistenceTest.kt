package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.FaultingPreferenceStore
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EqualizerOrdinaryPersistenceTest {
    private val car = EqOutput("bluetooth:car", "Car")
    private val phones = EqOutput("wired:phones", "Headphones")
    private val correction = AutoEqCorrection(
        AutoEqEntry("Studio", "Source", "Source/over-ear/Studio", "7ae0f56d53074872b028649617a22bbb4232feb7"),
        listOf(EqFilter(FilterType.Peaking, 100.0, -2.0, 1.0)),
    )

    @Test fun enablingWithABandOrPresetIsOneAcceptedChangeAndKeepsDefaultCallersCompatible() {
        val actions: List<(Equalizer) -> Unit> = listOf(
            { it.setGain(0, 3.0, enable = true) },
            { it.applyPreset(EqPreset.Voice, enable = true) },
        )
        for ((index, action) in actions.withIndex()) {
            val store = FaultingPreferenceStore()
            val eq = Equalizer(store)
            eq.setEnabled(false)
            failAndRetry(store, eq, action = action)
            assertTrue(eq.state.value.enabled)
            assertEquals(if (index == 0) listOf(3.0) + GraphicEq.flat.drop(1) else EqPreset.Voice.gains,
                eq.state.value.gains)
            eq.setEnabled(false)
            eq.setGain(1, 1.0)
            assertFalse(eq.state.value.enabled)
            eq.applyPreset(EqPreset.Flat)
            assertFalse(eq.state.value.enabled)
        }
    }

    @Test fun failedManualCatalogApplyCannotLeaveAnOrphanOrHalfSelectedCorrection() {
        val store = FaultingPreferenceStore()
        val eq = Equalizer(store)
        eq.applyPreset(EqPreset.Voice)
        store.putString("foreign.preference", "keep")
        failAndRetry(store, eq, failAfter = 2) { assertTrue(it.applyCatalogProfile(correction)) }
        assertEquals("autoeq:${correction.entry.id}", eq.state.value.profileId)
        assertEquals(eq.state.value.profileId, eq.state.value.manualCurve.profileId)
        assertEquals(EqPreset.Voice, eq.state.value.preset)
        assertEquals(1, eq.state.value.profiles.size)
        assertEquals("keep", store.getString("foreign.preference"))
    }

    @Test fun catalogBindingBatchPreservesManualFallbackAndTheOtherOutputOnFailureAndRetry() {
        val store = FaultingPreferenceStore()
        val eq = Equalizer(store)
        eq.applyPreset(EqPreset.Voice, enable = true)
        eq.setOutput(phones)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        eq.applyPreset(EqPreset.LessTreble)
        eq.setOutput(car)
        val manual = eq.state.value.manualCurve
        val other = eq.state.value.outputProfiles.single()
        failAndRetry(store, eq, failAfter = 3) { assertTrue(it.applyCatalogProfile(correction, car)) }
        assertTrue(eq.state.value.automaticOutputs)
        assertTrue(eq.state.value.usesOutputProfile)
        assertEquals(manual, eq.state.value.manualCurve)
        assertEquals(other, eq.state.value.outputProfiles.single { it.output == phones })
        assertEquals("autoeq:${correction.entry.id}", eq.state.value.outputProfile?.profileId)
        eq.setOutput(null)
        assertEquals(manual.gains, eq.state.value.gains)
        assertEquals(manual.profileId, eq.state.value.profileId)
    }

    @Test fun deletingACorrectionRollsBackItsIndexManualSelectionAndEveryOutputReference() {
        val store = FaultingPreferenceStore()
        val eq = Equalizer(store)
        assertTrue(eq.applyCatalogProfile(correction))
        val removed = assertNotNull(eq.state.value.profileId)
        eq.setOutput(car)
        eq.saveForCurrentOutput()
        eq.setOutput(phones)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        eq.applyPreset(EqPreset.MoreBass)
        val manualGains = eq.state.value.manualCurve.gains
        val gains = eq.state.value.gains
        failAndRetry(store, eq, failAfter = 2) { it.deleteProfile(removed) }
        assertTrue(eq.state.value.profiles.isEmpty())
        assertNull(eq.state.value.profileId)
        assertNull(eq.state.value.manualCurve.profileId)
        assertTrue(eq.state.value.outputProfiles.all { it.profileId == null })
        assertEquals(gains, eq.state.value.gains)
        assertEquals(manualGains, eq.state.value.manualCurve.gains)
        assertNull(store.getString("equalizer.profiles.$removed.name"))
        assertNull(store.getString("equalizer.profiles.$removed.filters"))
        assertNull(store.getString("equalizer.profile"))
    }

    @Test fun removingTheActiveOutputCannotLeaveAnIndexedButIncompleteBinding() {
        val store = FaultingPreferenceStore()
        val eq = configuredOutputs(store)
        val manual = eq.state.value.manualCurve
        val other = eq.state.value.outputProfiles.single { it.output == phones }
        failAndRetry(store, eq) { it.removeOutputProfile(car.id) }
        assertFalse(eq.state.value.usesOutputProfile)
        assertEquals(manual.gains, eq.state.value.gains)
        assertEquals(manual.enabled, eq.state.value.enabled)
        assertEquals(listOf(other), eq.state.value.outputProfiles)
        for (field in listOf("name", "enabled", "gains", "profile")) {
            assertNull(store.getString("equalizer.outputs.${car.id}.$field"))
        }
    }

    @Test fun updatingASavedOutputCannotMixItsOldCurveWithNewEnableOrSelection() {
        val store = FaultingPreferenceStore()
        val eq = configuredOutputs(store)
        eq.setEnabled(false)
        eq.setAutomaticOutputs(false)
        val manual = eq.state.value.manualCurve
        val other = eq.state.value.outputProfiles.single { it.output == phones }
        failAndRetry(store, eq) { it.saveForCurrentOutput() }
        assertEquals(manual.enabled, eq.state.value.outputProfile?.enabled)
        assertEquals(manual.gains, eq.state.value.outputProfile?.gains)
        assertEquals(manual.profileId, eq.state.value.outputProfile?.profileId)
        assertEquals(other, eq.state.value.outputProfiles.single { it.output == phones })
    }

    @Test fun editingABoundCurveAndClearingItsCorrectionNeverModifyTheManualFallback() {
        val actions: List<(Equalizer) -> Unit> = listOf(
            { it.setEnabled(false) },
            { it.setGain(1, 4.0, enable = true) },
            { it.selectProfile(null) },
            { assertTrue(it.applyCatalogProfile(correction.copy(filters = listOf(EqFilter(FilterType.Peaking, 150.0, -3.0, 1.0))))) },
        )
        for (action in actions) {
            val store = FaultingPreferenceStore()
            val eq = configuredOutputs(store)
            assertTrue(eq.applyCatalogProfile(correction, car))
            val manual = eq.state.value.manualCurve
            val other = eq.state.value.outputProfiles.single { it.output == phones }
            failAndRetry(store, eq, action = action)
            assertEquals(manual, eq.state.value.manualCurve)
            assertEquals(other, eq.state.value.outputProfiles.single { it.output == phones })
        }
    }

    @Test fun acceptedOutputRemovalOrAutomaticToggleNeedsNoFallibleReadBeforePublication() {
        val actions: List<(Equalizer) -> Unit> = listOf(
            { it.removeOutputProfile(car.id) },
            { it.setAutomaticOutputs(false) },
        )
        for (action in actions) {
            val store = ReadBoundaryStore()
            val eq = configuredOutputs(store)
            val manual = eq.state.value.manualCurve
            store.blockReadsAfterBatch = true
            action(eq)
            assertEquals(manual.enabled, eq.state.value.enabled)
            assertEquals(manual.gains, eq.state.value.gains)
            // A later device fact also selects from accepted memory, never half-read preferences.
            eq.setOutput(phones)
            store.blockReadsAfterBatch = false
            store.readsBlocked = false
            val reloaded = Equalizer(store).apply { setOutput(phones) }
            assertEquals(eq.state.value, reloaded.state.value)
        }
    }

    private fun configuredOutputs(store: KeyValueStore): Equalizer = Equalizer(store).apply {
        applyPreset(EqPreset.Voice, enable = true)
        setOutput(car)
        saveForCurrentOutput()
        setAutomaticOutputs(true)
        applyPreset(EqPreset.MoreBass)
        setOutput(phones)
        saveForCurrentOutput()
        applyPreset(EqPreset.LessTreble)
        setOutput(car)
    }

    private fun failAndRetry(
        store: FaultingPreferenceStore,
        eq: Equalizer,
        failAfter: Int = 1,
        action: (Equalizer) -> Unit,
    ) {
        val before = eq.state.value
        val saved = store.snapshot()
        store.afterWrite = { assertSame(before, eq.state.value) }
        store.failAfterChanges(failAfter)
        assertFailsWith<IllegalStateException> { action(eq) }
        assertEquals(failAfter, store.changedBeforeFailure)
        assertSame(before, eq.state.value)
        assertEquals(saved, store.snapshot())
        assertEquals(before, Equalizer(store).apply { setOutput(before.output) }.state.value)
        action(eq)
        store.afterWrite = null
        assertEquals(eq.state.value, Equalizer(store).apply { setOutput(before.output) }.state.value)
    }

    private class ReadBoundaryStore : KeyValueStore {
        private val backing = InMemoryKeyValueStore()
        var blockReadsAfterBatch = false
        var readsBlocked = false
        override fun getString(key: String): String? {
            check(!readsBlocked) { "A successful batch cannot be followed by fallible store reads" }
            return backing.getString(key)
        }
        override fun putString(key: String, value: String?) { backing.putString(key, value) }
        override fun updateStrings(removing: Set<String>, writing: Map<String, String>) {
            backing.updateStrings(removing, writing)
            if (blockReadsAfterBatch) readsBlocked = true
        }
    }
}
