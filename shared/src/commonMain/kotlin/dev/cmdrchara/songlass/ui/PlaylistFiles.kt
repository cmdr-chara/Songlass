package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** Opens an M3U/M3U8 file; [onResult] gets its name (without extension) and text, or null text on failure. */
@Composable
expect fun rememberPlaylistImporter(onResult: (name: String?, text: String?) -> Unit): (() -> Unit)?

/** Saves [content] as a new file the user places, suggesting [fileName]. */
@Composable
expect fun rememberPlaylistExporter(): ((fileName: String, content: String) -> Unit)?
