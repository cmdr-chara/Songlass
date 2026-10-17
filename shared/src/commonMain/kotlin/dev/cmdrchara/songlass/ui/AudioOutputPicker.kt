package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** Native output selection, with system volume controls as the fallback; null when unsupported. */
@Composable
expect fun rememberAudioOutputPicker(): (() -> Unit)?
