package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.cmdrchara.songlass.core.library.FolderSort
import dev.cmdrchara.songlass.designsystem.SortOptions
import dev.cmdrchara.songlass.shared.resources.library_playlists
import dev.cmdrchara.songlass.shared.resources.artwork_online_action
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.library.ratedTracks
import dev.cmdrchara.songlass.core.library.LibraryFilter
import dev.cmdrchara.songlass.core.library.AlphabeticalIndex
import dev.cmdrchara.songlass.core.settings.AlbumLayout
import dev.cmdrchara.songlass.core.library.PinnedItem
import dev.cmdrchara.songlass.designsystem.CollectionTile
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import dev.cmdrchara.songlass.shared.resources.library_pinned
import dev.cmdrchara.songlass.shared.resources.sort_name
import dev.cmdrchara.songlass.shared.resources.sort_recent
import dev.cmdrchara.songlass.shared.resources.sort_most_played
import dev.cmdrchara.songlass.shared.resources.action_pin
import dev.cmdrchara.songlass.shared.resources.action_unpin
import dev.cmdrchara.songlass.designsystem.SegmentedControl
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.cmdrchara.songlass.core.library.Folder
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.shared.resources.library_scanning
import dev.cmdrchara.songlass.shared.resources.library_scanning_hint
import dev.cmdrchara.songlass.shared.resources.library_filtered_empty
import dev.cmdrchara.songlass.shared.resources.library_filtered_hint
import dev.cmdrchara.songlass.shared.resources.library_folders
import dev.cmdrchara.songlass.shared.resources.tag_folder
import dev.cmdrchara.songlass.shared.resources.folders_count
import dev.cmdrchara.songlass.designsystem.FloatingBackButton
import dev.cmdrchara.songlass.shared.resources.tag_album
import dev.cmdrchara.songlass.shared.resources.tag_artist
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Artist
import dev.cmdrchara.songlass.core.library.Playlist
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.artworkTileFeedback
import dev.cmdrchara.songlass.designsystem.BackPageTitle
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.ArtistAvatar
import dev.cmdrchara.songlass.designsystem.CollectionDetails
import dev.cmdrchara.songlass.designsystem.ArtworkShelf
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.bookmark_open
import dev.cmdrchara.songlass.shared.resources.bookmark_library_hint
import dev.cmdrchara.songlass.shared.resources.albums_count
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
import dev.cmdrchara.songlass.shared.resources.library_albums_empty
import dev.cmdrchara.songlass.shared.resources.library_albums_empty_hint
import dev.cmdrchara.songlass.shared.resources.library_artists
import dev.cmdrchara.songlass.shared.resources.library_favorites
import dev.cmdrchara.songlass.shared.resources.library_songs
import dev.cmdrchara.songlass.shared.resources.songs_count
import dev.cmdrchara.songlass.shared.resources.tab_library
import dev.cmdrchara.songlass.shared.resources.settings_title
import dev.cmdrchara.songlass.shared.resources.ratings_library_title
import dev.cmdrchara.songlass.shared.resources.bookmark_open
import dev.cmdrchara.songlass.shared.resources.bookmark_library_hint
import dev.cmdrchara.songlass.shared.resources.library_metadata
import dev.cmdrchara.songlass.shared.resources.library_metadata_empty
import dev.cmdrchara.songlass.shared.resources.library_rescan
import dev.cmdrchara.songlass.shared.resources.library_layout_list
import dev.cmdrchara.songlass.shared.resources.library_layout_grid
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Library, after Apple Music's: the categories as a short grouped list with counts, then the
 * user's folders as a grid of covers. Folders are browsed like folders (see [FolderScreen]).
 */
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
    val refreshFailure by state.libraryRefreshFailure.collectAsState()
    val onImportPlaylist = rememberPlaylistImportAction(state)
    val favoriteTracks = remember(favoriteIds, library) { state.favorites.tracksIn(library) }
    val pinContexts by state.pins.contexts.collectAsState()
    val pinnedItems = remember(pinContexts, library) { state.pins.resolve(library) }
    val history by state.history.state.collectAsState()
    val preferences by state.settings.state.collectAsState()
    val folderSort = preferences.folderSort
    val sortedFolders = remember(library, folderSort, history) { folderSort.sort(library.folders, history.playCounts) }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
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
        item {
            val saved by state.trackMarkers.state.collectAsState()
            ListRow(stringResource(Res.string.bookmark_open),
                subtitle = stringResource(Res.string.bookmark_library_hint),
                leading = { AppIcon(AppIcons.Bookmark, null, size = 24.dp, tint = AppTheme.colors.accent) },
                trailing = { AppText(saved.bookmarks.size.toString(), AppTheme.typography.body, color = AppTheme.colors.secondaryLabel) },
                onClick = { state.openBookmarks() })
        }
        if (hasAccess && refreshFailure != null) item(key = "library.scan-error") {
            val loading by state.isLibraryLoading.collectAsState()
            LibraryRefreshNotice(refreshFailure, loading, state.requestLibraryRefresh)
        }
        if (!hasAccess || library.tracks.isEmpty()) {
            // Playlist management remains reachable when filters hide every song or access is absent.
            item { SectionTitle(stringResource(Res.string.library_playlists)) }
            item { UserPlaylistRow(state, onImportPlaylist) }
            if (hasAccess && refreshFailure != null) return@LazyColumn
            item {
                    val loading by state.isLibraryLoading.collectAsState()
                    val preferences by state.settings.state.collectAsState()
                    LibraryEmptyState(hasAccess, onRequestAccess, loading,
                        preferences.libraryFilter != dev.cmdrchara.songlass.core.library.LibraryFilter())
                }
            return@LazyColumn
        }
        val pinned = pinnedItems
        if (pinned.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.library_pinned)) }
            item { PinnedRow(pinned, state) }
        }
        item { CategoryGrid(library, favoriteTracks, state) }
        item {
            val storedRatings by state.ratings.values.collectAsState()
            val ratedCount = remember(storedRatings, library) { state.ratings.ratedTracks(library).size }
            ListRow(stringResource(Res.string.ratings_library_title),
                leading = { AppIcon(AppIcons.StarFilled, null, size = 24.dp, tint = AppTheme.colors.accent) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(ratedCount.toString(), AppTheme.typography.body, color = AppTheme.colors.secondaryLabel)
                        Spacer(Modifier.width(12.dp))
                        AppIcon(AppIcons.ChevronRight, null, size = 14.dp, tint = AppTheme.colors.tertiaryLabel)
                    }
                }, onClick = { state.openRatings() })
        }
        item { SectionTitle(stringResource(Res.string.library_metadata)) }
        item { LibraryFacetCategories(state) }
        item { SectionTitle(stringResource(Res.string.library_playlists)) }
        item { UserPlaylistRow(state, onImportPlaylist) }
        if (library.folders.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.library_folders)) }
            item {
                SortOptions(
                    options = FolderSort.entries.map { stringResource(it.label) },
                    selectedIndex = folderSort.ordinal,
                    onSelect = { state.settings.setFolderSort(FolderSort.entries[it]) },
                    modifier = Modifier.padding(horizontal = pageInset, vertical = 4.dp),
                )
            }
            items(sortedFolders.chunked(2)) { pair -> FolderGridRow(pair, state) }
        }
    }
}

