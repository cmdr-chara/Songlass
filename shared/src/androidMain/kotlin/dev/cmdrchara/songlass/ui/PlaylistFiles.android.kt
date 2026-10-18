package dev.cmdrchara.songlass.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.cmdrchara.songlass.core.playlists.M3u
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPlaylistImporter(onResult: (name: String?, text: String?) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (name, text) = withContext(Dispatchers.IO) {
                try {
                    val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readNBytes(MAX_PLAYLIST_BYTES + 1)
                        // M3U8 is UTF-8; plain M3U is often Latin-1, which UTF-8 decoding keeps readable enough.
                        if (bytes.size > MAX_PLAYLIST_BYTES) null else bytes.decodeToString()
                    }
                    M3u.nameFromFile(fileName) to text
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null to null
                }
            }
            currentResult(name, text)
        }
    }
    // M3U files have no consistently registered MIME type; the parser ignores anything else.
    return { launcher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "text/plain", "application/octet-stream")) }
}

@Composable
actual fun rememberPlaylistExporter(): ((fileName: String, content: String) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val content = pending
        pending = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(content.encodeToByteArray()) } }
        }
    }
    return { fileName, content ->
        pending = content
        launcher.launch(fileName)
    }
}

private const val MAX_PLAYLIST_BYTES = 1024 * 1024
