package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.UserPlaylist
import dev.cmdrchara.songlass.core.playlists.PlaylistInsertion
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.InfoSheet
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.ArtworkShelf
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.PromptSheet
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.shared.resources.smart_new
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.action_cancel
import dev.cmdrchara.songlass.shared.resources.action_more
import dev.cmdrchara.songlass.shared.resources.action_reorder
import dev.cmdrchara.songlass.shared.resources.playlist_add_to
import dev.cmdrchara.songlass.shared.resources.playlist_create
import dev.cmdrchara.songlass.shared.resources.playlist_delete
import dev.cmdrchara.songlass.shared.resources.playlist_done
import dev.cmdrchara.songlass.shared.resources.playlist_edit
import dev.cmdrchara.songlass.shared.resources.playlist_empty
import dev.cmdrchara.songlass.shared.resources.playlist_export
import dev.cmdrchara.songlass.shared.resources.playlist_import
import dev.cmdrchara.songlass.shared.resources.playlist_name
import dev.cmdrchara.songlass.shared.resources.playlist_new
import dev.cmdrchara.songlass.shared.resources.playlist_remove_song
import dev.cmdrchara.songlass.shared.resources.playlist_rename
import dev.cmdrchara.songlass.shared.resources.playlist_save
import dev.cmdrchara.songlass.shared.resources.songs_count
import dev.cmdrchara.songlass.shared.resources.tag_playlist
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Cover of a user playlist: its first song's, or a generated one from its name when empty. */
fun UserPlaylist.artwork(tracks: List<Track>): dev.cmdrchara.songlass.designsystem.Artwork =
    tracks.firstOrNull()?.artwork() ?: Playlist(id, name, emptyList(), emptyList()).artwork()

private typealias Playlist = dev.cmdrchara.songlass.core.library.Playlist

