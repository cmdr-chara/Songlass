package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.model.FileTagEditor

@Composable
actual fun rememberTagRecoveryExporter(editor: FileTagEditor, onResult: (TagRecoveryExportResult) -> Unit): TagRecoveryExporter? = null
