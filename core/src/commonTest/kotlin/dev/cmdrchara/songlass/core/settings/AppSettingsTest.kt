package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsTest {
    @Test
    fun emptyStoreUsesSystemAppearanceAndDefaultAccessibility() {
        assertEquals(AppPreferences(), AppSettings(InMemoryKeyValueStore()).state.value)
    }

    @Test
    fun preferencesSurviveRecreation() {
        val store = InMemoryKeyValueStore()
        AppSettings(store).apply {
            setAppearance(AppearanceMode.Dark)
            setReduceMotion(true)
            setReduceTransparency(true)
        }

        assertEquals(
            AppPreferences(AppearanceMode.Dark, reduceMotion = true, reduceTransparency = true),
            AppSettings(store).state.value,
        )
    }

    @Test
    fun appearanceUsesStableStoredValues() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        val values = mapOf(
            AppearanceMode.System to "system",
            AppearanceMode.Light to "light",
            AppearanceMode.Dark to "dark",
        )

        for ((appearance, storedValue) in values) {
            settings.setAppearance(appearance)
            assertEquals(storedValue, store.getString("settings.appearance"))
            assertEquals(appearance, AppSettings(store).state.value.appearance)
        }
    }

    @Test
    fun resettingPreferencesToDefaultsPersists() {
        val store = InMemoryKeyValueStore()
        AppSettings(store).apply {
            setAppearance(AppearanceMode.Dark)
            setReduceMotion(true)
            setReduceTransparency(true)
            setAppearance(AppearanceMode.System)
            setReduceMotion(false)
            setReduceTransparency(false)
        }

        assertEquals(AppPreferences(), AppSettings(store).state.value)
    }

    @Test
    fun unknownAppearanceFallsBackWithoutChangingAccessibility() {
        for (storedValue in listOf("", "sepia", "DARK", "dark ")) {
            val store = InMemoryKeyValueStore().apply {
                putString("settings.appearance", storedValue)
                putString("settings.reduce_motion", "true")
                putString("settings.reduce_transparency", "true")
            }

            assertEquals(
                AppPreferences(reduceMotion = true, reduceTransparency = true),
                AppSettings(store).state.value,
            )
        }
    }

    @Test
    fun malformedBooleanFallsBackIndependently() {
        for (storedValue in listOf("", "1", "TRUE", "true ", "enabled")) {
            val store = InMemoryKeyValueStore().apply {
                putString("settings.appearance", "light")
                putString("settings.reduce_motion", storedValue)
                putString("settings.reduce_transparency", "true")
            }
            assertEquals(
                AppPreferences(AppearanceMode.Light, reduceMotion = false, reduceTransparency = true),
                AppSettings(store).state.value,
            )

            store.putString("settings.reduce_motion", "true")
            store.putString("settings.reduce_transparency", storedValue)
            assertEquals(
                AppPreferences(AppearanceMode.Light, reduceMotion = true, reduceTransparency = false),
                AppSettings(store).state.value,
            )
        }
    }

    @Test
    fun changingEachSettingPreservesTheOthersAndUnrelatedData() {
        val store = InMemoryKeyValueStore().apply { putString("future.preference", "preserved") }
        Favorites(store).apply { toggle("track-a"); toggle("track-b") }
        val settings = AppSettings(store)

        settings.setReduceTransparency(true)
        assertEquals(AppPreferences(reduceTransparency = true), AppSettings(store).state.value)

        settings.setReduceMotion(true)
        assertEquals(
            AppPreferences(reduceMotion = true, reduceTransparency = true),
            AppSettings(store).state.value,
        )

        settings.setAppearance(AppearanceMode.Light)
        assertEquals(
            AppPreferences(AppearanceMode.Light, reduceMotion = true, reduceTransparency = true),
            AppSettings(store).state.value,
        )

        settings.setReduceTransparency(false)
        assertEquals(
            AppPreferences(AppearanceMode.Light, reduceMotion = true),
            AppSettings(store).state.value,
        )
        settings.setReduceMotion(false)
        assertEquals(AppPreferences(AppearanceMode.Light), AppSettings(store).state.value)

        assertEquals(listOf("track-a", "track-b"), Favorites(store).ids.value)
        assertEquals("preserved", store.getString("future.preference"))
    }

    @Test
    fun stateEmitsChangesAndSuppressesIdenticalValues() = runTest {
        val settings = AppSettings(InMemoryKeyValueStore())
        val observed = mutableListOf<AppPreferences>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            settings.state.collect { observed += it }
        }

        settings.setAppearance(AppearanceMode.Dark)
        settings.setReduceMotion(true)
        settings.setReduceTransparency(true)
        settings.setReduceTransparency(true)

        assertEquals(
            listOf(
                AppPreferences(),
                AppPreferences(AppearanceMode.Dark),
                AppPreferences(AppearanceMode.Dark, reduceMotion = true),
                AppPreferences(AppearanceMode.Dark, reduceMotion = true, reduceTransparency = true),
            ),
            observed,
        )
    }

    @Test
    fun artworkStyleDefaultsToFullBleedAndPersists() {
        val store = InMemoryKeyValueStore()
        assertEquals(ArtworkStyle.FullBleed, AppSettings(store).state.value.artworkStyle)
        AppSettings(store).setArtworkStyle(ArtworkStyle.Square)
        assertEquals(ArtworkStyle.Square, AppSettings(store).state.value.artworkStyle)
        AppSettings(store).setArtworkStyle(ArtworkStyle.FullBleed)
        assertEquals(ArtworkStyle.FullBleed, AppSettings(store).state.value.artworkStyle)
    }

    @Test
    fun unknownArtworkStyleFallsBackToDefault() {
        val store = InMemoryKeyValueStore().apply { putString("settings.artwork_style", "hologram") }
        assertEquals(ArtworkStyle.FullBleed, AppSettings(store).state.value.artworkStyle)
    }

    @Test
    fun interfaceStyleDefaultsToLiquidGlassAndPersists() {
        val store = InMemoryKeyValueStore()
        assertEquals(InterfaceStyle.LiquidGlass, AppSettings(store).state.value.interfaceStyle)
        AppSettings(store).setInterfaceStyle(InterfaceStyle.MaterialGlass)
        assertEquals(InterfaceStyle.MaterialGlass, AppSettings(store).state.value.interfaceStyle)
        store.putString("settings.interface_style", "holo")
        assertEquals(InterfaceStyle.LiquidGlass, AppSettings(store).state.value.interfaceStyle)
    }
}
