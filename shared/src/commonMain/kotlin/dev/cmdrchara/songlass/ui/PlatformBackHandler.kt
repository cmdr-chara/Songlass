package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** Handles the system back action where the platform has one (Android). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
