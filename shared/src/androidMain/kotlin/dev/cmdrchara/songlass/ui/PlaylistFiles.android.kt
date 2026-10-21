package dev.cmdrchara.songlass.ui

import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import dev.cmdrchara.songlass.core.playlists.M3u
import dev.cmdrchara.songlass.core.storage.DocumentExportRequest
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.InfoSheet
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.util.UUID

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

private enum class PlaylistExportNotice { Saved, Failed, Interrupted }

private class PlaylistExportRegistrations {
    var active = true
    val launchers = mutableMapOf<DocumentExportRequest, ActivityResultLauncher<String>>()

    fun dispose() {
        active = false
        launchers.forEach { (request, launcher) -> request.invalidate(); launcher.unregister() }
        launchers.clear()
    }
}

@Composable
actual fun rememberPlaylistExporter(): ((fileName: String, content: String) -> Unit)? {
    val registry = LocalActivityResultRegistryOwner.current?.activityResultRegistry ?: return null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Only an identifier enters saved state. Large playlist text stays in its one-shot request.
    // A recreated UI deliberately does not re-register the old request's ActivityResult key.
    var unfinishedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoredRequestId = remember(registry) { unfinishedRequestId }
    val registrations = remember(registry) { PlaylistExportRegistrations() }
    var busy by remember(registry) { mutableStateOf(false) }
    var saved by remember(registry) {
        mutableStateOf(if (restoredRequestId != null) PlaylistExportNotice.Interrupted else null)
    }
    LaunchedEffect(registry) {
        if (unfinishedRequestId == restoredRequestId) unfinishedRequestId = null
    }
    DisposableEffect(registrations) { onDispose { registrations.dispose() } }
    PlatformBackHandler(saved != null) { saved = null }
    InfoSheet(
        visible = saved != null,
        title = stringResource(Res.string.playlist_export),
        closeLabel = stringResource(Res.string.action_close),
        onDismiss = { saved = null },
        rows = listOf(InfoRow(
            stringResource(when (saved) {
                PlaylistExportNotice.Saved -> Res.string.playlist_export_saved
                PlaylistExportNotice.Interrupted -> Res.string.playlist_export_interrupted
                else -> Res.string.playlist_export_failed
            }),
            stringResource(when (saved) {
                PlaylistExportNotice.Saved -> Res.string.playlist_export_saved_hint
                PlaylistExportNotice.Interrupted -> Res.string.playlist_export_interrupted_hint
                else -> Res.string.playlist_export_failed_hint
            }),
        )),
    )
    return { fileName, content ->
        if (registrations.active && !busy) {
            val requestId = UUID.randomUUID().toString()
            val request = DocumentExportRequest(content)
            busy = true
            saved = null
            unfinishedRequestId = requestId
            try {
                // Every registration captures only its own immutable payload. A late A result
                // cannot observe B's text, including after Activity/process recreation.
                val launcher = registry.register(
                    "songlass-playlist-export-$requestId",
                    ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
                ) result@{ uri ->
                    registrations.launchers.remove(request)?.unregister()
                    if (!registrations.active) { request.invalidate(); return@result }
                    val payload = request.claim() ?: return@result
                    if (uri == null) {
                        if (unfinishedRequestId == requestId) unfinishedRequestId = null
                        busy = false
                        return@result
                    }
                    scope.launch {
                        try {
                            val written = withContext(Dispatchers.IO) {
                                try {
                                    currentCoroutineContext().ensureActive()
                                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                                    stream.use { it.write(payload.encodeToByteArray()); it.flush() }
                                    true
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { false }
                            }
                            saved = if (written) PlaylistExportNotice.Saved else PlaylistExportNotice.Failed
                        } finally {
                            if (unfinishedRequestId == requestId) unfinishedRequestId = null
                            busy = false
                        }
                    }
                }
                registrations.launchers[request] = launcher
                launcher.launch(M3u.exportFileName(fileName))
            } catch (failure: Exception) {
                registrations.launchers.remove(request)?.unregister()
                request.invalidate()
                if (unfinishedRequestId == requestId) unfinishedRequestId = null
                busy = false
                if (failure is CancellationException) throw failure
                saved = PlaylistExportNotice.Failed
            }
        }
    }
}
