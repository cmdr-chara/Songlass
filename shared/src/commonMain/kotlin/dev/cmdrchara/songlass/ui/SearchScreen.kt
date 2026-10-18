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
import dev.cmdrchara.songlass.designsystem.FilterChips
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SearchField
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.library_albums
import dev.cmdrchara.songlass.shared.resources.library_artists
import dev.cmdrchara.songlass.shared.resources.library_playlists
import dev.cmdrchara.songlass.shared.resources.library_songs
import dev.cmdrchara.songlass.shared.resources.search_all
import dev.cmdrchara.songlass.shared.resources.search_clear
import dev.cmdrchara.songlass.shared.resources.search_hint
import dev.cmdrchara.songlass.shared.resources.search_no_results
import dev.cmdrchara.songlass.shared.resources.search_placeholder
import dev.cmdrchara.songlass.shared.resources.tab_search
import org.jetbrains.compose.resources.stringResource

/** What the search results show: everything, or one kind of result. */
enum class SearchFilter { All, Songs, Albums, Artists, Playlists }

@Composable
fun SearchScreen(state: AppState, currentTrackId: String?, contentPadding: PaddingValues) {
    val results = state.searchResults
    // Only kinds that have results get a chip; a filter whose kind vanished falls back to All.
    val available = buildList {
        add(SearchFilter.All)
        if (results.tracks.isNotEmpty()) add(SearchFilter.Songs)
        if (results.albums.isNotEmpty()) add(SearchFilter.Albums)
        if (results.artists.isNotEmpty()) add(SearchFilter.Artists)
        if (results.playlists.isNotEmpty()) add(SearchFilter.Playlists)
    }
    val filter = state.searchFilter.takeIf { it in available } ?: SearchFilter.All
    fun shows(kind: SearchFilter) = filter == SearchFilter.All || filter == kind
    // "All" is a preview: a few of each kind; a chip shows a kind in full.
    fun <T> List<T>.preview(count: Int) = if (filter == SearchFilter.All) take(count) else this
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { ScreenTitle(stringResource(Res.string.tab_search)) }
        item {
            SearchField(
                query = state.searchQuery,
                onQueryChange = { state.searchQuery = it },
                placeholder = stringResource(Res.string.search_placeholder),
                clearLabel = stringResource(Res.string.search_clear),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when {
            state.searchQuery.isBlank() -> item { Message(stringResource(Res.string.search_hint)) }
            results.isEmpty -> item { Message(stringResource(Res.string.search_no_results, state.searchQuery.trim())) }
            else -> {
                if (available.size > 2) {
                    item {
                        FilterChips(
                            options = available.map { stringResource(it.label) },
                            selectedIndex = available.indexOf(filter),
                            onSelect = { state.searchFilter = available[it] },
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                if (shows(SearchFilter.Artists) && results.artists.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_artists)) }
                    items(results.artists.preview(3)) { artist -> ArtistRow(artist) { state.open(Route.ArtistDetail(artist)) } }
                }
                if (shows(SearchFilter.Albums) && results.albums.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_albums)) }
                    items(results.albums.preview(3)) { album ->
                        ListRow(
                            title = albumTitle(album.title),
                            subtitle = artistName(album.artist),
                            leading = { AlbumArt(album.artwork(), Modifier.size(48.dp), cornerRadius = 6.dp) },
                            onClick = { state.open(Route.AlbumDetail(album)) },
                        )
                    }
                }
                if (shows(SearchFilter.Playlists) && results.playlists.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_playlists)) }
                    items(results.playlists.preview(3), key = { it.id }) { playlist -> PlaylistRow(playlist) { state.openPlaylist(playlist) } }
                }
                if (shows(SearchFilter.Songs) && results.tracks.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_songs)) }
                    val tracks = results.tracks.preview(8)
                    itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                            state.controller.playTracks(results.tracks, index)
                        }
                    }
                }
            }
        }
    }
}

private val SearchFilter.label
    get() = when (this) {
        SearchFilter.All -> Res.string.search_all
        SearchFilter.Songs -> Res.string.library_songs
        SearchFilter.Albums -> Res.string.library_albums
        SearchFilter.Artists -> Res.string.library_artists
        SearchFilter.Playlists -> Res.string.library_playlists
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