/** Artists, Albums, Songs and Favorites as tiles with a fan of their covers. */
@Composable
private fun CategoryGrid(library: MusicLibrary, favorites: List<Track>, state: AppState) {
    if (LocalGlassLook.current.libraryCategoriesAsRows) {
        Column(Modifier.padding(vertical = 8.dp)) {
            LibraryCategory.entries.forEach { category ->
                val count = when (category) {
                    LibraryCategory.Artists -> library.artists.size
                    LibraryCategory.Albums -> library.albums.size
                    LibraryCategory.Songs -> library.tracks.size
                    LibraryCategory.Favorites -> favorites.size
                }
                ListRow(stringResource(category.label),
                    leading = { AppIcon(category.icon, null, size = 24.dp, tint = AppTheme.colors.accent) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(count.toString(), AppTheme.typography.body, color = AppTheme.colors.secondaryLabel)
                            Spacer(Modifier.width(12.dp))
                            AppIcon(AppIcons.ChevronRight, null, size = 14.dp, tint = AppTheme.colors.tertiaryLabel)
                        }
                    }, onClick = { state.open(Route.LibraryPage(category)) })
            }
        }
        return
    }
    @Composable
    fun tile(category: LibraryCategory, count: Int, covers: List<Artwork>, modifier: Modifier) = CollectionTile(
        title = stringResource(category.label),
        subtitle = count.toString(),
        icon = category.icon,
        artworks = covers,
        circular = category == LibraryCategory.Artists,
        onClick = { state.open(Route.LibraryPage(category)) },
        modifier = modifier,
    )
    val artistCovers = remember(library) { library.artists.mapNotNull { it.albums.firstOrNull()?.artwork() }.take(3) }
    val albumCovers = remember(library) { library.albums.take(3).map { it.artwork() } }
    val songCovers = remember(library) { library.tracks.distinctBy { it.album }.takeLast(3).map { it.artwork() } }
    val favoriteCovers = remember(favorites) { favorites.distinctBy { it.album }.take(3).map { it.artwork() } }
    Column(Modifier.padding(horizontal = pageInset, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tile(LibraryCategory.Artists, library.artists.size, artistCovers, Modifier.weight(1f))
            tile(LibraryCategory.Albums, library.albums.size, albumCovers, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tile(LibraryCategory.Songs, library.tracks.size, songCovers, Modifier.weight(1f))
            tile(LibraryCategory.Favorites, favorites.size, favoriteCovers, Modifier.weight(1f))
        }
    }
}

