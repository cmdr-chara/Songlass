package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.player.TransportFadeSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.*

class TransportFadeSettingsTest {
    @Test fun freshAndLegacyPreferencesKeepAllFadesOff() {
        val store = InMemoryKeyValueStore()
        store.putString(AppSettings.CROSSFADE_KEY, "1250")
        assertEquals(TransportFadeSettings(), AppSettings(store).state.value.transportFades)
        assertEquals("1250", store.getString(AppSettings.CROSSFADE_KEY))
    }

    @Test fun independentExactDurationsSurviveRecreation() {
        val store = InMemoryKeyValueStore()
        AppSettings(store).apply { setPauseFadeDuration(123); setStopFadeDuration(456); setSeekFadeDuration(789) }
        assertEquals(TransportFadeSettings(123, 456, 789), AppSettings(store).state.value.transportFades)
        assertEquals("123", store.getString(AppSettings.PAUSE_FADE_KEY))
        assertEquals("456", store.getString(AppSettings.STOP_FADE_KEY))
        assertEquals("789", store.getString(AppSettings.SEEK_FADE_KEY))
    }

    @Test fun soundResetRemovesAllThreeKeysAndPreservesOtherPages() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store).apply {
            setPauseFadeDuration(100); setStopFadeDuration(200); setSeekFadeDuration(300)
            setResumeOnReconnect(true)
        }
        settings.resetToDefaults(SettingsArea.Player)
        assertEquals(TransportFadeSettings(100, 200, 300), settings.state.value.transportFades)
        settings.setResumeOnReconnect(true)
        settings.resetToDefaults(SettingsArea.Sound)
        assertEquals(TransportFadeSettings(), settings.state.value.transportFades)
        assertTrue(settings.state.value.resumeOnReconnect)
        for (key in keys) assertNull(store.getString(key))
        assertEquals(settings.state.value, AppSettings(store).state.value)
    }

    @Test fun malformedPersistenceIsOffAndInvalidSettersDoNotMutate() {
        for (value in listOf("-1", "3001", "1.5", "100 ms", "9223372036854775808")) {
            val store = InMemoryKeyValueStore()
            keys.forEach { store.putString(it, value) }
            assertEquals(TransportFadeSettings(), AppSettings(store).state.value.transportFades)
        }
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        for (value in listOf(-1L, 3_001L, Long.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { settings.setPauseFadeDuration(value) }
            assertFailsWith<IllegalArgumentException> { settings.setStopFadeDuration(value) }
            assertFailsWith<IllegalArgumentException> { settings.setSeekFadeDuration(value) }
        }
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun editorAcceptsExactIntegersAndRejectsInvalidDrafts() {
        for ((text, value) in listOf("0" to 0L, "1" to 1L, " 00125 " to 125L, "3000" to 3_000L))
            assertEquals(value, parseTransportFadeDurationMs(text))
        for (text in listOf("", " ", "-1", "+1", "3001", "0.5", "1,000", "1e3", "100 ms", "１００", "9223372036854775808"))
            assertNull(parseTransportFadeDurationMs(text), text)
    }

    @Test fun allThreeOptionsRoundTripThroughBackupAndLegacyRestoreDisablesThem() {
        val source = InMemoryKeyValueStore()
        val original = AppSettings(source).apply { setPauseFadeDuration(123); setStopFadeDuration(456); setSeekFadeDuration(789) }
        val target = InMemoryKeyValueStore()
        val settings = AppSettings(target)
        val backup = DataBackup(target)
        val preview = backup.preview(DataBackup(source).export(emptyList()).text, emptyList())
        assertTrue(target.snapshot().isEmpty())
        backup.apply(preview)
        settings.reload()
        assertEquals(original.state.value.transportFades, settings.state.value.transportFades)
        backup.apply(backup.preview(DataBackup(InMemoryKeyValueStore()).export(emptyList()).text, emptyList()))
        settings.reload()
        assertEquals(TransportFadeSettings(), settings.state.value.transportFades)
        for (key in keys) assertNull(target.getString(key))
    }

    @Test fun searchHasSeparateItalianAndEnglishTargetsAndHidesUnsupportedControls() {
        val index = SettingsSearchIndex(SettingsTarget.entries.map { SettingsSearchDocument(it, it.name, "Settings") })
        for ((target, queries) in listOf(
            SettingsTarget.PauseFadeDuration to listOf("dissolvenza pausa", "pause fade", "pausa ms"),
            SettingsTarget.StopFadeDuration to listOf("dissolvenza stop", "stop fade", "arresto ms"),
            SettingsTarget.SeekFadeDuration to listOf("dissolvenza spostamento", "seek fade", "cursore ms"),
        )) for (query in queries) {
            assertEquals(listOf(target), index.search(query, true), query)
            assertTrue(index.search(query, true, supportsTransportFades = false).isEmpty(), query)
        }
    }

    companion object {
        private val keys = listOf(AppSettings.PAUSE_FADE_KEY, AppSettings.STOP_FADE_KEY, AppSettings.SEEK_FADE_KEY)
    }
}
