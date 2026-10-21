package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

// iOS still uses the fictional library and silent player; file import joins native library integration.
@Composable
actual fun rememberLyricsFilePicker(
    requestKey: String,
    onResult: (String, LyricsFileSelection?) -> Boolean,
): LyricsFilePicker? = null