/** Pinned albums, folders and artists, one tap away (as Pins in iOS 26 Apple Music). */
@Composable
private fun PinnedRow(items: List<PinnedItem>, state: AppState) {
    val shelf = items.map { item ->
        when (item) {
            is PinnedItem.AlbumPin -> ShelfItem(item.context.key, albumTitle(item.album.title), null, item.album.artwork(), { state.open(Route.AlbumDetail(item.album)) })
            is PinnedItem.FolderPin -> ShelfItem(item.context.key, item.folder.name, null, item.folder.artwork(), { state.open(Route.FolderDetail(item.folder)) })
            is PinnedItem.ArtistPin -> ShelfItem(
                item.context.key,
                artistName(item.artist.name),
                null,
                item.artist.albums.firstOrNull()?.artwork() ?: item.artist.tracks.first().artwork(),
                { state.open(Route.ArtistDetail(item.artist)) },
                circular = true,
            )
            is PinnedItem.FacetPin -> ShelfItem(item.context.key, item.facet.value, facetKindLabel(item.facet.kind),
                item.facet.tracks.first().artwork(), { state.open(Route.FacetDetail(item.facet.context)) })
        }
    }
    ArtworkShelf(shelf, ShelfStyle.Pins, PinSize)
}

private val PinSize = 96.dp

private val FolderSort.label
    get() = when (this) {
        FolderSort.Name -> Res.string.sort_name
        FolderSort.RecentlyAdded -> Res.string.sort_recent
        FolderSort.MostPlayed -> Res.string.sort_most_played
    }

