package dev.cmdrchara.songlass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.cmdrchara.songlass.designsystem.AppScaffold
import dev.cmdrchara.songlass.designsystem.MiniPlayer
import dev.cmdrchara.songlass.designsystem.MiniPlayerState
import dev.cmdrchara.songlass.designsystem.NowPlayingSheet
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.playback_close
import dev.cmdrchara.songlass.shared.resources.tab_library
import dev.cmdrchara.songlass.shared.resources.tab_search
import dev.cmdrchara.songlass.theme.liquidglass.LiquidGlassTheme
import dev.cmdrchara.songlass.ui.AlbumScreen
import dev.cmdrchara.songlass.ui.AppState
import dev.cmdrchara.songlass.ui.ArtistScreen
import dev.cmdrchara.songlass.ui.LibraryScreen
import dev.cmdrchara.songlass.ui.NowPlayingContent
import dev.cmdrchara.songlass.ui.PlatformBackHandler
import dev.cmdrchara.songlass.ui.Route
import dev.cmdrchara.songlass.ui.SearchScreen
import dev.cmdrchara.songlass.ui.Tab
import dev.cmdrchara.songlass.ui.artistName
import dev.cmdrchara.songlass.ui.artwork
import dev.cmdrchara.songlass.ui.playbackLabels
import org.jetbrains.compose.resources.stringResource

/** Root composable shared by Android and iOS. */
@Composable
fun App() {
    LiquidGlassTheme {
        val scope = rememberCoroutineScope()
        val state = remember { AppState.demo(scope) }
        LaunchedEffect(state) { state.runDemoClock() }
        PlatformBackHandler(enabled = state.canGoBack) { state.goBack() }

        val queue by state.controller.queue.collectAsState()
        val player by state.controller.playerState.collectAsState()
        val labels = playbackLabels()
        val current = queue.current?.track
        val currentId = current?.id
        val tabTitles = mapOf(Tab.Library to stringResource(Res.string.tab_library), Tab.Search to stringResource(Res.string.tab_search))

        val accessory: (@Composable () -> Unit)? = if (current == null) null else {
            {
                MiniPlayer(
                    state = MiniPlayerState(
                        title = current.title,
                        subtitle = artistName(current.artist),
                        artwork = current.artwork(),
                        isPlaying = player.isPlaying,
                        progress = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f,
                    ),
                    labels = labels,
                    onClick = state::openNowPlaying,
                    onPlayPause = state.controller::togglePlayPause,
                    onNext = state.controller::skipToNext,
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            AppScaffold(
                tabs = listOf(
                    TabItem(tabTitles.getValue(Tab.Library), AppIcons.Library),
                    TabItem(tabTitles.getValue(Tab.Search), AppIcons.Search),
                ),
                selectedTab = state.tab.ordinal,
                onTabSelected = { state.selectTab(Tab.entries[it]) },
                accessory = accessory,
            ) { padding ->
                val backLabel = tabTitles.getValue(state.tab)
                when (val route = state.currentRoute) {
                    is Route.AlbumDetail -> AlbumScreen(route.album, state, currentId, backLabel, padding)
                    is Route.ArtistDetail -> ArtistScreen(route.artist, state, currentId, backLabel, padding)
                    null -> when (state.tab) {
                        Tab.Library -> LibraryScreen(state, currentId, padding)
                        Tab.Search -> SearchScreen(state, currentId, padding)
                    }
                }
            }
            NowPlayingSheet(
                visible = state.isNowPlayingOpen && current != null,
                artwork = current?.artwork(),
                dismissLabel = stringResource(Res.string.playback_close),
                onDismiss = state::closeNowPlaying,
            ) {
                NowPlayingContent(state, queue, player, labels)
            }
        }
    }
}
