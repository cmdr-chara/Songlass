package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.isSameFileAs
import dev.cmdrchara.songlass.core.playback.TrackBookmark
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource

private sealed interface BookmarkEdit {
    data class Add(val track: Track, val positionMs: Long) : BookmarkEdit
    data class Rename(val id: String) : BookmarkEdit
}

private enum class BookmarkFailure { Write, Unavailable }

/**
 * One shared page using the native primitives of each Songlass look. Missing files remain
 * visible for renaming/deletion; they can never start a different recording by recycled id.
 */
@Composable
fun BookmarksScreen(track: Track?, state: AppState, backLabel: String, padding: PaddingValues) {
    val saved by state.trackMarkers.state.collectAsState()
    val libraryRevision by state.trackMarkers.libraryRevision.collectAsState()
    val queue by state.controller.queue.collectAsState()
    val player by state.controller.playerState.collectAsState()
    val current = queue.current?.track
    val selection = track ?: current
    val available = remember(selection, libraryRevision) {
        selection?.let(state.trackMarkers::resolveSelection)
    }
    val selectionIsPlaying = selection != null && current?.isSameFileAs(selection) == true
    val position = if (selectionIsPlaying) player.positionMs.coerceAtLeast(0) else 0L
    val visible = remember(saved.bookmarks, track, available, libraryRevision) {
        if (track == null) saved.bookmarks else state.trackMarkers.bookmarksForDisplay(track)
    }
    var edit by remember { mutableStateOf<BookmarkEdit?>(null) }
    var input by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<BookmarkFailure?>(null) }
    var attention by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val editorFocus = remember { FocusRequester() }

    fun fail(reason: BookmarkFailure) {
        failure = reason
        attention++
    }

    fun begin(next: BookmarkEdit, name: String = "") {
        edit = next
        input = name
        failure = null
        attention++
    }
    fun save() {
        try {
            val success = when (val request = edit) {
                is BookmarkEdit.Add -> {
                    val fresh = state.trackMarkers.resolveSelection(request.track)
                    if (fresh == null) { fail(BookmarkFailure.Unavailable); return }
                    state.trackMarkers.add(fresh, request.positionMs, input)
                    true
                }
                is BookmarkEdit.Rename -> state.trackMarkers.rename(request.id, input)
                null -> false
            }
            if (success) { edit = null; failure = null }
            else fail(BookmarkFailure.Write)
        } catch (_: Exception) { fail(BookmarkFailure.Write) }
    }

    LaunchedEffect(attention) {
        if (failure != null) listState.scrollToItem(2)
        else if (edit != null) {
            listState.scrollToItem(if (selection != null) 3 else 2)
            withFrameNanos { }
            editorFocus.requestFocus()
        }
    }

    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding(),
        state = listState, contentPadding = padding) {
        item {
            BackPageTitle(stringResource(Res.string.bookmark_open), backLabel, state::goBack)
        }
        item { FeatureHint(stringResource(Res.string.bookmark_intro)) }
        if (failure != null) item(key = "bookmark-error") {
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }) {
                AppText(stringResource(if (failure == BookmarkFailure.Unavailable)
                    Res.string.bookmark_unavailable else Res.string.bookmark_write_failed),
                    AppTheme.typography.footnote, Modifier.padding(12.dp))
            }
        }
        if (selection != null) item {
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 6.dp)) {
                ListRow(available?.title ?: selection.title, subtitle = if (available == null) stringResource(Res.string.bookmark_unavailable)
                    else stringResource(Res.string.bookmark_current_position, formatDuration(position)))
                if (available != null) ActionButton(stringResource(Res.string.bookmark_add), {
                    val fresh = state.trackMarkers.resolveSelection(selection)
                    if (fresh == null) fail(BookmarkFailure.Unavailable) else {
                        val now = if (state.controller.queue.value.current?.track?.isSameFileAs(fresh) == true)
                            state.controller.playerState.value.positionMs.coerceAtLeast(0) else 0L
                        begin(BookmarkEdit.Add(fresh, now))
                    }
                }, Modifier.fillMaxWidth().padding(10.dp), icon = AppIcons.Bookmark)
            }
        }
        if (edit != null) item(key = "bookmark-editor") {
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(if (edit is BookmarkEdit.Add) Res.string.bookmark_add else Res.string.bookmark_rename),
                        AppTheme.typography.headline)
                    FormField(input, { input = it.take(200) }, stringResource(Res.string.bookmark_title_optional),
                        Modifier.fillMaxWidth().focusRequester(editorFocus))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(stringResource(Res.string.action_cancel), { edit = null; failure = null }, Modifier.weight(1f))
                        ActionButton(stringResource(Res.string.bookmark_save), ::save, Modifier.weight(1f))
                    }
                }
            }
        }
        if (visible.isEmpty()) item {
            FeatureHint(stringResource(Res.string.bookmark_empty))
        }
        items(visible, key = { it.id }) { marker: TrackBookmark ->
            val target = remember(marker.track, libraryRevision) { state.trackMarkers.resolve(marker.track) }
            val label = marker.title ?: stringResource(Res.string.bookmark_untitled, formatDuration(marker.positionMs))
            val subtitle = listOfNotNull(marker.track.title, formatDuration(marker.positionMs),
                if (target == null) stringResource(Res.string.bookmark_unavailable) else null).joinToString(" · ")
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 5.dp)) {
                ListRow(
                    label,
                    subtitle = subtitle,
                    leading = { AppIcon(AppIcons.Bookmark, null, size = 22.dp, tint = AppTheme.colors.accent) },
                    onClick = target?.let { ({
                        val latest = state.trackMarkers.state.value.bookmarks.firstOrNull { it.id == marker.id }
                        val song = latest?.let { state.trackMarkers.resolve(it.track) }
                        if (latest == null || song == null) fail(BookmarkFailure.Unavailable) else {
                            state.controller.playBookmark(song, latest.positionMs)
                            state.goBack()
                        }
                    }) },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(stringResource(Res.string.bookmark_rename), {
                        begin(BookmarkEdit.Rename(marker.id), marker.title.orEmpty())
                    }, Modifier.weight(1f))
                    ActionButton(stringResource(Res.string.bookmark_delete), {
                        try {
                            if (!state.trackMarkers.delete(marker.id)) fail(BookmarkFailure.Write)
                            else failure = null
                        } catch (_: Exception) { fail(BookmarkFailure.Write) }
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}
