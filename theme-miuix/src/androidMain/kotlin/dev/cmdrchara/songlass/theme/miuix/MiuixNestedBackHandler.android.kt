package dev.cmdrchara.songlass.theme.miuix

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun MiuixNestedBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
