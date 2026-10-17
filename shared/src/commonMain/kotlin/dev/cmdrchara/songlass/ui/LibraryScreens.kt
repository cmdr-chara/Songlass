package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.designsystem.FloatingBackButton
import dev.cmdrchara.songlass.shared.resources.tag_playlist
import dev.cmdrchara.songlass.shared.resources.tag_album
import dev.cmdrchara.songlass.designsystem.Tag
import dev.cmdrchara.songlass.designsystem.ProminentButton
import dev.cmdrchara.songlass.designsystem.ArtworkTintedPage
import dev.cmdrchara.songlass.designsystem.ArtworkHeader
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.core.model.Track
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Artist
import dev.cmdrchara.songlass.core.library.Playlist
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SegmentedControl
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.library_access_button
import dev.cmdrchara.songlass.shared.resources.library_access_message
import dev.cmdrchara.songlass.shared.resources.library_access_title
import dev.cmdrchara.songlass.shared.resources.library_empty_hint
import dev.cmdrchara.songlass.shared.resources.library_empty
import dev.cmdrchara.songlass.shared.resources.action_play
import dev.cmdrchara.songlass.shared.resources.action_shuffle
import dev.cmdrchara.songlass.shared.resources.album_minutes
import dev.cmdrchara.songlass.shared.resources.favorites_empty
import dev.cmdrchara.songlass.shared.resources.library_albums
import dev.cmdrchara.songlass.shared.resources.library_artists
import dev.cmdrchara.songlass.shared.resources.library_favorites
import dev.cmdrchara.songlass.shared.resources.library_playlists
import dev.cmdrchara.songlass.shared.resources.library_songs
import dev.cmdrchara.songlass.shared.resources.songs_count
import dev.cmdrchara.songlass.shared.resources.tab_library
import dev.cmdrchara.songlass.shared.resources.settings_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryScreen(
    state: AppState,
    currentTrackId: String?,
    favoriteIds: List<String>,
    hasAccess: Boolean,
    onRequestAccess: (() -> Unit)?,
    contentPadding: PaddingValues,
) {
    val library = state.library
    val favoriteTracks = remember(favoriteIds, library) { state.favorites.tracksIn(library) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ScreenTitle(stringResource(Res.string.tab_library)) {
                IconButton(
                    icon = AppIcons.Settings,
                    contentDescription = stringResource(Res.string.settings_title),
                    onClick = { state.open(Route.Settings) },
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        if (!hasAccess || library.tracks.isEmpty()) {
            item { LibraryEmptyState(hasAccess, onRequestAccess) }
            return@LazyColumn
        }
        item {
            SegmentedControl(
                options = LibrarySection.entries.map { stringResource(it.label) },
                selectedIndex = state.librarySection.ordinal,
                onSelect = { state.librarySection = LibrarySection.entries[it] },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when (state.librarySection) {
            LibrarySection.Playlists -> items(library.playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist) { state.open(Route.PlaylistDetail(playlist)) }
            }
            LibrarySection.Songs -> itemsIndexed(library.tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                    state.controller.playTracks(library.tracks, index)
                }
            }
            LibrarySection.Albums -> items(library.albums.chunked(2)) { pair ->
                AlbumGridRow(pair) { state.open(Route.AlbumDetail(it)) }
            }
            LibrarySection.Artists -> items(library.artists) { artist ->
                ArtistRow(artist) { state.open(Route.ArtistDetail(artist)) }
            }
            LibrarySection.Favorites -> if (favoriteTracks.isEmpty()) {
                item {
                    AppText(
                        stringResource(Res.string.favorites_empty),
                        AppTheme.typography.subheadline,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            } else {
                itemsIndexed(favoriteTracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                        state.controller.playTracks(favoriteTracks, index, PlayContext.Favorites)
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    ListRow(
        title = artistName(artist.name),
        leading = {
            val art = artist.albums.firstOrNull()?.artwork()
            if (art != null) AlbumArt(art, Modifier.size(48.dp), cornerRadius = 24.dp)
        },
        onClick = onClick,
    )
}


@Composable
fun ArtistScreen(artist: Artist, state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { BackLink(backLabel) { state.goBack() } }
        item {
            AppText(
                artistName(artist.name),
                AppTheme.typography.largeTitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 2,
            )
        }
        if (artist.albums.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.library_albums)) }
            items(artist.albums.chunked(2)) { pair -> AlbumGridRow(pair) { state.open(Route.AlbumDetail(it)) } }
        }
        item { SectionTitle(stringResource(Res.string.library_songs)) }
        itemsIndexed(artist.tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                state.controller.playTracks(artist.tracks, index, PlayContext.Artist(artist.name))
            }
        }
    }
}

/** Shown before the user grants access to their music, or when no music was found. */
@Composable
internal fun LibraryEmptyState(hasAccess: Boolean, onRequestAccess: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = stringResource(if (hasAccess) Res.string.library_empty else Res.string.library_access_title),
            style = AppTheme.typography.title3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        AppText(
            text = stringResource(if (hasAccess) Res.string.library_empty_hint else Res.string.library_access_message),
            style = AppTheme.typography.subheadline,
            color = AppTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        if (!hasAccess && onRequestAccess != null) {
            Spacer(Modifier.height(20.dp))
            ActionButton(stringResource(Res.string.library_access_button), onClick = onRequestAccess)
        }
    }
}

private val LibrarySection.label
    get() = when (this) {
        LibrarySection.Playlists -> Res.string.library_playlists
        LibrarySection.Albums -> Res.string.library_albums
        LibrarySection.Artists -> Res.string.library_artists
        LibrarySection.Songs -> Res.string.library_songs
        LibrarySection.Favorites -> Res.string.library_favorites
    }

/** "Soundtracks › Show · 12 songs" (the location only for nested folders). */
@Composable
fun playlistSubtitle(playlist: Playlist): String {
    val count = pluralStringResource(Res.plurals.songs_count, playlist.tracks.size, playlist.tracks.size)
    return if (playlist.location.isEmpty()) count else playlist.location.joinToString(" › ") + " · " + count
}

@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    ListRow(
        title = playlist.name,
        subtitle = playlistSubtitle(playlist),
        leading = { AlbumArt(playlist.artwork(), Modifier.size(56.dp), cornerRadius = 8.dp) },
        onClick = onClick,
    )
}


/**
 * Album page in the album's own colors: edge-to-edge cover fading into the page, title,
 * artist and details, shuffle plus a large play button, then the tracks.
 */
@Composable
fun AlbumScreen(album: Album, state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    val isCompilation = album.tracks.any { it.artist != album.artist }
    val artist = state.library.artists.firstOrNull { it.name == album.artist }
    ArtworkCollectionPage(
        artwork = album.artwork(),
        kind = stringResource(Res.string.tag_album),
        title = albumTitle(album.title),
        subtitle = artistName(album.artist),
        onSubtitleClick = artist?.let { { state.open(Route.ArtistDetail(it)) } },
        tracks = album.tracks,
        durationMs = album.durationMs,
        context = album.playContext(),
        state = state,
        backLabel = backLabel,
        contentPadding = contentPadding,
    ) { index, track ->
        ListRow(
            title = track.title,
            subtitle = if (isCompilation) artistName(track.artist) else null,
            leading = {
                AppText(
                    text = (track.trackNumber ?: (index + 1)).toString(),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.End,
                )
            },
            trailing = {
                AppText(formatDuration(track.durationMs), AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
                MoreButton { state.openMenu(track) }
            },
            highlighted = track.id == currentTrackId,
            onClick = { state.controller.playTracks(album.tracks, index, album.playContext()) },
        )
    }
}

@Composable
fun PlaylistScreen(playlist: Playlist, state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    ArtworkCollectionPage(
        artwork = playlist.artwork(),
        kind = stringResource(Res.string.tag_playlist),
        title = playlist.name,
        subtitle = playlist.location.takeIf { it.isNotEmpty() }?.joinToString(" › "),
        onSubtitleClick = null,
        tracks = playlist.tracks,
        durationMs = playlist.durationMs,
        context = PlayContext.Playlist(playlist.id),
        state = state,
        backLabel = backLabel,
        contentPadding = contentPadding,
    ) { index, track ->
        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
            state.controller.playTracks(playlist.tracks, index, PlayContext.Playlist(playlist.id))
        }
    }
}

/** Shared layout of album and playlist pages; [row] draws each track. */
@Composable
private fun ArtworkCollectionPage(
    artwork: Artwork,
    kind: String,
    title: String,
    subtitle: String?,
    onSubtitleClick: (() -> Unit)?,
    tracks: List<Track>,
    durationMs: Long,
    context: PlayContext,
    state: AppState,
    backLabel: String,
    contentPadding: PaddingValues,
    row: @Composable (index: Int, track: Track) -> Unit,
) {
    val minutes = (durationMs / 60_000).toInt()
    ArtworkTintedPage(artwork, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { ArtworkHeader(artwork) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Tag(kind)
                    Spacer(Modifier.height(8.dp))
                    AppText(title, AppTheme.typography.title1.copy(fontWeight = FontWeight.Bold), maxLines = 3)
                    // Play and shuffle sit beside the details instead of on a row of their own.
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (subtitle != null) {
                                AppText(
                                    subtitle,
                                    AppTheme.typography.headline,
                                    color = AppTheme.colors.secondaryLabel,
                                    maxLines = 1,
                                    modifier = if (onSubtitleClick != null) {
                                        Modifier.clickable(role = Role.Button, onClick = onSubtitleClick).padding(vertical = 4.dp)
                                    } else Modifier.padding(vertical = 4.dp),
                                )
                            }
                            AppText(
                                text = pluralStringResource(Res.plurals.songs_count, tracks.size, tracks.size) +
                                    " · " + stringResource(Res.string.album_minutes, minutes),
                                style = AppTheme.typography.footnote,
                                color = AppTheme.colors.secondaryLabel,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        IconButton(AppIcons.Shuffle, stringResource(Res.string.action_shuffle), onClick = { state.controller.shuffleTracks(tracks, context) }, filled = true)
                        Spacer(Modifier.width(12.dp))
                        ProminentButton(AppIcons.Play, stringResource(Res.string.action_play), onClick = { state.controller.playTracks(tracks, context = context) })
                    }
                }
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track -> row(index, track) }
        }
        // Back stays reachable over the cover and while scrolling.
        FloatingBackButton(backLabel, onClick = state::goBack, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 8.dp, top = 4.dp))
    }
}

/** Albums always have a title (see MusicLibrary.albums). */
fun Album.playContext(): PlayContext = PlayContext.Album(title.orEmpty(), artist)