/** Artists, Albums, Songs or Favorites on their own page. */
@Composable
fun LibraryCategoryScreen(
    category: LibraryCategory,
    state: AppState,
    currentTrackId: String?,
    favoriteIds: List<String>,
    hasAccess: Boolean,
    onRequestAccess: (() -> Unit)?,
    backLabel: String,
    contentPadding: PaddingValues,
) {
    val library = state.library
    val favoriteTracks = remember(favoriteIds, library) { state.favorites.tracksIn(library) }
    val favoriteIdSet = remember(favoriteIds) { favoriteIds.toSet() }
    val preferences by state.settings.state.collectAsState()
    val loading by state.isLibraryLoading.collectAsState()
    val completeScan by state.hasCompleteLibraryScan.collectAsState()
    val refreshFailure by state.libraryRefreshFailure.collectAsState()
    val allTracks by state.allTracks.collectAsState()
    val favoritesFilteredOut = remember(category, favoriteTracks.isEmpty(), favoriteIdSet, allTracks, preferences.libraryFilter) {
        category == LibraryCategory.Favorites && favoriteTracks.isEmpty() &&
            allTracks.any { it.id in favoriteIdSet && !preferences.libraryFilter.includes(it) }
    }
    val albumLayout = preferences.albumLayout
    val categoryEmpty = when (category) {
        LibraryCategory.Artists -> library.artists.isEmpty()
        LibraryCategory.Albums -> library.albums.isEmpty()
        LibraryCategory.Songs -> library.tracks.isEmpty()
        LibraryCategory.Favorites -> favoriteTracks.isEmpty()
    }
    val libraryUnavailable = !hasAccess || library.tracks.isEmpty()
    val showRefreshNotice = hasAccess && refreshFailure != null
    val hasContent = hasAccess && !categoryEmpty
    val showAlbumLayout = hasContent && category == LibraryCategory.Albums
    val showPlayback = hasContent && (category == LibraryCategory.Songs || category == LibraryCategory.Favorites)
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val alphabet = remember(category, library) {
        val titles = when (category) {
            LibraryCategory.Artists -> library.artists.map { it.name }
            LibraryCategory.Albums -> library.albums.map { it.title }
            LibraryCategory.Songs -> library.tracks.map { it.title }
            LibraryCategory.Favorites -> emptyList()
        }
        if (titles.size < ALPHABET_MIN_ITEMS) emptyList()
        else AlphabeticalIndex.build(titles).takeIf { it.size > 1 }.orEmpty()
    }
    val showAlphabet = hasContent && alphabet.isNotEmpty()
    // These are the exact lazy items preceding the first artist, album row or song.
    val firstContentItem = 1 + (if (showRefreshNotice) 1 else 0) +
        (if (showAlbumLayout) 1 else 0) + (if (showAlphabet) 1 else 0) + (if (showPlayback) 1 else 0)
    // A scan notice can appear while the alphabet picker retains its selection callback.
    val currentContentStart by rememberUpdatedState(firstContentItem)
    val currentAlbumGrid by rememberUpdatedState(category == LibraryCategory.Albums && albumLayout == AlbumLayout.Grid)
    val albumRows = remember(library) { library.albums.chunked(2) }
    if (hasContent) {
        when (category) {
            LibraryCategory.Songs -> PrefetchTrackRowArtwork(listState, library.tracks)
            LibraryCategory.Favorites -> PrefetchTrackRowArtwork(listState, favoriteTracks)
            else -> Unit
        }
    }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), state = listState, contentPadding = contentPadding) {
        item { BackPageTitle(stringResource(category.label), backLabel, state::goBack) }
        if (showRefreshNotice) item(key = "category.scan-error") {
            LibraryRefreshNotice(refreshFailure, loading, state.requestLibraryRefresh)
        }
        if (!hasContent) {
            // A failed empty scan has its own retry notice; do not call it an empty library.
            if (libraryUnavailable && showRefreshNotice) return@LazyColumn
            item(key = "category.empty") {
                if (libraryUnavailable) {
                    LibraryEmptyState(
                        hasAccess = hasAccess,
                        onRequestAccess = onRequestAccess,
                        loading = loading || !completeScan,
                        filtered = allTracks.isNotEmpty() && preferences.libraryFilter != LibraryFilter(),
                        onOpenSettings = { state.open(Route.LibrarySettings) },
                        onRescan = state.requestLibraryRefresh,
                    )
                } else if (favoritesFilteredOut) {
                    LibraryEmptyState(
                        hasAccess = hasAccess,
                        onRequestAccess = onRequestAccess,
                        filtered = true,
                        onOpenSettings = { state.open(Route.LibrarySettings) },
                    )
                } else if (category == LibraryCategory.Albums) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppText(stringResource(Res.string.library_albums_empty), AppTheme.typography.title3)
                        AppText(
                            stringResource(Res.string.library_albums_empty_hint, MusicLibrary.MIN_ALBUM_TRACKS),
                            AppTheme.typography.subheadline,
                            color = AppTheme.colors.secondaryLabel,
                        )
                        ActionButton(stringResource(Res.string.library_songs),
                            onClick = { state.open(Route.LibraryPage(LibraryCategory.Songs)) },
                            modifier = Modifier.fillMaxWidth(), icon = AppIcons.MusicNote)
                        if (preferences.libraryFilter != LibraryFilter()) {
                            ActionButton(stringResource(Res.string.settings_title),
                                onClick = { state.open(Route.LibrarySettings) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    AppText(
                        stringResource(if (category == LibraryCategory.Favorites) Res.string.favorites_empty else Res.string.library_metadata_empty),
                        AppTheme.typography.subheadline,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = pageInset, vertical = 12.dp),
                    )
                }
            }
            return@LazyColumn
        }
        if (showAlbumLayout) item {
            SegmentedControl(listOf(stringResource(Res.string.library_layout_grid), stringResource(Res.string.library_layout_list)),
                if (albumLayout == AlbumLayout.Grid) 0 else 1,
                { state.settings.setAlbumLayout(if (it == 0) AlbumLayout.Grid else AlbumLayout.List) },
                Modifier.padding(horizontal = pageInset, vertical = 8.dp))
        }
        if (showAlphabet) item {
            LibraryAlphabetButton(state, alphabet, listState, scrollScope) { index ->
                currentContentStart + if (currentAlbumGrid) index / 2 else index
            }
        }
        if (showPlayback) item {
            PlayShuffleRow(
                if (category == LibraryCategory.Favorites) favoriteTracks else library.tracks,
                if (category == LibraryCategory.Favorites) PlayContext.Favorites else null,
                state,
            )
        }
        when (category) {
            LibraryCategory.Artists -> items(library.artists) { artist ->
                ArtistRow(artist) { state.open(Route.ArtistDetail(artist)) }
            }
            LibraryCategory.Albums -> if (albumLayout == AlbumLayout.Grid) {
                items(albumRows) { pair -> AlbumGridRow(pair) { state.open(Route.AlbumDetail(it)) } }
            } else {
                items(library.albums) { album ->
                    ListRow(albumTitle(album.title), subtitle = artistName(album.artist),
                        leading = { AlbumArt(album.artwork(), Modifier.size(48.dp), cornerRadius = 8.dp) },
                        onClick = { state.open(Route.AlbumDetail(album)) })
                }
            }
            LibraryCategory.Songs -> {
                itemsIndexed(library.tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                        state.tapTrack(library.tracks, index)
                    }
                }
            }
            LibraryCategory.Favorites -> {
                itemsIndexed(favoriteTracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                        state.tapTrack(favoriteTracks, index, PlayContext.Favorites)
                    }
                }
            }
        }
    }
}

