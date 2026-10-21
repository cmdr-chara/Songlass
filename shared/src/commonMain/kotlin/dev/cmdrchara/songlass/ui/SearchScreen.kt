package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.cmdrchara.songlass.core.settings.InterfaceStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.search.SearchItem
import dev.cmdrchara.songlass.core.search.SearchSuggestions
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.ArtistAvatar
import dev.cmdrchara.songlass.designsystem.ArtworkShelf
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
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
import dev.cmdrchara.songlass.shared.resources.search_no_results
import dev.cmdrchara.songlass.shared.resources.search_placeholder
import dev.cmdrchara.songlass.shared.resources.tab_search
import dev.cmdrchara.songlass.shared.resources.search_recent
import dev.cmdrchara.songlass.shared.resources.search_clear_recent
import dev.cmdrchara.songlass.shared.resources.search_remove_recent
import dev.cmdrchara.songlass.shared.resources.search_library_artists
import dev.cmdrchara.songlass.shared.resources.search_library_albums
import dev.cmdrchara.songlass.shared.resources.search_library_songs
import dev.cmdrchara.songlass.shared.resources.tag_song
import dev.cmdrchara.songlass.shared.resources.tag_album
import dev.cmdrchara.songlass.shared.resources.tag_artist
import dev.cmdrchara.songlass.shared.resources.tag_playlist
import dev.cmdrchara.songlass.shared.resources.library_metadata
import org.jetbrains.compose.resources.stringResource

/** What the search results show: everything, or one kind of result. */
enum class SearchFilter { All, Songs, Albums, Artists, Playlists, Metadata }

