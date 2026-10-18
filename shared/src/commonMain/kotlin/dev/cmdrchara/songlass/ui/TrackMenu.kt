package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.playlist_add_to
import dev.cmdrchara.songlass.shared.resources.playlist_remove_song
import dev.cmdrchara.songlass.shared.resources.action_add_to_queue
import dev.cmdrchara.songlass.shared.resources.action_cancel
import dev.cmdrchara.songlass.shared.resources.action_favorite
import dev.cmdrchara.songlass.shared.resources.action_go_to_album
import dev.cmdrchara.songlass.shared.resources.action_go_to_named_artist
import dev.cmdrchara.songlass.shared.resources.action_go_to_artist
import dev.cmdrchara.songlass.shared.resources.action_play_next
import dev.cmdrchara.songlass.shared.resources.action_unfavorite
import org.jetbrains.compose.resources.stringResource

/** The "…" actions for the track in [AppState.menuTrack]. */
@Composable
fun TrackMenu(state: AppState, favoriteIds: List<String>) {
    // Keep the last track while the sheet animates out.
    var lastShown by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(state.menuTrack) { state.menuTrack?.let { lastShown = it } }
    val track = state.menuTrack ?: lastShown ?: return
    val isFavorite = track.id in favoriteIds

    ActionSheet(
        visible = state.menuTrack != null,
        title = track.title,
        subtitle = artistName(track.artist),
        artwork = track.artwork(),
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = state::closeMenu,
        actions = listOfNotNull(
            SheetAction(stringResource(Res.string.action_play_next), AppIcons.PlayNext) { state.controller.playNext(listOf(track)) },
            SheetAction(stringResource(Res.string.action_add_to_queue), AppIcons.AddToQueue) { state.controller.addToQueue(listOf(track)) },
            SheetAction(stringResource(Res.string.playlist_add_to), AppIcons.Library) { state.openAddToPlaylist(listOf(track)) },
            state.menuPlaylistId?.let { playlistId ->
                SheetAction(stringResource(Res.string.playlist_remove_song), AppIcons.Close) { state.userPlaylists.remove(playlistId, track.id) }
            },
            SheetAction(
                stringResource(if (isFavorite) Res.string.action_unfavorite else Res.string.action_favorite),
                if (isFavorite) AppIcons.StarFilled else AppIcons.Star,
            ) { state.favorites.toggle(track.id) },
            // Lone songs are not albums, so there is nothing to go to.
            if (state.library.albumOf(track) != null) {
                SheetAction(stringResource(Res.string.action_go_to_album), AppIcons.Library) { state.showAlbumOf(track) }
            } else {
                null
            },
            SheetAction(stringResource(Res.string.action_go_to_artist), AppIcons.MusicNote) { state.showArtistOf(track) },
        ) + state.library.artistsOf(track).filter { it != state.library.artistOf(track) }.map { artist ->
            SheetAction(stringResource(Res.string.action_go_to_named_artist, artistName(artist.name)), AppIcons.Person) {
                state.open(Route.ArtistDetail(artist))
            }
        },
    )
}