/** Play and Shuffle share a row only while both labels fit the current typography. */
@Composable
internal fun PlayShuffleRow(tracks: List<Track>, context: PlayContext?, state: AppState) {
    val playLabel = stringResource(Res.string.action_play)
    val shuffleLabel = stringResource(Res.string.action_shuffle)
    val measurer = rememberTextMeasurer()
    val typography = AppTheme.typography
    val density = LocalDensity.current
    val widestLabelPx = listOf(playLabel, shuffleLabel).maxOf { label ->
        maxOf(
            measurer.measure(label, typography.headline, softWrap = false, maxLines = 1).size.width,
            measurer.measure(label, typography.body.copy(fontWeight = FontWeight.Medium), softWrap = false, maxLines = 1).size.width,
        )
    }
    // Match the largest current icon, gap and padding footprint, including pixel rounding.
    val buttonOverheadPx = with(density) {
        maxOf(
            2 * 20.dp.roundToPx() + 18.dp.roundToPx() + 8.dp.roundToPx(),
            2 * 16.dp.roundToPx() + 20.dp.roundToPx() + 8.dp.roundToPx(),
        )
    }
    val gapPx = with(density) { 12.dp.roundToPx() }
    val onPlay = { state.controller.playTracks(tracks, context = context) }
    val onShuffle = { state.controller.shuffleTracks(tracks, context) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 8.dp)) {
        if (constraints.maxWidth - gapPx >= 2 * (widestLabelPx + buttonOverheadPx)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(playLabel, onClick = onPlay, modifier = Modifier.weight(1f), icon = AppIcons.Play)
                ActionButton(shuffleLabel, onClick = onShuffle, modifier = Modifier.weight(1f), icon = AppIcons.Shuffle)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(playLabel, onClick = onPlay, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Play)
                ActionButton(shuffleLabel, onClick = onShuffle, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Shuffle)
            }
        }
    }
}

