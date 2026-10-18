package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PinsTest {
    private val library = MusicLibrary(
        listOf(
            track("a1", artist = "A", album = "One").copy(folder = "Music/Chill"),
            track("a2", artist = "A", album = "One").copy(folder = "Music/Chill"),
            track("b1", artist = "B", album = null).copy(folder = "Music/Gym/Cardio"),
        ),
    )

    @Test
    fun togglePinsAtTheFrontAndUnpins() {
        val pins = Pins()
        assertTrue(pins.toggle(PlayContext.Artist("A")))
        assertTrue(pins.toggle(PlayContext.Album("One", "A")))
        assertEquals(listOf(PlayContext.Album("One", "A"), PlayContext.Artist("A")), pins.contexts.value)
        assertFalse(pins.toggle(PlayContext.Artist("A")))
        assertFalse(pins.isPinned(PlayContext.Artist("A")))
    }

    @Test
    fun pinsSurviveRecreationAndAreCapped() {
        val store = InMemoryKeyValueStore()
        repeat(Pins.MAX + 2) { Pins(store).toggle(PlayContext.Playlist("Music/$it")) }
        val restored = Pins(store).contexts.value
        assertEquals(Pins.MAX, restored.size)
        assertEquals(PlayContext.Playlist("Music/${Pins.MAX + 1}"), restored.first())
    }

    @Test
    fun resolveFindsAlbumsFoldersArtistsAndSkipsMissing() {
        val pins = Pins()
        pins.toggle(PlayContext.Artist("Gone"))
        pins.toggle(PlayContext.Playlist("Music/Gym"))
        pins.toggle(PlayContext.Album("One", "A"))
        val items = pins.resolve(library)
        assertEquals(2, items.size)
        assertIs<PinnedItem.AlbumPin>(items[0])
        // An intermediate folder (no files of its own) can be pinned too.
        assertEquals("Gym", (items[1] as PinnedItem.FolderPin).folder.name)
    }
}
