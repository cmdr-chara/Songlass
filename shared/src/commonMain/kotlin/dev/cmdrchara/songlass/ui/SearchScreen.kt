package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SearchField
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.library_albums
import dev.cmdrchara.songlass.shared.resources.library_artists
import dev.cmdrchara.songlass.shared.resources.library_playlists
import dev.cmdrchara.songlass.shared.resources.library_songs
import dev.cmdrchara.songlass.shared.resources.search_hint
import dev.cmdrchara.songlass.shared.resources.search_no_results
import dev.cmdrchara.songlass.shared.resources.search_placeholder
import dev.cmdrchara.songlass.shared.resources.tab_search
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(state: AppState, currentTrackId: String?, contentPadding: PaddingValues) {
    val results = state.searchResults
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { ScreenTitle(stringResource(Res.string.tab_search)) }
        item {
            SearchField(
                query = state.searchQuery,
                onQueryChange = { state.searchQuery = it },
                placeholder = stringResource(Res.string.search_placeholder),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when {
            state.searchQuery.isBlank() -> item { Message(stringResource(Res.string.search_hint)) }
            results.isEmpty -> item { Message(stringResource(Res.string.search_no_results, state.searchQuery.trim())) }
            else -> {
                if (results.playlists.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_playlists)) }
                    items(results.playlists, key = { it.id }) { playlist -> PlaylistRow(playlist) { state.openPlaylist(playlist) } }
                }
                if (results.artists.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_artists)) }
                    items(results.artists) { artist -> ArtistRow(artist) { state.open(Route.ArtistDetail(artist)) } }
                }
                if (results.albums.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_albums)) }
                    items(results.albums) { album ->
                        ListRow(
                            title = albumTitle(album.title),
                            subtitle = artistName(album.artist),
                            leading = { AlbumArt(album.artwork(), Modifier.size(48.dp), cornerRadius = 6.dp) },
                            onClick = { state.open(Route.AlbumDetail(album)) },
                        )
                    }
                }
                if (results.tracks.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_songs)) }
                    itemsIndexed(results.tracks, key = { _, t -> t.id }) { index, track ->
                        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                            state.controller.playTracks(results.tracks, index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    AppText(
        text = text,
        style = AppTheme.typography.subheadline,
        color = AppTheme.colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