/** Two folders per row, as cover tiles. */
@Composable
internal fun FolderGridRow(folders: List<Folder>, state: AppState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        folders.forEach { folder -> FolderTile(folder, Modifier.weight(1f)) { state.open(Route.FolderDetail(folder)) } }
        if (folders.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FolderTile(folder: Folder, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.prefetchArtworkOnPress(folder.artwork())
            .artworkTileFeedback(interaction, RoundedCornerShape(10.dp))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        FolderCover(folder, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        AppText(folder.name, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium), maxLines = 2)
        AppText(folderSummary(folder), AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
    }
}

/** "3 folders · 120 songs", or just the songs when there are no sub-folders. */
@Composable
private fun folderSummary(folder: Folder): String {
    val songs = pluralStringResource(Res.plurals.songs_count, folder.allTracks.size, folder.allTracks.size)
    if (folder.children.isEmpty()) return songs
    return pluralStringResource(Res.plurals.folders_count, folder.children.size, folder.children.size) + " · " + songs
}

/** A 2×2 mosaic when the folder spans at least four albums, otherwise its first cover. */
@Composable
private fun FolderCover(folder: Folder, modifier: Modifier) {
    val covers = remember(folder) { folder.coverTracks(4) }
    if (covers.size < 4) {
        AlbumArt(folder.artwork(), modifier, cornerRadius = 10.dp)
        return
    }
    Column(modifier.clip(RoundedCornerShape(10.dp))) {
        covers.chunked(2).forEach { pair ->
            Row(Modifier.weight(1f)) {
                pair.forEach { AlbumArt(it.artwork(), Modifier.weight(1f).fillMaxHeight(), cornerRadius = 0.dp) }
            }
        }
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    val art = artist.albums.firstOrNull()?.artwork() ?: artist.tracks.firstOrNull()?.artwork()
    ListRow(
        title = artistName(artist.name),
        modifier = Modifier.prefetchArtworkOnPress(art),
        leading = { if (art != null) ArtistAvatar(art, Modifier.size(48.dp)) },
        onClick = onClick,
    )
}


/** An artist in their cover's colours: header, Play/Shuffle, their albums as a shelf, then every song. */
@Composable
fun ArtistScreen(artist: Artist, state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    val context = PlayContext.Artist(artist.name)
    val albums = artist.albums
    val albumItems = albums.map { album ->
        ShelfItem(album.playContext().key, albumTitle(album.title), stringResource(Res.string.tag_album), album.artwork(), { state.open(Route.AlbumDetail(album)) })
    }
    ArtworkCollectionPage(
        artwork = albums.firstOrNull()?.artwork() ?: artist.tracks.first().artwork(),
        kind = stringResource(Res.string.tag_artist),
        title = artistName(artist.name),
        subtitle = if (albums.isEmpty()) null else pluralStringResource(Res.plurals.albums_count, albums.size, albums.size),
        onSubtitleClick = null,
        tracks = artist.tracks,
        durationMs = artist.tracks.sumOf { it.durationMs },
        context = context,
        state = state,
        backLabel = backLabel,
        contentPadding = contentPadding,
        extra = if (albums.isEmpty()) null else {
            {
                item { SectionTitle(stringResource(Res.string.library_albums)) }
                item { ArtworkShelf(albumItems, ShelfStyle.Carousel, 150.dp) }
                item { SectionTitle(stringResource(Res.string.library_songs)) }
            }
        },
    ) { index, track ->
        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
            state.tapTrack(artist.tracks, index, context)
        }
    }
}

/** Shown before the user grants access to their music, or when no music was found. */
@Composable
internal fun LibraryEmptyState(
    hasAccess: Boolean,
    onRequestAccess: (() -> Unit)?,
    loading: Boolean = false,
    filtered: Boolean = false,
    onOpenSettings: (() -> Unit)? = null,
    onRescan: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppText(
            text = stringResource(when {
                !hasAccess -> Res.string.library_access_title
                loading -> Res.string.library_scanning
                filtered -> Res.string.library_filtered_empty
                else -> Res.string.library_empty
            }),
            style = AppTheme.typography.title3,
            textAlign = TextAlign.Center,
        )
        if (!hasAccess || !loading) {
            Spacer(Modifier.height(8.dp))
            AppText(
                text = stringResource(when {
                    !hasAccess -> Res.string.library_access_message
                    filtered -> Res.string.library_filtered_hint
                    else -> Res.string.library_empty_hint
                }),
                style = AppTheme.typography.subheadline,
                color = AppTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
        if (!hasAccess && onRequestAccess != null) {
            Spacer(Modifier.height(20.dp))
            ActionButton(stringResource(Res.string.library_access_button), onClick = onRequestAccess)
        } else if (hasAccess && !loading) {
            if (filtered && onOpenSettings != null) {
                Spacer(Modifier.height(20.dp))
                ActionButton(stringResource(Res.string.settings_title), onClick = onOpenSettings)
            } else if (onRescan != null) {
                Spacer(Modifier.height(20.dp))
                ActionButton(stringResource(Res.string.library_rescan), onClick = onRescan)
            }
        }
    }
}

private val LibraryCategory.label
    get() = when (this) {
        LibraryCategory.Albums -> Res.string.library_albums
        LibraryCategory.Artists -> Res.string.library_artists
        LibraryCategory.Songs -> Res.string.library_songs
        LibraryCategory.Favorites -> Res.string.library_favorites
    }

private val LibraryCategory.icon
    get() = when (this) {
        LibraryCategory.Artists -> AppIcons.Person
        LibraryCategory.Albums -> AppIcons.Album
        LibraryCategory.Songs -> AppIcons.MusicNote
        LibraryCategory.Favorites -> AppIcons.Star
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
        modifier = Modifier.prefetchArtworkOnPress(playlist.artwork()),
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
    // A cover the user downloaded and applied replaces the album's own; the entry point next to the
    // pin opens the picker (and is where that cover is removed again).
    val cover = rememberAlbumCoverView(album)
    val onlineArtworkAvailable = LocalOnlineArtwork.current != null
    ArtworkCollectionPage(
        artwork = cover.artwork,
        kind = stringResource(Res.string.tag_album),
        title = albumTitle(album.title),
        subtitle = artistName(album.artist),
        onSubtitleClick = artist?.let { { state.open(Route.ArtistDetail(it)) } },
        tracks = album.tracks,
        durationMs = album.durationMs,
        releaseYear = album.tracks.mapNotNull { it.releaseDate?.year }.distinct().singleOrNull(),
        context = album.playContext(),
        state = state,
        backLabel = backLabel,
        contentPadding = contentPadding,
        headerAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PinButton(album.playContext(), state)
                if (onlineArtworkAvailable) {
                    IconButton(
                        AppIcons.Search,
                        stringResource(Res.string.artwork_online_action),
                        onClick = { state.open(Route.OnlineArtwork(album)) },
                        selected = cover.downloaded != null,
                        filled = true,
                    )
                }
            }
        },
        extra = if (cover.downloaded == null) null else ({ downloadedCoverNotice() }),
        rowArtwork = false,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(formatDuration(track.durationMs), AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
                    MoreButton { state.openMenu(track) }
                }
            },
            highlighted = track.id == currentTrackId,
            onClick = { state.tapTrack(album.tracks, index, album.playContext()) },
        )
    }
}

