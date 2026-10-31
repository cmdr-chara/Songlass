package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/**
 * No online covers on iOS yet: `platformHttpTransport()` is null there because no HTTP engine has
 * been exercised on a device. Returning null hides the album entry point and the settings section
 * instead of offering a search that cannot run.
 */
@Composable
actual fun rememberPlatformOnlineArtwork(): OnlineArtworkPlatform? = null

@Composable
actual fun rememberArtworkFolderPicker(): ArtworkFolderPicker? = null
