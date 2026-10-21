package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.*
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.M3u
import dev.cmdrchara.songlass.core.playlists.M3uPlaylistExport
import dev.cmdrchara.songlass.core.playlists.UserPlaylist
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.InfoSheet
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private enum class ExportStage { None, Working, Confirm, Details, Notice }
private enum class ExportNotice { LibraryUnavailable, Changed, Empty, Failed }
private data class PlaylistExportReview(val playlist: UserPlaylist, val source: List<Track>, val result: M3uPlaylistExport)

/** Build exports from the complete scan and ask before omitting unavailable references. */
@Composable
internal fun rememberUserPlaylistExportAction(
    state: AppState,
    onExport: ((fileName: String, content: String) -> Unit)?,
): ((UserPlaylist) -> Unit)? {
    val scope = rememberCoroutineScope()
    val export by rememberUpdatedState(onExport)
    var stage by remember { mutableStateOf(ExportStage.None) }
    var notice by remember { mutableStateOf(ExportNotice.Failed) }
    var review by remember { mutableStateOf<PlaylistExportReview?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    PlatformBackHandler(stage != ExportStage.None) {
        if (stage == ExportStage.Details) stage = ExportStage.Confirm
        else { job?.cancel(); stage = ExportStage.None }
    }
    fun showNotice(value: ExportNotice) { notice = value; stage = ExportStage.Notice }
    fun isCurrent(value: PlaylistExportReview) = state.isLibraryReadyForPlaylistFiles() &&
        state.allTracks.value === value.source && state.userPlaylists.get(value.playlist.id) == value.playlist
    fun save(value: PlaylistExportReview) {
        if (!isCurrent(value)) { showNotice(ExportNotice.Changed); return }
        stage = ExportStage.None
        export?.invoke(M3u.exportFileName("${value.playlist.name}.m3u8"), value.result.output.text)
    }
    val checked = review
    val close = stringResource(Res.string.action_close)
    val cancel = stringResource(Res.string.action_cancel)
    ActionSheet(
        visible = stage == ExportStage.Confirm && checked != null,
        title = checked?.playlist?.name ?: stringResource(Res.string.playlist_export),
        subtitle = checked?.let { stringResource(Res.string.playlist_import_summary, it.result.output.exported, it.result.missing + it.result.output.skipped) },
        cancelLabel = cancel,
        onDismiss = { stage = ExportStage.None },
        actions = if (checked == null) emptyList() else listOf(
            SheetAction(stringResource(Res.string.playlist_export_confirm, checked.result.output.exported), AppIcons.ImportFile) { save(checked) },
            SheetAction(stringResource(Res.string.playlist_export_details), AppIcons.Library) { stage = ExportStage.Details },
        ),
    )
    InfoSheet(
        visible = stage == ExportStage.Details && checked != null,
        title = stringResource(Res.string.playlist_export_details), closeLabel = close,
        onDismiss = { stage = ExportStage.Confirm },
        rows = if (checked == null) emptyList() else listOf(
            InfoRow(stringResource(Res.string.playlist_export_found), checked.result.output.exported.toString()),
            InfoRow(stringResource(Res.string.playlist_export_missing), checked.result.missing.toString()),
            InfoRow(stringResource(Res.string.playlist_export_skipped), checked.result.output.skipped.toString()),
            InfoRow(stringResource(Res.string.playlist_export_rule_title), stringResource(Res.string.playlist_export_rule)),
        ),
    )
    InfoSheet(
        visible = stage == ExportStage.Working,
        title = stringResource(Res.string.playlist_export), closeLabel = cancel,
        onDismiss = { job?.cancel(); stage = ExportStage.None },
        rows = listOf(InfoRow(stringResource(Res.string.playlist_file_preparing), stringResource(Res.string.playlist_export_rule))),
    )
    InfoSheet(
        visible = stage == ExportStage.Notice,
        title = stringResource(Res.string.playlist_export), closeLabel = close,
        onDismiss = { stage = ExportStage.None },
        rows = listOf(InfoRow(stringResource(Res.string.playlist_export_failed), stringResource(when (notice) {
            ExportNotice.LibraryUnavailable -> Res.string.playlist_file_library_unavailable
            ExportNotice.Changed -> Res.string.playlist_file_library_changed
            ExportNotice.Empty -> Res.string.playlist_export_empty
            ExportNotice.Failed -> Res.string.playlist_export_failed_hint
        }))),
    )
    return if (onExport == null) null else { playlist ->
        job?.cancel()
        review = null
        if (!state.isLibraryReadyForPlaylistFiles()) showNotice(ExportNotice.LibraryUnavailable) else {
            val source = state.allTracks.value
            stage = ExportStage.Working
            job = scope.launch {
                try {
                    val checkedExport = withContext(Dispatchers.Default) {
                        PlaylistExportReview(playlist, source, M3u.writePlaylist(playlist, source))
                    }
                    when {
                        !isCurrent(checkedExport) -> showNotice(ExportNotice.Changed)
                        checkedExport.result.output.exported == 0 -> showNotice(ExportNotice.Empty)
                        checkedExport.result.missing + checkedExport.result.output.skipped == 0 -> save(checkedExport)
                        else -> { review = checkedExport; stage = ExportStage.Confirm }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { showNotice(ExportNotice.Failed) }
            }
        }
    }
}
