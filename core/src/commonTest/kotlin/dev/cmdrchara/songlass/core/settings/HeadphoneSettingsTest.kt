package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.playback.HeadphoneSettings
import dev.cmdrchara.songlass.core.storage.*
import dev.cmdrchara.songlass.core.backup.DataBackup
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HeadphoneSettingsTest {
    @Test fun defaultsAreOptInAndLegacyPreferenceSeedsBothKindsWithoutWriting() {
        val store = InMemoryKeyValueStore()
        assertEquals(HeadphoneSettings(), AppSettings(store).state.value.headphones)
        store.putString(HeadphoneSettings.LEGACY_KEY, "true")
        val before = store.snapshot()
        assertEquals(HeadphoneSettings(true, true), AppSettings(store).state.value.headphones)
        assertEquals(before, store.snapshot())
    }
    @Test fun allThreePreferencesPersistAndIndependentOverrideKeepsTheOtherLegacyValue() {
        val store = InMemoryKeyValueStore(); store.putString(HeadphoneSettings.LEGACY_KEY, "true")
        val settings = AppSettings(store)
        settings.setResumeWired(false); settings.setBluetoothCommandDelay(5000)
        assertEquals(HeadphoneSettings(false, true, 5000), AppSettings(store).state.value.headphones)
        settings.setResumeBluetooth(false)
        assertEquals(HeadphoneSettings(false, false, 5000), settings.state.value.headphones)
        assertEquals(HeadphoneSettings.keys + HeadphoneSettings.LEGACY_KEY, store.snapshot().keys)
        settings.setResumeOnReconnect(true)
        assertEquals(HeadphoneSettings(true, true, 5000), AppSettings(store).state.value.headphones)
    }
    @Test fun malformedExplicitValuesDoNotReenableResumeFromLegacyTrue() {
        val store = InMemoryKeyValueStore()
        store.putString(HeadphoneSettings.LEGACY_KEY, "true")
        HeadphoneSettings.keys.forEach { store.putString(it, "broken") }
        val before = store.snapshot()
        assertEquals(HeadphoneSettings(), AppSettings(store).state.value.headphones)
        assertEquals(before, store.snapshot())
    }
    @Test fun invalidDurationsFailWithoutPublishingOrWriting() {
        val store = InMemoryKeyValueStore(); val settings = AppSettings(store)
        for (duration in listOf(-1L, 1L, 999L, 4000L, 21000L, Long.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { settings.setBluetoothCommandDelay(duration) }
        }
        assertTrue(store.snapshot().isEmpty())
        assertEquals(HeadphoneSettings(), settings.state.value.headphones)
    }
    @Test fun playerResetRemovesNewAndLegacyKeysButSoundResetPreservesThem() {
        val store = InMemoryKeyValueStore(); val settings = AppSettings(store)
        settings.setResumeOnReconnect(true); settings.setBluetoothCommandDelay(20000)
        settings.resetToDefaults(SettingsArea.Sound)
        assertEquals(HeadphoneSettings(true, true, 20000), settings.state.value.headphones)
        settings.resetToDefaults(SettingsArea.Player)
        assertEquals(HeadphoneSettings(), settings.state.value.headphones)
        assertTrue(store.snapshot().isEmpty())
    }
    @Test fun backupRoundTripAndLegacyArchivesPreserveTheirMeaning() {
        val source = InMemoryKeyValueStore(); val settings = AppSettings(source)
        settings.setResumeWired(true); settings.setResumeBluetooth(false); settings.setBluetoothCommandDelay(3000)
        val target = InMemoryKeyValueStore(); val backup = DataBackup(target)
        backup.apply(backup.preview(DataBackup(source).export(emptyList()).text, emptyList()))
        assertEquals(settings.state.value.headphones, AppSettings(target).state.value.headphones)
        val legacy = InMemoryKeyValueStore(); legacy.putString(HeadphoneSettings.LEGACY_KEY, "true")
        backup.apply(backup.preview(DataBackup(legacy).export(emptyList()).text, emptyList()))
        assertEquals(HeadphoneSettings(true, true), AppSettings(target).state.value.headphones)
        backup.apply(backup.preview(DataBackup(InMemoryKeyValueStore()).export(emptyList()).text, emptyList()))
        assertEquals(HeadphoneSettings(), AppSettings(target).state.value.headphones)
    }
    @Test fun failedWritesAndRestoreGateNeverPublishUnstoredOptions() = runTest {
        val delegate = InMemoryKeyValueStore()
        val settings = AppSettings(object : KeyValueStore by delegate {
            override fun putString(key: String, value: String?) { error("disk failure") }
        })
        assertFailsWith<IllegalStateException> { settings.setResumeWired(true) }
        assertFailsWith<IllegalStateException> { settings.setResumeBluetooth(true) }
        assertFailsWith<IllegalStateException> { settings.setBluetoothCommandDelay(1000) }
        assertEquals(HeadphoneSettings(), settings.state.value.headphones)
        val gate = RestoreMutationGate(); val guarded = AppSettings(delegate, gate)
        gate.restoring {
            guarded.setResumeWired(true); guarded.setResumeBluetooth(true); guarded.setBluetoothCommandDelay(2000)
            guarded.setResumeOnReconnect(true)
        }
        assertEquals(HeadphoneSettings(), guarded.state.value.headphones); assertTrue(delegate.snapshot().isEmpty())
    }
    @Test fun eachOptionHasBilingualSearchAndUnsupportedTargetsAreHidden() {
        val index = SettingsSearchIndex(SettingsTarget.entries.map { SettingsSearchDocument(it, it.name, "Settings") })
        for ((target, queries) in listOf(
            SettingsTarget.ResumeOnReconnect to listOf("Resume on wired headset", "Riprendi con cuffie cablate"),
            SettingsTarget.ResumeBluetooth to listOf("Resume on Bluetooth", "Riprendi con Bluetooth"),
            SettingsTarget.BluetoothCommandDelay to listOf("Bluetooth command filter", "Filtro comandi Bluetooth", "Ignore Bluetooth commands"),
        )) for (query in queries) {
            assertEquals(target, index.search(query, true).first(), query)
            assertTrue(target !in index.search(query, true, supportsHeadphoneSettings = false), query)
        }
    }
}
