package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Opens a system share sheet; success means presented, never that a recipient received it. */
@Composable
expect fun rememberLyricsCardSharer(onResult: (Boolean) -> Unit): ((ImageBitmap, String) -> Unit)?
