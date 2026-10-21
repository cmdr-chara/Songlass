package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.model.FileTagEditor
import dev.cmdrchara.songlass.core.model.TagEditRequest

enum class TagSaveResult { Saved, PermissionDenied, SourceChanged, RecoveryRequired, Failed }

/** Native image picker; selecting a cover does not modify the audio file. */
@Composable
expect fun rememberTagCoverPicker(onSelected: (String) -> Unit): (() -> Unit)?

/** Android owns the per-file write-consent prompt; a denied prompt never retries writing. */
@Composable
expect fun rememberTagSaver(editor: FileTagEditor, onResult: (TagSaveResult) -> Unit): ((TagEditRequest) -> Unit)?
