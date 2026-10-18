package dev.cmdrchara.songlass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import dev.cmdrchara.songlass.designsystem.theme.LocalNavigationTint
import dev.cmdrchara.songlass.designsystem.theme.LocalSheetTint
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkTint
import dev.cmdrchara.songlass.designsystem.foundation.LocalArtworkPresentation
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkPresentation
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.ArtworkStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.cmdrchara.songlass.core.settings.AppearanceMode
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility
import dev.cmdrchara.songlass.designsystem.NavigationDirection
import dev.cmdrchara.songlass.designsystem.ScreenTransition
import dev.cmdrchara.songlass.designsystem.AppScaffold
import dev.cmdrchara.songlass.designsystem.MiniPlayer
import dev.cmdrchara.songlass.designsystem.MiniPlayerState
import dev.cmdrchara.songlass.designsystem.NowPlayingSheet
import dev.cmdrchara.songlass.designsystem.TabItem
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkImageLoader
import dev.cmdrchara.songlass.designsystem.foundation.LocalArtworkImageLoader
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.playback_close
import dev.cmdrchara.songlass.shared.resources.playback_artwork
import dev.cmdrchara.songlass.shared.resources.playback_expand_artwork
import dev.cmdrchara.songlass.shared.resources.playback_close_artwork
import dev.cmdrchara.songlass.ui.PlayerPage
import dev.cmdrchara.songlass.ui.SongInformation
import dev.cmdrchara.songlass.shared.resources.tab_home
import dev.cmdrchara.songlass.shared.resources.tab_library
import dev.cmdrchara.songlass.shared.resources.tab_search
import dev.cmdrchara.songlass.theme.liquidglass.LiquidGlassTheme
import dev.cmdrchara.songlass.ui.AlbumScreen
import dev.cmdrchara.songlass.ui.AppState
import dev.cmdrchara.songlass.ui.ArtistScreen
import dev.cmdrchara.songlass.ui.HomeScreen
import dev.cmdrchara.songlass.ui.LibraryScreen
import dev.cmdrchara.songlass.ui.SystemBarAppearance
import dev.cmdrchara.songlass.ui.SettingsScreen
import dev.cmdrchara.songlass.ui.rememberSystemAccessibility
import dev.cmdrchara.songlass.ui.NowPlayingContent
import dev.cmdrchara.songlass.ui.PlatformBackHandler
import dev.cmdrchara.songlass.ui.FolderScreen
import dev.cmdrchara.songlass.core.settings.InterfaceStyle
import dev.cmdrchara.songlass.designsystem.theme.LocalAmbientArtwork
import dev.cmdrchara.songlass.theme.materialglass.MaterialGlassTheme
import dev.cmdrchara.songlass.ui.LibraryCategoryScreen
import dev.cmdrchara.songlass.ui.Route
import dev.cmdrchara.songlass.ui.SearchScreen
import dev.cmdrchara.songlass.ui.Tab
import dev.cmdrchara.songlass.ui.TrackMenu
import dev.cmdrchara.songlass.ui.artistName
import dev.cmdrchara.songlass.ui.artwork
import dev.cmdrchara.songlass.ui.playbackLabels
import org.jetbrains.compose.resources.stringResource

/**
 * Root composable shared by Android and iOS. [graph] outlives the UI; [onRequestLibraryAccess]
 * asks the platform for permission to read music (null where no permission is needed);
 * [artworkLoader] reads real cover art (null: placeholders only).
 */
@Composable
fun App(
    graph: AppGraph,
    onRequestLibraryAccess: (() -> Unit)? = null,
    artworkLoader: ArtworkImageLoader? = null,
) {
    val preferences by graph.settings.state.collectAsState()
    val presentation = when (preferences.artworkStyle) {
        ArtworkStyle.FullBleed -> ArtworkPresentation.FullBleed
        ArtworkStyle.Square -> ArtworkPresentation.Square
    }
    CompositionLocalProvider(
        LocalArtworkImageLoader provides artworkLoader,
        LocalArtworkPresentation provides presentation,
    ) { AppContent(graph, onRequestLibraryAccess, preferences) }
}

