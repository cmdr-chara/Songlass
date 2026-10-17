package dev.cmdrchara.songlass.core.library

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
    fun initialIdsAreDeduplicated() {
        assertEquals(listOf("a", "b"), Favorites(listOf("a", "b", "a")).ids.value)
    }

    @Test
    fun tracksInLibraryAreNewestFirstAndSkipMissing() {
        val library = MusicLibrary(listOf(track("a"), track("b"), track("c")))
        val favorites = Favorites(listOf("a", "gone", "c"))
        assertEquals(listOf("c", "a"), favorites.tracksIn(library).map { it.id })
    }
}
