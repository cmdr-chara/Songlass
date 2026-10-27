package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.settings.AppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bridge between persisted preferences and the scrobbler's read-only configuration. */
class LastFmPreferencesTest {
    @Test
    fun scrobblingNeedsBothTheSwitchAndACompleteKeyPair() {
        val defaults = AppPreferences()
        assertFalse(lastFmConfig(defaults).isActive)
        assertNull(lastFmConfig(defaults).credentials)

        // Only a key: cannot sign a request, so the feature stays inactive.
        val keyOnly = defaults.copy(lastFmEnabled = true, lastFmApiKey = "key")
        assertFalse(lastFmConfig(keyOnly).isActive)
        assertNull(lastFmConfig(keyOnly).credentials)

        // A complete pair with the switch off is still inactive: opt-in comes first.
        val switchOff = defaults.copy(lastFmApiKey = "key", lastFmApiSecret = "secret")
        assertFalse(lastFmConfig(switchOff).isActive)
        assertEquals(LastFmCredentials("key", "secret"), lastFmConfig(switchOff).credentials)

        val active = lastFmConfig(switchOff.copy(lastFmEnabled = true))
        assertTrue(active.isActive)
        assertEquals(LastFmCredentials("key", "secret"), active.credentials)
    }

    @Test
    fun blankCredentialsAreNeverComplete() {
        assertFalse(LastFmCredentials("", "").isComplete)
        assertFalse(LastFmCredentials("", "secret").isComplete)
        assertFalse(LastFmCredentials("key", "   ").isComplete)
        assertTrue(LastFmCredentials("key", "secret").isComplete)
    }
}
