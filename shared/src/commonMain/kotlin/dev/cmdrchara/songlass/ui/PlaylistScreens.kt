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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.M3u
import dev.cmdrchara.songlass.core.playlists.UserPlaylist
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
    val playlist = playlists.firstOrNull { it.id == id } ?: return
    val tracks = remember(playlist, state.library) { playlist.tracksIn(state.library) }
    val context = PlayContext.UserPlaylist(id)
    val editing = state.editingPlaylistId == id
    val rowHeightPx = with(LocalDensity.current) { EditRowHeight.toPx() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentTracks by rememberUpdatedState(tracks)
    val reorderLabel = stringResource(Res.string.action_reorder)
    val removeLabel = stringResource(Res.string.playlist_remove_song)

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
        extra = if (tracks.isEmpty()) {
            {
                item {
                    AppText(
                        stringResource(Res.string.playlist_empty),
                        AppTheme.typography.subheadline,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        } else null,
        headerAction = {
            if (editing) {
                IconButton(AppIcons.Checkmark, stringResource(Res.string.playlist_done), onClick = { state.editingPlaylistId = null }, selected = true, filled = true)
            } else {
                IconButton(AppIcons.More, stringResource(Res.string.action_more), onClick = { state.openPlaylistOptions(id) }, filled = true)
            }
        },
    ) { index, track ->
        if (!editing) {
            TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track, fromPlaylist = id) }) {
                state.controller.playTracks(tracks, index, context)
            }
            return@ArtworkCollectionPage
        }
        // Edit mode: fixed-height rows, so the drag offset maps directly to positions.
        val dragging = track.id == draggedId
        Box(
            Modifier
                .height(EditRowHeight)
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
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
                            .size(44.dp)
                            .padding(10.dp)
                            .semantics { contentDescription = reorderLabel }
                            .pointerInput(track.id) {
                                detectDragGestures(
                                    onDragStart = { draggedId = track.id; dragOffset = 0f },
                                    onDragEnd = { draggedId = null; dragOffset = 0f },
                                    onDragCancel = { draggedId = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val position = currentTracks.indexOfFirst { it.id == track.id }
                                    val all = state.userPlaylists.get(id)?.trackIds ?: return@detectDragGestures
                                    val from = all.indexOf(track.id)
                                    if (dragOffset > rowHeightPx / 2 && position < currentTracks.lastIndex) {
                                        state.userPlaylists.move(id, from, all.indexOf(currentTracks[position + 1].id))
                                        dragOffset -= rowHeightPx
                                    } else if (dragOffset < -rowHeightPx / 2 && position > 0) {
                                        state.userPlaylists.move(id, from, all.indexOf(currentTracks[position - 1].id))
                                        dragOffset += rowHeightPx
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
            playlists.forEach { playlist -> add(SheetAction(playlist.name, AppIcons.Library) { state.userPlaylists.add(playlist.id, ids) }) }
        },
    )

    var lastPrompt by remember { mutableStateOf<PlaylistPrompt?>(null) }
    state.playlistPrompt?.let { lastPrompt = it }
    val prompt = lastPrompt
    PromptSheet(
        visible = state.playlistPrompt != null,
        title = stringResource(if (prompt is PlaylistPrompt.Rename) Res.string.playlist_rename else Res.string.playlist_new),
        initialText = (prompt as? PlaylistPrompt.Rename)?.currentName.orEmpty(),
        placeholder = stringResource(Res.string.playlist_name),
        confirmLabel = stringResource(if (prompt is PlaylistPrompt.Rename) Res.string.playlist_save else Res.string.playlist_create),
        cancelLabel = cancel,
        onConfirm = { name ->
            when (prompt) {
                is PlaylistPrompt.Create -> {
                    val id = state.userPlaylists.create(name, prompt.trackIds)
                    // A playlist made from the Library opens right away; one made from a song does not interrupt.
                    if (prompt.trackIds.isEmpty()) state.open(Route.UserPlaylistDetail(id))
                }
                is PlaylistPrompt.Rename -> state.userPlaylists.rename(prompt.id, name)
                null -> Unit
            }
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
            if (onExport != null) add(
                SheetAction(stringResource(Res.string.playlist_export), AppIcons.ImportFile) {
                    onExport("${options.name}.m3u8", M3u.write(options.tracksIn(state.library)))
                },
            )
            add(
                SheetAction(stringResource(Res.string.playlist_delete), AppIcons.Close) {
                    state.userPlaylists.delete(options.id)
                    state.goBack()
                },
            )
        },
    )
}
