package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

// Native sharing is deferred along with the real iOS player and file integration.
@Composable
actual fun rememberLyricsCardSharer(onResult: (Boolean) -> Unit): ((ImageBitmap, String) -> Unit)? = null