/** A playlist the user made: header in its cover's colours, then its songs; editable in place. */
@Composable
fun UserPlaylistScreen(id: String, state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    val playlists by state.userPlaylists.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == id }
    if (playlist == null) {
        UnavailablePlaylistPage(backLabel, state::goBack, contentPadding)
        return
    }
    val allTracks by state.allTracks.collectAsState()
    val scanning by state.isLibraryLoading.collectAsState()
    val scanComplete by state.hasCompleteLibraryScan.collectAsState()
    val access by state.libraryAccess.collectAsState()
    val availability = remember(playlist, state.library, allTracks) { playlist.availability(state.library, allTracks) }
    val tracks = availability.tracks
    val context = PlayContext.UserPlaylist(id)
    val editing = state.editingPlaylistId == id
    val density = LocalDensity.current
    val rowMetrics = remember(id, density.density, density.fontScale, AppTheme.typography.body, AppTheme.typography.subheadline) {
        PlaylistEditRowMetrics()
    }
    val fallbackRowHeightPx = with(density) { EditRowHeight.toPx() }
    var draggedId by remember(rowMetrics) { mutableStateOf<String?>(null) }
    var dragOffset by remember(rowMetrics) { mutableFloatStateOf(0f) }
    val visibleTrackIds = remember(tracks) { tracks.mapTo(mutableSetOf()) { it.id } }
    val currentVisibleTrackIds by rememberUpdatedState(visibleTrackIds)
    val reorderLabel = stringResource(Res.string.action_reorder)
    val removeLabel = stringResource(Res.string.playlist_remove_song)
    val upLabel = stringResource(Res.string.playlist_move_up)
    val downLabel = stringResource(Res.string.playlist_move_down)

    LaunchedEffect(editing, rowMetrics) {
        draggedId = null
        dragOffset = 0f
        if (!editing) {
            rowMetrics.heights.clear()
            rowMetrics.widthPx = 0
        }
    }

    fun adjacentVisibleIndex(ids: List<String>, from: Int, direction: Int): Int? {
        if (from !in ids.indices || ids[from] !in currentVisibleTrackIds) return null
        var target = from + direction
        while (target in ids.indices) {
            if (ids[target] in currentVisibleTrackIds) return target
            target += direction
        }
        return null
    }

    fun moveVisible(trackId: String, offset: Int): Boolean {
        // Read stored order: its update can precede the next UI recomposition during a drag.
        val all = state.userPlaylists.get(id)?.trackIds ?: return false
        val from = all.indexOf(trackId)
        val to = adjacentVisibleIndex(all, from, offset) ?: return false
        state.userPlaylists.move(id, from, to)
        return state.userPlaylists.get(id)?.trackIds?.indexOf(trackId) == to
    }

    ArtworkCollectionPage(
        artwork = playlist.artwork(tracks),
        kind = stringResource(Res.string.tag_playlist),
        title = playlist.name,
        subtitle = null,
        onSubtitleClick = null,
        tracks = tracks,
        durationMs = tracks.sumOf { it.durationMs },
        context = context,
        state = state,
        backLabel = backLabel,
        contentPadding = contentPadding,
        extra = {
            if (playlist.trackIds.isEmpty()) {
                item {
                    AppText(
                        stringResource(Res.string.playlist_empty),
                        AppTheme.typography.subheadline,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            if (availability.filteredTrackIds.isNotEmpty()) {
                item {
                    AppText(pluralStringResource(Res.plurals.playlist_filtered_count, availability.filteredTrackIds.size, availability.filteredTrackIds.size),
                        AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    ActionButton(stringResource(Res.string.library_settings), { state.open(Route.LibrarySettings) },
                        Modifier.padding(horizontal = pageInset))
                }
            }
            if (access && scanComplete && !scanning && availability.missingTrackIds.isNotEmpty()) {
                item {
                    AppText(pluralStringResource(Res.plurals.playlist_missing_count, availability.missingTrackIds.size, availability.missingTrackIds.size),
                        AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    ActionButton(stringResource(Res.string.playlist_remove_missing), { state.askPlaylistRemoval(id, missingOnly = true) },
                        Modifier.padding(horizontal = pageInset))
                }
            }
        },
        headerAction = {
            if (editing) {
                IconButton(AppIcons.Checkmark, stringResource(Res.string.playlist_done), onClick = { state.editingPlaylistId = null }, selected = true, filled = true)
            } else {
                Row {
                    ResumeOverrideButton(state, context, filled = true)
                    IconButton(AppIcons.More, stringResource(Res.string.action_more), onClick = { state.openPlaylistOptions(id) }, filled = true)
                }
            }
        },
    ) { index, track ->
        if (!editing) {
            TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track, fromPlaylist = id) }) {
                state.tapTrack(tracks, index, context)
            }
            return@ArtworkCollectionPage
        }
        // Stored-index mapping keeps hidden/missing entries intact when visible rows move.
        val dragging = track.id == draggedId
        DisposableEffect(track.id, rowMetrics) {
            onDispose {
                // Keep measurements bounded to composed rows, including lazy prefetch items.
                rowMetrics.heights.remove(track.id)
                if (draggedId == track.id) {
                    draggedId = null
                    dragOffset = 0f
                }
            }
        }
        Box(
            Modifier
                .heightIn(min = EditRowHeight)
                .onSizeChanged { size ->
                    if (rowMetrics.widthPx != 0 && rowMetrics.widthPx != size.width) {
                        rowMetrics.heights.clear()
                        draggedId = null
                        dragOffset = 0f
                    }
                    rowMetrics.widthPx = size.width
                    rowMetrics.heights[track.id] = size.height
                }
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                .semantics {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction(upLabel) { moveVisible(track.id, -1) })
                        if (index < tracks.lastIndex) add(CustomAccessibilityAction(downLabel) { moveVisible(track.id, 1) })
                    }
                },
        ) {
            ListRow(
                title = track.title,
                subtitle = artistName(track.artist),
                leading = {
                    IconButton(AppIcons.Close, removeLabel, onClick = { state.userPlaylists.remove(id, track.id) })
                },
                trailing = {
                    AppIcon(
                        icon = AppIcons.DragHandle,
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(48.dp)
                            .padding(10.dp)
                            .semantics { contentDescription = reorderLabel }
                            .pointerInput(track.id, rowMetrics) {
                                detectDragGestures(
                                    onDragStart = { draggedId = track.id; dragOffset = 0f },
                                    onDragEnd = { draggedId = null; dragOffset = 0f },
                                    onDragCancel = { draggedId = null; dragOffset = 0f },
                                ) { change, amount ->
                                    if (draggedId != track.id) return@detectDragGestures
                                    change.consume()
                                    dragOffset += amount.y
                                    val all = state.userPlaylists.get(id)?.trackIds ?: return@detectDragGestures
                                    val from = all.indexOf(track.id)
                                    val direction = if (dragOffset > 0f) 1 else -1
                                    val to = adjacentVisibleIndex(all, from, direction)
                                    val ownHeight = rowMetrics.heights[track.id]?.toFloat() ?: fallbackRowHeightPx
                                    val neighborHeight = to?.let { rowMetrics.heights[all[it]] }?.toFloat()
                                    if (neighborHeight == null) {
                                        // Bound dragging at list edges and defer swaps across
                                        // unmeasured lazy rows; their height must not be guessed.
                                        dragOffset = dragOffset.coerceIn(-ownHeight, ownHeight)
                                        return@detectDragGestures
                                    }
                                    // Cross the neighbor's midpoint, then compensate by the
                                    // measured space it occupied to keep the row under the finger.
                                    if (dragOffset * direction > neighborHeight / 2 && moveVisible(track.id, direction)) {
                                        dragOffset -= direction * neighborHeight
                                    }
                                }
                            },
                    )
                },
                onClick = null,
            )
        }
    }
}

