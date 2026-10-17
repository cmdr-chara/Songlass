package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.putStringList
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoritesTest {

    @Test
    fun toggleAddsThenRemoves() {
        val favorites = Favorites()
        assertTrue(favorites.toggle("a"))
        assertTrue(favorites.isFavorite("a"))
        assertFalse(favorites.toggle("a"))
        assertFalse(favorites.isFavorite("a"))
        assertEquals(emptyList(), favorites.ids.value)
    }

    @Test
    fun favoritesSurviveANewInstanceOnTheSameStore() {
        val store = InMemoryKeyValueStore()
        Favorites(store).apply { toggle("a"); toggle("b"); toggle("a"); toggle("c") }
        assertEquals(listOf("b", "c"), Favorites(store).ids.value)
    }

    @Test
    fun storedDuplicatesAreIgnored() {
        val store = InMemoryKeyValueStore().apply { putStringList("favorites", listOf("a", "b", "a")) }
        assertEquals(listOf("a", "b"), Favorites(store).ids.value)
    }

    @Test
    fun tracksInLibraryAreNewestFirstAndSkipMissing() {
        val library = MusicLibrary(listOf(track("a"), track("b"), track("c")))
        val store = InMemoryKeyValueStore().apply { putStringList("favorites", listOf("a", "gone", "c")) }
        val favorites = Favorites(store)
        assertEquals(listOf("c", "a"), favorites.tracksIn(library).map { it.id })
    }
}
