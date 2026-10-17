package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

// iOS has no back button; edge-swipe navigation is still to be implemented.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
