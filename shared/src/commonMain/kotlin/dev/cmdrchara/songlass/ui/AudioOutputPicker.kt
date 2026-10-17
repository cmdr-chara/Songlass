package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** Native output selection, with system volume controls as the fallback; null when unsupported. */
@Composable
expect fun rememberAudioOutputPicker(): (() -> Unit)?

/** Name of the device audio currently plays on, or null if unknown. Updates as devices change. */
@Composable
expect fun rememberAudioOutputName(): String?