private val EditRowHeight = 64.dp

private class PlaylistEditRowMetrics {
    var widthPx: Int = 0
    val heights = mutableMapOf<String, Int>()
}

/** The user's playlists in the Library: a row of covers, with New and Import at the start. */
@Composable
internal fun UserPlaylistRow(state: AppState, onImport: (() -> Unit)?) {
    val playlists by state.userPlaylists.playlists.collectAsState()
    val smart by state.smartPlaylists.playlists.collectAsState()
    val history by state.history.state.collectAsState()
    val favorites by state.favorites.ids.collectAsState()
    val now = rememberPlaylistTime()
    val smartLabel = stringResource(dev.cmdrchara.songlass.shared.resources.Res.string.smart_new)
    val newLabel = stringResource(Res.string.playlist_new)
    val importLabel = stringResource(Res.string.playlist_import)
    val shelf = buildList {
        add(ShelfItem("new", newLabel, null, null, { state.askPlaylistName(PlaylistPrompt.Create(emptyList())) }, icon = AppIcons.AddToQueue, isAction = true))
        add(ShelfItem("smart-new", smartLabel, null, null, { state.open(Route.SmartPlaylistEditor()) }, icon = AppIcons.ChartBars, isAction = true))
        if (onImport != null) add(ShelfItem("import", importLabel, null, null, onImport, icon = AppIcons.ImportFile, isAction = true))
        smart.forEach { playlist ->
            val tracks = playlist.rules.resolve(state.library, history, favorites.toSet(), now)
            add(ShelfItem(playlist.id, playlist.name, pluralStringResource(Res.plurals.songs_count, tracks.size, tracks.size),
                tracks.firstOrNull()?.artwork(), { state.open(Route.SmartPlaylistDetail(playlist.id)) }, icon = if (tracks.isEmpty()) AppIcons.ChartBars else null))
        }
        playlists.forEach { playlist ->
            val tracks = playlist.tracksIn(state.library)
            add(
                ShelfItem(
                    playlist.id,
                    playlist.name,
                    pluralStringResource(Res.plurals.songs_count, tracks.size, tracks.size),
                    playlist.artwork(tracks),
                    { state.open(Route.UserPlaylistDetail(playlist.id)) },
                ),
            )
        }
    }
    ArtworkShelf(shelf, ShelfStyle.Carousel, PlaylistTileSize)
}

