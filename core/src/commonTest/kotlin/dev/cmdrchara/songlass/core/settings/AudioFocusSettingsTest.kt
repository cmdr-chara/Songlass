package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.player.*
import dev.cmdrchara.songlass.core.storage.*
import dev.cmdrchara.songlass.core.backup.DataBackup
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AudioFocusSettingsTest {
    @Test fun defaultsAndIndependentPreferencesPersist() {
        val store = InMemoryKeyValueStore(); val settings = AppSettings(store)
        assertEquals(AudioFocusSettings(), settings.state.value.audioFocus)
        configure(settings)
        assertEquals(AudioFocusSettings(FocusDucking.Pause, false, false), AppSettings(store).state.value.audioFocus)
        assertEquals(AudioFocusSettings.keys, store.snapshot().keys)
    }
    @Test fun resetSoundRemovesOnlyItsKeysAndOtherResetsKeepThem() {
        val store = InMemoryKeyValueStore(); val settings = AppSettings(store); configure(settings)
        settings.setResumeOnReconnect(true)
        settings.resetToDefaults(SettingsArea.Player)
        assertEquals(FocusDucking.Pause, settings.state.value.audioFocus.ducking)
        settings.resetToDefaults(SettingsArea.Sound)
        assertEquals(AudioFocusSettings(), settings.state.value.audioFocus)
        AudioFocusSettings.keys.forEach { assertNull(store.getString(it)) }
    }
    @Test fun malformedValuesFallBackWithoutWritingMigration() {
        val store = InMemoryKeyValueStore()
        AudioFocusSettings.keys.forEach { store.putString(it, "invalid") }
        val before = store.snapshot()
        assertEquals(AudioFocusSettings(), AppSettings(store).state.value.audioFocus)
        assertEquals(before, store.snapshot())
    }
    @Test fun backupRoundTripAndLegacyBackupRestoreDefaults() {
        val source = InMemoryKeyValueStore(); configure(AppSettings(source))
        val target = InMemoryKeyValueStore(); val backup = DataBackup(target)
        backup.apply(backup.preview(DataBackup(source).export(emptyList()).text, emptyList()))
        assertEquals(AppSettings(source).state.value.audioFocus, AppSettings(target).state.value.audioFocus)
        backup.apply(backup.preview(DataBackup(InMemoryKeyValueStore()).export(emptyList()).text, emptyList()))
        assertEquals(AudioFocusSettings(), AppSettings(target).state.value.audioFocus)
    }
    @Test fun allNewOptionsRespectRestoreGate() = runTest {
        val store = InMemoryKeyValueStore(); val gate = RestoreMutationGate(); val settings = AppSettings(store, gate)
        gate.restoring { configure(settings) }
        assertEquals(AudioFocusSettings(), settings.state.value.audioFocus)
        assertTrue(store.snapshot().isEmpty())
    }
    @Test fun failedWritesCannotPublishUnstoredValues() {
        val delegate = InMemoryKeyValueStore()
        val settings = AppSettings(object : KeyValueStore by delegate {
            override fun putString(key: String, value: String?) { error("disk failure") }
        })
        assertFailsWith<IllegalStateException> { settings.setFocusDucking(FocusDucking.Soft) }
        assertFailsWith<IllegalStateException> { settings.setResumeOnFocusGain(false) }
        assertFailsWith<IllegalStateException> { settings.setResumeAfterCall(false) }
        assertEquals(AudioFocusSettings(), settings.state.value.audioFocus)
        assertTrue(delegate.snapshot().isEmpty())
    }
    @Test fun eachOptionIsSearchableInBothLanguagesAndHiddenOnUnsupportedPlayers() {
        val index = SettingsSearchIndex(SettingsTarget.entries.map { SettingsSearchDocument(it, it.name, "Settings") })
        for ((target, queries) in listOf(
            SettingsTarget.FocusDucking to listOf("Ducking volume", "Volume durante le interruzioni", "abbassamento"),
            SettingsTarget.ResumeOnFocusGain to listOf("Resume on focus gain", "Riprendi al ritorno del focus"),
            SettingsTarget.ResumeAfterCall to listOf("Resume after call", "Riprendi dopo le chiamate", "riaggancio"),
        )) for (query in queries) {
            assertEquals(target, index.search(query, true).first(), query)
            assertTrue(target !in index.search(query, true, supportsAudioFocusSettings = false), query)
        }
    }
    private fun configure(settings: AppSettings) {
        settings.setFocusDucking(FocusDucking.Pause)
        settings.setResumeOnFocusGain(false)
        settings.setResumeAfterCall(false)
    }
}
