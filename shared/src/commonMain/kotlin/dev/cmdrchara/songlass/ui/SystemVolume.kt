package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** The device's media volume, 0..1, kept in sync with the hardware buttons. */
class SystemVolume(val value: Float, val set: (Float) -> Unit)

/** Null where an app cannot set the system volume (iOS allows only the system's own slider). */
@Composable
expect fun rememberSystemVolume(): SystemVolume?
