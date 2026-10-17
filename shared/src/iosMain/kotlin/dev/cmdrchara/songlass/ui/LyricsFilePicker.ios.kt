package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.model.Track

// iOS still uses the fictional library and silent player; file import joins native library integration.
@Composable
actual fun rememberLyricsFilePicker(onResult: (Track, String?) -> Unit): ((Track) -> Unit)? = null
