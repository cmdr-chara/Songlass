package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.model.FileTagEditor
import dev.cmdrchara.songlass.core.model.TagEditRequest

@Composable
actual fun rememberTagCoverPicker(onSelected: (String) -> Unit): (() -> Unit)? = null

@Composable
actual fun rememberTagSaver(editor: FileTagEditor, onResult: (TagSaveResult) -> Unit): ((TagEditRequest) -> Unit)? = null
