package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class AppIconSelectionTest {
    @Test
    fun automaticTracksEveryInterfaceInBothAppearances() {
        val expected = listOf(
            Triple(InterfaceStyle.LiquidGlass, LauncherIconVariant.LiquidLight, LauncherIconVariant.LiquidDark),
            Triple(InterfaceStyle.MaterialGlass, LauncherIconVariant.MaterialLight, LauncherIconVariant.MaterialDark),
            Triple(InterfaceStyle.Miuix, LauncherIconVariant.MiuixLight, LauncherIconVariant.MiuixDark),
        )
        for ((style, light, dark) in expected) {
            assertEquals(light, AppIconSelection(AppIconChoice.Automatic, style, AppearanceMode.System).resolve(false))
            assertEquals(dark, AppIconSelection(AppIconChoice.Automatic, style, AppearanceMode.System).resolve(true))
            assertEquals(light, AppIconSelection(AppIconChoice.Automatic, style, AppearanceMode.Light).resolve(true))
            assertEquals(dark, AppIconSelection(AppIconChoice.Automatic, style, AppearanceMode.Dark).resolve(false))
        }
    }

    @Test
    fun explicitFinishesIgnoreTheInterfaceAndLegacyIconsKeepTheirColors() {
        for (style in InterfaceStyle.entries) for (dark in listOf(false, true)) {
            assertEquals(LauncherIconVariant.Violet, AppIconChoice.Violet.resolve(style, dark))
            assertEquals(LauncherIconVariant.Night, AppIconChoice.Night.resolve(style, dark))
            assertEquals(if (dark) LauncherIconVariant.LiquidDark else LauncherIconVariant.LiquidLight,
                AppIconChoice.LiquidGlass.resolve(style, dark))
            assertEquals(if (dark) LauncherIconVariant.MaterialDark else LauncherIconVariant.MaterialLight,
                AppIconChoice.MaterialGlass.resolve(style, dark))
            assertEquals(if (dark) LauncherIconVariant.MiuixDark else LauncherIconVariant.MiuixLight,
                AppIconChoice.Miuix.resolve(style, dark))
        }
    }

    @Test
    fun everyChoiceRoundTripsWithoutChangingTheLegacyDefault() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        assertEquals(AppIconChoice.Violet, settings.state.value.appIcon)
        for (choice in AppIconChoice.entries) {
            settings.setAppIcon(choice)
            assertEquals(choice, AppSettings(store).state.value.appIcon)
            assertEquals(choice, AppIconChoice.fromStorageKey(choice.storageKey))
        }
        settings.resetToDefaults(SettingsArea.Appearance)
        assertEquals(AppIconChoice.Violet, AppSettings(store).state.value.appIcon)
        assertEquals(AppIconChoice.Violet, AppIconChoice.fromStorageKey("future-finish"))
    }
}
