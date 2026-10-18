package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.home.ChangeOfPaceReason
import dev.cmdrchara.songlass.core.home.HomeFeed
import dev.cmdrchara.songlass.core.home.HomeItem
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.ArtworkShelf
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.PosterCard
import dev.cmdrchara.songlass.designsystem.ShortcutTile
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.home_change_of_pace
import dev.cmdrchara.songlass.shared.resources.home_for_you
import dev.cmdrchara.songlass.shared.resources.home_label_collection
import dev.cmdrchara.songlass.shared.resources.home_label_library
import dev.cmdrchara.songlass.shared.resources.home_most_played
import dev.cmdrchara.songlass.shared.resources.home_pace_artist
import dev.cmdrchara.songlass.shared.resources.home_pace_lately
import dev.cmdrchara.songlass.shared.resources.home_pace_never
import dev.cmdrchara.songlass.shared.resources.home_play_all
import dev.cmdrchara.songlass.shared.resources.home_recently_added
import dev.cmdrchara.songlass.shared.resources.home_rediscover_months
import dev.cmdrchara.songlass.shared.resources.home_rediscover_never
import dev.cmdrchara.songlass.shared.resources.home_rediscover_weeks
import dev.cmdrchara.songlass.shared.resources.home_shuffle_all
import dev.cmdrchara.songlass.shared.resources.home_your_playlists
import dev.cmdrchara.songlass.shared.resources.library_favorites
import dev.cmdrchara.songlass.shared.resources.songs_count
import dev.cmdrchara.songlass.shared.resources.tab_home
import dev.cmdrchara.songlass.shared.resources.settings_title
import dev.cmdrchara.songlass.shared.resources.tag_album
import dev.cmdrchara.songlass.shared.resources.tag_artist
import dev.cmdrchara.songlass.shared.resources.tag_playlist
import dev.cmdrchara.songlass.shared.resources.tag_song
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Home, shaped after what the best players share (Spotify's shortcuts, Apple Music's Top Picks,
 * Plexamp's hubs): a grid to jump back in, featured cards that say why they are there, recent
 * additions, paged song shelves with a "play all" and the playlists. Content comes from core's
 * [HomeFeed]; sections without content are not shown.
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
    val history by state.history.state.collectAsState()
    // Read the day once per visit: the feed is stable while the Home is shown.
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val feed = remember(library, history, favoriteIds, now) { HomeFeed.build(library, history, favoriteIds, now) }
    val controller = state.controller

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        // Carousels show a peek of the next item, as on iOS.
        val posterWidth = screenWidth * 0.62f
        val tileWidth = (screenWidth - 16.dp * 2 - 14.dp) / 2.3f
        val shelfWidth = screenWidth - 16.dp * 2 - 28.dp

        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item {
                // Settings are reachable from both main tabs, like the account button in Apple Music.
                ScreenTitle(stringResource(Res.string.tab_home)) {
                    IconButton(
                        icon = AppIcons.Settings,
                        contentDescription = stringResource(Res.string.settings_title),
                        onClick = { state.open(Route.Settings) },
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            if (!hasAccess || library.tracks.isEmpty()) {
                item {
                    val loading by state.isLibraryLoading.collectAsState()
                    val preferences by state.settings.state.collectAsState()
                    LibraryEmptyState(hasAccess, onRequestAccess, loading,
                        preferences.libraryFilter != dev.cmdrchara.songlass.core.library.LibraryFilter())
                }
                return@LazyColumn
            }

            if (feed.shortcuts.isNotEmpty()) {
                item { ShortcutGrid(feed.shortcuts, state) }
            }

            item { SectionTitle(stringResource(Res.string.home_for_you)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (feed.favorites.isNotEmpty()) item {
                        PosterCard(
                            label = stringResource(Res.string.home_label_collection),
                            title = stringResource(Res.string.library_favorites),
                            subtitle = pluralStringResource(Res.plurals.songs_count, feed.favorites.size, feed.favorites.size),
                            artwork = feed.favorites.first().artwork(),
                            onClick = { controller.playTracks(feed.favorites, context = PlayContext.Favorites) },
                            modifier = Modifier.width(posterWidth),
                        )
                    }
                    item {
                        PosterCard(
                            label = stringResource(Res.string.home_label_library),
                            title = stringResource(Res.string.home_shuffle_all),
                            subtitle = pluralStringResource(Res.plurals.songs_count, library.tracks.size, library.tracks.size),
                            artwork = remember(library) { library.tracks.random().artwork() },
                            onClick = { controller.shuffleTracks(library.tracks) },
                            modifier = Modifier.width(posterWidth),
                        )
                    }
                    feed.rediscover?.let { pick ->
                        item {
                            PosterCard(
                                label = idleLabel(pick.idleDays),
                                title = albumTitle(pick.album.title),
                                subtitle = artistName(pick.album.artist),
                                artwork = pick.album.artwork(),
                                onClick = { state.open(Route.AlbumDetail(pick.album)) },
                                modifier = Modifier.width(posterWidth),
                            )
                        }
                    }
                }
            }

            if (feed.recentlyAdded.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.home_recently_added)) }
                item { HomeItemRow(feed.recentlyAdded, state, tileWidth) }
            }
            feed.changeOfPace?.let { pace ->
                item { SectionTitle(stringResource(Res.string.home_change_of_pace), action = { PlayAllButton { controller.playTracks(pace.tracks) } }) }
                item {
                    AppText(
                        text = when (val reason = pace.reason) {
                            is ChangeOfPaceReason.MostlyOneArtist -> stringResource(Res.string.home_pace_artist, reason.artist)
                            is ChangeOfPaceReason.NeverPlayed -> pluralStringResource(Res.plurals.home_pace_never, reason.count, reason.count)
                            ChangeOfPaceReason.NotLately -> stringResource(Res.string.home_pace_lately)
                        },
                        style = AppTheme.typography.subheadline,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
                item { TrackShelf(pace.tracks, currentTrackId, state, shelfWidth, screenWidth - 32.dp) }
            }
            if (feed.mostPlayed.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.home_most_played), action = { PlayAllButton { controller.playTracks(feed.mostPlayed) } }) }
                item { TrackShelf(feed.mostPlayed, currentTrackId, state, shelfWidth, screenWidth - 32.dp) }
            }
            if (feed.playlists.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.home_your_playlists)) }
                item { HomeItemRow(feed.playlists.map(HomeItem::PlaylistItem), state, tileWidth) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
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
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(horizontal = 16.dp),
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

private const val ShelfRows = 4

private fun openItem(item: HomeItem, state: AppState) {
    when (item) {
        is HomeItem.AlbumItem -> state.open(Route.AlbumDetail(item.album))
        is HomeItem.PlaylistItem -> state.openPlaylist(item.playlist)
        is HomeItem.ArtistItem -> state.open(Route.ArtistDetail(item.artist))
        is HomeItem.TrackItem -> state.controller.playTracks(listOf(item.track))
        is HomeItem.FavoritesItem -> state.controller.playTracks(item.tracks, context = PlayContext.Favorites)
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
    is HomeItem.ArtistItem -> Tile(artistName(item.artist.name), stringResource(Res.string.tag_artist), (item.artist.albums.firstOrNull()?.artwork() ?: item.artist.tracks.first().artwork()))
    is HomeItem.TrackItem -> Tile(item.track.title, stringResource(Res.string.tag_song) + " · " + artistName(item.track.artist), item.track.artwork())
    is HomeItem.FavoritesItem -> Tile(stringResource(Res.string.library_favorites), pluralStringResource(Res.plurals.songs_count, item.tracks.size, item.tracks.size), item.tracks.first().artwork())
}
