package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeCustomizationTest {
    private val store = InMemoryKeyValueStore()
    private val keys = listOf("home.sections", "home.hidden_sections", "home.shortcuts")

    private fun customization() = HomeCustomization(store)

    private fun artist(n: Int) = PlayContext.Artist("Artist $n")

    @Test
    fun theDefaultIsTheOriginalHomeAndWritesNothing() {
        val home = customization()
        assertTrue(home.state.value.isDefault)
        assertEquals(
            listOf("statistics", "on_this_day", "shortcuts", "for_you", "recently_added", "change_of_pace", "most_played", "playlists"),
            home.state.value.visibleSections.map { it.id },
        )
        assertEquals(8, HomeSection.entries.size)
        assertTrue(keys.all { store.getString(it) == null })
    }

    @Test
    fun theOrderIsPersistedAndSurvivesARestart() {
        customization().apply {
            moveSection(HomeSection.Playlists, -7)
            moveSection(HomeSection.MostPlayed, -1)
        }
        val restarted = customization().state.value
        assertEquals(
            listOf(
                HomeSection.Playlists, HomeSection.Statistics, HomeSection.OnThisDay, HomeSection.Shortcuts,
                HomeSection.ForYou, HomeSection.RecentlyAdded, HomeSection.MostPlayed, HomeSection.ChangeOfPace,
            ),
            restarted.order,
        )
    }

    @Test
    fun movingStopsAtTheEnds() {
        val home = customization()
        home.moveSection(HomeSection.Statistics, -1)
        assertTrue(home.state.value.isDefault)
        home.moveSection(HomeSection.Playlists, 1)
        assertTrue(home.state.value.isDefault)
        home.moveSection(HomeSection.Statistics, 100)
        assertEquals(HomeSection.Statistics, home.state.value.order.last())
        assertEquals(8, home.state.value.order.size)
    }

    @Test
    fun visibilityIsPersistedAndHidingEverythingIsAllowed() {
        customization().apply {
            setSectionVisible(HomeSection.ForYou, false)
            setSectionVisible(HomeSection.Statistics, false)
        }
        val restarted = customization().state.value
        assertEquals(setOf(HomeSection.ForYou, HomeSection.Statistics), restarted.hidden)
        assertFalse(restarted.isVisible(HomeSection.ForYou))
        assertEquals(6, restarted.visibleSections.size)

        val home = customization()
        HomeSection.entries.forEach { home.setSectionVisible(it, false) }
        assertTrue(home.state.value.visibleSections.isEmpty())
        assertEquals(8, home.state.value.order.size)
        home.setSectionVisible(HomeSection.Playlists, true)
        assertEquals(listOf(HomeSection.Playlists), home.state.value.visibleSections)
    }

    @Test
    fun hiddenSectionsKeepTheirPlaceInTheOrder() {
        val home = customization()
        home.setSectionVisible(HomeSection.Shortcuts, false)
        home.moveSection(HomeSection.ForYou, -2)
        home.setSectionVisible(HomeSection.Shortcuts, true)
        // For you moved up two places; the section hidden meanwhile kept its slot in the order.
        assertEquals(HomeSection.ForYou, home.state.value.order[1])
        assertEquals(HomeSection.Shortcuts, home.state.value.order[3])
    }

    @Test
    fun invalidPersistedSectionsAreNormalized() {
        store.putStringList("home.sections", listOf("bogus", "playlists", "playlists", "statistics", ""))
        store.putStringList("home.hidden_sections", listOf("nope", "statistics", "statistics", "for_you"))
        val snapshot = customization().state.value
        assertEquals(
            listOf(
                HomeSection.Playlists, HomeSection.Statistics, HomeSection.OnThisDay, HomeSection.Shortcuts,
                HomeSection.ForYou, HomeSection.RecentlyAdded, HomeSection.ChangeOfPace, HomeSection.MostPlayed,
            ),
            snapshot.order,
        )
        assertEquals(setOf(HomeSection.Statistics, HomeSection.ForYou), snapshot.hidden)
    }

    @Test
    fun garbageInEveryKeyFallsBackToTheDefault() {
        store.putString("home.sections", "\u001F\u001F")
        store.putString("home.hidden_sections", "x")
        store.putString("home.shortcuts", "not a context\u001Fplaylist")
        assertTrue(customization().state.value.isDefault)
    }

    @Test
    fun shortcutsKeepTheirOrderAndAreCappedAtSix() {
        val home = customization()
        (1..6).forEach { assertTrue(home.addShortcut(artist(it))) }
        assertFalse(home.addShortcut(artist(7)))
        assertFalse(home.addShortcut(artist(3)))
        assertEquals((1..6).map(::artist), home.state.value.shortcuts)
        assertEquals((1..6).map(::artist), customization().state.value.shortcuts)
    }

    @Test
    fun aRemovalFreesASlotAndShortcutsCanBeReordered() {
        val home = customization()
        (1..6).forEach { home.addShortcut(artist(it)) }
        home.removeShortcut(artist(2))
        assertTrue(home.addShortcut(artist(9)))
        home.moveShortcut(artist(9), -10)
        home.moveShortcut(artist(1), 1)
        assertEquals(listOf(9, 3, 1, 4, 5, 6), home.state.value.shortcuts.map { (it as PlayContext.Artist).name!!.removePrefix("Artist ").toInt() })
        home.moveShortcut(artist(6), 5)
        assertEquals(artist(6), home.state.value.shortcuts.last())
        home.moveShortcut(artist(77), 1)
        assertEquals(6, home.state.value.shortcuts.size)
    }

    @Test
    fun anyStoredIdentityKindSurvivesARestart() {
        val contexts = listOf(
            PlayContext.Album("Title", "Someone"),
            PlayContext.Album("Untitled artist", null),
            PlayContext.Playlist("Music/Gym"),
            PlayContext.Artist(null),
            PlayContext.Favorites,
        )
        customization().apply { contexts.forEach(::addShortcut) }
        assertEquals(contexts, customization().state.value.shortcuts)
    }

    @Test
    fun moreThanSixPersistedShortcutsAreTruncatedAndDeduplicatedOnLoad() {
        store.putStringList("home.shortcuts", (1..4).map { artist(it).key } + artist(2).key + (5..9).map { artist(it).key })
        assertEquals((1..6).map(::artist), customization().state.value.shortcuts)
    }

    @Test
    fun savedShortcutsAreNeverDroppedBecauseOfTheLibrary() {
        // The customization knows nothing about the library: a hidden or missing album stays saved.
        val gone = PlayContext.Album("Gone", "Nobody")
        val home = customization()
        home.addShortcut(gone)
        home.addShortcut(PlayContext.Favorites)
        home.setSectionVisible(HomeSection.Playlists, false)
        assertEquals(listOf(gone, PlayContext.Favorites), customization().state.value.shortcuts)
    }

    @Test
    fun resetRestoresEveryDefaultAndRemovesTheKeys() {
        val home = customization()
        home.moveSection(HomeSection.MostPlayed, -5)
        home.setSectionVisible(HomeSection.OnThisDay, false)
        home.addShortcut(artist(1))
        assertTrue(keys.all { store.getString(it) != null })
        home.reset()
        assertTrue(home.state.value.isDefault)
        assertTrue(keys.all { store.getString(it) == null })
        assertTrue(customization().state.value.isDefault)
    }

    @Test
    fun partialResetsOnlyTouchTheirOwnPart() {
        val home = customization()
        home.moveSection(HomeSection.MostPlayed, -5)
        home.setSectionVisible(HomeSection.OnThisDay, false)
        home.addShortcut(artist(1))
        home.resetSections()
        assertEquals(HomeSection.DEFAULT_ORDER, home.state.value.order)
        assertTrue(home.state.value.hidden.isEmpty())
        assertEquals(listOf(artist(1)), home.state.value.shortcuts)
        home.moveSection(HomeSection.Playlists, -1)
        home.resetShortcuts()
        assertTrue(home.state.value.shortcuts.isEmpty())
        assertEquals(HomeSection.Playlists, home.state.value.order[6])
    }

    @Test
    fun returningToTheDefaultRemovesTheStoredValues() {
        val home = customization()
        home.moveSection(HomeSection.Playlists, -1)
        assertTrue(store.getString("home.sections") != null)
        home.moveSection(HomeSection.Playlists, 1)
        assertNull(store.getString("home.sections"))
        home.setSectionVisible(HomeSection.ForYou, false)
        home.setSectionVisible(HomeSection.ForYou, true)
        assertNull(store.getString("home.hidden_sections"))
    }

    @Test
    fun reloadPicksUpRestoredValuesAndDefaultsWhenTheyAreGone() {
        val home = customization()
        val restoredElsewhere = customization()
        restoredElsewhere.moveSection(HomeSection.Playlists, -7)
        restoredElsewhere.addShortcut(artist(4))
        assertTrue(home.state.value.isDefault)

        home.reload()
        assertEquals(HomeSection.Playlists, home.state.value.order.first())
        assertEquals(listOf(artist(4)), home.state.value.shortcuts)

        // A backup that has no home settings removes them, as a restore of an older backup would.
        keys.forEach { store.putString(it, null) }
        home.reload()
        assertTrue(home.state.value.isDefault)
    }

    @Test
    fun newSectionsMissingFromASavedOrderGoToTheEnd() {
        store.putStringList("home.sections", listOf("playlists", "statistics", "for_you"))
        assertEquals(
            listOf(
                HomeSection.Playlists, HomeSection.Statistics, HomeSection.ForYou, HomeSection.OnThisDay,
                HomeSection.Shortcuts, HomeSection.RecentlyAdded, HomeSection.ChangeOfPace, HomeSection.MostPlayed,
            ),
            customization().state.value.order,
        )
    }
}
