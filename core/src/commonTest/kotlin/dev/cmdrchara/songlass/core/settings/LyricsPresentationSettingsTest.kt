package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.lyrics.LyricsPresentation
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsPresentationSettingsTest {
    @Test fun defaultsPreservePlaybackAndDoNotWritePreferences() {
        val store = InMemoryKeyValueStore()
        assertEquals(LyricsPresentation(), AppSettings(store).state.value.lyricsPresentation)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun adjustmentsPersistClampAndKeepOtherPreferences() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setShowPlayerRating(false)
        settings.setLyricsOffset(Int.MAX_VALUE)
        settings.setLyricsTextSize(Int.MIN_VALUE)
        settings.setLyricsKeepScreenOn(true)
        assertEquals(LyricsPresentation(5_000, 80, true), AppSettings(store).state.value.lyricsPresentation)
        assertFalse(settings.state.value.showPlayerRating)
        settings.setLyricsOffset(-1_026)
        settings.setLyricsTextSize(123)
        assertEquals(LyricsPresentation(-1_050, 125, true), AppSettings(store).state.value.lyricsPresentation)
    }

    @Test fun invalidStoredValuesFallBackWithoutDestructiveMigration() {
        val store = InMemoryKeyValueStore().apply {
            putString(AppSettings.LYRICS_OFFSET_KEY, "5001")
            putString(AppSettings.LYRICS_TEXT_SIZE_KEY, "NaN")
            putString(AppSettings.LYRICS_KEEP_SCREEN_ON_KEY, "invalid")
        }
        val before = store.snapshot()
        assertEquals(LyricsPresentation(), AppSettings(store).state.value.lyricsPresentation)
        assertEquals(before, store.snapshot())
        store.putString(AppSettings.LYRICS_OFFSET_KEY, "51")
        store.putString(AppSettings.LYRICS_TEXT_SIZE_KEY, "121")
        assertEquals(LyricsPresentation(), AppSettings(store).state.value.lyricsPresentation)
    }

    @Test fun onlyPlaybackResetResetsLyricsPreferences() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setLyricsOffset(350)
        settings.setLyricsTextSize(140)
        settings.setLyricsKeepScreenOn(true)
        settings.resetToDefaults(SettingsArea.Appearance)
        assertEquals(LyricsPresentation(350, 140, true), settings.state.value.lyricsPresentation)
        settings.resetToDefaults(SettingsArea.Player)
        assertEquals(LyricsPresentation(), settings.state.value.lyricsPresentation)
        assertTrue(store.snapshot().keys.none { it.startsWith("settings.lyrics_") })
    }

    @Test fun backupPreviewDoesNotApplyLyricsUntilConfirmation() {
        val tracks = listOf(Track("track", "Title", "content://media/1"))
        val source = InMemoryKeyValueStore()
        AppSettings(source).apply {
            setLyricsOffset(-750); setLyricsTextSize(135); setLyricsKeepScreenOn(true)
        }
        val destination = InMemoryKeyValueStore()
        val settings = AppSettings(destination)
        val backup = DataBackup(destination) { 1_000L }
        val preview = backup.preview(DataBackup(source) { 1_000L }.export(tracks).text, tracks)
        assertEquals(LyricsPresentation(), settings.state.value.lyricsPresentation)
        assertTrue(destination.snapshot().isEmpty())
        backup.apply(preview)
        settings.reload()
        assertEquals(LyricsPresentation(-750, 135, true), settings.state.value.lyricsPresentation)
    }

    @Test fun restoreRejectsLatePresentationEdits() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val settings = AppSettings(store, gate)
        gate.restoring {
            settings.setLyricsOffset(500)
            settings.setLyricsTextSize(160)
            settings.setLyricsKeepScreenOn(true)
            assertEquals(LyricsPresentation(), settings.state.value.lyricsPresentation)
            assertTrue(store.snapshot().isEmpty())
        }
        settings.setLyricsOffset(500)
        assertEquals(500, settings.state.value.lyricsPresentation.offsetMs)
    }

    @Test fun searchFindsLocalLyricsOptionsEvenWithOnlineProvidersUnavailable() {
        val index = SettingsSearchIndex(SettingsTarget.entries.map { SettingsSearchDocument(it, it.name, "Settings") })
        for ((query, target) in listOf(
            "ritardo testi" to SettingsTarget.LyricsOffset,
            "lyrics offset" to SettingsTarget.LyricsOffset,
            "dimensione testi" to SettingsTarget.LyricsSize,
            "lyrics zoom" to SettingsTarget.LyricsSize,
            "schermo acceso testi" to SettingsTarget.LyricsKeepScreenOn,
            "keep screen on" to SettingsTarget.LyricsKeepScreenOn,
        )) assertEquals(target, index.search(query, false, supportsOnlineLyrics = false).first(), query)
    }
}
