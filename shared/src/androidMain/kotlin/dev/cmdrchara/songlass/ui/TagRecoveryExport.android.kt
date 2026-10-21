package dev.cmdrchara.songlass.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import dev.cmdrchara.songlass.core.model.FileTagEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
actual fun rememberTagRecoveryExporter(editor: FileTagEditor, onResult: (TagRecoveryExportResult) -> Unit): TagRecoveryExporter? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val recoveryId = pending
        pending = null
        when {
            uri == null -> result(TagRecoveryExportResult.Cancelled)
            recoveryId == null -> result(TagRecoveryExportResult.Failed)
            else -> {
                // The user may return later to acknowledge the export; providers may decline a
                // persistent grant, in which case verification can fail safely and export is retried.
                runCatching { context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                job = scope.launch {
                    try { editor.exportRecovery(recoveryId, uri.toString()); result(TagRecoveryExportResult.Saved) }
                    catch (cancelled: CancellationException) { result(TagRecoveryExportResult.Cancelled); throw cancelled }
                    catch (_: Exception) { result(TagRecoveryExportResult.Failed) }
                    finally { job = null }
                }
            }
        }
    }
    return TagRecoveryExporter(
        inProgress = pending != null || job != null,
        launch = { recoveryId ->
            if (pending == null && job == null) {
                pending = recoveryId
                try { picker.launch("Songlass-tag-recovery-${recoveryId.take(8)}.zip") }
                catch (_: Exception) { pending = null; result(TagRecoveryExportResult.Failed) }
            }
        },
        cancel = {
            if (job != null) job?.cancel() else { pending = null; result(TagRecoveryExportResult.Cancelled) }
        },
    )
}
