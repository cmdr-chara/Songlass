package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EqualizerPersistenceTest {
    @Test fun malformedBandsNeverCrashOrShiftTheOtherFrequencies() {
        for (invalid in listOf("NaN", "Infinity", "-Infinity", "broken")) {
            val store = InMemoryKeyValueStore()
            store.putString("equalizer.enabled", "true")
            store.putString("equalizer.gains", (listOf(invalid) + List(9) { "4.5" }).joinToString(","))
            assertEquals(GraphicEq.flat, Equalizer(store).state.value.gains, invalid)

            // The old mapNotNull parser could discard an extra malformed value and apply the
            // remaining ten values to different bands. A malformed shape is not a valid curve.
            store.putString("equalizer.gains", (listOf(invalid) + List(10) { "4.5" }).joinToString(","))
            assertEquals(GraphicEq.flat, Equalizer(store).state.value.gains, invalid)
        }
    }

    @Test fun nonfiniteEditsLeaveTheAudibleAndPersistentCurveUnchanged() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        eq.applyPreset(EqPreset.Voice)
        val previous = store.snapshot()
        for (gain in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) eq.setGain(0, gain)
        assertEquals(EqPreset.Voice, eq.state.value.preset)
        assertEquals(previous, store.snapshot())
    }

    @Test fun oneBadFilterRejectsTheWholeCorrectionWithoutBreakingTheEqualizer() {
        val valid = "Peaking:100.0:-2.0:1.0"
        val invalidFilters = listOf(
            "Peaking:NaN:3.0:1.0", "Peaking:100.0:Infinity:1.0", "Peaking:100.0:3.0:NaN",
            "Peaking:0.0:3.0:1.0", "Peaking:22001.0:3.0:1.0", "Peaking:100.0:25.0:1.0",
            "Peaking:100.0:3.0:0.0", "Peaking:100.0:3.0:11.0", "Peaking:100.0", "Other:100.0:3.0:1.0",
        )
        for (invalid in invalidFilters) {
            val store = InMemoryKeyValueStore()
            store.putString("equalizer.enabled", "true")
            store.putString("equalizer.profiles", "bad")
            store.putString("equalizer.profiles.bad.name", "Damaged correction")
            store.putString("equalizer.profiles.bad.filters", "$valid;$invalid")
            store.putString("equalizer.profile", "bad")
            val state = Equalizer(store).state.value
            assertTrue(state.profiles.isEmpty(), invalid)
            assertNull(state.profileId, invalid)
            assertEquals(EqChain.Flat, state.chain, invalid)
        }
    }

    @Test fun aProfileCannotRestoreAnUnboundedNumberOfFilters() {
        val store = InMemoryKeyValueStore()
        store.putString("equalizer.profiles", "too-many")
        store.putString("equalizer.profiles.too-many.name", "Too many")
        store.putString("equalizer.profiles.too-many.filters", List(AutoEq.MAX_FILTERS + 1) { "Peaking:100.0:1.0:1.0" }.joinToString(";"))
        assertTrue(Equalizer(store).state.value.profiles.isEmpty())
    }

    @Test fun malformedOutputBindingFallsBackWithoutOverwritingManualSound() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        eq.applyPreset(EqPreset.Voice)
        eq.setEnabled(true)
        val output = EqOutput("usb:headphones", "USB headphones")
        eq.setOutput(output)
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        store.putString("equalizer.outputs.usb:headphones.gains", (listOf("NaN") + List(9) { "0" }).joinToString(","))
        eq.reload()
        assertFalse(eq.state.value.usesOutputProfile)
        assertEquals(EqPreset.Voice, eq.state.value.preset)
        assertEquals(EqPreset.Voice, eq.state.value.manualCurve.preset)
        assertEquals(output, eq.state.value.output)
    }

    @Test fun malformedBackupCanBeAppliedReloadedAndRestartedSafely() {
        val source = InMemoryKeyValueStore()
        source.putString("equalizer.enabled", "true")
        source.putString("equalizer.gains", (listOf("NaN") + List(9) { "6" }).joinToString(","))
        source.putString("equalizer.profiles", "bad")
        source.putString("equalizer.profiles.bad.name", "Broken")
        source.putString("equalizer.profiles.bad.filters", "Peaking:100.0:Infinity:1.0")
        source.putString("equalizer.profile", "bad")
        val text = DataBackup(source).export(emptyList()).text
        val target = InMemoryKeyValueStore()
        val eq = Equalizer(target)
        eq.applyPreset(EqPreset.MoreBass)
        val backup = DataBackup(target)
        backup.apply(backup.preview(text, emptyList()))
        eq.reload()
        assertEquals(EqChain.Flat, eq.state.value.chain)
        assertNull(eq.state.value.profile)
        assertEquals(eq.state.value, Equalizer(target).state.value)
    }

    @Test fun supportedCorrectionAndManualSummaryRoundTripUnchanged() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        val profile = eq.importProfile("Phones.txt", "Filter 1: ON PK Fc 100 Hz Gain -2 dB Q 1")!!
        eq.applyPreset(EqPreset.Voice)
        val manual = eq.state.value.manualCurve
        assertEquals(profile.id, manual.profileId)
        eq.setOutput(EqOutput("bt:car", "Car"))
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        eq.applyPreset(EqPreset.MoreBass)
        eq.selectProfile(null)
        assertEquals(manual, eq.state.value.manualCurve)
        val restored = Equalizer(store)
        assertEquals(manual, restored.state.value.manualCurve)
        assertEquals(profile, restored.state.value.profile)
        restored.deleteProfile(profile.id)
        assertNull(restored.state.value.manualCurve.profileId)
        assertEquals(EqPreset.Voice, restored.state.value.manualCurve.preset)
    }
}
