package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.player.AudioEffect
import dev.cmdrchara.songlass.core.player.AudioEffectsSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import dev.cmdrchara.songlass.core.backup.DataBackup
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import dev.cmdrchara.songlass.core.storage.KeyValueStore

class AudioEffectsSettingsTest {
    @Test fun defaultIsNeutralAndEachOptionPersistsIndependently() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        assertEquals(AudioEffectsSettings(), settings.state.value.audioEffects)
        configure(settings)
        assertEquals(AudioEffectsSettings(-25, 40, 125, 30), settings.state.value.audioEffects)
        assertEquals(settings.state.value, AppSettings(store).state.value)
        assertEquals(setOf("settings.balance_percent", "settings.stereo_expand_percent", "settings.tempo_percent", "settings.reverb_percent"), store.snapshot().keys)
    }
    @Test fun soundResetRemovesEffectsAndOtherAreaResetsKeepThem() {
        val store = InMemoryKeyValueStore(); val settings = AppSettings(store)
        configure(settings); settings.setResumePerTrack(true)
        settings.resetToDefaults(SettingsArea.Appearance)
        assertEquals(125, settings.state.value.audioEffects.tempoPercent)
        settings.resetToDefaults(SettingsArea.Sound)
        assertEquals(AudioEffectsSettings(), settings.state.value.audioEffects)
        assertTrue(settings.state.value.resumePerTrack)
        keys.forEach { assertNull(store.getString(it)) }
    }
    @Test fun malformedDiskValuesFallBackIndividuallyAndInvalidSettersDoNotWrite() {
        val store = InMemoryKeyValueStore()
        keys.zip(listOf("101", "-1", "NaN", "101")).forEach { (key, value) -> store.putString(key, value) }
        val settings = AppSettings(store)
        assertEquals(AudioEffectsSettings(), settings.state.value.audioEffects)
        val before = store.snapshot()
        for (effect in AudioEffect.entries) {
            assertFailsWith<IllegalArgumentException> { settings.setAudioEffect(effect, effect.minimum - 1) }
            assertFailsWith<IllegalArgumentException> { settings.setAudioEffect(effect, effect.maximum + 1) }
        }
        assertEquals(before, store.snapshot())
    }
    @Test fun effectsRoundTripThroughBackupAndOldBackupsRestoreNeutral() {
        val source = InMemoryKeyValueStore(); val settings = AppSettings(source); configure(settings)
        val target = InMemoryKeyValueStore(); val backup = DataBackup(target)
        val preview = backup.preview(DataBackup(source).export(emptyList()).text, emptyList())
        assertTrue(target.snapshot().isEmpty()); backup.apply(preview)
        assertEquals(settings.state.value.audioEffects, AppSettings(target).state.value.audioEffects)
        backup.apply(backup.preview(DataBackup(InMemoryKeyValueStore()).export(emptyList()).text, emptyList()))
        assertEquals(AudioEffectsSettings(), AppSettings(target).state.value.audioEffects)
        keys.forEach { assertNull(target.getString(it)) }
    }
    @Test fun everyOptionIsSearchableInBothLanguagesAndHiddenWhenUnsupported() {
        val index = SettingsSearchIndex(SettingsTarget.entries.map { SettingsSearchDocument(it, it.name, "Settings") })
        for ((target, queries) in listOf(
            SettingsTarget.Balance to listOf("bilanciamento", "balance", "sinistra", "left"),
            SettingsTarget.StereoExpand to listOf("espansione stereo", "stereo expand", "ampiezza", "width"),
            SettingsTarget.Tempo to listOf("tempo di riproduzione", "playback tempo", "velocità", "speed"),
            SettingsTarget.Reverb to listOf("riverbero", "reverb", "ambiente", "room"),
        )) for (query in queries) {
            assertEquals(target, index.search(query, true).first(), query)
            assertTrue(index.search(query, true, supportsAudioEffects = false).isEmpty(), query)
        }
    }
    @Test fun effectsCannotMutateDuringBackupRestore() = runTest {
        val store = InMemoryKeyValueStore(); val gate = RestoreMutationGate(); val settings = AppSettings(store, gate)
        val before = settings.state.value
        gate.restoring {
            configure(settings)
            assertEquals(before, settings.state.value)
            assertTrue(store.snapshot().isEmpty())
        }
        configure(settings)
        assertEquals(125, settings.state.value.audioEffects.tempoPercent)
    }
    @Test fun failedPersistenceCannotPublishUnstoredEffectValues() {
        val delegate = InMemoryKeyValueStore()
        val store = object : KeyValueStore by delegate {
            override fun putString(key: String, value: String?) { error("disk failure") }
        }
        val settings = AppSettings(store)
        for (effect in AudioEffect.entries) {
            assertFailsWith<IllegalStateException> { settings.setAudioEffect(effect, effect.maximum) }
            assertEquals(AudioEffectsSettings(), settings.state.value.audioEffects)
            assertTrue(delegate.snapshot().isEmpty())
        }
    }
    companion object {
        val keys = listOf(AppSettings.BALANCE_KEY, AppSettings.STEREO_EXPAND_KEY, AppSettings.TEMPO_KEY, AppSettings.REVERB_KEY)
        fun configure(settings: AppSettings) {
            listOf(-25, 40, 125, 30).forEachIndexed { i, value -> settings.setAudioEffect(AudioEffect.entries[i], value) }
        }
    }
}
