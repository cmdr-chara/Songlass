package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.home.ChangeOfPaceReason
import dev.cmdrchara.songlass.core.home.HomeFeed
import dev.cmdrchara.songlass.core.home.HomeItem
import dev.cmdrchara.songlass.core.home.HomeSection
import dev.cmdrchara.songlass.core.home.OnThisDay
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.ArtworkShelf
import dev.cmdrchara.songlass.designsystem.HomeHighlight
import dev.cmdrchara.songlass.designsystem.HomeHighlights
import dev.cmdrchara.songlass.designsystem.HomePage
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.ShortcutTile
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.home_change_of_pace
import dev.cmdrchara.songlass.shared.resources.home_for_you
import dev.cmdrchara.songlass.shared.resources.home_label_collection
import dev.cmdrchara.songlass.shared.resources.home_label_library
import dev.cmdrchara.songlass.shared.resources.home_listening_memories
import dev.cmdrchara.songlass.shared.resources.home_most_played
import dev.cmdrchara.songlass.shared.resources.home_on_this_day
import dev.cmdrchara.songlass.shared.resources.home_pace_artist
import dev.cmdrchara.songlass.shared.resources.home_pace_lately
import dev.cmdrchara.songlass.shared.resources.home_pace_never
import dev.cmdrchara.songlass.shared.resources.home_play_all
import dev.cmdrchara.songlass.shared.resources.home_recently_added
import dev.cmdrchara.songlass.shared.resources.home_rediscover_months
import dev.cmdrchara.songlass.shared.resources.home_rediscover_never
import dev.cmdrchara.songlass.shared.resources.home_rediscover_weeks
import dev.cmdrchara.songlass.shared.resources.home_release_anniversaries
import dev.cmdrchara.songlass.shared.resources.home_shuffle_all
import dev.cmdrchara.songlass.shared.resources.home_your_playlists
import dev.cmdrchara.songlass.shared.resources.homecfg_no_content
import dev.cmdrchara.songlass.shared.resources.homecfg_all_hidden
import dev.cmdrchara.songlass.shared.resources.homecfg_kind_smart_playlist
import dev.cmdrchara.songlass.shared.resources.homecfg_customize
import dev.cmdrchara.songlass.shared.resources.library_favorites
import dev.cmdrchara.songlass.shared.resources.settings_title
import dev.cmdrchara.songlass.shared.resources.songs_count
import dev.cmdrchara.songlass.shared.resources.stats_home_hint
import dev.cmdrchara.songlass.shared.resources.stats_title
import dev.cmdrchara.songlass.shared.resources.tab_home
import dev.cmdrchara.songlass.shared.resources.tag_album
import dev.cmdrchara.songlass.shared.resources.tag_artist
import dev.cmdrchara.songlass.shared.resources.tag_playlist
import dev.cmdrchara.songlass.shared.resources.tag_song
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Home, shaped after what the best players share (Spotify's shortcuts, Apple Music's Top Picks,
 * Plexamp's hubs): a grid to jump back in, featured cards that say why they are there, recent
 * additions, paged song shelves with a "play all" and the playlists. Content comes from core's
 * [HomeFeed]; sections without content are not shown. Which sections appear, in what order and
 * which shortcuts lead the grid is the user's choice ([HomeCustomization]); the default is the
 * original layout.
 */
@Composable
fun HomeScreen(
    state: AppState,
    currentTrackId: String?,
    favoriteIds: List<String>,
    hasAccess: Boolean,
    onRequestAccess: (() -> Unit)?,
    contentPadding: PaddingValues,
) {
    val library = state.library
    val refreshFailure by state.libraryRefreshFailure.collectAsState()
    val inset = pageInset
    val history by state.history.state.collectAsState()
    val layout by state.homeCustomization.state.collectAsState()
    val personal by state.userPlaylists.playlists.collectAsState()
    val smart by state.smartPlaylists.playlists.collectAsState()
    val now = rememberPlaylistTime()
    val feed = remember(library, history, favoriteIds, now, layout.shortcuts, personal, smart) {
        HomeFeed.build(library, history, favoriteIds, now, layout.shortcuts, personal, smart)
    }
    val showOnThisDay = layout.isVisible(HomeSection.OnThisDay)
    val anniversaries = remember(library, history, now, showOnThisDay) {
        if (showOnThisDay) OnThisDay.build(library, history, localCalendarDate(now), ::localCalendarDate)
        else OnThisDay(emptyList(), emptyList())
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        // Carousels show a peek of the next item, as on iOS.
        val posterWidth = minOf(screenWidth - 48.dp, (screenWidth * 0.56f).coerceIn(200.dp, 340.dp))
        val tileWidth = (screenWidth - inset * 2 - 14.dp) / 2.3f
        val shelfWidth = screenWidth - inset * 2 - 28.dp

        HomePage(
            title = stringResource(Res.string.tab_home),
            trailing = {
                IconButton(
                    icon = AppIcons.Settings,
                    contentDescription = stringResource(Res.string.settings_title),
                    onClick = { state.open(Route.Settings) },
                    modifier = Modifier.size(48.dp),
                )
            },
            contentPadding = contentPadding,
        ) {
            if (hasAccess && refreshFailure != null) item(key = "home.scan-error") {
                val loading by state.isLibraryLoading.collectAsState()
                LibraryRefreshNotice(refreshFailure, loading, state.requestLibraryRefresh)
            }
            if (!hasAccess || library.tracks.isEmpty()) {
                if (hasAccess && refreshFailure != null) return@HomePage
                item(key = "home.empty") {
                    val loading by state.isLibraryLoading.collectAsState()
                    val preferences by state.settings.state.collectAsState()
                    LibraryEmptyState(hasAccess, onRequestAccess, loading,
                        preferences.libraryFilter != dev.cmdrchara.songlass.core.library.LibraryFilter())
                }
                return@HomePage
            }

            val sections = layout.visibleSections
            val hasContent = sections.any { section ->
                when (section) {
                    HomeSection.Statistics, HomeSection.ForYou -> true
                    HomeSection.OnThisDay -> anniversaries.releases.isNotEmpty() || anniversaries.memories.isNotEmpty()
                    HomeSection.Shortcuts -> feed.shortcuts.isNotEmpty()
                    HomeSection.RecentlyAdded -> feed.recentlyAdded.isNotEmpty()
                    HomeSection.ChangeOfPace -> feed.changeOfPace?.tracks?.isNotEmpty() == true
                    HomeSection.MostPlayed -> feed.mostPlayed.isNotEmpty()
                    HomeSection.Playlists -> feed.playlists.isNotEmpty()
                }
            }
            if (!hasContent) {
                // Selected sections may legitimately have no content today. Keep recovery
                // available for that case as well as for explicitly hidden sections.
                item(key = "home.all_hidden") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppText(stringResource(if (sections.isEmpty()) Res.string.homecfg_all_hidden else Res.string.homecfg_no_content), AppTheme.typography.subheadline,
                            color = AppTheme.colors.secondaryLabel)
                        ActionButton(stringResource(Res.string.homecfg_customize), { state.open(Route.HomeCustomization) })
                    }
                }
                return@HomePage
            }
            // Item keys are per section, so reordering or hiding keeps each item's identity.
            sections.forEach { section ->
                when (section) {
                    HomeSection.Statistics -> statisticsSection(state)
                    HomeSection.OnThisDay -> onThisDaySection(anniversaries, state, tileWidth)
                    HomeSection.Shortcuts -> shortcutsSection(feed, state)
                    HomeSection.ForYou -> forYouSection(feed, library, state, posterWidth)
                    HomeSection.RecentlyAdded -> recentlyAddedSection(feed, state, tileWidth)
                    HomeSection.ChangeOfPace -> changeOfPaceSection(feed, currentTrackId, state, shelfWidth, screenWidth - inset * 2)
                    HomeSection.MostPlayed -> mostPlayedSection(feed, currentTrackId, state, shelfWidth, screenWidth - inset * 2)
                    HomeSection.Playlists -> playlistsSection(feed, state, tileWidth)
                }
            }
            item(key = "home.footer") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

private fun LazyListScope.statisticsSection(state: AppState) {
    item(key = "home.statistics") {
        ListRow(
            title = stringResource(Res.string.stats_title),
            subtitle = stringResource(Res.string.stats_home_hint),
            leading = { AppIcon(AppIcons.ChartBars, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
            trailing = { AppIcon(AppIcons.ChevronRight, contentDescription = null, size = 16.dp) },
            onClick = { state.open(Route.ListeningStatistics) },
        )
    }
}

private fun LazyListScope.onThisDaySection(anniversaries: OnThisDay, state: AppState, tileWidth: Dp) {
    if (anniversaries.releases.isEmpty() && anniversaries.memories.isEmpty()) return
    item(key = "home.on_this_day.title") { SectionTitle(stringResource(Res.string.home_on_this_day)) }
    if (anniversaries.releases.isNotEmpty()) {
        item(key = "home.on_this_day.releases.label") {
            AppText(stringResource(Res.string.home_release_anniversaries), AppTheme.typography.subheadline,
                color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(horizontal = pageInset, vertical = 4.dp))
        }
        item(key = "home.on_this_day.releases.row") { HomeItemRow(anniversaries.releases.map(HomeItem::TrackItem), state, tileWidth) }
    }
    if (anniversaries.memories.isNotEmpty()) {
        item(key = "home.on_this_day.memories.label") {
            AppText(stringResource(Res.string.home_listening_memories), AppTheme.typography.subheadline,
                color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(horizontal = pageInset, vertical = 4.dp))
        }
        item(key = "home.on_this_day.memories.row") { HomeItemRow(anniversaries.memories.map(HomeItem::TrackItem), state, tileWidth) }
    }
}

private fun LazyListScope.shortcutsSection(feed: HomeFeed, state: AppState) {
    if (feed.shortcuts.isEmpty()) return
    item(key = "home.shortcuts") { ShortcutGrid(feed.shortcuts, state) }
}

private fun LazyListScope.forYouSection(feed: HomeFeed, library: MusicLibrary, state: AppState, posterWidth: Dp) {
    val controller = state.controller
    item(key = "home.for_you.title") { SectionTitle(stringResource(Res.string.home_for_you)) }
    item(key = "home.for_you.content") {
        val libraryArtwork = remember(library) { library.tracks.random().artwork() }
        val libraryMosaic = remember(library) {
            library.tracks.asSequence().map { it.artwork() }.distinctBy { it.seed }.take(4).toList()
        }
        val highlights = buildList {
            if (feed.favorites.isNotEmpty()) add(HomeHighlight(
                key = "favorites", label = stringResource(Res.string.home_label_collection),
                title = stringResource(Res.string.library_favorites),
                subtitle = pluralStringResource(Res.plurals.songs_count, feed.favorites.size, feed.favorites.size),
                artwork = feed.favorites.first().artwork(),
                artworks = feed.favorites.asSequence().map { it.artwork() }.distinctBy { it.seed }.take(4).toList(),
                icon = AppIcons.Star,
                onClick = { controller.playTracks(feed.favorites, context = PlayContext.Favorites) },
            ))
            add(HomeHighlight(
                key = "library", label = stringResource(Res.string.home_label_library),
                title = stringResource(Res.string.home_shuffle_all),
                subtitle = pluralStringResource(Res.plurals.songs_count, library.tracks.size, library.tracks.size),
                artwork = libraryArtwork, artworks = libraryMosaic, icon = AppIcons.Shuffle,
                onClick = { controller.shuffleTracks(library.tracks) },
            ))
            feed.rediscover?.let { pick -> add(HomeHighlight(
                key = "rediscover", label = idleLabel(pick.idleDays),
                title = albumTitle(pick.album.title), subtitle = artistName(pick.album.artist),
                artwork = pick.album.artwork(), artworks = emptyList(), icon = null,
                onClick = { state.open(Route.AlbumDetail(pick.album)) },
            )) }
        }
        HomeHighlights(highlights, posterWidth)
    }
}

private fun LazyListScope.recentlyAddedSection(feed: HomeFeed, state: AppState, tileWidth: Dp) {
    if (feed.recentlyAdded.isEmpty()) return
    item(key = "home.recently_added.title") { SectionTitle(stringResource(Res.string.home_recently_added)) }
    item(key = "home.recently_added.row") { HomeItemRow(feed.recentlyAdded, state, tileWidth) }
}

private fun LazyListScope.changeOfPaceSection(feed: HomeFeed, currentTrackId: String?, state: AppState, shelfWidth: Dp, fullWidth: Dp) {
    val pace = feed.changeOfPace ?: return
    item(key = "home.change_of_pace.title") {
        SectionTitle(stringResource(Res.string.home_change_of_pace), action = { PlayAllButton { state.controller.playTracks(pace.tracks) } })
    }
    item(key = "home.change_of_pace.reason") {
        AppText(
            text = when (val reason = pace.reason) {
                is ChangeOfPaceReason.MostlyOneArtist -> stringResource(Res.string.home_pace_artist, reason.artist)
                is ChangeOfPaceReason.NeverPlayed -> pluralStringResource(Res.plurals.home_pace_never, reason.count, reason.count)
                ChangeOfPaceReason.NotLately -> stringResource(Res.string.home_pace_lately)
            },
            style = AppTheme.typography.subheadline,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(start = pageInset, end = pageInset, bottom = 4.dp),
        )
    }
    item(key = "home.change_of_pace.shelf") { TrackShelf(pace.tracks, currentTrackId, state, shelfWidth, fullWidth) }
}

private fun LazyListScope.mostPlayedSection(feed: HomeFeed, currentTrackId: String?, state: AppState, shelfWidth: Dp, fullWidth: Dp) {
    if (feed.mostPlayed.isEmpty()) return
    item(key = "home.most_played.title") {
        SectionTitle(stringResource(Res.string.home_most_played), action = { PlayAllButton { state.controller.playTracks(feed.mostPlayed) } })
    }
    item(key = "home.most_played.shelf") { TrackShelf(feed.mostPlayed, currentTrackId, state, shelfWidth, fullWidth) }
}

private fun LazyListScope.playlistsSection(feed: HomeFeed, state: AppState, tileWidth: Dp) {
    if (feed.playlists.isEmpty()) return
    item(key = "home.playlists.title") { SectionTitle(stringResource(Res.string.home_your_playlists)) }
    item(key = "home.playlists.row") { HomeItemRow(feed.playlists.map(HomeItem::PlaylistItem), state, tileWidth) }
}

/** Why the rediscover pick is there: never played, or for how long it has not been. */
@Composable
private fun idleLabel(idleDays: Int?): String = when {
    idleDays == null -> stringResource(Res.string.home_rediscover_never)
    idleDays < 60 -> (idleDays / 7).let { pluralStringResource(Res.plurals.home_rediscover_weeks, it, it) }
    else -> (idleDays / 30).let { pluralStringResource(Res.plurals.home_rediscover_months, it, it) }
}

@Composable
private fun PlayAllButton(onClick: () -> Unit) {
    IconButton(AppIcons.Play, stringResource(Res.string.home_play_all), onClick, filled = true)
}

/** Two columns of compact shortcuts to jump back into recent albums, playlists and artists. */
@Composable
private fun ShortcutGrid(items: List<HomeItem>, state: AppState) {
    val inset = dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook.current.contentHorizontalInset
    Column(
        Modifier.padding(start = inset, end = inset, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { item ->
                    val tile = tileContent(item)
                    ShortcutTile(
                        title = tile.title,
                        artwork = tile.artwork,
                        circular = item is HomeItem.ArtistItem,
                        onClick = { openItem(item, state) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Songs in columns of four that page sideways, like Apple Music's song shelves. A single
 * column takes the full width.
 */
@Composable
private fun TrackShelf(tracks: List<Track>, currentTrackId: String?, state: AppState, columnWidth: Dp, fullWidth: Dp) {
    val columns = tracks.chunked(ShelfRows)
    val listState = rememberLazyListState()
    androidx.compose.runtime.CompositionLocalProvider(dev.cmdrchara.songlass.theme.liquidglass.LocalGlassRowHorizontalInset provides 0.dp) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = pageInset),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(columns) { columnIndex, column ->
                Column(Modifier.width(if (columns.size == 1) fullWidth else columnWidth)) {
                    column.forEachIndexed { row, track ->
                        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                            state.controller.playTracks(tracks, columnIndex * ShelfRows + row)
                        }
                    }
                }
            }
        }
    }
}

private const val ShelfRows = 4

private fun openItem(item: HomeItem, state: AppState) {
    when (item) {
        is HomeItem.AlbumItem -> state.open(Route.AlbumDetail(item.album))
        is HomeItem.PlaylistItem -> state.openPlaylist(item.playlist)
        is HomeItem.ArtistItem -> state.open(Route.ArtistDetail(item.artist))
        is HomeItem.FacetItem -> state.open(Route.FacetDetail(item.facet.context))
        is HomeItem.TrackItem -> state.controller.playTracks(listOf(item.track))
        is HomeItem.FavoritesItem -> state.controller.playTracks(item.tracks, context = PlayContext.Favorites)
        is HomeItem.UserPlaylistItem -> state.open(Route.UserPlaylistDetail(item.playlist.id))
        is HomeItem.SmartPlaylistItem -> state.open(Route.SmartPlaylistDetail(item.playlist.id))
    }
}

/** Horizontal carousel of albums, playlists, artists and songs. */
@Composable
private fun HomeItemRow(items: List<HomeItem>, state: AppState, tileWidth: Dp) {
    val shelf = items.map { item ->
        val tile = tileContent(item)
        ShelfItem(
            key = item.hashCode(),
            title = tile.title,
            subtitle = tile.subtitle,
            artwork = tile.artwork,
            onClick = { openItem(item, state) },
            circular = item is HomeItem.ArtistItem,
        )
    }
    ArtworkShelf(shelf, ShelfStyle.Carousel, tileWidth)
}

private class Tile(val title: String, val subtitle: String, val artwork: Artwork)

@Composable
private fun tileContent(item: HomeItem): Tile = when (item) {
    is HomeItem.AlbumItem -> Tile(albumTitle(item.album.title), stringResource(Res.string.tag_album) + " · " + artistName(item.album.artist), item.album.artwork())
    is HomeItem.PlaylistItem -> Tile(item.playlist.name, stringResource(Res.string.tag_playlist), item.playlist.artwork())
    is HomeItem.UserPlaylistItem -> Tile(item.playlist.name, stringResource(Res.string.tag_playlist), item.playlist.artwork(item.tracks))
    is HomeItem.SmartPlaylistItem -> Tile(item.playlist.name, stringResource(Res.string.homecfg_kind_smart_playlist), item.playlist.artwork(item.tracks))
    is HomeItem.ArtistItem -> Tile(artistName(item.artist.name), stringResource(Res.string.tag_artist), (item.artist.albums.firstOrNull()?.artwork() ?: item.artist.tracks.first().artwork()))
    is HomeItem.FacetItem -> Tile(item.facet.value, facetKindLabel(item.facet.kind), item.facet.tracks.first().artwork())
    is HomeItem.TrackItem -> Tile(item.track.title, stringResource(Res.string.tag_song) + " · " + artistName(item.track.artist), item.track.artwork())
    is HomeItem.FavoritesItem -> Tile(stringResource(Res.string.library_favorites), pluralStringResource(Res.plurals.songs_count, item.tracks.size, item.tracks.size), item.tracks.first().artwork())
}
