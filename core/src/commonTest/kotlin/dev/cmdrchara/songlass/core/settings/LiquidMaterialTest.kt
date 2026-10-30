package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LiquidMaterialTest {
    @Test fun defaultsAndUnknownValuesKeepGlassWithoutMigrationWrites() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        assertEquals(LiquidMaterial.Glass, settings.state.value.liquidMaterial)
        settings.setLiquidMaterial(LiquidMaterial.Glass)
        assertEquals(emptyMap(), store.snapshot())
        store.putString(AppSettings.LIQUID_MATERIAL_KEY, "future")
        settings.reload()
        assertEquals(LiquidMaterial.Glass, settings.state.value.liquidMaterial)
        assertEquals("future", store.getString(AppSettings.LIQUID_MATERIAL_KEY))
    }

    @Test fun materialPersistsAcrossStyleChangesAndReloadAndResetsWithAppearanceOnly() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setLiquidMaterial(LiquidMaterial.Metal)
        settings.setInterfaceStyle(InterfaceStyle.Miuix)
        assertEquals(LiquidMaterial.Metal, AppSettings(store).state.value.liquidMaterial)
        settings.resetToDefaults(SettingsArea.Sound)
        assertEquals(LiquidMaterial.Metal, settings.state.value.liquidMaterial)
        settings.resetToDefaults(SettingsArea.Appearance)
        assertEquals(LiquidMaterial.Glass, settings.state.value.liquidMaterial)
        assertNull(store.getString(AppSettings.LIQUID_MATERIAL_KEY))
        assertEquals(settings.state.value, AppSettings(store).state.value)
    }

    @Test fun restoringOwnsThePreference() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val settings = AppSettings(store, gate)
        gate.restoring { settings.setLiquidMaterial(LiquidMaterial.Metal) }
        assertEquals(emptyMap(), store.snapshot())
        assertEquals(LiquidMaterial.Glass, settings.state.value.liquidMaterial)
    }

    @Test fun finishIsSearchableInBothLanguages() {
        val index = SettingsSearchIndex(SettingsTarget.entries.map { SettingsSearchDocument(it, it.name, "") })
        for (query in listOf("liquid metal", "metallo", "cromo", "finitura", "chrome", "reflections")) {
            assertEquals(SettingsTarget.LiquidMaterial, index.search(query, true).first(), query)
        }
        assertTrue(AppSettings.LIQUID_MATERIAL_KEY.startsWith("settings."))
    }
}
