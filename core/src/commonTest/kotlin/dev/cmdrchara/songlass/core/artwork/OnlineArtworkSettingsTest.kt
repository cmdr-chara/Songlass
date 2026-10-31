package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.SettingsArea
import dev.cmdrchara.songlass.core.settings.SettingsSearchDocument
import dev.cmdrchara.songlass.core.settings.SettingsSearchIndex
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two online-cover preferences, their storage keys, the Appearance reset and the settings
 * search entries. Absent preferences use automatic lookup; an explicit disabled choice survives.
 */
class OnlineArtworkSettingsTest {
    private val documents = SettingsTarget.entries.map { SettingsSearchDocument(it, "", "") }

    @Test
    fun automaticCoversAreOnByDefaultAndWifiOnlyIsOn() {
        val preferences: AppPreferences = AppSettings(InMemoryKeyValueStore()).state.value
        assertTrue(preferences.onlineArtworkEnabled, "missing covers are fetched automatically")
        assertTrue(preferences.onlineArtworkWifiOnly, "downloads wait for Wi-Fi unless allowed")
    }

    @Test
    fun explicitlyDisabledOnlineCoversRemainDisabledAfterTheDefaultChanges() {
        val store = InMemoryKeyValueStore()
        store.putString(AppSettings.ONLINE_ARTWORK_KEY, "false")
        val settings = AppSettings(store)
        assertFalse(settings.state.value.onlineArtworkEnabled)
        assertTrue(settings.state.value.onlineArtworkWifiOnly)
        assertEquals("false", store.getString(AppSettings.ONLINE_ARTWORK_KEY))
    }

    @Test
    fun bothValuesSurviveARestart() {
        val store = InMemoryKeyValueStore()
        AppSettings(store).apply {
            setOnlineArtworkEnabled(true)
            setOnlineArtworkWifiOnly(false)
        }
        val reloaded = AppSettings(store)
        assertTrue(reloaded.state.value.onlineArtworkEnabled)
        assertFalse(reloaded.state.value.onlineArtworkWifiOnly)
        assertEquals("true", store.getString(AppSettings.ONLINE_ARTWORK_KEY))
        assertEquals("false", store.getString(AppSettings.ONLINE_ARTWORK_WIFI_KEY))
    }

    @Test
    fun resettingTheAppearancePageRestoresBothDefaults() {
        val settings = AppSettings(InMemoryKeyValueStore())
        settings.setOnlineArtworkEnabled(true)
        settings.setOnlineArtworkWifiOnly(false)
        settings.resetToDefaults(SettingsArea.Appearance)
        assertTrue(settings.state.value.onlineArtworkEnabled)
        assertTrue(settings.state.value.onlineArtworkWifiOnly)
    }

    @Test
    fun otherPagesKeepTheCoverPreferences() {
        val settings = AppSettings(InMemoryKeyValueStore())
        settings.setOnlineArtworkEnabled(true)
        settings.setOnlineArtworkWifiOnly(false)
        for (area in SettingsArea.entries.filter { it != SettingsArea.Appearance }) {
            settings.resetToDefaults(area)
        }
        assertTrue(settings.state.value.onlineArtworkEnabled)
        assertFalse(settings.state.value.onlineArtworkWifiOnly)
    }

    @Test
    fun settingsSearchReachesBothSwitchesInEitherLanguage() {
        val index = SettingsSearchIndex(documents)
        fun search(query: String, onlineArtwork: Boolean = true) =
            index.search(query, supportsAmllLyrics = true, supportsOnlineArtwork = onlineArtwork)

        assertEquals(SettingsTarget.OnlineArtworkWifiOnly, search("wifi").first())
        assertTrue(SettingsTarget.OnlineArtworkEnabled in search("copertine online"))
        assertTrue(SettingsTarget.OnlineArtworkEnabled in search("online covers"))
        assertTrue(SettingsTarget.OnlineArtworkEnabled in search("itunes"))
        assertTrue(SettingsTarget.OnlineArtworkWifiOnly in search("dati mobili"))
        assertTrue(SettingsTarget.OnlineArtworkEnabled in search("download"))
        assertTrue(SettingsTarget.OnlineArtworkEnabled in search("privacy"))
    }

    @Test
    fun theSwitchesAreNotSearchableWhereThePlatformHasNoEngine() {
        val index = SettingsSearchIndex(documents)
        for (query in listOf("copertine online", "online covers", "wifi", "itunes")) {
            assertTrue(
                index.search(query, supportsAmllLyrics = true, supportsOnlineArtwork = false).isEmpty(),
                "query: $query",
            )
        }
    }
}