/**
 * A folder in its cover's colors: header, play/shuffle for everything inside, then its
 * sub-folders as cover tiles and its own songs.
 */
@Composable
fun FolderScreen(folder: Folder, state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    val context = PlayContext.Playlist(folder.id)
    ArtworkCollectionPage(
        artwork = folder.artwork(),
        kind = stringResource(Res.string.tag_folder),
        title = folder.name,
        subtitle = if (folder.children.isEmpty()) null else pluralStringResource(Res.plurals.folders_count, folder.children.size, folder.children.size),
        onSubtitleClick = null,
        tracks = folder.allTracks,
        rows = folder.tracks,
        durationMs = folder.durationMs,
        context = context,
        state = state,
        backLabel = backLabel,
        contentPadding = contentPadding,
        headerAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResumeOverrideButton(state, context)
                PinButton(context, state)
            }
        },
        extra = if (folder.children.isEmpty()) null else {
            {
                item { SectionTitle(stringResource(Res.string.library_folders)) }
                items(folder.children.chunked(2)) { pair -> FolderGridRow(pair, state) }
                if (folder.tracks.isNotEmpty()) item { SectionTitle(stringResource(Res.string.library_songs)) }
            }
        },
    ) { index, track ->
        // The folder's own songs come first in allTracks, so the index carries over.
        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
            state.tapTrack(folder.allTracks, index, context)
        }
    }
}

