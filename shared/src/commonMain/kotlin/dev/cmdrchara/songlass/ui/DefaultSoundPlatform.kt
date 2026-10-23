package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.sound.DefaultSoundTarget

enum class DefaultSoundResult { Applied, PermissionRequired, SourceChanged, Failed }

data class DefaultSoundActions(
    val permissionGranted: Boolean,
    val isApplying: Boolean,
    val openSettings: () -> Unit,
    val apply: (Track, DefaultSoundTarget) -> Unit,
)

/** Apply is invoked only after explicit confirmation; permission return never resumes a write. */
@Composable
expect fun rememberDefaultSoundActions(
    isSourceCurrent: (Track) -> Boolean,
    onResult: (DefaultSoundResult) -> Unit,
): DefaultSoundActions?