@Composable
private fun AppContent(graph: AppGraph, onRequestLibraryAccess: (() -> Unit)?, preferences: AppPreferences) {
    val systemAccessibility = rememberSystemAccessibility()
    val accessibility = UiAccessibility(
        reduceMotion = preferences.reduceMotion || systemAccessibility.reduceMotion,
        reduceTransparency = preferences.reduceTransparency || systemAccessibility.reduceTransparency,
    )
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (preferences.appearance) {
        AppearanceMode.System -> systemDarkTheme
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    // Navigation state outlives a change of interface style.
    val state = remember(graph) { AppState(graph) }
    val playingQueue by graph.controller.queue.collectAsState()
    val ambientArtwork = playingQueue.current?.track?.artwork()
    CompositionLocalProvider(LocalAmbientArtwork provides ambientArtwork) {
    InterfaceTheme(preferences.interfaceStyle, darkTheme, accessibility) {
        val library by graph.library.collectAsState()
        val hasLibraryAccess by graph.source.hasAccess.collectAsState()
        LaunchedEffect(library) { state.library = library }
        LaunchedEffect(hasLibraryAccess) { graph.refreshLibrary() }
        PlatformBackHandler(enabled = state.canGoBack) { state.goBack() }

        val queue by state.controller.queue.collectAsState()
        val player by state.controller.playerState.collectAsState()
        val favoriteIds by state.favorites.ids.collectAsState()
        val labels = playbackLabels()
        val current = queue.current?.track
        val currentId = current?.id
        LaunchedEffect(currentId) { state.updateArtworkExpansion(false); state.closeSongInfo() }
        val nowPlayingVisible = state.isNowPlayingOpen && current != null
        // Tinted surfaces may be dark in light mode (or the opposite) when they follow the cover.
        val followArtwork = preferences.artworkStyle == ArtworkStyle.FullBleed
        val tintedArtwork = when {
            nowPlayingVisible -> current?.artwork()
            else -> when (val route = state.currentRoute) {
                is Route.AlbumDetail -> route.album.artwork()
                is Route.FolderDetail -> route.folder.artwork()
                else -> null
            }
        }
        val contextTint = rememberArtworkTint(tintedArtwork, darkTheme, followArtwork)
        val tintIsDark = contextTint.isDark
        val routeArtwork = when (val route = state.currentRoute) {
            is Route.AlbumDetail -> route.album.artwork()
            is Route.FolderDetail -> route.folder.artwork()
            else -> null
        }
        val routeTint = rememberArtworkTint(routeArtwork, darkTheme, followArtwork)
        SystemBarAppearance(lightIcons = if (tintedArtwork != null) tintIsDark else darkTheme)
        val tabTitles = mapOf(
            Tab.Home to stringResource(Res.string.tab_home),
            Tab.Library to stringResource(Res.string.tab_library),
            Tab.Search to stringResource(Res.string.tab_search),
        )

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

        CompositionLocalProvider(LocalNavigationTint provides routeArtwork?.let { routeTint }) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (nowPlayingVisible || state.menuTrack != null || state.isSongInfoVisible) Modifier.clearAndSetSemantics {} else Modifier)) {
                AppScaffold(
                    tabs = listOf(
                        TabItem(tabTitles.getValue(Tab.Home), AppIcons.Home),
                        TabItem(tabTitles.getValue(Tab.Library), AppIcons.Library),
                        TabItem(tabTitles.getValue(Tab.Search), AppIcons.Search, isSearch = true),
                    ),
                    selectedTab = state.tab.ordinal,
                    onTabSelected = { state.selectTab(Tab.entries[it]) },
                    accessory = accessory,
                ) { padding ->
                    ScreenTransition(
                        targetState = state.destination,
                        modifier = Modifier.fillMaxSize(),
                        direction = { from, to ->
                            when {
                                from.tab != to.tab -> NavigationDirection.Crossfade
                                to.depth > from.depth -> NavigationDirection.Forward
                                to.depth < from.depth -> NavigationDirection.Backward
                                else -> NavigationDirection.Crossfade
                            }
                        },
                    ) { destination ->
                        val backLabel = tabTitles.getValue(destination.tab)
                        when (val route = destination.route) {
                            Route.Settings -> SettingsScreen(
                                settings = graph.settings,
                                preferences = preferences,
                                systemAccessibility = systemAccessibility,
                                backLabel = backLabel,
                                onBack = state::goBack,
                                contentPadding = padding,
                            )
                            is Route.AlbumDetail -> AlbumScreen(route.album, state, currentId, backLabel, padding)
                            is Route.ArtistDetail -> ArtistScreen(route.artist, state, currentId, backLabel, padding)
                            is Route.FolderDetail -> FolderScreen(route.folder, state, currentId, backLabel, padding)
                            is Route.LibraryPage -> LibraryCategoryScreen(route.category, state, currentId, favoriteIds, backLabel, padding)
                            null -> when (destination.tab) {
                                Tab.Home -> HomeScreen(state, currentId, favoriteIds, hasLibraryAccess, onRequestLibraryAccess, padding)
                                Tab.Library -> LibraryScreen(state, currentId, favoriteIds, hasLibraryAccess, onRequestLibraryAccess, padding)
                                Tab.Search -> SearchScreen(state, currentId, padding)
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize().then(if (state.menuTrack != null || state.isSongInfoVisible) Modifier.clearAndSetSemantics {} else Modifier)) {
                NowPlayingSheet(
                    visible = nowPlayingVisible,
                    artwork = current?.artwork(),
                    artworkVisible = state.playerPage == PlayerPage.Artwork,
                    artworkExpanded = state.isArtworkExpanded,
                    artworkLabel = stringResource(Res.string.playback_artwork),
                    expandArtworkLabel = stringResource(Res.string.playback_expand_artwork),
                    closeArtworkLabel = stringResource(Res.string.playback_close_artwork),
                    onArtworkExpandedChange = state::updateArtworkExpansion,
                    dismissLabel = stringResource(Res.string.playback_close),
                    onDismiss = state::closeNowPlaying,
                ) { onArtworkTap ->
                    NowPlayingContent(state, queue, player, favoriteIds, labels, onArtworkTap)
                }
            }
            // Sheets take the tint of the screen they open from (player, album or playlist page).
            CompositionLocalProvider(LocalSheetTint provides tintedArtwork?.let { contextTint }) {
                if (current != null) SongInformation(state, current, player)
                TrackMenu(state, favoriteIds)
            }
        }
        }
    }
    }
}

/** The chosen interface style's tokens and components around [content]. */
@Composable
private fun InterfaceTheme(style: InterfaceStyle, darkTheme: Boolean, accessibility: UiAccessibility, content: @Composable () -> Unit) {
    when (style) {
        InterfaceStyle.LiquidGlass -> LiquidGlassTheme(darkTheme = darkTheme, accessibility = accessibility, content = content)
        InterfaceStyle.MaterialGlass -> MaterialGlassTheme(darkTheme = darkTheme, accessibility = accessibility, content = content)
    }
}
