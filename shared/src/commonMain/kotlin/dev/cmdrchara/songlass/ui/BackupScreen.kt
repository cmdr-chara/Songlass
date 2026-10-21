package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.LibrarySnapshotUnavailableException
import dev.cmdrchara.songlass.core.backup.BackupPreview
import dev.cmdrchara.songlass.core.backup.BackupPreviewChangedException
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private enum class BackupNotice { None, Saved, Restored, Failed, Invalid, PreviewChanged, LibraryUnavailable }

/** The file is validated and previewed before an explicit replacement of app data. */
@Composable
fun BackupScreen(graph: AppGraph, onBack: () -> Unit, contentPadding: PaddingValues, searchFocus: SettingsFocusRequest? = null) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchReveal = SettingsSearchScroll(searchFocus, listState, if (searchFocus?.target == SettingsTarget.Backup) 0 else null)
    val restoring by graph.isRestoringBackup.collectAsState()
    val scanning by graph.isLibraryLoading.collectAsState()
    val completeScan by graph.hasCompleteLibraryScan.collectAsState()
    val libraryFailure by graph.libraryRefreshFailure.collectAsState()
    val hasAccess by graph.source.hasAccess.collectAsState()
    val libraryReady = !scanning && completeScan && libraryFailure == null && hasAccess && graph.isLibraryReadyForBackup()
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(BackupNotice.None) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var omittedLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(libraryReady) { if (!libraryReady) confirming = false }
    fun readPreview(text: String) {
        busy = true
        source = text
        confirming = false
        scope.launch {
            try {
                preview = withContext(Dispatchers.Default) { graph.previewBackup(text) }
                notice = BackupNotice.None
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: LibrarySnapshotUnavailableException) {
                notice = BackupNotice.LibraryUnavailable
                preview = null
                // Keep the selected document so it can be reviewed once a complete scan succeeds.
            } catch (_: Exception) { notice = BackupNotice.Invalid; preview = null; source = null }
            finally { busy = false }
        }
    }
    val importer = rememberBackupImporter { result ->
        when (result) {
            is BackupFileImport.Loaded -> readPreview(result.text)
            BackupFileImport.Cancelled -> busy = false
            is BackupFileImport.Failed -> { busy = false; notice = BackupNotice.Invalid }
        }
    }
    val exporter = rememberBackupExporter { result ->
        busy = false
        notice = when (result) {
            BackupFileExport.Saved -> BackupNotice.Saved
            BackupFileExport.Cancelled -> BackupNotice.None
            BackupFileExport.Failed -> BackupNotice.Failed
        }
    }
    val working = busy || restoring
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).then(searchReveal), state = listState, contentPadding = contentPadding) {
        item {
            SettingsSearchAnchor(SettingsTarget.Backup, searchFocus) {
                BackPageTitle(stringResource(Res.string.backup_title),
                    stringResource(if (searchFocus == null) Res.string.settings_title else Res.string.settings_search_results), { if (!working) onBack() })
            }
        }
        item { FeatureHint(stringResource(Res.string.backup_intro)) }
        if (!libraryReady) item {
            FeatureHint(stringResource(if (scanning) Res.string.backup_library_loading else Res.string.backup_library_unavailable))
            LibraryRefreshNotice(libraryFailure, scanning, graph::requestLibraryRefresh)
        }
        if (working) item { FeatureHint(stringResource(Res.string.backup_busy)) }
        if (notice != BackupNotice.None && (notice != BackupNotice.LibraryUnavailable || libraryReady)) item {
            FeatureHint(stringResource(when (notice) {
                BackupNotice.Saved -> Res.string.backup_export_success
                BackupNotice.Restored -> Res.string.backup_restore_success
                BackupNotice.Invalid -> Res.string.backup_invalid
                BackupNotice.PreviewChanged -> Res.string.backup_preview_changed
                BackupNotice.LibraryUnavailable -> Res.string.backup_library_unavailable
                else -> Res.string.backup_failed
            }))
        }
        if (omittedLyrics) item { FeatureHint(stringResource(Res.string.backup_omitted_lyrics)) }
        if (importer == null || exporter == null) item { FeatureHint(stringResource(Res.string.backup_unavailable)) }
        if (!working && libraryReady) {
            if (exporter != null) item {
                ActionButton(stringResource(Res.string.backup_export), {
                    if (!graph.isLibraryReadyForBackup()) { notice = BackupNotice.LibraryUnavailable; return@ActionButton }
                    busy = true; notice = BackupNotice.None
                    scope.launch {
                        try {
                            val archive = withContext(Dispatchers.Default) { graph.exportBackup() }
                            omittedLyrics = archive.omittedLyrics > 0
                            exporter("Songlass-backup.songlass", archive.text)
                        } catch (cancelled: CancellationException) { busy = false; throw cancelled }
                        catch (_: LibrarySnapshotUnavailableException) { busy = false; notice = BackupNotice.LibraryUnavailable }
                        catch (_: Exception) { busy = false; notice = BackupNotice.Failed }
                    }
                }, Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 6.dp))
            }
            if (importer != null) item {
                ActionButton(stringResource(Res.string.backup_import), {
                    if (!graph.isLibraryReadyForBackup()) { notice = BackupNotice.LibraryUnavailable; return@ActionButton }
                    busy = true; notice = BackupNotice.None
                    preview = null; source = null; confirming = false
                    importer()
                },
                    Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 6.dp))
            }
            if (preview == null && source != null) item {
                ActionButton(stringResource(Res.string.backup_review_again), { source?.let(::readPreview) },
                    Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 6.dp))
            }
        }
        val checked = preview
        if (checked != null) {
            item { SectionTitle(stringResource(Res.string.backup_preview)) }
            item { FeatureHint(stringResource(Res.string.backup_preview_hint)) }
            item {
                SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                    BackupCountRow(stringResource(Res.string.library_favorites), checked.counts.favorites)
                    BackupCountRow(stringResource(Res.string.backup_playlists), checked.counts.userPlaylists + checked.counts.smartPlaylists)
                    BackupCountRow(stringResource(Res.string.backup_history), checked.counts.datedPlays)
                    BackupCountRow(stringResource(Res.string.backup_profiles), checked.counts.headphoneProfiles + checked.counts.outputProfiles)
                    BackupCountRow(stringResource(Res.string.bookmark_backup_progress), checked.counts.trackProgress)
                    if (checked.unavailableTrackProgress > 0) BackupCountRow(
                        stringResource(Res.string.backup_unavailable_progress), checked.unavailableTrackProgress)
                    BackupCountRow(stringResource(Res.string.bookmark_open), checked.counts.bookmarks)
                    if (checked.unavailableBookmarks > 0) BackupCountRow(
                        stringResource(Res.string.backup_unavailable_bookmarks), checked.unavailableBookmarks)
                    BackupCountRow(stringResource(Res.string.backup_matched), checked.tracks.unchanged + checked.tracks.remapped)
                    BackupCountRow(stringResource(Res.string.backup_missing), checked.tracks.missing)
                    if (checked.tracks.conflicting > 0) BackupCountRow(stringResource(Res.string.backup_conflicting), checked.tracks.conflicting)
                }
            }
            if (checked.unavailableTrackProgress > 0 || checked.unavailableBookmarks > 0) item {
                FeatureHint(stringResource(Res.string.backup_unavailable_markers_hint))
            }
            if (checked.tracks.missing > 0) item { FeatureHint(stringResource(Res.string.backup_missing_hint)) }
            if (checked.tracks.conflicting > 0) item { FeatureHint(stringResource(Res.string.backup_conflict_hint)) }
            if (!working && libraryReady) {
                if (!confirming) item {
                    ActionButton(stringResource(Res.string.backup_restore), { confirming = true },
                        Modifier.fillMaxWidth().padding(16.dp))
                } else {
                    item { SectionTitle(stringResource(Res.string.backup_confirm_title)) }
                    item { FeatureHint(stringResource(Res.string.backup_confirm_hint)) }
                    item {
                        ActionButton(stringResource(Res.string.backup_restore), {
                            val text = source ?: return@ActionButton
                            if (!graph.isLibraryReadyForBackup()) {
                                confirming = false; notice = BackupNotice.LibraryUnavailable
                                return@ActionButton
                            }
                            busy = true; confirming = false; notice = BackupNotice.None
                            scope.launch {
                                try { graph.restoreBackup(text, checked); notice = BackupNotice.Restored; preview = null; source = null }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (changed: BackupPreviewChangedException) {
                                    preview = changed.preview
                                    notice = BackupNotice.PreviewChanged
                                }
                                catch (_: LibrarySnapshotUnavailableException) { notice = BackupNotice.LibraryUnavailable }
                                catch (_: Exception) { notice = BackupNotice.Failed }
                                finally { busy = false }
                            }
                        }, Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 6.dp))
                    }
                    item {
                        ActionButton(stringResource(Res.string.backup_cancel), { confirming = false },
                            Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupCountRow(title: String, count: Int) {
    ListRow(title, trailing = { AppText(count.toString(), AppTheme.typography.body) })
}
