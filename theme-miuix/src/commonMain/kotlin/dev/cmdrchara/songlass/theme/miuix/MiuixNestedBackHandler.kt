package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.runtime.Composable

/** Registers nested sheet navigation inside the platform popup, after its dismiss handler. */
@Composable
internal expect fun MiuixNestedBackHandler(enabled: Boolean, onBack: () -> Unit)
