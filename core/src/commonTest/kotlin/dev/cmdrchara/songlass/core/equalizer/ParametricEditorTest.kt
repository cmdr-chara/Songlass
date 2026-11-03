package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.*

class ParametricEditorTest {
    @Test fun editableCopiesKeepTheSourceAndSurviveReload() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        val source = assertNotNull(eq.importProfile("Studio.txt", "Filter 1: ON PK Fc 120 Hz Gain -3 dB Q 1.2"))
        eq.applyPreset(EqPreset.Voice)
        assertTrue(eq.createEditableProfile("Manual EQ"))
        val copy = assertNotNull(eq.state.value.profile)
        assertNotEquals(source.id, copy.id)
        assertEquals(source.filters, copy.filters)
        val changed = copy.filters.map { it.copy(frequencyHz = 300.0, gainDb = -6.0, q = 2.0) }
        assertTrue(eq.updateEditableProfile(copy, changed))
        assertEquals(source, eq.state.value.profiles.single { it.id == source.id })
        val restored = Equalizer(store)
        assertEquals(eq.state.value, restored.state.value)
        assertEquals(changed, restored.state.value.profile!!.filters)
        assertEquals(EqPreset.Voice.gains, restored.state.value.gains)
        assertTrue(restored.state.value.chain.responseDb(300.0).isFinite())
    }

    @Test fun rejectsStaleCallbacksAndUnsupportedOrEmptyFilters() {
        val eq = Equalizer()
        assertTrue(eq.createEditableProfile("Manual EQ"))
        val before = assertNotNull(eq.state.value.profile)
        assertFalse(eq.updateEditableProfile(before, emptyList()))
        assertFalse(eq.updateEditableProfile(before, listOf(before.filters.first().copy(q = Double.NaN))))
        assertFalse(eq.updateEditableProfile(before, List(AutoEq.MAX_FILTERS + 1) { before.filters.first() }))
        assertTrue(eq.updateEditableProfile(before, before.filters + before.filters.first()))
        assertFalse(eq.updateEditableProfile(before, before.filters))
        assertEquals(2, eq.state.value.profile!!.filters.size)
        eq.selectProfile(null)
        assertFalse(eq.updateEditableProfile(before, before.filters))
    }

    @Test fun continuousDragMergesLatestValuesButRejectsReplacedRows() {
        val eq = Equalizer()
        eq.createEditableProfile("Manual EQ")
        val captured = assertNotNull(eq.state.value.profile)
        assertTrue(eq.editProfileFilter(captured, 0) { it.copy(frequencyHz = 200.0) })
        for (gain in 1..12) assertTrue(eq.editProfileFilter(captured, 0) { it.copy(gainDb = gain.toDouble()) })
        assertEquals(200.0, eq.state.value.profile!!.filters[0].frequencyHz)
        assertEquals(12.0, eq.state.value.profile!!.filters[0].gainDb)
        val latest = eq.state.value.profile!!
        assertTrue(eq.updateEditableProfile(latest, latest.filters + latest.filters.first()))
        assertFalse(eq.editProfileFilter(captured, 0) { it.copy(q = 9.0) })
        eq.selectProfile(null)
        assertFalse(eq.editProfileFilter(captured, 0) { it.copy(q = 9.0) })
    }

    @Test fun reloadAndProfileRecreationInvalidateOldEditorCallbacks() {
        val eq = Equalizer()
        eq.createEditableProfile("Manual EQ")
        val captured = eq.state.value.profile!!
        eq.reload()
        assertFalse(eq.editProfileFilter(captured, 0) { it.copy(q = 9.0) })
        val current = eq.state.value.profile!!
        eq.deleteProfile(current.id)
        eq.createEditableProfile("Manual EQ")
        assertNotEquals(current.id, eq.state.value.profile!!.id)
        assertFalse(eq.editProfileFilter(current, 0) { it.copy(q = 9.0) })
    }

    @Test fun outputBoundCreationDoesNotOverwriteTheManualFallback() {
        val store = InMemoryKeyValueStore()
        val eq = Equalizer(store)
        eq.applyPreset(EqPreset.Voice)
        val manual = eq.state.value.manualCurve
        eq.setOutput(EqOutput("wired:test", "Headphones"))
        eq.saveForCurrentOutput()
        eq.setAutomaticOutputs(true)
        assertTrue(eq.createEditableProfile("Headphones EQ"))
        val selected = assertNotNull(eq.state.value.profile)
        assertEquals(selected.id, eq.state.value.outputProfile!!.profileId)
        assertEquals(manual, eq.state.value.manualCurve)
        eq.setAutomaticOutputs(false)
        assertNull(eq.state.value.profile)
        val restored = Equalizer(store)
        restored.setOutput(EqOutput("wired:test", "Headphones"))
        restored.setAutomaticOutputs(true)
        assertEquals(selected, restored.state.value.profile)
    }
}
