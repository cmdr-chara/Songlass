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
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.InfoSheet
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberTextFileImporter(kind: TextFileKind, onResult: (fileName: String?, text: String?) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentResult by rememberUpdatedState(onResult)
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { busy = false; return@rememberLauncherForActivityResult }
        scope.launch {
            try {
                val (name, text) = withContext(Dispatchers.IO) {
                    try {
                        // A provider may allow opening the document but omit its display-name column.
                        val fileName = try {
                            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                        } catch (_: Exception) { null }
                        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readNBytes(M3u.MAX_FILE_BYTES + 1)
                            if (kind == TextFileKind.Playlist) M3u.decodeText(bytes, fileName)
                            else if (bytes.size > M3u.MAX_FILE_BYTES) null else bytes.decodeToString(throwOnInvalidSequence = true)
                        }
                        fileName to text
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null to null
                    }
                }
                currentResult(name, text)
            } finally { busy = false }
        }
    }
    val types = when (kind) {
        // M3U files have no consistently registered MIME type; the parser ignores anything else.
        TextFileKind.Playlist -> arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "text/plain", "application/octet-stream")
        TextFileKind.EqualizerProfile -> arrayOf("text/plain", "application/octet-stream")
    }
    return {
        if (!busy) {
            busy = true
            try { launcher.launch(types) }
            catch (_: Exception) { busy = false; currentResult(null, null) }
        }
    }
}

@Composable
actual fun rememberPlaylistExporter(): ((fileName: String, content: String) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Boolean?>(null) }
    PlatformBackHandler(saved != null) { saved = null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val content = pending
        pending = null
        if (uri == null) { busy = false; return@rememberLauncherForActivityResult }
        if (content == null) { busy = false; saved = false; return@rememberLauncherForActivityResult }
        scope.launch {
            try {
                saved = withContext(Dispatchers.IO) {
                    try {
                        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                        stream.use { it.write(content.encodeToByteArray()); it.flush() }
                        true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                }
            } finally { busy = false }
        }
    }
    InfoSheet(
        visible = saved != null,
        title = stringResource(Res.string.playlist_export),
        closeLabel = stringResource(Res.string.action_close),
        onDismiss = { saved = null },
        rows = listOf(InfoRow(
            stringResource(if (saved == true) Res.string.playlist_export_saved else Res.string.playlist_export_failed),
            stringResource(if (saved == true) Res.string.playlist_export_saved_hint else Res.string.playlist_export_failed_hint),
        )),
    )
    return { fileName, content ->
        if (!busy) {
            busy = true
            saved = null
            pending = content
            try { launcher.launch(M3u.exportFileName(fileName)) }
            catch (_: Exception) { busy = false; pending = null; saved = false }
        }
    }
}
