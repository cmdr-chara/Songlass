package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.runtime.Composable

// iOS has no system Back button; the explicit sheet Back action remains available.
@Composable
internal actual fun MiuixNestedBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
