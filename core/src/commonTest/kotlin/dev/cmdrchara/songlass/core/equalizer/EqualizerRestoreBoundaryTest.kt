package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class EqualizerRestoreBoundaryTest {
    private val text = "Preamp: -4 dB\nFilter 1: ON PK Fc 100 Hz Gain 3 dB Q 0.7"
    private val car = EqOutput("bluetooth:car", "Car")
    private val phones = EqOutput("wired:phones", "Headphones")

    @Test fun anOldFileOrCatalogReviewCannotApplyEvenIfARestoreFailsWithEqualData() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val eq = Equalizer(store, gate) { 7L }
        val state = eq.state.value
        val imported = assertNotNull(eq.prepareProfileImport("Old.txt", text))
        val ticket = assertNotNull(gate.ticket())
        val correction = AutoEqCorrection(AutoEqEntry("Test", "Test", "a/b/c", "a".repeat(40)), AutoEq.parse(text).filters)
        val before = store.snapshot()
        assertFailsWith<IllegalStateException> {
            gate.restoring {
                assertEquals(EqualizerProfileImportResult.Changed, eq.confirmProfileImport(imported))
                assertFalse(eq.applyCatalogProfile(correction, mutationTicket = ticket))
                error("Backup preparation failed")
            }
        }
        assertSame(state, eq.state.value)
        assertEquals(EqualizerProfileImportResult.Changed, eq.confirmProfileImport(imported))
        assertFalse(eq.applyCatalogProfile(correction, mutationTicket = ticket))
        assertEquals(before, store.snapshot())
        val fresh = assertNotNull(eq.prepareProfileImport("Fresh.txt", text))
        assertIs<EqualizerProfileImportResult.Saved>(eq.confirmProfileImport(fresh))
    }

    @Test fun preparedPublicationChoosesTheLatestRouteWithoutReadingTheRealStore() = runTest {
        val store = ReadControlledStore()
        val gate = RestoreMutationGate()
        val eq = Equalizer(store, gate)
        eq.applyPreset(EqPreset.Voice)
        eq.setOutput(car)
        val staged = InMemoryKeyValueStore()
        val restored = Equalizer(staged)
        restored.applyPreset(EqPreset.Flat)
        restored.setOutput(phones)
        restored.applyPreset(EqPreset.MoreBass)
        restored.saveForCurrentOutput()
        restored.setAutomaticOutputs(true)
        val original = store.snapshot()
        gate.restoring {
            val publish = eq.prepareReload(staged)
            val audible = eq.state.value.gains
            eq.setOutput(phones)
            assertEquals(phones, eq.state.value.output)
            assertEquals(audible, eq.state.value.gains)
            assertEquals(original, store.snapshot())
            store.rejectReads = true
            publish()
            eq.reconcileCurrentOutput()
            assertEquals(phones, eq.state.value.output)
            assertEquals(EqPreset.MoreBass.gains, eq.state.value.gains)
            assertEquals(original, store.snapshot())
        }
    }

    @Test fun failedRestoreSelectsExistingBindingForNewDeviceWithoutWritingOrReadingMixedStore() = runTest {
        val store = ReadControlledStore()
        val gate = RestoreMutationGate()
        val eq = Equalizer(store, gate)
        eq.applyPreset(EqPreset.Voice)
        eq.setOutput(phones)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        eq.applyPreset(EqPreset.MoreBass)
        eq.setOutput(car)
        val before = store.snapshot()
        assertEquals(EqPreset.Voice.gains, eq.state.value.gains)
        assertFailsWith<IllegalStateException> {
            gate.restoring {
                try {
                    eq.setOutput(phones)
                    assertEquals(EqPreset.Voice.gains, eq.state.value.gains)
                    store.rejectReads = true
                    error("Prepare/commit failed")
                } finally { eq.reconcileCurrentOutput() }
            }
        }
        assertEquals(phones, eq.state.value.output)
        assertEquals(EqPreset.MoreBass.gains, eq.state.value.gains)
        assertEquals(before, store.snapshot())
    }

    private class ReadControlledStore : KeyValueStore {
        private val backing = InMemoryKeyValueStore()
        var rejectReads = false
        override fun getString(key: String): String? {
            check(!rejectReads) { "Publication must use prepared values" }
            return backing.getString(key)
        }
        override fun putString(key: String, value: String?) = backing.putString(key, value)
        override fun snapshot() = backing.snapshot()
    }
}
