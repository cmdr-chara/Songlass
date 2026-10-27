package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Text size and background blur: defaults, storage compatibility, reset and the font-scale math. */
class AppearanceScaleTest {
    @Test
    fun defaultsKeepTheCurrentRenderingAndWriteNothing() {
        val store = InMemoryKeyValueStore()
        val prefs = AppSettings(store).state.value
        assertEquals(1f, prefs.textScale)
        assertEquals(BlurIntensity.Default, prefs.blurIntensity)
        assertEquals(emptyMap(), store.snapshot(), "opening the settings must not create keys")
    }

    @Test
    fun everyOfferedTextScaleRoundTripsThroughStorage() {
        for (scale in AppSettings.TEXT_SCALE_CHOICES) {
            val store = InMemoryKeyValueStore()
            val settings = AppSettings(store)
            // Move away from the default first: storing the default again is a no-op otherwise.
            settings.setTextScale(if (scale == 1f) 1.5f else 1f)
            settings.setTextScale(scale)
            assertEquals(scale.toString(), store.getString(AppSettings.TEXT_SCALE_KEY))
            assertEquals(scale, AppSettings(store).state.value.textScale, "reload keeps $scale")
        }
    }

    @Test
    fun unsupportedTextScalesAreRejectedWithoutChangingStateOrStorage() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setTextScale(1.3f)
        for (invalid in listOf(1.1f, 0.7f, 2f, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { settings.setTextScale(invalid) }
            assertEquals(1.3f, settings.state.value.textScale)
            assertEquals("1.3", store.getString(AppSettings.TEXT_SCALE_KEY))
        }
    }

    @Test
    fun unknownStoredTextScaleFallsBackWithoutRewritingTheValue() {
        for (stored in listOf("0.9", "150 %", "huge", "", "NaN", "Infinity")) {
            val store = InMemoryKeyValueStore().apply { putString(AppSettings.TEXT_SCALE_KEY, stored) }
            assertEquals(1f, AppSettings(store).state.value.textScale, stored)
            assertEquals(stored, store.getString(AppSettings.TEXT_SCALE_KEY))
        }
        // A decimal string written by an older build keeps working (tolerance comparison).
        val store = InMemoryKeyValueStore().apply { putString(AppSettings.TEXT_SCALE_KEY, "1.15") }
        assertEquals(1.15f, AppSettings(store).state.value.textScale)
    }

    @Test
    fun settingTheDefaultAgainDoesNotWriteAKey() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setTextScale(1f)
        settings.setBlurIntensity(BlurIntensity.Default)
        assertNull(store.getString(AppSettings.TEXT_SCALE_KEY))
        assertNull(store.getString(AppSettings.BLUR_INTENSITY_KEY))
    }

    @Test
    fun everyBlurIntensityRoundTripsAndUnknownValuesFallBack() {
        for (intensity in BlurIntensity.entries) {
            val store = InMemoryKeyValueStore()
            val settings = AppSettings(store)
            // Move away from the default first: storing the default again is a no-op otherwise.
            settings.setBlurIntensity(if (intensity == BlurIntensity.Default) BlurIntensity.Off else BlurIntensity.Default)
            settings.setBlurIntensity(intensity)
            assertEquals(intensity.storageKey, store.getString(AppSettings.BLUR_INTENSITY_KEY))
            assertEquals(intensity, AppSettings(store).state.value.blurIntensity)
        }
        val store = InMemoryKeyValueStore().apply { putString(AppSettings.BLUR_INTENSITY_KEY, "ultra") }
        assertEquals(BlurIntensity.Default, AppSettings(store).state.value.blurIntensity)
        assertEquals("ultra", store.getString(AppSettings.BLUR_INTENSITY_KEY))
    }

    @Test
    fun restoringTheAppearancePageResetsBothSettingsAndLeavesOtherPagesAlone() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setTextScale(1.5f)
        settings.setBlurIntensity(BlurIntensity.High)
        settings.setReduceMotion(true)
        settings.setCrossfadeDuration(4_000L)
        settings.setAlbumLayout(AlbumLayout.List)
        settings.resetToDefaults(SettingsArea.Appearance)
        val state = settings.state.value
        assertEquals(1f, state.textScale)
        assertEquals(BlurIntensity.Default, state.blurIntensity)
        assertEquals(false, state.reduceMotion)
        assertEquals(4_000L, state.crossfadeDurationMs, "sound page untouched")
        assertEquals(AlbumLayout.List, state.albumLayout, "library page untouched")
        assertNull(store.getString(AppSettings.TEXT_SCALE_KEY))
        assertNull(store.getString(AppSettings.BLUR_INTENSITY_KEY))
        assertEquals(state, AppSettings(store).state.value)
        for (key in listOf(AppSettings.TEXT_SCALE_KEY, AppSettings.BLUR_INTENSITY_KEY)) {
            assertTrue(key.startsWith("settings."), "$key travels with the other preferences in a backup")
        }
    }

    @Test
    fun restoringOwnsTheDataSoTheNewSettersAreIgnored() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val settings = AppSettings(store, gate)
        gate.restoring {
            settings.setTextScale(1.3f)
            settings.setBlurIntensity(BlurIntensity.Low)
        }
        assertEquals(1f, settings.state.value.textScale)
        assertEquals(BlurIntensity.Default, settings.state.value.blurIntensity)
        assertEquals(emptyMap(), store.snapshot())
    }

    @Test
    fun combinedFontScaleMultipliesAndClamps() {
        assertEquals(1f, AppFontScale.resolve(1f, 1f))
        assertEquals(1.15f, AppFontScale.resolve(1f, 1.15f))
        assertEquals(1.5f, AppFontScale.resolve(1f, 1.5f))
        assertEquals(1.275f, AppFontScale.resolve(0.85f, 1.5f), absoluteTolerance = 0.0001f)
        assertEquals(1.495f, AppFontScale.resolve(1.3f, 1.15f), absoluteTolerance = 0.0001f)
        // The largest system setting times the largest app choice would be 3x: clamp to 2x.
        assertEquals(AppFontScale.MAX, AppFontScale.resolve(2f, 1.5f))
        assertEquals(AppFontScale.MAX, AppFontScale.resolve(1.5f, 1.5f))
        // The smallest system setting times the smallest app choice would be ~0.72x: clamp to 0.85x.
        assertEquals(AppFontScale.MIN, AppFontScale.resolve(0.85f, 0.85f))
    }

    @Test
    fun invalidFontScalesFallBackToTheOtherValueOrOne() {
        assertEquals(1.15f, AppFontScale.resolve(Float.NaN, 1.15f))
        assertEquals(1.15f, AppFontScale.resolve(0f, 1.15f))
        assertEquals(1.15f, AppFontScale.resolve(-1f, 1.15f))
        assertEquals(1f, AppFontScale.resolve(1f, Float.NaN))
        assertEquals(1f, AppFontScale.resolve(1f, Float.POSITIVE_INFINITY))
        assertEquals(1f, AppFontScale.resolve(1f, 0f))
    }
}
