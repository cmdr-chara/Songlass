package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.storage.FaultingPreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HomeCustomizationPersistenceTest {
    private val first = PlayContext.Artist("First artist")
    private val second = PlayContext.Artist("Second artist")

    @Test fun editsPublishOnlyAfterTheWholeSaveAndLeaveThePreviousLayoutOnFailure() {
        val actions: List<(HomeCustomization) -> Unit> = listOf(
            { it.moveSection(HomeSection.Playlists, 1) },
            { it.setSectionVisible(HomeSection.ForYou, true) },
            { assertTrue(it.addShortcut(PlayContext.Favorites)) },
            { it.removeShortcut(first) },
            { it.moveShortcut(second, -1) },
        )
        for (action in actions) {
            val store = FaultingPreferenceStore()
            val home = customized(store)
            val before = home.state.value
            val saved = store.snapshot()
            store.afterWrite = { assertSame(before, home.state.value) }
            store.failAfterChanges(1)
            assertFailsWith<IllegalStateException> { action(home) }
            assertEquals(1, store.changedBeforeFailure)
            assertSame(before, home.state.value)
            assertEquals(saved, store.snapshot())
            assertEquals(before, HomeCustomization(store).state.value)
            action(home)
            store.afterWrite = null
            assertNotEquals(before, home.state.value)
            assertEquals(home.state.value, HomeCustomization(store).state.value)
        }
    }

    @Test fun fullResetRollsBackRemovedDefaultsAndRetryReallyRemovesAllThreeKeys() {
        val store = FaultingPreferenceStore()
        val home = customized(store)
        val before = home.state.value
        val saved = store.snapshot()
        store.failAfterChanges(2)
        assertFailsWith<IllegalStateException> { home.reset() }
        assertEquals(2, store.changedBeforeFailure)
        assertSame(before, home.state.value)
        assertEquals(saved, store.snapshot())
        assertEquals(before, HomeCustomization(store).state.value)
        home.reset()
        assertTrue(home.state.value.isDefault)
        for (key in listOf("home.sections", "home.hidden_sections", "home.shortcuts")) assertNull(store.getString(key))
        assertEquals(mapOf("foreign.preference" to "keep"), store.snapshot())
        assertTrue(HomeCustomization(store).state.value.isDefault)
    }

    @Test fun partialResetFailurePreservesBothSectionsAndShortcuts() {
        for (sections in listOf(true, false)) {
            val store = FaultingPreferenceStore()
            val home = customized(store)
            val before = home.state.value
            val saved = store.snapshot()
            fun reset() { if (sections) home.resetSections() else home.resetShortcuts() }
            store.failAfterChanges(1)
            assertFailsWith<IllegalStateException> { reset() }
            assertSame(before, home.state.value)
            assertEquals(saved, store.snapshot())
            assertEquals(before, HomeCustomization(store).state.value)
            reset()
            if (sections) {
                assertEquals(before.shortcuts, home.state.value.shortcuts)
                assertNull(store.getString("home.sections"))
                assertNull(store.getString("home.hidden_sections"))
            } else {
                assertEquals(before.order, home.state.value.order)
                assertEquals(before.hidden, home.state.value.hidden)
                assertNull(store.getString("home.shortcuts"))
            }
            assertEquals(home.state.value, HomeCustomization(store).state.value)
        }
    }

    private fun customized(store: FaultingPreferenceStore): HomeCustomization = HomeCustomization(store).apply {
        store.putString("foreign.preference", "keep")
        moveSection(HomeSection.Playlists, -7)
        setSectionVisible(HomeSection.ForYou, false)
        addShortcut(first)
        addShortcut(second)
    }
}
