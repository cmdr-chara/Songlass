package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.library.PossibleDuplicates
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.BackPageTitle
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun PossibleDuplicatesScreen(state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    val library = state.library
    // Screen-owned work: a rescan replaces the snapshot; leaving cancels the result delivery.
    val groups by produceState<List<List<Track>>?>(null, library) {
        value = null
        value = withContext(Dispatchers.Default) { PossibleDuplicates.find(library.tracks) }
    }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
        item { BackPageTitle(stringResource(Res.string.duplicates_title), backLabel, state::goBack) }
        item {
            AppText(stringResource(Res.string.duplicates_hint), AppTheme.typography.subheadline,
                color = AppTheme.colors.secondaryLabel, modifier = Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 12.dp))
        }
        val result = groups
        if (result.isNullOrEmpty()) item {
            AppText(stringResource(if (result == null) Res.string.duplicates_scanning else Res.string.duplicates_empty),
                AppTheme.typography.body, modifier = Modifier.padding(horizontal = pageInset, vertical = 24.dp))
        }
        result.orEmpty().forEach { group ->
            item(key = "duplicates.group.${group.first().id}") {
                SectionTitle(stringResource(Res.string.duplicates_copies, group.size))
            }
            itemsIndexed(group, key = { _, track -> track.id }) { index, track ->
                TrackRow(track, track.id == currentTrackId, onMore = { state.openMenu(track) }) {
                    state.tapTrack(group, index)
                }
                AppText(listOfNotNull(track.album, track.path ?: stringResource(Res.string.duplicates_unknown_path),
                    formatDuration(track.durationMs)).joinToString(" · "), AppTheme.typography.footnote,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.fillMaxWidth().padding(start = pageInset, end = pageInset, bottom = 12.dp))
            }
        }
    }
}
