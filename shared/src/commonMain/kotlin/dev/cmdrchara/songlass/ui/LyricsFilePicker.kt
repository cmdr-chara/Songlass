package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.model.Track

/** The chosen text is paired with the track that opened the picker, even if playback changes. */
@Composable
expect fun rememberLyricsFilePicker(onResult: (Track, String?) -> Unit): ((Track) -> Unit)?
