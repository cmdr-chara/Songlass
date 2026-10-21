package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.core.model.*
import dev.cmdrchara.songlass.core.tags.TagRecoveryRequiredException
import dev.cmdrchara.songlass.core.tags.TagEditSelection
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

/** Native read snapshots bind this form's drafts and later saves to the selected recording. */
@Composable
fun TagEditorScreen(track: Track, graph: AppGraph, state: AppState, backLabel: String, contentPadding: PaddingValues) {
    val editor = graph.tagEditor ?: return
    val scope = rememberCoroutineScope()
    var original by remember(track.id, track.uri) { mutableStateOf<TagEditSnapshot?>(null) }
    var loadFailed by remember(track.id) { mutableStateOf(false) }
    var sourceChanged by remember(track.id, track.uri) { mutableStateOf(false) }
    var recoveryRequired by remember(track.id) { mutableStateOf(false) }
    var loadRevision by remember(track.id) { mutableIntStateOf(0) }
    var recoveryRefresh by remember(track.id) { mutableIntStateOf(0) }
    var draftSourceKey by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var title by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var artist by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var album by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var albumArtist by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var number by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var disc by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var date by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var cover by rememberSaveable(track.id, track.uri) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TagSaveResult?>(null) }
    var reloading by remember { mutableStateOf(false) }
    fun libraryReady() = graph.source.hasAccess.value && graph.hasCompleteLibraryScan.value &&
        !graph.isLibraryLoading.value && graph.libraryRefreshFailure.value == null
    fun currentSelection() = libraryReady() && TagEditSelection.isCurrent(track, graph.allTracks.value)
    fun discardDraft() {
        original = null; draftSourceKey = null
        title = null; artist = null; album = null; albumArtist = null
        number = null; disc = null; date = null; cover = null
        result = null; invalid = false
    }
    LaunchedEffect(track, editor, loadRevision) {
        original = null
        loadFailed = false
        recoveryRequired = false
        sourceChanged = false
        try {
            if (!currentSelection()) throw TagEditSourceChangedException()
            val snapshot = editor.read(track)
            if (!currentSelection()) throw TagEditSourceChangedException()
            if (draftSourceKey != null && draftSourceKey != snapshot.sourceKey) {
                // Activity/process recreation must not attach a saved draft for A to newly read B.
                loadFailed = true
                sourceChanged = true
            } else {
                original = snapshot
                draftSourceKey = snapshot.sourceKey
            }
        }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: TagEditSourceChangedException) { loadFailed = true; sourceChanged = true }
        catch (_: TagRecoveryRequiredException) { loadFailed = true; recoveryRequired = true }
        catch (_: Exception) { loadFailed = true }
        finally { recoveryRefresh++ }
    }
    val picker = rememberTagCoverPicker { cover = it }
    val saver = rememberTagSaver(editor) { status ->
        saving = false
        result = status
        recoveryRefresh++
        if (status == TagSaveResult.SourceChanged) {
            original = null
            loadFailed = true
            sourceChanged = true
        }
        if (status == TagSaveResult.Saved) {
            if ((state.currentRoute as? Route.TagEditor)?.track?.id == track.id) state.goBack()
        }
    }
    val snapshot = original
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding(), contentPadding = contentPadding) {
        item { BackLink(backLabel, { if (!saving) state.goBack() }) }
        item { SectionTitle(stringResource(Res.string.tags_edit)) }
        item { FeatureHint(stringResource(Res.string.tags_hint)) }
        item {
            TagRecoveryPanel(editor, refreshKey = recoveryRefresh, onRecoveryArchived = {
                discardDraft()
                loadFailed = false; recoveryRequired = false; sourceChanged = false
                loadRevision++
            })
        }
        if (snapshot == null) {
            item { FeatureHint(stringResource(when {
                sourceChanged -> Res.string.tags_source_changed
                recoveryRequired -> Res.string.tags_recovery_needed
                loadFailed -> Res.string.tags_read_failed
                else -> Res.string.tags_loading
            })) }
            if (sourceChanged) item {
                ActionButton(stringResource(if (reloading) Res.string.tags_loading else Res.string.tags_discard_reload), {
                    if (!reloading) {
                        discardDraft()
                        reloading = true
                        scope.launch {
                            try {
                                graph.refreshLibrary()
                                val current = if (libraryReady()) graph.allTracks.value.singleOrNull { it.id == track.id } else null
                                when {
                                    current == track -> loadRevision++
                                    current != null -> {
                                        // A new explicit choice: old fields and witness were discarded first.
                                        state.goBack()
                                        state.openTagEditor(current)
                                    }
                                    else -> {
                                        state.goBack()
                                        // The second selection opens the library root, even if another tab
                                        // previously retained a deeper library page.
                                        state.selectTab(Tab.Library)
                                        state.selectTab(Tab.Library)
                                    }
                                }
                            } finally { reloading = false }
                        }
                    }
                }, Modifier.padding(horizontal = pageInset))
            }
        } else {
            val tags = snapshot.tags
            item { AlbumArt(track.artwork().let { if (cover == null) it else it.copy(imageKey = cover) }, Modifier.padding(16.dp).size(128.dp)) }
            if (picker != null) item { ActionButton(stringResource(Res.string.tags_cover), { if (!saving) picker() }, Modifier.padding(horizontal = pageInset)) }
            if (cover != null) item { ActionButton(stringResource(Res.string.tags_cover_reset), { if (!saving) cover = null }, Modifier.padding(horizontal = pageInset)) }
            item { FormField(title ?: tags.title, { title = it; invalid = false }, stringResource(Res.string.tags_title), Modifier.padding(horizontal = pageInset), !saving) }
            item { FormField(artist ?: tags.artist, { artist = it }, stringResource(Res.string.tags_artist), Modifier.padding(horizontal = pageInset), !saving) }
            item { FormField(album ?: tags.album, { album = it }, stringResource(Res.string.tags_album), Modifier.padding(horizontal = pageInset), !saving) }
            item { FormField(albumArtist ?: tags.albumArtist, { albumArtist = it }, stringResource(Res.string.tags_album_artist), Modifier.padding(horizontal = pageInset), !saving) }
            item { FormField(number ?: tags.trackNumber, { number = it; invalid = false }, stringResource(Res.string.tags_track), Modifier.padding(horizontal = pageInset), !saving) }
            item { FormField(disc ?: tags.discNumber, { disc = it; invalid = false }, stringResource(Res.string.tags_disc), Modifier.padding(horizontal = pageInset), !saving) }
            item { FormField(date ?: tags.date, { date = it; invalid = false }, stringResource(Res.string.tags_date), Modifier.padding(horizontal = pageInset), !saving) }
            if (invalid) item { FeatureHint(stringResource(Res.string.tags_invalid)) }
            if (result != null && result != TagSaveResult.Saved) item { FeatureHint(stringResource(when (result) {
                TagSaveResult.PermissionDenied -> Res.string.tags_denied
                TagSaveResult.SourceChanged -> Res.string.tags_source_changed
                TagSaveResult.RecoveryRequired -> Res.string.tags_recovery_needed
                else -> Res.string.tags_save_failed
            })) }
            if (saver != null) item { ActionButton(stringResource(if (saving) Res.string.tags_saving else Res.string.playlist_save), {
                if (!saving) {
                    if (!currentSelection()) {
                        original = null
                        loadFailed = true
                        sourceChanged = true
                    } else {
                        val edited = EditableTags(title ?: tags.title, artist ?: tags.artist, album ?: tags.album, albumArtist ?: tags.albumArtist,
                            number ?: tags.trackNumber, disc ?: tags.discNumber, date ?: tags.date)
                        invalid = !edited.isValidComparedTo(tags)
                        if (!invalid) { saving = true; result = null; saver(TagEditRequest(track, snapshot, edited, cover)) }
                    }
                }
            }, Modifier.fillMaxWidth().padding(16.dp)) }
        }
    }
}
