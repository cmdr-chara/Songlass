package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.model.FileTagEditor

enum class TagRecoveryExportResult { Saved, Cancelled, Failed }

class TagRecoveryExporter(val inProgress: Boolean, val launch: (recoveryId: String) -> Unit, val cancel: () -> Unit)

@Composable
expect fun rememberTagRecoveryExporter(
    editor: FileTagEditor,
    onResult: (TagRecoveryExportResult) -> Unit,
): TagRecoveryExporter?
