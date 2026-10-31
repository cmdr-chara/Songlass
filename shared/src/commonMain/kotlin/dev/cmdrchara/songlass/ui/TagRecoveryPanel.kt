package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.model.FileTagEditor
import dev.cmdrchara.songlass.core.model.TagRecoveryInfo
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private enum class RecoveryNotice { None, Saved, Cancelled, Failed, Archived }
private enum class RecoveryOperation { Exporting, Archiving }

/** Track-independent: Settings/About can expose this even when the original song has disappeared. */
@Composable
fun TagRecoveryPanel(
    editor: FileTagEditor,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    onRecoveryArchived: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var pending by remember(editor) { mutableStateOf<TagRecoveryInfo?>(null) }
    var checking by remember(editor) { mutableStateOf(true) }
    var revision by remember(editor) { mutableIntStateOf(0) }
    var operation by remember(editor) { mutableStateOf<RecoveryOperation?>(null) }
    var notice by remember(editor) { mutableStateOf(RecoveryNotice.None) }
    var confirming by remember(editor) { mutableStateOf(false) }
    LaunchedEffect(editor, refreshKey, revision) {
        checking = true
        try { pending = editor.recoveryInfo() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { notice = RecoveryNotice.Failed }
        finally { checking = false }
    }
    val exporter = rememberTagRecoveryExporter(editor) { result ->
        operation = null
        notice = when (result) {
            TagRecoveryExportResult.Saved -> RecoveryNotice.Saved
            TagRecoveryExportResult.Cancelled -> RecoveryNotice.Cancelled
            TagRecoveryExportResult.Failed -> RecoveryNotice.Failed
        }
        revision++
    }
    val exporting = operation == RecoveryOperation.Exporting || exporter?.inProgress == true
    // An initial check is not evidence of an interrupted edit. Keep the page quiet
    // until recovery copies or an actual failure are known; rechecks of an existing
    // recovery and all export/archive progress remain visible.
    if (pending == null && operation == null && !exporting && notice == RecoveryNotice.None) return
    SettingsGroup(modifier.padding(horizontal = pageInset, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppText(stringResource(Res.string.tags_recovery_title), AppTheme.typography.headline,
                modifier = Modifier.semantics { heading() })
            val recovery = pending
            if (recovery != null) {
                AppText(stringResource(Res.string.tags_recovery_intro), AppTheme.typography.body)
                AppText(pluralStringResource(Res.plurals.tags_recovery_files, recovery.retainedFiles, recovery.retainedFiles),
                    AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel)
                if (recovery.retainedFiles == 0) AppText(stringResource(Res.string.tags_recovery_no_copies), AppTheme.typography.body)
            }
            val status = when {
                exporting -> Res.string.tags_recovery_exporting
                operation == RecoveryOperation.Archiving -> Res.string.tags_recovery_archiving
                checking -> Res.string.tags_recovery_checking
                notice == RecoveryNotice.Saved -> Res.string.tags_recovery_saved
                notice == RecoveryNotice.Cancelled -> Res.string.tags_recovery_cancelled
                notice == RecoveryNotice.Failed -> Res.string.tags_recovery_failed
                notice == RecoveryNotice.Archived -> Res.string.tags_recovery_archived
                else -> null
            }
            status?.let { AppText(stringResource(it), AppTheme.typography.body,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (exporting) {
                ActionButton(stringResource(Res.string.tags_recovery_cancel), { exporter?.cancel?.invoke() })
            } else if (operation == null && !checking && recovery != null) {
                if (exporter != null) {
                    ActionButton(stringResource(Res.string.tags_recovery_export), {
                        confirming = false
                        notice = RecoveryNotice.None
                        operation = RecoveryOperation.Exporting
                        exporter.launch(recovery.id)
                    }, Modifier.fillMaxWidth())
                } else AppText(stringResource(Res.string.tags_recovery_unavailable), AppTheme.typography.body)
                if (recovery.hasVerifiedExport) {
                    if (!confirming) {
                        ActionButton(stringResource(Res.string.tags_recovery_archive), { confirming = true }, Modifier.fillMaxWidth())
                    } else {
                        AppText(stringResource(Res.string.tags_recovery_confirm), AppTheme.typography.body)
                        ActionButton(stringResource(Res.string.tags_recovery_confirm_action), {
                            confirming = false
                            operation = RecoveryOperation.Archiving
                            scope.launch {
                                try {
                                    editor.archiveRecovery(recovery.id)
                                    pending = null
                                    notice = RecoveryNotice.Archived
                                    onRecoveryArchived()
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { notice = RecoveryNotice.Failed; revision++ }
                                finally { operation = null }
                            }
                        }, Modifier.fillMaxWidth())
                        ActionButton(stringResource(Res.string.tags_recovery_keep_pending), { confirming = false }, Modifier.fillMaxWidth())
                    }
                }
            }
            if (operation == null && !exporting && !checking && recovery == null && notice == RecoveryNotice.Failed) {
                ActionButton(stringResource(Res.string.tags_recovery_retry), { notice = RecoveryNotice.None; revision++ })
            }
        }
    }
}
