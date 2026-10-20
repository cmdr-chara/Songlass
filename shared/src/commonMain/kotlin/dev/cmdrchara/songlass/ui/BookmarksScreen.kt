package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.TrackBookmark
import dev.cmdrchara.songlass.core.playback.TrackSignature
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

/**
 * One shared page using the native primitives of each Songlass look. Missing files remain
 * visible for renaming/deletion; they can never start a different recording by recycled id.
 */
@Composable
fun BookmarksScreen(track: Track?, state: AppState, backLabel: String, padding: PaddingValues) {
    val saved by state.trackMarkers.state.collectAsState()
    val queue by state.controller.queue.collectAsState()
    val player by state.controller.playerState.collectAsState()
    val current = queue.current?.track
    val selection = track ?: current
    val available = selection?.let { state.trackMarkers.resolve(TrackSignature.of(it)) }
    val selectionIsPlaying = selection != null && current != null && selection.id == current.id && selection.uri == current.uri
    val position = if (selectionIsPlaying) player.positionMs.coerceAtLeast(0) else 0L
    val visible = if (track == null) saved.bookmarks else saved.bookmarks.filter { marker ->
        marker.track == TrackSignature.of(track) || state.trackMarkers.resolve(marker.track) == track
    }
    var edit by remember { mutableStateOf<BookmarkEdit?>(null) }
    var input by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    fun begin(next: BookmarkEdit, name: String = "") {
        edit = next
        input = name
        failed = false
    }
    fun save() {
        try {
            val success = when (val request = edit) {
                is BookmarkEdit.Add -> {
                    state.trackMarkers.add(request.track, request.positionMs, input)
                    true
                }
                is BookmarkEdit.Rename -> state.trackMarkers.rename(request.id, input)
                null -> false
            }
            failed = !success
            if (success) edit = null
        } catch (_: Exception) { failed = true }
    }

    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = padding) {
        item {
            BackPageTitle(stringResource(Res.string.bookmark_open), backLabel, state::goBack)
        }
        item { FeatureHint(stringResource(Res.string.bookmark_intro)) }
        if (selection != null) item {
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 6.dp)) {
                ListRow(selection.title, subtitle = if (available == null) stringResource(Res.string.bookmark_unavailable)
                    else stringResource(Res.string.bookmark_current_position, formatDuration(position)))
                if (available != null) ActionButton(stringResource(Res.string.bookmark_add), {
                    begin(BookmarkEdit.Add(selection, position))
                }, Modifier.fillMaxWidth().padding(10.dp), icon = AppIcons.Bookmark)
            }
        }
        if (edit != null) item(key = "bookmark-editor") {
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(stringResource(if (edit is BookmarkEdit.Add) Res.string.bookmark_add else Res.string.bookmark_rename),
                        AppTheme.typography.headline)
                    FormField(input, { input = it.take(200) }, stringResource(Res.string.bookmark_title_optional),
                        Modifier.fillMaxWidth())
                    if (failed) AppText(stringResource(Res.string.bookmark_write_failed),
                        AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(stringResource(Res.string.action_cancel), { edit = null }, Modifier.weight(1f))
                        ActionButton(stringResource(Res.string.bookmark_save), ::save, Modifier.weight(1f))
                    }
                }
            }
        }
        if (visible.isEmpty()) item {
            FeatureHint(stringResource(Res.string.bookmark_empty))
        }
        items(visible, key = { it.id }) { marker: TrackBookmark ->
            val target = state.trackMarkers.resolve(marker.track)
            val label = marker.title ?: stringResource(Res.string.bookmark_untitled, formatDuration(marker.positionMs))
            val subtitle = listOfNotNull(marker.track.title, formatDuration(marker.positionMs),
                if (target == null) stringResource(Res.string.bookmark_unavailable) else null).joinToString(" · ")
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 5.dp)) {
                ListRow(
                    label,
                    subtitle = subtitle,
                    leading = { AppIcon(AppIcons.Bookmark, null, size = 22.dp, tint = AppTheme.colors.accent) },
                    onClick = target?.let { song -> ({
                        state.controller.playBookmark(song, marker.positionMs)
                        state.goBack()
                    }) },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(stringResource(Res.string.bookmark_rename), {
                        begin(BookmarkEdit.Rename(marker.id), marker.title.orEmpty())
                    }, Modifier.weight(1f))
                    ActionButton(stringResource(Res.string.bookmark_delete), {
                        try { if (!state.trackMarkers.delete(marker.id)) failed = true }
                        catch (_: Exception) { failed = true }
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}
