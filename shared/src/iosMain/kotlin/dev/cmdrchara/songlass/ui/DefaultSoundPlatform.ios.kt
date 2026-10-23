package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.model.Track

@Composable
actual fun rememberDefaultSoundActions(
    isSourceCurrent: (Track) -> Boolean,
    onResult: (DefaultSoundResult) -> Unit,
): DefaultSoundActions? = null