@Composable
fun SearchScreen(
    state: AppState,
    currentTrackId: String?,
    hasAccess: Boolean,
    onRequestAccess: (() -> Unit)?,
    contentPadding: PaddingValues,
) {
    val loading by state.isLibraryLoading.collectAsState()
    val refreshFailure by state.libraryRefreshFailure.collectAsState()
    val preferences by state.settings.state.collectAsState()
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchRootActive = state.tab == Tab.Search && state.currentRoute == null &&
        !state.isNowPlayingOpen && !state.hasOverlay
    val results = state.searchResults
    val targets by state.searchHistory.targets.collectAsState()
    val library = state.library
    val recent = remember(targets, library) { state.searchHistory.resolve(library) }
    val suggestions = remember(library) { SearchSuggestions.from(library) }
    fun open(item: SearchItem) {
        focusManager.clearFocus()
        keyboard?.hide()
        state.searchHistory.record(item.target)
        when (item) {
            is SearchItem.Song -> state.controller.playTracks(listOf(item.track), 0)
            is SearchItem.AlbumItem -> state.open(Route.AlbumDetail(item.album))
            is SearchItem.ArtistItem -> state.open(Route.ArtistDetail(item.artist))
            is SearchItem.FolderItem -> state.openPlaylist(item.playlist)
            is SearchItem.FacetItem -> state.open(Route.FacetDetail(item.facet.context))
        }
    }
    // Only kinds that have results get a chip; a filter whose kind vanished falls back to All.
    val available = buildList {
        add(SearchFilter.All)
        if (results.tracks.isNotEmpty()) add(SearchFilter.Songs)
        if (results.albums.isNotEmpty()) add(SearchFilter.Albums)
        if (results.artists.isNotEmpty()) add(SearchFilter.Artists)
        if (results.playlists.isNotEmpty()) add(SearchFilter.Playlists)
        if (results.facets.isNotEmpty()) add(SearchFilter.Metadata)
    }
    val filter = state.searchFilter.takeIf { it in available } ?: SearchFilter.All
    fun shows(kind: SearchFilter) = filter == SearchFilter.All || filter == kind
    // "All" is a preview: a few of each kind; a chip shows a kind in full.
    fun <T> List<T>.preview(count: Int) = if (filter == SearchFilter.All) take(count) else this
    val listState = rememberLazyListState()
    val focusRequest = state.searchFocusRequest
    val reduceMotion = AppTheme.accessibility.reduceMotion
    // This effect must outlive the lazy search-field item: a repeated Search tap must also
    // work after a long list has scrolled the field out of composition. Consume the explicit
    // request once so returning from a result never unexpectedly opens the keyboard.
    LaunchedEffect(searchRootActive, focusRequest, preferences.interfaceStyle) {
        if (!searchRootActive || !state.consumeSearchFocusRequest(focusRequest)) return@LaunchedEffect
        // Material and Miuix enter their discovery page without opening the keyboard.
        if (preferences.interfaceStyle != InterfaceStyle.LiquidGlass) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > SEARCH_FIELD_INDEX }
        if (reduceMotion) listState.scrollToItem(SEARCH_FIELD_INDEX)
        else listState.animateScrollToItem(SEARCH_FIELD_INDEX)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == SEARCH_FIELD_INDEX } }.first { it }
        withFrameNanos { }
        if (state.tab == Tab.Search && state.currentRoute == null && !state.isNowPlayingOpen &&
            !state.hasOverlay && state.searchFocusRequest == focusRequest &&
            state.settings.state.value.interfaceStyle == InterfaceStyle.LiquidGlass) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }
    PrefetchTrackRowArtwork(listState, if (shows(SearchFilter.Songs)) results.tracks.preview(8) else emptyList())
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), state = listState, contentPadding = contentPadding) {
        item { ScreenTitle(stringResource(Res.string.tab_search)) }
        item {
            SearchField(
                query = state.searchQuery,
                onQueryChange = { state.searchQuery = it },
                placeholder = stringResource(Res.string.search_placeholder),
                clearLabel = stringResource(Res.string.search_clear),
                modifier = Modifier.padding(horizontal = pageInset, vertical = 8.dp).focusRequester(searchFocus),
            )
        }
        if (hasAccess && refreshFailure != null) item(key = "search.scan-error") {
            LibraryRefreshNotice(refreshFailure, loading, state.requestLibraryRefresh)
        }
        when {
            hasAccess && refreshFailure != null && library.tracks.isEmpty() -> Unit
            !hasAccess || library.tracks.isEmpty() -> item {
                LibraryEmptyState(hasAccess, onRequestAccess, loading,
                    preferences.libraryFilter != dev.cmdrchara.songlass.core.library.LibraryFilter())
            }
            state.searchQuery.isBlank() -> {
                if (recent.isNotEmpty()) {
                    item {
                        SectionTitle(stringResource(Res.string.search_recent)) {
                            ActionButton(stringResource(Res.string.search_clear_recent), state.searchHistory::clear)
                        }
                    }
                    items(recent) { item ->
                        RecentSearchRow(item, currentTrackId, onRemove = { state.searchHistory.remove(item.target) }) { open(item) }
                    }
                } else {
                    if (suggestions.artists.isNotEmpty()) {
                        item { SectionTitle(stringResource(Res.string.search_library_artists)) }
                        item {
                            ArtworkShelf(
                                suggestions.artists.map { artist -> ShelfItem(
                                    key = artist.name.orEmpty(), title = artistName(artist.name), subtitle = null,
                                    artwork = artist.tracks.first().artwork(), circular = true,
                                    onClick = { open(SearchItem.ArtistItem(artist)) },
                                ) }, ShelfStyle.Carousel, 112.dp,
                            )
                        }
                    }
                    if (suggestions.albums.isNotEmpty()) {
                        item { SectionTitle(stringResource(Res.string.search_library_albums)) }
                        item {
                            ArtworkShelf(
                                suggestions.albums.mapIndexed { index, album -> ShelfItem(
                                    key = index, title = albumTitle(album.title), subtitle = artistName(album.artist),
                                    artwork = album.artwork(), onClick = { open(SearchItem.AlbumItem(album)) },
                                ) }, ShelfStyle.Carousel, 144.dp,
                            )
                        }
                    }
                    if (suggestions.songs.isNotEmpty()) {
                        item { SectionTitle(stringResource(Res.string.search_library_songs)) }
                        items(suggestions.songs, key = { "suggested:${it.id}" }) { track ->
                            TrackRow(track, track.id == currentTrackId, onMore = { state.openMenu(track) }) { open(SearchItem.Song(track)) }
                        }
                    }
                }
            }
            results.isEmpty -> item { Message(stringResource(Res.string.search_no_results, state.searchQuery.trim())) }
            else -> {
                if (available.size > 1) {
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
                    items(results.artists.preview(3)) { artist -> ArtistRow(artist) { open(SearchItem.ArtistItem(artist)) } }
                }
                if (shows(SearchFilter.Albums) && results.albums.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_albums)) }
                    items(results.albums.preview(3)) { album ->
                        ListRow(
                            title = albumTitle(album.title),
                            modifier = Modifier.prefetchArtworkOnPress(album.artwork()),
                            subtitle = artistName(album.artist),
                            leading = { AlbumArt(album.artwork(), Modifier.size(48.dp), cornerRadius = 6.dp) },
                            onClick = { open(SearchItem.AlbumItem(album)) },
                        )
                    }
                }
                if (shows(SearchFilter.Playlists) && results.playlists.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_playlists)) }
                    items(results.playlists.preview(3), key = { it.id }) { playlist -> PlaylistRow(playlist) { open(SearchItem.FolderItem(playlist)) } }
                }
                if (shows(SearchFilter.Metadata) && results.facets.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_metadata)) }
                    items(results.facets.preview(3), key = { it.context.key }) { facet ->
                        LibraryFacetRow(facet) { open(SearchItem.FacetItem(facet)) }
                    }
                }
                if (shows(SearchFilter.Songs) && results.tracks.isNotEmpty()) {
                    item { SectionTitle(stringResource(Res.string.library_songs)) }
                    val tracks = results.tracks.preview(8)
                    itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                            state.searchHistory.record(SearchItem.Song(track).target)
                            state.controller.playTracks(results.tracks, index)
                        }
                    }
                }
            }
        }
    }
}

