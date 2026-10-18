package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

// iOS lets apps change the volume only through MPVolumeView (a UIKit view); to be embedded with
// the real iOS playback integration.
@Composable
actual fun rememberSystemVolume(): SystemVolume? = null