/** Shared layout of album and playlist pages; [row] draws each track. */
@Composable
internal fun ArtworkCollectionPage(
    artwork: Artwork,
    kind: String,
    title: String,
    subtitle: String?,
    onSubtitleClick: (() -> Unit)?,
    tracks: List<Track>,
    durationMs: Long,
    releaseYear: Int? = null,
    context: PlayContext,
    state: AppState,
    backLabel: String,
    contentPadding: PaddingValues,
    /** Songs listed as rows; [tracks] (counted and played by the header) by default. */
    rows: List<Track> = tracks,
    /** Content between the header and the rows (e.g. a folder's sub-folders). */
    extra: (LazyListScope.() -> Unit)? = null,
    /** Small action beside shuffle; pinning by default. */
    headerAction: @Composable () -> Unit = { PinButton(context, state) },
    /** Whether [row] shows each song's cover, so it is loaded ahead while scrolling. */
    rowArtwork: Boolean = true,
    row: @Composable (index: Int, track: Track) -> Unit,
) {
    val minutes = (durationMs / 60_000).toInt()
    val listState = rememberLazyListState()
    if (rowArtwork) PrefetchTrackRowArtwork(listState, rows)
    ArtworkTintedPage(artwork, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            item { ArtworkHeader(artwork) }
            item {
                CollectionDetails(
                    kind = kind,
                    title = title,
                    subtitle = subtitle,
                    onSubtitleClick = onSubtitleClick,
                    meta = pluralStringResource(Res.plurals.songs_count, tracks.size, tracks.size) +
                        " · " + stringResource(Res.string.album_minutes, minutes) + (releaseYear?.let { " · $it" } ?: ""),
                    playLabel = stringResource(Res.string.action_play),
                    shuffleLabel = stringResource(Res.string.action_shuffle),
                    onPlay = { state.controller.playTracks(tracks, context = context) },
                    onShuffle = { state.controller.shuffleTracks(tracks, context) },
                    action = headerAction,
                )
            }
            extra?.invoke(this)
            itemsIndexed(rows, key = { _, t -> t.id }) { index, track -> row(index, track) }
        }
        // Back stays reachable over the cover and while scrolling.
        FloatingBackButton(backLabel, onClick = state::goBack, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 8.dp, top = 4.dp))
    }
}

/** Pins or unpins [context] at the top of the Library. */
@Composable
internal fun PinButton(context: PlayContext, state: AppState) {
    val pins by state.pins.contexts.collectAsState()
    val pinned = context in pins
    IconButton(
        AppIcons.Pin,
        stringResource(if (pinned) Res.string.action_unpin else Res.string.action_pin),
        onClick = { state.pins.toggle(context) },
        selected = pinned,
        filled = true,
    )
}

/** Albums always have a title (see MusicLibrary.albums). */
fun Album.playContext(): PlayContext = PlayContext.Album(title.orEmpty(), artist)
