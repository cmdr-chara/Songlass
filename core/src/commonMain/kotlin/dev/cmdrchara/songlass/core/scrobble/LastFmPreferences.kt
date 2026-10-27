package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.settings.AppPreferences

/**
 * Reads the scrobbling configuration from the persisted preferences.
 *
 * The scrobbler takes this as a lambda instead of depending on [AppPreferences] directly, so the
 * whole feature stays testable with a plain function and never writes a preference by itself.
 */
fun lastFmConfig(preferences: AppPreferences): LastFmConfig = LastFmConfig(
    enabled = preferences.lastFmEnabled,
    credentials = LastFmCredentials(preferences.lastFmApiKey, preferences.lastFmApiSecret)
        .takeIf { it.isComplete },
)