private val PlaylistTileSize: Dp = 112.dp

/** Every playlist sheet: pick a playlist, name one, and the options of a playlist page. */
@Composable
fun PlaylistSheets(state: AppState, onExport: ((fileName: String, content: String) -> Unit)?) {
    val playlists by state.userPlaylists.playlists.collectAsState()
    val cancel = stringResource(Res.string.action_cancel)
    val exportPlaylist = rememberUserPlaylistExportAction(state, onExport)
    var playlistSaveFailed by remember { mutableStateOf(false) }
    var queueSelectionChanged by remember { mutableStateOf(false) }
    InfoSheet(
        visible = state.playlistMutationRejected || playlistSaveFailed || queueSelectionChanged,
        title = stringResource(Res.string.playlist_save),
        closeLabel = stringResource(Res.string.action_close),
        onDismiss = { state.closePlaylistMutationNotice(); playlistSaveFailed = false; queueSelectionChanged = false },
        rows = if (queueSelectionChanged) listOf(InfoRow(stringResource(Res.string.queue_save_upcoming), stringResource(Res.string.queue_save_selection_changed)))
            else listOf(InfoRow(stringResource(if (playlistSaveFailed) Res.string.bookmark_write_failed else Res.string.restore_operation_changed),
                stringResource(if (playlistSaveFailed) Res.string.playlist_import_save_failed else Res.string.restore_operation_retry))),
    )

    // Keep the last values while sheets animate out.
    var lastTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    state.addToPlaylistTracks?.let { lastTracks = it }
    ActionSheet(
        visible = state.addToPlaylistTracks != null,
        title = stringResource(Res.string.playlist_add_to),
        subtitle = lastTracks.singleOrNull()?.title,
        cancelLabel = cancel,
        onDismiss = state::closeAddToPlaylist,
        actions = buildList {
            val ids = lastTracks.map { it.id }
            add(SheetAction(stringResource(Res.string.playlist_new), AppIcons.AddToQueue) { state.askPlaylistName(PlaylistPrompt.Create(ids)) })
            playlists.forEach { playlist -> add(SheetAction(playlist.name, AppIcons.Library) { state.askPlaylistInsertion(playlist.id, ids) }) }
        },
    )

    var lastInsertion by remember { mutableStateOf<PlaylistInsertionRequest?>(null) }
    state.playlistInsertionRequest?.let { lastInsertion = it }
    val insertion = lastInsertion
    ActionSheet(
        visible = state.playlistInsertionRequest != null,
        title = stringResource(Res.string.playlist_insert_title),
        subtitle = playlists.firstOrNull { it.id == insertion?.playlistId }?.name,
        cancelLabel = cancel, onDismiss = state::closePlaylistInsertion,
        actions = if (insertion == null) emptyList() else listOf(
            SheetAction(stringResource(Res.string.playlist_insert_end), AppIcons.AddToQueue) { state.addToPlaylist(insertion, PlaylistInsertion.End) },
            SheetAction(stringResource(Res.string.playlist_insert_start), AppIcons.AddToQueue) { state.addToPlaylist(insertion, PlaylistInsertion.Start) },
        ),
    )

    var lastNotice by remember { mutableStateOf<PlaylistAdditionNotice?>(null) }
    state.playlistAdditionNotice?.let { lastNotice = it }
    val notice = lastNotice
    InfoSheet(
        visible = state.playlistAdditionNotice != null,
        title = notice?.playlistName?.ifEmpty { null } ?: stringResource(Res.string.playlist_add_to),
        closeLabel = stringResource(Res.string.playlist_done), onDismiss = state::closePlaylistAdditionNotice,
        rows = if (notice == null) emptyList() else if (!notice.result.playlistFound) listOf(
            InfoRow(stringResource(Res.string.playlist_unavailable), stringResource(Res.string.playlist_pick_another)),
        ) else if (!notice.result.accepted) listOf(
            InfoRow(stringResource(Res.string.restore_operation_changed), stringResource(Res.string.restore_operation_retry)),
        ) else listOf(
            InfoRow(stringResource(Res.string.playlist_added), notice.result.added.toString()),
            InfoRow(stringResource(Res.string.playlist_already_present), notice.result.alreadyPresent.toString()),
        ),
    )

    var lastRemoval by remember { mutableStateOf<PlaylistRemovalRequest?>(null) }
    state.playlistRemovalRequest?.let { lastRemoval = it }
    val removal = lastRemoval
    ActionSheet(
        visible = state.playlistRemovalRequest != null,
        title = stringResource(if (removal?.missingOnly == true) Res.string.playlist_remove_missing_confirm else Res.string.playlist_delete_confirm),
        subtitle = playlists.firstOrNull { it.id == removal?.playlistId }?.name,
        cancelLabel = cancel, onDismiss = state::closePlaylistRemoval,
        actions = if (removal == null) emptyList() else listOf(
            SheetAction(stringResource(if (removal.missingOnly) Res.string.playlist_remove_missing else Res.string.playlist_delete), AppIcons.Close) {
                state.removePlaylist(removal)
            },
        ),
    )

    var lastPrompt by remember { mutableStateOf<PlaylistPrompt?>(null) }
    state.playlistPrompt?.let { lastPrompt = it }
    val prompt = state.playlistPrompt ?: lastPrompt
    PromptSheet(
        visible = state.playlistPrompt != null,
        title = stringResource(if (prompt is PlaylistPrompt.Rename) Res.string.playlist_rename else Res.string.playlist_new),
        initialText = (prompt as? PlaylistPrompt.Rename)?.currentName.orEmpty(),
        placeholder = stringResource(Res.string.playlist_name),
        confirmLabel = stringResource(if (prompt is PlaylistPrompt.Rename) Res.string.playlist_save else Res.string.playlist_create),
        cancelLabel = cancel,
        onConfirm = { name ->
            try {
                queueSelectionChanged = state.confirmPlaylistName(prompt, name) == PlaylistNameResult.QueueSelectionChanged
            } catch (_: Exception) { playlistSaveFailed = true }
        },
        onDismiss = state::closePlaylistPrompt,
    )

    var lastOptions by remember { mutableStateOf<String?>(null) }
    state.playlistOptionsId?.let { lastOptions = it }
    val options = playlists.firstOrNull { it.id == lastOptions }
    ActionSheet(
        visible = state.playlistOptionsId != null && options != null,
        title = options?.name.orEmpty(),
        cancelLabel = cancel,
        onDismiss = state::closePlaylistOptions,
        actions = if (options == null) emptyList() else buildList {
            add(SheetAction(stringResource(Res.string.playlist_edit), AppIcons.DragHandle) { state.editingPlaylistId = options.id })
            add(SheetAction(stringResource(Res.string.playlist_rename), AppIcons.Lyrics) { state.askPlaylistName(PlaylistPrompt.Rename(options.id, options.name)) })
            if (exportPlaylist != null) add(
                SheetAction(stringResource(Res.string.playlist_export), AppIcons.ImportFile) {
                    exportPlaylist(options)
                },
            )
            add(
                SheetAction(stringResource(Res.string.playlist_delete), AppIcons.Close) {
                    state.askPlaylistRemoval(options.id)
                },
            )
        },
    )
}
