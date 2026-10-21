package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cmdrchara.songlass.core.model.TrackAvailability
import dev.cmdrchara.songlass.core.queue.PlaybackQueue
import dev.cmdrchara.songlass.core.queue.QueuePlaylistSnapshot
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.queue.UpcomingQueue
import dev.cmdrchara.songlass.core.queue.UpcomingQueueSummary
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private enum class QueueSaveProblem { LibraryUnavailable, SelectionChanged, Empty }

/** Up Next keeps slot-based editing while making one-shot additions and the source distinct. */
@Composable
fun QueuePane(
    state: AppState,
    queue: PlaybackQueue,
    onSaveUpcoming: (QueuePlaylistSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Player position ticks do not rebuild the queue projection or scan the library.
    val upcoming = remember(queue) { UpcomingQueue.from(queue) }
    val currentQueue by rememberUpdatedState(queue)
    val hasAccess by state.libraryAccess.collectAsState()
    val hasScan by state.hasCompleteLibraryScan.collectAsState()
    val loading by state.isLibraryLoading.collectAsState()
    val canOfferSave = upcoming.summary.availableCount > 0 && hasAccess && hasScan && !loading
    val listState = rememberLazyListState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    var options by remember { mutableStateOf(false) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var review by remember { mutableStateOf<QueuePlaylistSnapshot?>(null) }
    var saveProblem by remember { mutableStateOf<QueueSaveProblem?>(null) }
    val editLabel = stringResource(Res.string.queue_edit_entry)
    val upLabel = stringResource(Res.string.queue_move_up)
    val downLabel = stringResource(Res.string.queue_move_down)
    val removeLabel = stringResource(Res.string.queue_remove_entry)

    fun checkSelection(snapshot: QueuePlaylistSnapshot): QueueSaveProblem? = when {
        !state.restoreMutations.canMutate || !state.libraryAccess.value ||
            !state.hasCompleteLibraryScan.value || state.isLibraryLoading.value -> QueueSaveProblem.LibraryUnavailable
        snapshot.tracks.isEmpty() -> QueueSaveProblem.Empty
        snapshot.currentTrackIds(TrackAvailability(state.allTracks.value)) == null -> QueueSaveProblem.SelectionChanged
        else -> null
    }

    fun beginSave() {
        state.clearSavedQueuePlaylist()
        if (!state.isNowPlayingOpen || state.playerPage != PlayerPage.Queue || state.hasOverlay) return
        val snapshot = QueuePlaylistSnapshot.capture(state.controller.queue.value)
        saveProblem = checkSelection(snapshot)
        review = snapshot.takeIf { saveProblem == null }
    }

    fun confirmSelection(snapshot: QueuePlaylistSnapshot) {
        if (!state.isNowPlayingOpen || state.playerPage != PlayerPage.Queue || state.hasOverlay) return
        saveProblem = checkSelection(snapshot)
        review = null
        if (saveProblem == null) onSaveUpcoming(snapshot)
    }

    fun canMove(id: Long, direction: Int): Boolean {
        val latest = currentQueue
        val index = latest.indexOf(id)
        val target = index + direction
        return index > latest.currentIndex && target > latest.currentIndex && target in latest.entries.indices &&
            latest.entries[index].isTemporary == latest.entries[target].isTemporary
    }

    fun move(id: Long, direction: Int): Boolean = state.controller.moveQueueEntry(id, direction)

    PlatformBackHandler(state.isNowPlayingOpen && state.playerPage == PlayerPage.Queue && !state.hasOverlay &&
        (review != null || saveProblem != null || options || editId != null)) {
        when {
            editId != null -> editId = null
            options -> options = false
            review != null -> review = null
            else -> saveProblem = null
        }
    }
    LaunchedEffect(review, saveProblem) {
        if ((review != null || saveProblem != null) && state.isNowPlayingOpen && state.playerPage == PlayerPage.Queue) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(queue) {
        if (upcoming.entries.none { it.id == editId }) editId = null
        if (queue.pendingTemporaryCount == 0 && queue.current?.isTemporary != true) options = false
    }

    val reviewing = review
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item(key = "queue-summary") {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppText(stringResource(Res.string.up_next), AppTheme.typography.headline,
                            Modifier.weight(1f).semantics { heading() })
                        if (queue.pendingTemporaryCount > 0 || queue.current?.isTemporary == true) {
                            IconButton(AppIcons.More, stringResource(Res.string.queue_options), { options = true })
                        }
                    }
                    AppText(upcomingSummaryLabel(upcoming.summary), AppTheme.typography.footnote,
                        color = AppTheme.colors.secondaryLabel)
                    if (queue.repeatMode != RepeatMode.Off) AppText(
                        stringResource(when {
                            queue.repeatMode == RepeatMode.One -> Res.string.queue_repeat_one_explanation
                            queue.hasSource -> Res.string.queue_repeat_collection_explanation
                            else -> Res.string.queue_repeat_temporary_explanation
                        }), AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
                    )
                    if (reviewing == null && canOfferSave) ActionButton(
                        stringResource(Res.string.queue_save_upcoming), ::beginSave,
                        Modifier.fillMaxWidth(), icon = AppIcons.Library,
                    )
                    else if (reviewing == null && upcoming.summary.availableCount > 0) AppText(
                        stringResource(Res.string.queue_save_library_wait), AppTheme.typography.footnote,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    if (upcoming.summary.entryCount > 0 && upcoming.summary.availableCount == 0) AppText(
                        stringResource(Res.string.queue_save_no_eligible), AppTheme.typography.footnote,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
            }
            if (saveProblem != null) item(key = "queue-save-problem") {
                AppText(stringResource(when (saveProblem) {
                    QueueSaveProblem.LibraryUnavailable -> Res.string.queue_save_library_wait
                    QueueSaveProblem.SelectionChanged -> Res.string.queue_save_selection_changed
                    else -> Res.string.queue_save_no_eligible
                }), AppTheme.typography.footnote,
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    color = AppTheme.colors.secondaryLabel)
            }
            if (reviewing != null) item(key = "queue-save-review") {
                QueuePlaylistReview(reviewing, onCancel = { review = null }, onContinue = { confirmSelection(reviewing) })
            }
            state.savedQueuePlaylist?.let { saved -> item(key = "queue-saved") {
                ListRow(
                    title = stringResource(Res.string.queue_saved_playlist, saved.name),
                    subtitle = stringResource(Res.string.queue_saved_open),
                    leading = { dev.cmdrchara.songlass.designsystem.foundation.AppIcon(AppIcons.Checkmark, null, tint = AppTheme.colors.accent) },
                    onClick = state::openSavedQueuePlaylist,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            } }
            if (options) item(key = "queue-options") {
                SettingsGroup(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(stringResource(Res.string.queue_options), AppTheme.typography.headline,
                            Modifier.semantics { heading() })
                        if (queue.pendingTemporaryCount > 0) ActionButton(stringResource(Res.string.queue_clear_pending), {
                            options = false
                            state.controller.clearTemporaryQueue()
                        }, Modifier.fillMaxWidth(), icon = AppIcons.Close)
                        if (queue.current?.isTemporary == true) ActionButton(
                            stringResource(if (queue.hasSource) Res.string.queue_return_source else Res.string.queue_finish_temporary), {
                                options = false
                                state.controller.returnToSource()
                            }, Modifier.fillMaxWidth(), icon = AppIcons.Library,
                        )
                        ActionButton(stringResource(Res.string.action_cancel), { options = false }, Modifier.fillMaxWidth())
                    }
                }
            }
            if (upcoming.entries.isEmpty()) item(key = "queue-empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(Res.string.up_next_empty), AppTheme.typography.subheadline,
                        color = AppTheme.colors.secondaryLabel)
                    ActionButton(stringResource(Res.string.queue_choose_songs), {
                        state.closeNowPlaying()
                        state.open(Route.LibraryPage(LibraryCategory.Songs))
                    }, Modifier.fillMaxWidth(), icon = AppIcons.Library)
                }
            }
            upcoming.sections.forEachIndexed { sectionIndex, section ->
                item(key = "queue-section-$sectionIndex") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)
                        .semantics(mergeDescendants = true) { heading() },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(stringResource(if (section.isTemporary) Res.string.queue_section_additions else Res.string.queue_section_collection),
                            AppTheme.typography.subheadline, Modifier.weight(1f))
                        AppText(section.entries.size.toString(), AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
                    }
                }
                items(section.entries, key = { it.id }) { entry ->
                    val dragging = entry.id == draggedId
                    var rowHeightPx by remember(entry.id) { mutableIntStateOf(0) }
                    Column(Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)) {
                        Box(Modifier.heightIn(min = 60.dp).onSizeChanged { rowHeightPx = it.height }
                            .semantics {
                                customActions = buildList {
                                    if (canMove(entry.id, -1)) add(CustomAccessibilityAction(upLabel) { move(entry.id, -1) })
                                    if (canMove(entry.id, 1)) add(CustomAccessibilityAction(downLabel) { move(entry.id, 1) })
                                    add(CustomAccessibilityAction(removeLabel) { state.controller.removeFromQueue(entry.id); true })
                                }
                            }
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) dragOffset else 0f
                                val scale = if (dragging && !reduceMotion) 1.02f else 1f
                                scaleX = scale; scaleY = scale
                            }) {
                            ListRow(
                                title = entry.track.title,
                                subtitle = if (!entry.isAvailable) stringResource(Res.string.queue_file_unavailable)
                                    else if (entry.isTemporary) stringResource(Res.string.queue_temporary_subtitle, artistName(entry.track.artist))
                                    else artistName(entry.track.artist),
                                leading = { AlbumArt(rememberTrackArtwork(entry.track), Modifier.size(40.dp), cornerRadius = 6.dp) },
                                trailing = {
                                    IconButton(AppIcons.DragHandle, editLabel, onClick = { editId = entry.id.takeUnless { editId == it } },
                                        modifier = Modifier.pointerInput(entry.id, rowHeightPx) {
                                            detectDragGestures(
                                                onDragStart = { editId = null; draggedId = entry.id; dragOffset = 0f },
                                                onDragEnd = { draggedId = null; dragOffset = 0f },
                                                onDragCancel = { draggedId = null; dragOffset = 0f },
                                            ) { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                if (rowHeightPx > 0) {
                                                    if (dragOffset > rowHeightPx / 2 && move(entry.id, 1)) dragOffset -= rowHeightPx
                                                    else if (dragOffset < -rowHeightPx / 2 && move(entry.id, -1)) dragOffset += rowHeightPx
                                                    else dragOffset = dragOffset.coerceIn(-rowHeightPx.toFloat(), rowHeightPx.toFloat())
                                                }
                                            }
                                        })
                                },
                                onClick = if (entry.isAvailable) ({ state.controller.skipTo(entry.id) }) else null,
                            )
                        }
                        if (entry.id == editId) SettingsGroup(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (canMove(entry.id, -1)) ActionButton(upLabel, {
                                    editId = null
                                    move(entry.id, -1)
                                }, Modifier.fillMaxWidth())
                                if (canMove(entry.id, 1)) ActionButton(downLabel, {
                                    editId = null
                                    move(entry.id, 1)
                                }, Modifier.fillMaxWidth())
                                ActionButton(removeLabel, {
                                    editId = null
                                    state.controller.removeFromQueue(entry.id)
                                }, Modifier.fillMaxWidth(), icon = AppIcons.Close)
                                ActionButton(stringResource(Res.string.action_cancel), { editId = null }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun upcomingSummaryLabel(summary: UpcomingQueueSummary): String = buildList {
    add(pluralStringResource(Res.plurals.queue_upcoming_count, summary.entryCount, summary.entryCount))
    when {
        summary.durationOverflow -> add(stringResource(Res.string.queue_duration_too_large))
        summary.knownDurationCount > 0 -> add(stringResource(Res.string.queue_known_duration, formatDuration(summary.knownDurationMs)))
    }
    if (summary.unknownDurationCount > 0) add(pluralStringResource(Res.plurals.queue_unknown_durations,
        summary.unknownDurationCount, summary.unknownDurationCount))
    if (summary.unavailableCount > 0) add(pluralStringResource(Res.plurals.queue_unavailable_count,
        summary.unavailableCount, summary.unavailableCount))
}.joinToString(" · ")

/** Full wrapping text is intentional: action-sheet subtitles truncate after one line. */
@Composable
private fun QueuePlaylistReview(snapshot: QueuePlaylistSnapshot, onCancel: () -> Unit, onContinue: () -> Unit) {
    SettingsGroup(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppText(stringResource(Res.string.queue_save_upcoming), AppTheme.typography.headline,
                Modifier.semantics { heading() })
            AppText(pluralStringResource(Res.plurals.queue_save_song_count, snapshot.tracks.size, snapshot.tracks.size),
                AppTheme.typography.body)
            AppText(stringResource(Res.string.queue_save_selection_hint), AppTheme.typography.footnote,
                color = AppTheme.colors.secondaryLabel)
            if (snapshot.duplicateCount > 0) AppText(pluralStringResource(Res.plurals.queue_save_duplicates,
                snapshot.duplicateCount, snapshot.duplicateCount), AppTheme.typography.footnote)
            if (snapshot.unavailableCount > 0) AppText(pluralStringResource(Res.plurals.queue_save_unavailable,
                snapshot.unavailableCount, snapshot.unavailableCount), AppTheme.typography.footnote)
            ActionButton(stringResource(Res.string.queue_save_name), onContinue, Modifier.fillMaxWidth(), icon = AppIcons.Library)
            ActionButton(stringResource(Res.string.action_cancel), onCancel, Modifier.fillMaxWidth())
        }
    }
}
