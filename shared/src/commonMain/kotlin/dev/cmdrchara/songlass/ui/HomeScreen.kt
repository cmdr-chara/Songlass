package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.home.ChangeOfPaceReason
import dev.cmdrchara.songlass.core.home.HomeFeed
import dev.cmdrchara.songlass.core.home.HomeItem
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.HighlightCard
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.home_change_of_pace
import dev.cmdrchara.songlass.shared.resources.home_most_played
import dev.cmdrchara.songlass.shared.resources.home_pace_artist
import dev.cmdrchara.songlass.shared.resources.home_pace_lately
import dev.cmdrchara.songlass.shared.resources.home_pace_never
import dev.cmdrchara.songlass.shared.resources.home_recently_added
import dev.cmdrchara.songlass.shared.resources.home_recently_played
import dev.cmdrchara.songlass.shared.resources.home_rediscover
import dev.cmdrchara.songlass.shared.resources.home_shuffle_all
import dev.cmdrchara.songlass.shared.resources.home_your_playlists
import dev.cmdrchara.songlass.shared.resources.library_favorites
import dev.cmdrchara.songlass.shared.resources.songs_count
import dev.cmdrchara.songlass.shared.resources.tab_home
import dev.cmdrchara.songlass.shared.resources.tag_album
import dev.cmdrchara.songlass.shared.resources.tag_artist
import dev.cmdrchara.songlass.shared.resources.tag_playlist
import dev.cmdrchara.songlass.shared.resources.tag_song
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Home: featured cards, then what was played and added lately, the most played songs, a
 * "change of pace" suggestion and the playlists. Content comes from core's [HomeFeed];
 * sections without content are not shown.
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { ScreenTitle(stringResource(Res.string.tab_home)) }
        if (!hasAccess || library.tracks.isEmpty()) {
            item { LibraryEmptyState(hasAccess, onRequestAccess) }
            return@LazyColumn
        }

        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (feed.favorites.isNotEmpty()) item {
                    HighlightCard(
                        title = stringResource(Res.string.library_favorites),
                        subtitle = pluralStringResource(Res.plurals.songs_count, feed.favorites.size, feed.favorites.size),
                        artwork = feed.favorites.first().artwork(),
                        onClick = { controller.playTracks(feed.favorites, context = PlayContext.Favorites) },
                        modifier = Modifier.width(HighlightWidth),
                    )
                }
                item {
                    HighlightCard(
                        title = stringResource(Res.string.home_shuffle_all),
                        subtitle = pluralStringResource(Res.plurals.songs_count, library.tracks.size, library.tracks.size),
                        artwork = remember(library) { library.tracks.random().artwork() },
                        onClick = { controller.shuffleTracks(library.tracks) },
                        modifier = Modifier.width(HighlightWidth),
                    )
                }
                feed.rediscover?.let { album ->
                    item {
                        HighlightCard(
                            title = stringResource(Res.string.home_rediscover),
                            subtitle = albumTitle(album.title) + " · " + artistName(album.artist),
                            artwork = album.artwork(),
                            onClick = { state.open(Route.AlbumDetail(album)) },
                            modifier = Modifier.width(HighlightWidth),
                        )
                    }
                }
            }
        }

        if (feed.recentlyPlayed.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.home_recently_played)) }
            item { HomeItemRow(feed.recentlyPlayed, state) }
        }
        if (feed.recentlyAdded.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.home_recently_added)) }
            item { HomeItemRow(feed.recentlyAdded, state) }
        }
        if (feed.mostPlayed.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.home_most_played)) }
            trackRows(feed.mostPlayed, currentTrackId, state)
        }
        feed.changeOfPace?.let { pace ->
            item { SectionTitle(stringResource(Res.string.home_change_of_pace)) }
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
            trackRows(pace.tracks, currentTrackId, state)
        }
        if (feed.playlists.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.home_your_playlists)) }
            item { HomeItemRow(feed.playlists.map(HomeItem::PlaylistItem), state) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private val HighlightWidth = 300.dp
private val TileSize = 150.dp

private fun androidx.compose.foundation.lazy.LazyListScope.trackRows(tracks: List<Track>, currentTrackId: String?, state: AppState) {
    itemsIndexed(tracks, key = { _, t -> "row-${t.id}" }) { index, track ->
        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
            state.controller.playTracks(tracks, index)
        }
    }
}

/** Horizontal carousel of albums, playlists, artists and songs. */
@Composable
private fun HomeItemRow(items: List<HomeItem>, state: AppState) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items) { item -> HomeTile(item, state) }
    }
}

@Composable
private fun HomeTile(item: HomeItem, state: AppState) {
    val tile = tileContent(item)
    Column(
        Modifier
            .width(TileSize)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                when (item) {
                    is HomeItem.AlbumItem -> state.open(Route.AlbumDetail(item.album))
                    is HomeItem.PlaylistItem -> state.open(Route.PlaylistDetail(item.playlist))
                    is HomeItem.ArtistItem -> state.open(Route.ArtistDetail(item.artist))
                    is HomeItem.TrackItem -> state.controller.playTracks(listOf(item.track))
                    is HomeItem.FavoritesItem -> state.controller.playTracks(item.tracks, context = PlayContext.Favorites)
                }
            },
    ) {
        AlbumArt(tile.artwork, Modifier.size(TileSize), cornerRadius = if (item is HomeItem.ArtistItem) TileSize / 2 else 10.dp)
        Spacer(Modifier.height(6.dp))
        AppText(tile.title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        AppText(tile.subtitle, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
    }
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
