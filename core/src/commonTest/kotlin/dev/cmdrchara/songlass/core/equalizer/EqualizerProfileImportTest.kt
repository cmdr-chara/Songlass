package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EqualizerProfileImportTest {
    private val text = "Preamp: -4 dB\nFilter 1: ON LSC Fc 100 Hz Gain 3 dB Q 0.7"
    private val car = EqOutput("bluetooth:car", "Car")
    private val phones = EqOutput("wired:phones", "Headphones")

    @Test fun parsingAndDismissingAPreviewNeverWriteOrChangeTheSound() {
        val store = BatchStore()
        var clockCalls = 0
        val eq = Equalizer(store) { clockCalls++; 7 }
        eq.applyPreset(EqPreset.Voice)
        val before = eq.state.value
        val saved = store.snapshot()
        val writes = store.writes
        val prepared = assertNotNull(eq.prepareProfileImport("Studio ParametricEQ.txt", "$text\nFilter 2: OFF PK Fc 200 Hz"))
        assertEquals("Studio", prepared.name)
        assertEquals(1, prepared.filters.size)
        assertEquals(1, prepared.skipped)
        assertNull(prepared.output)
        assertSame(before, eq.state.value)
        assertEquals(saved, store.snapshot())
        assertEquals(writes, store.writes)
        assertEquals(0, store.batches)
        assertEquals(0, clockCalls)
        // Discarding this value is Cancel; no apply action or persistence runs implicitly.
    }

    @Test fun confirmingManualImportPreservesGraphicBandsAndSurvivesReload() {
        val store = BatchStore()
        store.putString("unrelated.preference", "keep")
        val eq = Equalizer(store) { 7 }
        eq.applyPreset(EqPreset.Voice)
        eq.setOutput(car)
        val prepared = assertNotNull(eq.prepareProfileImport("Studio ParametricEQ.txt", text))
        val saved = assertIs<EqualizerProfileImportResult.Saved>(eq.confirmProfileImport(prepared)).profile
        assertTrue(eq.state.value.enabled)
        assertEquals(EqPreset.Voice.gains, eq.state.value.gains)
        assertEquals(saved.id, eq.state.value.manualCurve.profileId)
        assertEquals(saved, eq.state.value.profile)
        assertEquals(1, store.batches)
        assertEquals("keep", store.getString("unrelated.preference"))
        val restored = Equalizer(store)
        restored.setOutput(car)
        assertEquals(eq.state.value, restored.state.value)
    }

    @Test fun boundImportPreservesManualCurveAndEveryOtherOutput() {
        val store = BatchStore()
        val eq = Equalizer(store) { 7 }
        eq.applyPreset(EqPreset.Voice)
        eq.setEnabled(true)
        eq.setOutput(car)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        eq.applyPreset(EqPreset.MoreBass)
        eq.setOutput(phones)
        eq.saveForCurrentOutput()
        eq.applyPreset(EqPreset.LessTreble)
        eq.setEnabled(false)
        eq.setOutput(car)
        val before = eq.state.value
        val prepared = assertNotNull(eq.prepareProfileImport("Car.txt", text))
        assertEquals(car, prepared.output)
        val saved = assertIs<EqualizerProfileImportResult.Saved>(eq.confirmProfileImport(prepared)).profile
        assertEquals(before.manualCurve, eq.state.value.manualCurve)
        assertEquals(before.gains, eq.state.value.gains)
        assertEquals(before.outputProfiles.single { it.output == phones },
            eq.state.value.outputProfiles.single { it.output == phones })
        assertEquals(saved.id, eq.state.value.outputProfile?.profileId)
        assertEquals(1, store.batches)
        val restored = Equalizer(store)
        restored.setOutput(car)
        assertEquals(eq.state.value, restored.state.value)
        restored.setOutput(phones)
        assertFalse(restored.state.value.enabled)
        assertEquals(EqPreset.LessTreble.gains, restored.state.value.gains)
        restored.setAutomaticOutputs(false)
        assertEquals(EqPreset.Voice.gains, restored.state.value.gains)
        assertNull(restored.state.value.profileId)
    }

    @Test fun anUnboundOutputUsesTheManualFallbackWithoutCreatingABinding() {
        val store = BatchStore()
        val eq = Equalizer(store)
        eq.setOutput(car)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        eq.setOutput(phones)
        val bindings = eq.state.value.outputProfiles
        val prepared = assertNotNull(eq.prepareProfileImport("Phones.txt", text))
        assertNull(prepared.output)
        val saved = assertIs<EqualizerProfileImportResult.Saved>(eq.confirmProfileImport(prepared)).profile
        assertEquals(bindings, eq.state.value.outputProfiles)
        assertEquals(saved.id, eq.state.value.manualCurve.profileId)
        val restored = Equalizer(store)
        restored.setOutput(phones)
        assertEquals(saved, restored.state.value.profile)
        restored.setOutput(car)
        assertNull(restored.state.value.profile)
        assertFalse(restored.state.value.enabled)
    }

    @Test fun aRouteOrCurveChangeRejectsTheReviewedImportBeforeAnyWrite() {
        val changes: List<(Equalizer) -> Unit> = listOf(
            { it.setOutput(car) },
            { it.setGain(0, 3.0) },
            { it.setEnabled(true) },
            { it.setAutomaticOutputs(true) },
        )
        for (change in changes) {
            val store = BatchStore()
            val eq = Equalizer(store)
            val prepared = assertNotNull(eq.prepareProfileImport("Studio.txt", text))
            change(eq)
            val latest = eq.state.value
            val saved = store.snapshot()
            val writes = store.writes
            assertEquals(EqualizerProfileImportResult.Changed, eq.confirmProfileImport(prepared))
            assertSame(latest, eq.state.value)
            assertEquals(saved, store.snapshot())
            assertEquals(writes, store.writes)
            assertEquals(0, store.batches)
        }
    }

    @Test fun returningToTheSameRouteDoesNotReviveAnOldReview() {
        val store = BatchStore()
        val eq = Equalizer(store)
        val initial = eq.state.value
        val prepared = assertNotNull(eq.prepareProfileImport("Studio.txt", text))
        eq.setOutput(car)
        eq.setOutput(null)
        assertEquals(initial, eq.state.value)
        assertEquals(EqualizerProfileImportResult.Changed, eq.confirmProfileImport(prepared))
        assertEquals(0, store.batches)
    }

    @Test fun delayedParsingKeepsTheRevisionCapturedBeforeWorkerDispatch() {
        val store = BatchStore()
        val eq = Equalizer(store)
        val captured = eq.state.value
        eq.setOutput(car)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        val prepared = assertNotNull(eq.prepareProfileImport("Studio.txt", text, captured))
        assertNull(prepared.output)
        val snapshot = store.snapshot()
        assertEquals(EqualizerProfileImportResult.Changed, eq.confirmProfileImport(prepared))
        assertEquals(snapshot, store.snapshot())
        assertEquals(0, store.batches)
    }

    @Test fun backupReloadInvalidatesAPreviousReview() {
        val store = BatchStore()
        val eq = Equalizer(store)
        val prepared = assertNotNull(eq.prepareProfileImport("Studio.txt", text))
        store.putString("equalizer.enabled", "true")
        eq.reload()
        val snapshot = store.snapshot()
        assertEquals(EqualizerProfileImportResult.Changed, eq.confirmProfileImport(prepared))
        assertEquals(snapshot, store.snapshot())
        assertEquals(0, store.batches)
    }

    @Test fun confirmationCannotRunTwiceOrAcrossEqualizerOwners() {
        val store = BatchStore()
        val eq = Equalizer(store) { 7 }
        val prepared = assertNotNull(eq.prepareProfileImport("Studio.txt", text))
        assertEquals(EqualizerProfileImportResult.Changed, Equalizer(store).confirmProfileImport(prepared))
        assertIs<EqualizerProfileImportResult.Saved>(eq.confirmProfileImport(prepared))
        val snapshot = store.snapshot()
        assertEquals(EqualizerProfileImportResult.Changed, eq.confirmProfileImport(prepared))
        assertEquals(snapshot, store.snapshot())
        assertEquals(1, store.batches)
    }

    @Test fun compatibilityImportStillUsesUniqueIdsAndExistingPersistenceKeys() {
        val store = BatchStore()
        val eq = Equalizer(store) { 7 }
        val first = assertNotNull(eq.importProfile("First.txt", text))
        val second = assertNotNull(eq.importProfile("Second.txt", text))
        assertEquals("h7", first.id)
        assertEquals("h7-1", second.id)
        assertEquals(listOf(first, second), Equalizer(store).state.value.profiles)
        assertEquals(second, Equalizer(store).state.value.profile)
        assertEquals("h7\u001Fh7-1", store.getString("equalizer.profiles"))
        assertEquals(2, store.batches)
    }

    @Test fun aFailedFallbackBatchRestoresAllKeysAndCanBeRetried() {
        // Fail each individual write of the native-batch fallback once, including after
        // the profile/index were written. This would expose orphan or half-selected profiles.
        for (failureAt in 1..6) {
            val store = FailOnceStore()
            store.backing.putString("unrelated.preference", "keep")
            val eq = Equalizer(store) { 7 }
            val before = eq.state.value
            val snapshot = store.snapshot()
            val prepared = assertNotNull(eq.prepareProfileImport("Studio.txt", text))
            store.failAt = failureAt
            assertEquals(EqualizerProfileImportResult.Failed, eq.confirmProfileImport(prepared))
            assertSame(before, eq.state.value)
            assertEquals(snapshot, store.snapshot())
            assertTrue(Equalizer(store).state.value.profiles.isEmpty())
            assertIs<EqualizerProfileImportResult.Saved>(eq.confirmProfileImport(prepared))
            assertEquals(1, eq.state.value.profiles.size)
            assertEquals(eq.state.value, Equalizer(store).state.value)
        }
    }

    @Test fun invalidOrOversizedFilesDoNotWriteOrAllocateProfileIds() {
        val store = BatchStore()
        var ids = 0
        val eq = Equalizer(store) { ids++; ids.toLong() }
        val before = eq.state.value
        assertNull(eq.prepareProfileImport("NoFilters.txt", "Preamp: -4 dB"))
        assertNull(eq.prepareProfileImport(" ", text))
        assertNull(eq.prepareProfileImport("X".repeat(AutoEq.MAX_FILE_NAME_CHARS + 1), text))
        assertNull(eq.prepareProfileImport("TooBig.txt", text + "#".repeat(AutoEq.MAX_INPUT_CHARS)))
        assertSame(before, eq.state.value)
        assertTrue(store.snapshot().isEmpty())
        assertEquals(0, store.writes)
        assertEquals(0, store.batches)
        assertEquals(0, ids)
    }

    private class BatchStore : KeyValueStore {
        private val backing = InMemoryKeyValueStore()
        var writes = 0
        var batches = 0
        override fun getString(key: String): String? = backing.getString(key)
        override fun putString(key: String, value: String?) { writes++; backing.putString(key, value) }
        override fun snapshot(): Map<String, String> = backing.snapshot()
        override fun putStrings(writing: Map<String, String>) { batches++; backing.putStrings(writing) }
    }

    private class FailOnceStore : KeyValueStore {
        val backing = InMemoryKeyValueStore()
        var failAt: Int? = null
        private var writes = 0
        override fun getString(key: String): String? = backing.getString(key)
        override fun snapshot(): Map<String, String> = backing.snapshot()
        override fun putString(key: String, value: String?) {
            writes++
            if (failAt == writes) { failAt = null; error("Injected preference failure") }
            backing.putString(key, value)
        }
    }
}
