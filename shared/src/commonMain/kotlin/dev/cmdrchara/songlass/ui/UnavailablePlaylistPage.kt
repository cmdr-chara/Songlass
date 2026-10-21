package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.cmdrchara.songlass.designsystem.BackPageTitle
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.playlist_unavailable
import dev.cmdrchara.songlass.shared.resources.playlist_unavailable_hint
import org.jetbrains.compose.resources.stringResource

/** A stale tab/restore route keeps its Back path instead of becoming an empty surface. */
@Composable
internal fun UnavailablePlaylistPage(backLabel: String, onBack: () -> Unit, contentPadding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
        item { BackPageTitle(stringResource(Res.string.playlist_unavailable), backLabel, onBack) }
        item { FeatureHint(stringResource(Res.string.playlist_unavailable_hint)) }
    }
}
