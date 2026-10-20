package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

// Needs UIDocumentPickerViewController; to be added with the iOS library integration, like the
// other file pickers.
@Composable
actual fun rememberBackupImporter(onResult: (BackupFileImport) -> Unit): (() -> Unit)? = null

@Composable
actual fun rememberBackupExporter(onResult: (BackupFileExport) -> Unit): ((fileName: String, content: String) -> Unit)? = null