private const val SEARCH_FIELD_INDEX = 1

private val SearchFilter.label
    get() = when (this) {
        SearchFilter.All -> Res.string.search_all
        SearchFilter.Songs -> Res.string.library_songs
        SearchFilter.Albums -> Res.string.library_albums
        SearchFilter.Artists -> Res.string.library_artists
        SearchFilter.Playlists -> Res.string.library_playlists
        SearchFilter.Metadata -> Res.string.library_metadata
    }

@Composable
private fun Message(text: String) {
    AppText(
        text = text,
        style = AppTheme.typography.subheadline,
        color = AppTheme.colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = pageInset, vertical = 12.dp),
    )
}

@Composable
private fun RecentSearchRow(item: SearchItem, currentTrackId: String?, onRemove: () -> Unit, onOpen: () -> Unit) {
    val title = when (item) {
        is SearchItem.Song -> item.track.title
        is SearchItem.AlbumItem -> albumTitle(item.album.title)
        is SearchItem.ArtistItem -> artistName(item.artist.name)
        is SearchItem.FolderItem -> item.playlist.name
        is SearchItem.FacetItem -> item.facet.value
    }
    val subtitle = when (item) {
        is SearchItem.Song -> "${stringResource(Res.string.tag_song)} · ${artistName(item.track.artist)}"
        is SearchItem.AlbumItem -> "${stringResource(Res.string.tag_album)} · ${artistName(item.album.artist)}"
        is SearchItem.ArtistItem -> stringResource(Res.string.tag_artist)
        is SearchItem.FolderItem -> stringResource(Res.string.tag_playlist)
        is SearchItem.FacetItem -> facetKindLabel(item.facet.kind)
    }
    val artwork = when (item) {
        is SearchItem.Song -> rememberTrackArtwork(item.track)
        is SearchItem.AlbumItem -> item.album.artwork()
        is SearchItem.ArtistItem -> item.artist.tracks.first().artwork()
        is SearchItem.FolderItem -> item.playlist.artwork()
        is SearchItem.FacetItem -> item.facet.tracks.first().artwork()
    }
    ListRow(
        title = title, subtitle = subtitle,
        leading = {
            if (item is SearchItem.ArtistItem) ArtistAvatar(artwork, Modifier.size(48.dp))
            else AlbumArt(artwork, Modifier.size(48.dp), cornerRadius = 6.dp)
        },
        trailing = { IconButton(AppIcons.Close, stringResource(Res.string.search_remove_recent, title), onRemove) },
        highlighted = item is SearchItem.Song && item.track.id == currentTrackId,
        onClick = onOpen,
    )
}
