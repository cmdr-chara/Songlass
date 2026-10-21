package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.*
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.M3u
import dev.cmdrchara.songlass.core.playlists.M3uImport
import dev.cmdrchara.songlass.core.playlists.M3uIssueKind
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

private enum class ImportStage { None, Working, Confirm, Details, Notice }
private enum class ImportNotice { ReadFailed, FileLimit, Empty, LibraryUnavailable, LibraryChanged, SaveFailed }
private data class PlaylistImportReview(
    val name: String,
    val source: List<Track>,
    val result: M3uImport,
    val hidden: Int,
)

internal fun AppState.isLibraryReadyForPlaylistFiles(): Boolean = libraryAccess.value &&
    hasCompleteLibraryScan.value && !isLibraryLoading.value && libraryRefreshFailure.value == null &&
    !isRestoringBackup.value

/** Native file picker, background matching, then explicit review before creating any playlist. */
@Composable
internal fun rememberPlaylistImportAction(state: AppState): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(Res.string.playlist_import_default_name)
    var stage by remember { mutableStateOf(ImportStage.None) }
    var notice by remember { mutableStateOf(ImportNotice.ReadFailed) }
    var review by remember { mutableStateOf<PlaylistImportReview?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    PlatformBackHandler(stage != ImportStage.None) {
        if (stage == ImportStage.Details && review?.result?.tracks?.isNotEmpty() == true) stage = ImportStage.Confirm
        else { job?.cancel(); stage = ImportStage.None }
    }

    fun showNotice(value: ImportNotice) { notice = value; stage = ImportStage.Notice }
    val launcher = rememberPlaylistImporter { name, text ->
        job?.cancel()
        review = null
        when {
            text == null -> showNotice(ImportNotice.ReadFailed)
            !state.isLibraryReadyForPlaylistFiles() -> showNotice(ImportNotice.LibraryUnavailable)
            else -> {
                val source = state.allTracks.value
                val visible = state.library.tracks
                stage = ImportStage.Working
                job = scope.launch {
                    try {
                        val checked = withContext(Dispatchers.Default) {
                            val result = M3u.resolve(M3u.parseDetailed(text), source)
                            val visibleIds = visible.mapTo(HashSet()) { it.id }
                            PlaylistImportReview(name?.take(80) ?: defaultName, source, result,
                                result.tracks.count { it.id !in visibleIds })
                        }
                        when {
                            !state.isLibraryReadyForPlaylistFiles() || state.allTracks.value !== source -> showNotice(ImportNotice.LibraryChanged)
                            checked.result.failure != null -> showNotice(ImportNotice.FileLimit)
                            checked.result.tracks.isEmpty() && checked.result.issues.isEmpty() -> showNotice(ImportNotice.Empty)
                            else -> {
                                review = checked
                                stage = if (checked.result.tracks.isEmpty()) ImportStage.Details else ImportStage.Confirm
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { showNotice(ImportNotice.ReadFailed) }
                }
            }
        }
    }
    val checked = review
    val close = stringResource(Res.string.action_close)
    val cancel = stringResource(Res.string.action_cancel)
    ActionSheet(
        visible = stage == ImportStage.Confirm && checked != null,
        title = checked?.name ?: stringResource(Res.string.playlist_import),
        subtitle = checked?.let { stringResource(Res.string.playlist_import_summary, it.result.tracks.size, it.result.issues.size + it.result.duplicates) },
        cancelLabel = cancel,
        onDismiss = { stage = ImportStage.None },
        actions = if (checked == null) emptyList() else buildList {
            if (checked.result.tracks.isNotEmpty()) add(
                SheetAction(stringResource(Res.string.playlist_import_confirm, checked.result.tracks.size), AppIcons.AddToQueue) {
                    // A permission change, rescan or restore cannot turn an old review into a new match.
                    if (!state.isLibraryReadyForPlaylistFiles() || state.allTracks.value !== checked.source) {
                        showNotice(ImportNotice.LibraryChanged)
                    } else try {
                        val id = state.userPlaylists.create(checked.name, checked.result.tracks.map { it.id })
                        stage = ImportStage.None
                        state.open(Route.UserPlaylistDetail(id))
                    } catch (_: Exception) { showNotice(ImportNotice.SaveFailed) }
                },
            )
            add(SheetAction(stringResource(Res.string.playlist_import_details), AppIcons.ImportFile) { stage = ImportStage.Details })
        },
    )
    InfoSheet(
        visible = stage == ImportStage.Details && checked != null,
        title = stringResource(Res.string.playlist_import_details),
        closeLabel = close,
        onDismiss = { stage = if (checked?.result?.tracks?.isNotEmpty() == true) ImportStage.Confirm else ImportStage.None },
        rows = if (checked == null) emptyList() else buildList {
            add(InfoRow(stringResource(Res.string.playlist_import_found), checked.result.tracks.size.toString()))
            if (checked.hidden > 0) add(InfoRow(stringResource(Res.string.playlist_import_hidden), checked.hidden.toString()))
            if (checked.result.duplicates > 0) add(InfoRow(stringResource(Res.string.playlist_import_duplicates), checked.result.duplicates.toString()))
            for (kind in M3uIssueKind.entries) {
                val count = checked.result.count(kind)
                if (count > 0) add(InfoRow(stringResource(when (kind) {
                    M3uIssueKind.Missing -> Res.string.playlist_import_missing
                    M3uIssueKind.Ambiguous -> Res.string.playlist_import_ambiguous
                    M3uIssueKind.Conflict -> Res.string.playlist_import_conflicting
                    M3uIssueKind.Unsupported -> Res.string.playlist_import_unsupported
                    M3uIssueKind.Malformed -> Res.string.playlist_import_malformed
                }), count.toString()))
            }
            add(InfoRow(stringResource(Res.string.playlist_import_rule_title), stringResource(Res.string.playlist_import_rule)))
        },
    )
    InfoSheet(
        visible = stage == ImportStage.Working,
        title = stringResource(Res.string.playlist_import),
        closeLabel = cancel,
        onDismiss = { job?.cancel(); stage = ImportStage.None },
        rows = listOf(InfoRow(stringResource(Res.string.playlist_import_working), stringResource(Res.string.playlist_import_working_hint))),
    )
    InfoSheet(
        visible = stage == ImportStage.Notice,
        title = stringResource(Res.string.playlist_import),
        closeLabel = close,
        onDismiss = { stage = ImportStage.None },
        rows = listOf(InfoRow(stringResource(Res.string.playlist_import_not_created), stringResource(when (notice) {
            ImportNotice.ReadFailed -> Res.string.playlist_import_read_failed
            ImportNotice.FileLimit -> Res.string.playlist_import_limit
            ImportNotice.Empty -> Res.string.playlist_import_empty
            ImportNotice.LibraryUnavailable -> Res.string.playlist_file_library_unavailable
            ImportNotice.LibraryChanged -> Res.string.playlist_file_library_changed
            ImportNotice.SaveFailed -> Res.string.playlist_import_save_failed
        }))),
    )
    return launcher?.let { open ->
        {
            job?.cancel()
            stage = ImportStage.None
            if (state.isLibraryReadyForPlaylistFiles()) open() else showNotice(ImportNotice.LibraryUnavailable)
        }
    }
}
