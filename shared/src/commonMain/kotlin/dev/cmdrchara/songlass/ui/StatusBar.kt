package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** Keeps system bar icons legible over the current appearance and modal backdrop. */
@Composable
expect fun SystemBarAppearance(lightIcons: Boolean, navigationLightIcons: Boolean = lightIcons)
