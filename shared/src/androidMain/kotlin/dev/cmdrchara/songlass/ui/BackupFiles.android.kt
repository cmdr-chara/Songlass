package dev.cmdrchara.songlass.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.storage.DocumentExportRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

@Composable
actual fun rememberBackupImporter(onResult: (BackupFileImport) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            currentResult(BackupFileImport.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    readBackup(context, uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    BackupFileImport.Failed()
                }
            }
            currentResult(result)
        }
    }
    // Backups have no registered MIME type, and providers label unknown files inconsistently.
    return { launcher.launch(arrayOf("*/*")) }
}

private class BackupExportRegistrations {
    var active = true
    val launchers = mutableMapOf<DocumentExportRequest, ActivityResultLauncher<String>>()

    fun dispose() {
        active = false
        launchers.forEach { (request, launcher) -> request.invalidate(); launcher.unregister() }
        launchers.clear()
    }
}

@Composable
actual fun rememberBackupExporter(onResult: (BackupFileExport) -> Unit): ((fileName: String, content: String) -> Unit)? {
    val registry = LocalActivityResultRegistryOwner.current?.activityResultRegistry ?: return null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentResult by rememberUpdatedState(onResult)
    // Saved state contains only a token, never the archive or other private app data.
    var unfinishedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoredRequestId = remember(registry) { unfinishedRequestId }
    val registrations = remember(registry) { BackupExportRegistrations() }
    var busy by remember(registry) { mutableStateOf(false) }
    LaunchedEffect(registry) {
        if (restoredRequestId != null && unfinishedRequestId == restoredRequestId) {
            unfinishedRequestId = null
            currentResult(BackupFileExport.Interrupted)
        }
    }
    DisposableEffect(registrations) { onDispose { registrations.dispose() } }
    return { fileName, content ->
        if (registrations.active && !busy) {
            val requestId = UUID.randomUUID().toString()
            val request = DocumentExportRequest(content)
            busy = true
            unfinishedRequestId = requestId
            try {
                // The callback can claim only its own immutable archive. A recreated screen
                // never registers this UUID for a different export, including a late result.
                val launcher = registry.register(
                    "songlass-backup-export-$requestId",
                    ActivityResultContracts.CreateDocument("application/octet-stream"),
                ) result@{ uri ->
                    registrations.launchers.remove(request)?.unregister()
                    if (!registrations.active) { request.invalidate(); return@result }
                    val payload = request.claim() ?: return@result
                    if (uri == null) {
                        if (unfinishedRequestId == requestId) unfinishedRequestId = null
                        busy = false
                        currentResult(BackupFileExport.Cancelled)
                        return@result
                    }
                    // The destination is now accepted. Preserve the existing finish-write
                    // contract even if the UI is disposed before this coroutine is scheduled.
                    scope.launch(NonCancellable) {
                        try {
                            val saved = withContext(Dispatchers.IO) { writeBackup(context, uri, payload) }
                            if (registrations.active) {
                                currentResult(if (saved) BackupFileExport.Saved else BackupFileExport.Failed)
                            }
                        } finally {
                            if (unfinishedRequestId == requestId) unfinishedRequestId = null
                            busy = false
                        }
                    }
                }
                registrations.launchers[request] = launcher
                launcher.launch(fileName)
            } catch (failure: Exception) {
                registrations.launchers.remove(request)?.unregister()
                request.invalidate()
                if (unfinishedRequestId == requestId) unfinishedRequestId = null
                busy = false
                if (failure is CancellationException) throw failure
                currentResult(BackupFileExport.Failed)
            }
        }
    }
}

private fun readBackup(context: Context, uri: Uri): BackupFileImport {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(DataBackup.MAX_BYTES + 1) }
        ?: return BackupFileImport.Failed()
    if (bytes.size > DataBackup.MAX_BYTES) return BackupFileImport.Failed(tooLarge = true)
    // Strict: a file with invalid UTF-8 is not one of ours, and silently repaired text would fail its checksum anyway.
    val text = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
    return BackupFileImport.Loaded(text)
}

private fun writeBackup(context: Context, uri: Uri, content: String): Boolean {
    val written = try {
        val bytes = content.encodeToByteArray()
        // "wt" truncates: providers differ on whether plain "w" does.
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(bytes)
            it.flush()
        } != null
    } catch (_: Exception) {
        false
    }
    if (!written) removeDocument(context, uri)
    return written
}

/** Best effort: the picker created an empty or partial file that would otherwise look like a backup. */
private fun removeDocument(context: Context, uri: Uri) {
    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
}
