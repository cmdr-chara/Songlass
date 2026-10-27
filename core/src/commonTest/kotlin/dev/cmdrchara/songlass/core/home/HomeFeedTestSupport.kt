package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.playlists.SmartPlaylist
import dev.cmdrchara.songlass.core.playlists.UserPlaylist

/**
 * [HomeFeed.build] for tests that do not exercise the votes: only a smart playlist with a minimum
 * rating reads them, and the tests that cover that rule pass the map explicitly.
 */
fun homeFeed(
    library: MusicLibrary,
    history: HistorySnapshot,
    favoriteIds: List<String>,
    nowMs: Long,
    selectedShortcuts: List<PlayContext> = emptyList(),
    userPlaylists: List<UserPlaylist> = emptyList(),
    smartPlaylists: List<SmartPlaylist> = emptyList(),
): HomeFeed = HomeFeed.build(
    library = library,
    history = history,
    favoriteIds = favoriteIds,
    nowMs = nowMs,
    ratings = emptyMap(),
    selectedShortcuts = selectedShortcuts,
    userPlaylists = userPlaylists,
    smartPlaylists = smartPlaylists,
)
