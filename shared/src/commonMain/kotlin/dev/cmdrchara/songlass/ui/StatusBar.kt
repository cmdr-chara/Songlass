package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** A retained outgoing collection cannot set the chrome of the new active destination. */
internal data class CollectionStatusBarAppearance(val destination: ScreenDestination, val lightIcons: Boolean)

/** Keeps system bar icons legible over the current appearance and modal backdrop. */
@Composable
expect fun SystemBarAppearance(lightIcons: Boolean, navigationLightIcons: Boolean = lightIcons)
