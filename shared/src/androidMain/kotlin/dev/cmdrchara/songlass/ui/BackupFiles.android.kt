package dev.cmdrchara.songlass.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import dev.cmdrchara.songlass.core.backup.DataBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

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

@Composable
actual fun rememberBackupExporter(onResult: (BackupFileExport) -> Unit): ((fileName: String, content: String) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentResult by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val content = pending
        pending = null
        when {
            uri == null -> currentResult(BackupFileExport.Cancelled)
            content == null -> {
                // The content was lost (for example the activity was recreated while the picker was open).
                removeDocument(context, uri)
                currentResult(BackupFileExport.Failed)
            }
            else -> scope.launch {
                // Finish the write even if the screen is left meanwhile: a half-written backup is worse than none.
                val saved = withContext(NonCancellable + Dispatchers.IO) { writeBackup(context, uri, content) }
                currentResult(if (saved) BackupFileExport.Saved else BackupFileExport.Failed)
            }
        }
    }
    return { fileName, content ->
        pending = content
        launcher.launch(fileName)
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
