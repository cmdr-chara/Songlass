package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.core.model.*
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** Drafts live in this form; files are touched only by Save and the native consent flow. */
@Composable
fun TagEditorScreen(track: Track, graph: AppGraph, state: AppState, backLabel: String, contentPadding: PaddingValues) {
    val editor = graph.tagEditor ?: return
    var original by remember(track.id) { mutableStateOf<EditableTags?>(null) }
    var loadFailed by remember(track.id) { mutableStateOf(false) }
    var title by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var artist by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var album by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var albumArtist by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var number by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var disc by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var date by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var cover by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TagSaveResult?>(null) }
    LaunchedEffect(track.id, editor) {
        try { original = editor.read(track) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true }
    }
    val picker = rememberTagCoverPicker { cover = it }
    val saver = rememberTagSaver(editor) { status ->
        saving = false
        result = status
        if (status == TagSaveResult.Saved) {
            if ((state.currentRoute as? Route.TagEditor)?.track?.id == track.id) state.goBack()
        }
    }
    val tags = original
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding(), contentPadding = contentPadding) {
        item { BackLink(backLabel, { if (!saving) state.goBack() }) }
        item { SectionTitle(stringResource(Res.string.tags_edit)) }
        item { FeatureHint(stringResource(Res.string.tags_hint)) }
        if (tags == null) {
            item { FeatureHint(stringResource(if (loadFailed) Res.string.tags_read_failed else Res.string.tags_loading)) }
        } else {
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
            if (result != null && result != TagSaveResult.Saved) item { FeatureHint(stringResource(if (result == TagSaveResult.PermissionDenied) Res.string.tags_denied else Res.string.tags_save_failed)) }
            if (saver != null) item { ActionButton(stringResource(if (saving) Res.string.tags_saving else Res.string.playlist_save), {
                if (!saving) {
                    val edited = EditableTags(title ?: tags.title, artist ?: tags.artist, album ?: tags.album, albumArtist ?: tags.albumArtist,
                        number ?: tags.trackNumber, disc ?: tags.discNumber, date ?: tags.date)
                    invalid = !edited.isValidComparedTo(tags)
                    if (!invalid) { saving = true; result = null; saver(TagEditRequest(track, tags, edited, cover)) }
                }
            }, Modifier.fillMaxWidth().padding(16.dp)) }
        }
    }
}
