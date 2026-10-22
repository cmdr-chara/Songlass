package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.player.ReplayGainSettings
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.backup.DataBackup
import kotlin.test.*

class ReplayGainProcessingSettingsTest {
    @Test fun defaultsKeepThePreviousPlaybackPath() {
        val store = InMemoryKeyValueStore()
        store.putString(AppSettings.REPLAY_GAIN_KEY, "track")
        val state = AppSettings(store).state.value
        assertEquals(ReplayGainMode.Track, state.replayGain)
        assertEquals(ReplayGainSettings(), state.replayGainSettings)
        assertFalse(state.replayGainSettings.enabled)
    }
    @Test fun allFourIndependentOptionsPersistAndReload() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store).apply {
            setReplayGainProcessing(true); setReplayGainPreamp(7); setReplayGainMissingPreamp(-9); setReplayGainLimiter(false)
        }
        assertEquals(ReplayGainSettings(true, 7, -9, false), settings.state.value.replayGainSettings)
        assertEquals(settings.state.value, AppSettings(store).state.value)
        assertTrue(keys.all { store.getString(it) != null })
    }
    @Test fun soundResetRemovesOnlyItsKeysAndOtherResetKeepsTuning() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store).apply {
            setReplayGainProcessing(true); setReplayGainPreamp(20); setReplayGainMissingPreamp(-20); setReplayGainLimiter(false)
            setResumePerTrack(true)
        }
        settings.resetToDefaults(SettingsArea.Library)
        assertEquals(ReplayGainSettings(true, 20, -20, false), settings.state.value.replayGainSettings)
        settings.resetToDefaults(SettingsArea.Sound)
        assertEquals(ReplayGainSettings(), settings.state.value.replayGainSettings)
        assertTrue(settings.state.value.resumePerTrack)
        keys.forEach { assertNull(store.getString(it)) }
    }
    @Test fun malformedStoredValuesFallBackAndInvalidSettersPreserveState() {
        val store = InMemoryKeyValueStore()
        store.putString(AppSettings.RG_PROCESSING_KEY, "yes")
        store.putString(AppSettings.RG_PREAMP_KEY, "21")
        store.putString(AppSettings.RG_MISSING_PREAMP_KEY, "-21")
        store.putString(AppSettings.RG_LIMITER_KEY, "bad")
        val settings = AppSettings(store)
        assertEquals(ReplayGainSettings(), settings.state.value.replayGainSettings)
        val before = store.snapshot()
        assertFailsWith<IllegalArgumentException> { settings.setReplayGainPreamp(21) }
        assertFailsWith<IllegalArgumentException> { settings.setReplayGainMissingPreamp(-21) }
        assertEquals(before, store.snapshot())
    }
    @Test fun allOptionsRoundTripThroughBackupAndLegacyRestoresDefaults() {
        val source = InMemoryKeyValueStore()
        val original = AppSettings(source).apply {
            setReplayGainProcessing(true); setReplayGainPreamp(4); setReplayGainMissingPreamp(-3); setReplayGainLimiter(false)
        }
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target)
        val preview = backup.preview(DataBackup(source).export(emptyList()).text, emptyList())
        assertTrue(target.snapshot().isEmpty())
        backup.apply(preview)
        assertEquals(original.state.value.replayGainSettings, AppSettings(target).state.value.replayGainSettings)
        backup.apply(backup.preview(DataBackup(InMemoryKeyValueStore()).export(emptyList()).text, emptyList()))
        assertEquals(ReplayGainSettings(), AppSettings(target).state.value.replayGainSettings)
        keys.forEach { assertNull(target.getString(it)) }
    }
    @Test fun italianEnglishSearchReachesEveryOptionAndRespectsPlatformSupport() {
        val index = SettingsSearchIndex(SettingsTarget.entries.map { SettingsSearchDocument(it, it.name, "Settings") })
        for ((target, queries) in listOf(
            SettingsTarget.ReplayGainProcessing to listOf("ReplayGain avanzato", "advanced ReplayGain"),
            SettingsTarget.ReplayGainPreamp to listOf("preamplificazione con tag", "preamp with tags"),
            SettingsTarget.ReplayGainMissingPreamp to listOf("preamplificazione senza tag", "preamp without tags"),
            SettingsTarget.ReplayGainLimiter to listOf("limitatore ReplayGain", "ReplayGain limiter"),
        )) for (query in queries) {
            assertEquals(target, index.search(query, true).first(), query)
            assertTrue(index.search(query, true, supportsReplayGainProcessing = false).isEmpty(), query)
        }
    }
    companion object { val keys = listOf(AppSettings.RG_PROCESSING_KEY, AppSettings.RG_PREAMP_KEY, AppSettings.RG_MISSING_PREAMP_KEY, AppSettings.RG_LIMITER_KEY) }
}
