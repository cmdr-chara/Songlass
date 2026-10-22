package dev.cmdrchara.songlass.core.onboarding

import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.InterfaceStyle
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingTest {
    private val pixel = DeviceInfo.android("Google", "google")

    @Test
    fun eachPlatformStartsInItsOwnDesignLanguage() {
        assertEquals(InterfaceStyle.LiquidGlass, StyleRecommendation.forDevice(DeviceInfo.Apple))
        assertEquals(InterfaceStyle.MaterialGlass, StyleRecommendation.forDevice(pixel))
        for ((maker, brand) in listOf("Xiaomi" to "POCO", "Xiaomi" to "Redmi", "Xiaomi" to "Xiaomi", "POCO" to "POCO", "REDMI" to "")) {
            assertEquals(InterfaceStyle.Miuix, StyleRecommendation.forDevice(DeviceInfo.android(maker, brand)), "$maker / $brand")
        }
    }

    @Test
    fun otherAndroidPhonesGetMaterialAndUnknownNamesNeverBreakAnything() {
        for ((maker, brand) in listOf("samsung" to "samsung", "OnePlus" to "OnePlus", "" to "", null to null, "Nothing" to "Nothing")) {
            assertEquals(InterfaceStyle.MaterialGlass, StyleRecommendation.forDevice(DeviceInfo.android(maker, brand)))
            assertEquals(DeviceFamily.OtherAndroid, DeviceInfo.android(maker, brand).family)
        }
        assertEquals(DeviceFamily.Pixel, DeviceInfo.android(" GOOGLE ", "").family)
        // A phone that is not made by Xiaomi but is sold with "Mi" in its name is not guessed at.
        assertEquals(DeviceFamily.OtherAndroid, DeviceInfo.android("Mimic Corp", "Mimic").family)
    }

    @Test
    fun aFreshInstallSeesTheWelcomeInItsPlatformStyle() {
        val store = InMemoryKeyValueStore()
        val onboarding = Onboarding(store, DeviceInfo.android("Xiaomi", "POCO"))
        assertTrue(onboarding.visible.value)
        assertEquals(InterfaceStyle.Miuix, AppSettings(store).state.value.interfaceStyle)
    }

    @Test
    fun theWelcomeSurvivesARestartUntilItIsFinished() {
        val store = InMemoryKeyValueStore()
        Onboarding(store, pixel)
        // Starting wrote a style, which must not make the next launch think the person is an existing user.
        val again = Onboarding(store, pixel)
        assertTrue(again.visible.value)
        again.complete()
        assertFalse(again.visible.value)
        assertFalse(Onboarding(store, pixel).visible.value)
    }

    @Test
    fun anExistingUserNeverSeesItAndKeepsTheirStyle() {
        val store = InMemoryKeyValueStore()
        AppSettings(store).setInterfaceStyle(InterfaceStyle.LiquidGlass)
        store.putString("favorites.ids", "1")
        val onboarding = Onboarding(store, DeviceInfo.android("Xiaomi", "POCO"))
        assertFalse(onboarding.visible.value)
        assertEquals(InterfaceStyle.LiquidGlass, AppSettings(store).state.value.interfaceStyle)
        assertEquals("true", store.getString(Onboarding.DONE_KEY))
    }

    @Test
    fun aChosenStyleIsNeverOverwrittenByTheRecommendation() {
        val store = InMemoryKeyValueStore().apply { putString(AppSettings.INTERFACE_STYLE_KEY, "liquid_glass") }
        // The key alone already makes this an existing store, so nothing changes.
        Onboarding(store, DeviceInfo.android("Xiaomi", "POCO"))
        assertEquals(InterfaceStyle.LiquidGlass, AppSettings(store).state.value.interfaceStyle)
    }

    @Test
    fun withoutADeviceItStaysOffAndWritesNothing() {
        val store = InMemoryKeyValueStore()
        val onboarding = Onboarding(store, device = null)
        assertFalse(onboarding.visible.value)
        assertTrue(store.snapshot()!!.isEmpty())
    }

    @Test
    fun itsKeysStayOutOfBackups() {
        assertTrue(Onboarding.STARTED_KEY.startsWith("onboarding."))
        assertTrue(Onboarding.DONE_KEY.startsWith("onboarding."))
        assertFalse(listOf("settings.", "library.", "home.").any { Onboarding.DONE_KEY.startsWith(it) })
    }

    @Test
    fun restartingShowsItAgainAndCompletingEndsIt() {
        val store = InMemoryKeyValueStore()
        val first = Onboarding(store, pixel)
        first.complete()
        first.restart()
        assertTrue(first.visible.value)
        assertNull(store.getString(Onboarding.DONE_KEY))
        assertTrue(Onboarding(store, pixel).visible.value)
    }
}
