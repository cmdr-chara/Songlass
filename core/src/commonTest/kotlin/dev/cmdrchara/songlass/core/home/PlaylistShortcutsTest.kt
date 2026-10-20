package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.history.RecentContext
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.playlists.SmartPlaylist
import dev.cmdrchara.songlass.core.playlists.SmartRules
import dev.cmdrchara.songlass.core.playlists.UserPlaylist
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.*

class PlaylistShortcutsTest {
    private val a = track("a", "Alpha").copy(folder = "Music", genre = "Rock")
    private val b = track("b", "Beta").copy(folder = "Music", genre = "Pop")
    private val library = MusicLibrary(listOf(a, b))
    private val personal = UserPlaylist("same-id", "Road trip", listOf("b", "a", "missing"), 1)
    private val smart = SmartPlaylist("same-id", "Rock mix", SmartRules(genre = "rock"))
    private val userContext = PlayContext.UserPlaylist(personal.id)
    private val smartContext = PlayContext.SmartPlaylist(smart.id)
    private fun saved(user: UserPlaylist = personal, intelligent: SmartPlaylist = smart, lib: MusicLibrary = library, favorites: Set<String> = emptySet()) =
        HomeShortcuts.savedPlaylists(lib, listOf(user), listOf(intelligent), HistorySnapshot(), favorites, 100)

    @Test fun identitiesAreDistinctAndPickerFindsBothPlaylistTypes() {
        val entries = saved()
        assertEquals(listOf(userContext, smartContext), entries.map(HomeShortcuts::contextOf))
        val options = HomeShortcuts.options(library, entries)
        assertEquals(listOf(userContext, smartContext), options.take(2).map { it.context })
        assertEquals(listOf(userContext), HomeShortcuts.filter(options, "ROAD", ShortcutKind.UserPlaylist).map { it.context })
        assertEquals(listOf(smartContext), HomeShortcuts.filter(options, "rock", ShortcutKind.SmartPlaylist).map { it.context })
        assertEquals("Road trip", HomeShortcuts.describe(library, userContext, entries)?.title)
        assertEquals(entries[0], HomeShortcuts.resolve(library, userContext, emptyList(), entries))
        assertEquals(listOf("b", "a"), (entries[0] as HomeItem.UserPlaylistItem).tracks.map { it.id })
    }

    @Test fun chosenPlaylistsStayFirstAndRecentContextsDoNotDuplicateThem() {
        val history = HistorySnapshot(recentContexts = listOf(RecentContext(smartContext, 90), RecentContext(userContext, 80)))
        val feed = HomeFeed.build(library, history, emptyList(), 100, listOf(userContext, smartContext, userContext), listOf(personal), listOf(smart))
        val contexts = feed.shortcuts.map(HomeShortcuts::contextOf)
        assertEquals(listOf(userContext, smartContext), contexts.take(2))
        assertEquals(1, contexts.count { it == userContext })
        assertEquals(1, contexts.count { it == smartContext })
        assertEquals(listOf(smartContext, userContext), feed.recentlyPlayed.map(HomeShortcuts::contextOf))
    }

    @Test fun namesCoversAndMembershipFollowVisibleLibraryAndRules() {
        val updated = saved(personal.copy(name = "Renamed", trackIds = listOf("a", "b")), smart.copy(rules = SmartRules(favoritesOnly = true)), favorites = setOf("b"))
        assertEquals("Renamed", (updated[0] as HomeItem.UserPlaylistItem).playlist.name)
        assertEquals(a, (updated[0] as HomeItem.UserPlaylistItem).tracks.first())
        assertEquals(listOf(b), (updated[1] as HomeItem.SmartPlaylistItem).tracks)
        val filtered = saved(lib = MusicLibrary(listOf(a)))
        assertEquals(listOf(a), (filtered[0] as HomeItem.UserPlaylistItem).tracks)
        assertEquals(listOf(a), (filtered[1] as HomeItem.SmartPlaylistItem).tracks)
    }

    @Test fun emptyPlaylistsRemainOpenableAndDeletedReferencesAreRetained() {
        val empty = saved(personal.copy(trackIds = emptyList()), smart.copy(rules = SmartRules(genre = "Absent")))
        assertNotNull(HomeShortcuts.resolve(library, userContext, emptyList(), empty))
        assertTrue((empty[1] as HomeItem.SmartPlaylistItem).tracks.isEmpty())
        val store = InMemoryKeyValueStore();val customization = HomeCustomization(store)
        customization.addShortcut(userContext);customization.addShortcut(smartContext)
        val feed = HomeFeed.build(library, HistorySnapshot(), emptyList(), 100, customization.state.value.shortcuts)
        assertTrue(feed.shortcuts.none { it is HomeItem.UserPlaylistItem || it is HomeItem.SmartPlaylistItem })
        assertEquals(listOf(userContext, smartContext), HomeCustomization(store).state.value.shortcuts)
        val back = HomeFeed.build(library, HistorySnapshot(), emptyList(), 100, customization.state.value.shortcuts, listOf(personal), listOf(smart))
        assertEquals(listOf(userContext, smartContext), back.shortcuts.take(2).map(HomeShortcuts::contextOf))
    }
}
