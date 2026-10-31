package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.core.library.Ratings
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.sound.DefaultSoundTarget
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** Frequent actions stay on the first page; secondary actions share the existing sheet host. */
@Composable
fun TrackMenu(state: AppState, favoriteIds: List<String>) {
    val supportsDefaultSounds = DefaultSoundSheet(state)
    // Keep the last track and page while the sheet animates out.
    var lastShown by remember { mutableStateOf<Track?>(null) }
    var lastPage by remember { mutableStateOf(TrackMenuPage.Main) }
    LaunchedEffect(state.menuTrack, state.trackMenuPage) {
        if (state.menuTrack != null) {
            lastShown = state.menuTrack
            lastPage = state.trackMenuPage
        }
    }
    val track = state.menuTrack ?: lastShown ?: return
    val page = if (state.menuTrack != null) state.trackMenuPage else lastPage
    val playlistId = state.menuPlaylistId
    val isFavorite = track.id in favoriteIds
    val storedRatings by state.ratings.values.collectAsState()
    val rating = storedRatings[track.id] ?: 0
    val categories = state.categoryTargets
    val back = stringResource(Res.string.back)
    fun navigate(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, destination: TrackMenuPage) =
        SheetAction(label, icon, onClick = { state.showTrackMenuPage(destination) }, dismissOnClick = false)
    val actions = when (page) {
        TrackMenuPage.Main -> buildList {
            add(SheetAction(stringResource(Res.string.action_play_next), AppIcons.PlayNext) { state.controller.playNext(listOf(track)) })
            add(SheetAction(stringResource(Res.string.queue_add_temporary), AppIcons.AddToQueue) { state.controller.addToQueue(listOf(track)) })
            add(SheetAction(stringResource(Res.string.playlist_add_to), AppIcons.Library) { state.openAddToPlaylist(listOf(track)) })
            add(SheetAction(stringResource(if (isFavorite) Res.string.action_unfavorite else Res.string.action_favorite),
                if (isFavorite) AppIcons.StarFilled else AppIcons.Star) { state.favorites.toggle(track.id) })
            add(navigate(stringResource(Res.string.rating_title), if (rating > 0) AppIcons.StarFilled else AppIcons.Star, TrackMenuPage.Rating))
            if (LocalOnlineArtwork.current != null) add(SheetAction(
                stringResource(Res.string.feedback_find_cover), AppIcons.Search) { state.openArtworkSearch(track) })
            // Category commands only appear when the adjacent collection exists: no dead rows.
            categories.next?.let { target ->
                val label = target.title?.let { stringResource(Res.string.category_next_named, it) }
                    ?: stringResource(Res.string.category_next)
                add(SheetAction(label, AppIcons.Next) { state.switchCategory(forward = true) })
            }
            categories.previous?.let { target ->
                val label = target.title?.let { stringResource(Res.string.category_previous_named, it) }
                    ?: stringResource(Res.string.category_previous)
                add(SheetAction(label, AppIcons.Previous) { state.switchCategory(forward = false) })
            }
            add(navigate(stringResource(Res.string.action_more), AppIcons.More, TrackMenuPage.More))
        }
        TrackMenuPage.Rating -> buildList {
            add(SheetAction(back, AppIcons.ChevronLeft, onClick = state::backTrackMenu, dismissOnClick = false, isBackAction = true))
            for (stars in 1..Ratings.MAX_STARS) {
                val label = stringResource(
                    when (stars) {
                        1 -> Res.string.rating_stars_1
                        2 -> Res.string.rating_stars_2
                        3 -> Res.string.rating_stars_3
                        4 -> Res.string.rating_stars_4
                        else -> Res.string.rating_stars_5
                    },
                )
                add(SheetAction(label, if (stars <= rating) AppIcons.StarFilled else AppIcons.Star) { state.ratings.set(track.id, stars) })
            }
            add(SheetAction(stringResource(Res.string.rating_none), AppIcons.Close) { state.ratings.set(track.id, 0) })
        }
        TrackMenuPage.More -> listOfNotNull(
            SheetAction(back, AppIcons.ChevronLeft, onClick = state::backTrackMenu, dismissOnClick = false, isBackAction = true),
            SheetAction(stringResource(Res.string.bookmark_from_start), AppIcons.Previous) { state.controller.playFromBeginning(track) },
            SheetAction(stringResource(Res.string.bookmark_open), AppIcons.Bookmark) { state.openBookmarks(track) },
            if (state.library.albumOf(track) != null) SheetAction(stringResource(Res.string.action_go_to_album), AppIcons.Library) { state.showAlbumOf(track) } else null,
            navigate(stringResource(Res.string.track_menu_artists), AppIcons.Person, TrackMenuPage.Artists),
            if (state.tagEditor != null) SheetAction(stringResource(Res.string.tags_edit), AppIcons.Lyrics) { state.openTagEditor(track) } else null,
            playlistId?.let { id -> SheetAction(stringResource(Res.string.playlist_remove_song), AppIcons.Close) { state.userPlaylists.remove(id, track.id) } },
            if (supportsDefaultSounds) navigate(stringResource(Res.string.track_menu_use_as), AppIcons.MusicNote, TrackMenuPage.UseAs) else null,
        )
        TrackMenuPage.Artists -> listOf(
            SheetAction(back, AppIcons.ChevronLeft, onClick = state::backTrackMenu, dismissOnClick = false, isBackAction = true),
            SheetAction(stringResource(Res.string.action_go_to_artist), AppIcons.MusicNote) { state.showArtistOf(track) },
        ) + state.library.artistsOf(track).filter { it != state.library.artistOf(track) }.map { artist ->
            SheetAction(stringResource(Res.string.action_go_to_named_artist, artistName(artist.name)), AppIcons.Person) { state.showArtist(artist) }
        }
        TrackMenuPage.UseAs -> listOf(
            SheetAction(back, AppIcons.ChevronLeft, onClick = state::backTrackMenu, dismissOnClick = false, isBackAction = true),
            SheetAction(stringResource(Res.string.default_sound_ringtone), AppIcons.MusicNote) { state.askDefaultSound(track, DefaultSoundTarget.Ringtone) },
            SheetAction(stringResource(Res.string.default_sound_alarm), AppIcons.Timer) { state.askDefaultSound(track, DefaultSoundTarget.Alarm) },
        )
    }
    ActionSheet(
        visible = state.menuTrack != null,
        title = when (page) {
            TrackMenuPage.Main -> track.title
            TrackMenuPage.More -> stringResource(Res.string.action_more)
            TrackMenuPage.Artists -> stringResource(Res.string.track_menu_artists)
            TrackMenuPage.UseAs -> stringResource(Res.string.track_menu_use_as)
            TrackMenuPage.Rating -> stringResource(Res.string.rating_title)
        },
        subtitle = when (page) {
            TrackMenuPage.Main -> artistName(track.artist)
            TrackMenuPage.Rating -> if (rating > 0) stringResource(Res.string.rating_current, rating.toString())
                else stringResource(Res.string.rating_none)
            else -> track.title
        },
        artwork = rememberTrackArtwork(track).takeIf { page == TrackMenuPage.Main },
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = state::closeMenu,
        actions = actions,
    )
}
