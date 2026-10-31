package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerRatingSettingsTest {
    @Test
    fun ratingVisibilityDefaultsOnAndPersistsWithoutChangingRatings() {
        val store = InMemoryKeyValueStore().apply { putString("ratings", "preserve saved ratings") }
        val original = store.snapshot()
        val settings = AppSettings(store)
        assertTrue(settings.state.value.showPlayerRating)
        assertEquals(original, store.snapshot(), "Reading a new preference must not migrate user data")
        settings.setShowPlayerRating(false)
        assertFalse(AppSettings(store).state.value.showPlayerRating)
        assertEquals("preserve saved ratings", store.getString("ratings"))
        store.putString(AppSettings.SHOW_PLAYER_RATING_KEY, "unrecognised")
        settings.reload()
        assertTrue(settings.state.value.showPlayerRating)
        assertEquals("unrecognised", store.getString(AppSettings.SHOW_PLAYER_RATING_KEY))
    }

    @Test
    fun onlyPlayerResetRestoresVisibleRating() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setShowPlayerRating(false)
        settings.resetToDefaults(SettingsArea.Appearance)
        assertFalse(settings.state.value.showPlayerRating)
        settings.resetToDefaults(SettingsArea.Player)
        assertTrue(settings.state.value.showPlayerRating)
        assertEquals(null, store.getString(AppSettings.SHOW_PLAYER_RATING_KEY))
    }

    @Test
    fun backupCarriesVisibilityWithoutChangingTheDestinationUntilApplied() {
        val tracks = listOf(Track("track", "Title", "content://media/1"))
        val source = InMemoryKeyValueStore()
        AppSettings(source).setShowPlayerRating(false)
        val destination = InMemoryKeyValueStore()
        val settings = AppSettings(destination)
        val backup = DataBackup(destination) { 1_000L }
        val preview = backup.preview(DataBackup(source) { 1_000L }.export(tracks).text, tracks)
        assertTrue(settings.state.value.showPlayerRating)
        assertTrue(destination.snapshot().isEmpty())
        backup.apply(preview)
        settings.reload()
        assertFalse(settings.state.value.showPlayerRating)
    }

    @Test
    fun ratingVisibilitySearchDoesNotReplaceTheLibraryRatingFilter() {
        val index = SettingsSearchIndex(SettingsTarget.entries.map {
            SettingsSearchDocument(it, it.name, "Settings")
        })
        for (query in listOf("show rating", "mostra valutazione", "hide rating", "voto player")) {
            assertEquals(SettingsTarget.ShowPlayerRating, index.search(query, true).first(), query)
        }
        assertEquals(SettingsTarget.MinimumRating, index.search("minimum rating", true).first())
        assertEquals(SettingsTarget.MinimumRating, index.search("valutazione minima", true).first())
    }
}
