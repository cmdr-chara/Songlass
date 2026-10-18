package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.core.playlists.M3u

/** Kinds of text files the app opens; they decide what the system picker offers. */
enum class TextFileKind { Playlist, EqualizerProfile }

/**
 * Opens a small text file of [kind]; [onResult] gets its file name and text, or null text on
 * failure. Null where the platform has no picker yet.
 */
@Composable
expect fun rememberTextFileImporter(kind: TextFileKind, onResult: (fileName: String?, text: String?) -> Unit): (() -> Unit)?

/** Opens an M3U/M3U8 file; [onResult] gets its name (without extension) and text, or null text on failure. */
@Composable
fun rememberPlaylistImporter(onResult: (name: String?, text: String?) -> Unit): (() -> Unit)? =
    rememberTextFileImporter(TextFileKind.Playlist) { fileName, text -> onResult(M3u.nameFromFile(fileName), text) }

/** Saves [content] as a new file the user places, suggesting [fileName]. */
@Composable
expect fun rememberPlaylistExporter(): ((fileName: String, content: String) -> Unit)?
