package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

// The demo player has no native audio session to route. Enable with real iOS playback integration.
@Composable
actual fun rememberAudioOutputPicker(): (() -> Unit)? = null

// iOS still uses the silent demo player: there is no real route to show.
@Composable
actual fun rememberAudioOutputName(): String? = null
