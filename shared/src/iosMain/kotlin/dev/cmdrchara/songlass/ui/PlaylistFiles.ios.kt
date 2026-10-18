package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

// Needs UIDocumentPickerViewController; to be added with the iOS library integration.
@Composable
actual fun rememberPlaylistImporter(onResult: (name: String?, text: String?) -> Unit): (() -> Unit)? = null

@Composable
actual fun rememberPlaylistExporter(): ((fileName: String, content: String) -> Unit)? = null
