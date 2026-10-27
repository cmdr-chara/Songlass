package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.library.RatingGroup
import dev.cmdrchara.songlass.core.library.groupsIn
import dev.cmdrchara.songlass.core.library.ratedTracks
import dev.cmdrchara.songlass.designsystem.BackPageTitle
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.ratings_library_count
import dev.cmdrchara.songlass.shared.resources.ratings_library_empty
import dev.cmdrchara.songlass.shared.resources.ratings_library_empty_hint
import dev.cmdrchara.songlass.shared.resources.ratings_library_group
import dev.cmdrchara.songlass.shared.resources.ratings_library_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The user's rated songs, from five stars down, one page away from the Library: the rows are the
 * same as in every other song list, grouped so the best-rated music is reachable in one tap.
 *
 * Which rated songs still exist and how they are ordered belongs to the core
 * ([dev.cmdrchara.songlass.core.library.Ratings.groupsIn]); this page only draws the result.
 */
@Composable
fun RatingsScreen(state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    // Ratings change from the track menu, so follow the stored values rather than a first snapshot.
    val storedRatings by state.ratings.values.collectAsState()
    val library = state.library
    val groups = remember(storedRatings, library) { state.ratings.groupsIn(library) }
    val tracks = remember(groups, library) { state.ratings.ratedTracks(library) }
    val listState = rememberLazyListState()
    if (tracks.isNotEmpty()) PrefetchTrackRowArtwork(listState, tracks)
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), state = listState, contentPadding = contentPadding) {
        item { BackPageTitle(stringResource(Res.string.ratings_library_title), backLabel, state::goBack) }
        if (tracks.isEmpty()) {
            item(key = "ratings.empty") { RatingsEmptyState() }
            return@LazyColumn
        }
        // Play and Shuffle act on every rated song, highest rating first, as the Songs page does.
        item(key = "ratings.playback") { PlayShuffleRow(tracks, null, state) }
        groups.forEach { group ->
            item(key = "ratings.group.${group.stars}") { RatingsGroupHeader(group) }
            itemsIndexed(group.tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                    state.tapTrack(group.tracks, index)
                }
            }
        }
    }
}

/** "5 stars" plus how many songs have them, styled as any other section heading. */
@Composable
private fun RatingsGroupHeader(group: RatingGroup) {
    val stars = pluralStringResource(Res.plurals.ratings_library_group, group.stars, group.stars)
    val count = pluralStringResource(Res.plurals.ratings_library_count, group.tracks.size, group.tracks.size)
    SectionTitle(stars) {
        AppText(count, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
    }
}

/** No ratings yet: say so and point at the track menu, where a rating is set. */
@Composable
private fun RatingsEmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(AppIcons.Star, null, size = 36.dp, tint = AppTheme.colors.tertiaryLabel)
        Spacer(Modifier.height(12.dp))
        AppText(stringResource(Res.string.ratings_library_empty), AppTheme.typography.title3, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        AppText(
            stringResource(Res.string.ratings_library_empty_hint),
            AppTheme.typography.subheadline,
            color = AppTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}
