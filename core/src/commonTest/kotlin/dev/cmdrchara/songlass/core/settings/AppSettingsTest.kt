package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.scrobble.LastFmStorageKeys
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsTest {
    @Test
    fun albumLayoutPersistsAndUnknownValuesKeepTheExistingGridDefault() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        assertEquals(AlbumLayout.Grid, settings.state.value.albumLayout)
        settings.setAlbumLayout(AlbumLayout.List)
        assertEquals(AlbumLayout.List, AppSettings(store).state.value.albumLayout)
        store.putString(AppSettings.ALBUM_LAYOUT_KEY, "unknown")
        settings.reload()
        assertEquals(AlbumLayout.Grid, settings.state.value.albumLayout)
    }

    @Test
    fun playCountingRulePersistsWithoutRewritingHistory() {
        val store = InMemoryKeyValueStore().apply { putString("history.counts", "existing history") }
        val settings = AppSettings(store)
        assertEquals(dev.cmdrchara.songlass.core.history.PlayCountRule.ThirtySecondsOrHalf, settings.state.value.playCountRule)
        settings.setPlayCountRule(dev.cmdrchara.songlass.core.history.PlayCountRule.NearlyAll)
        assertEquals(dev.cmdrchara.songlass.core.history.PlayCountRule.NearlyAll, AppSettings(store).state.value.playCountRule)
        assertEquals("existing history", store.getString("history.counts"))
        store.putString(AppSettings.PLAY_COUNT_RULE_KEY, "unknown future value")
        settings.reload()
        assertEquals(dev.cmdrchara.songlass.core.history.PlayCountRule.ThirtySecondsOrHalf, settings.state.value.playCountRule)
        assertEquals("unknown future value", store.getString(AppSettings.PLAY_COUNT_RULE_KEY))
    }

    @Test
    fun perTrackResumeStartsOffAndThresholdPersistsIndependently() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        assertEquals(false, settings.state.value.resumePerTrack)
        assertEquals(600_000L, settings.state.value.longTrackThresholdMs)
        assertEquals(emptyMap(), store.snapshot())
        settings.setResumePerTrack(true)
        settings.setLongTrackThreshold(1_800_000L)
        assertEquals(true, AppSettings(store).state.value.resumePerTrack)
        assertEquals(1_800_000L, AppSettings(store).state.value.longTrackThresholdMs)
        kotlin.test.assertFailsWith<IllegalArgumentException> { settings.setLongTrackThreshold(42L) }
        assertEquals(1_800_000L, settings.state.value.longTrackThresholdMs)
        store.putString("settings.long_track_threshold_ms", "bad")
        settings.reload()
        assertEquals(600_000L, settings.state.value.longTrackThresholdMs)
        assertEquals(true, settings.state.value.resumePerTrack)
    }

    @Test
    fun existingAutomaticDurationIsPreservedWithoutEnablingManualOrWritingMigrationKeys() {
        val store = InMemoryKeyValueStore().apply {
            putString("settings.crossfade_duration_ms", "1250")
            putString("settings.replay_gain", "album")
        }
        val before = store.snapshot()
        val settings = AppSettings(store)
        assertEquals(1_250L, settings.state.value.crossfadeDurationMs)
        assertEquals(0L, settings.state.value.manualCrossfadeDurationMs)
        assertEquals(before, store.snapshot())
    }

    @Test
    fun manualDurationPersistsIndependentlyAndReloads() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setCrossfadeDuration(6_000L)
        settings.setManualCrossfadeDuration(1_250L)
        assertEquals("1250", store.getString("settings.manual_crossfade_duration_ms"))
        assertEquals(1_250L, AppSettings(store).state.value.manualCrossfadeDurationMs)
        assertEquals(6_000L, AppSettings(store).state.value.crossfadeDurationMs)
        settings.setCrossfadeDuration(0L)
        assertEquals(1_250L, settings.state.value.manualCrossfadeDurationMs)
        store.putString("settings.manual_crossfade_duration_ms", "250")
        settings.reload()
        assertEquals(250L, settings.state.value.manualCrossfadeDurationMs)
        settings.setManualCrossfadeDuration(0L)
        assertEquals(0L, AppSettings(store).state.value.manualCrossfadeDurationMs)
    }

    @Test
    fun invalidManualDurationsFallBackWithoutChangingAutomaticDurationOrStoredData() {
        val store = InMemoryKeyValueStore().apply { putString("settings.crossfade_duration_ms", "4000") }
        for (value in listOf("invalid", "-1", "249", "12001", "9223372036854775808")) {
            store.putString("settings.manual_crossfade_duration_ms", value)
            val settings = AppSettings(store)
            assertEquals(0L, settings.state.value.manualCrossfadeDurationMs)
            assertEquals(4_000L, settings.state.value.crossfadeDurationMs)
            assertEquals(value, store.getString("settings.manual_crossfade_duration_ms"))
        }
        val settings = AppSettings(store)
        settings.setManualCrossfadeDuration(1_250L)
        for (value in listOf(-1L, 1L, 249L, 12_001L)) {
            kotlin.test.assertFailsWith<IllegalArgumentException> { settings.setManualCrossfadeDuration(value) }
            assertEquals(1_250L, settings.state.value.manualCrossfadeDurationMs)
            assertEquals("1250", store.getString("settings.manual_crossfade_duration_ms"))
        }
    }

    @Test
    fun amllDefaultsOnAndPersistsBothChoicesAndReloads() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        assertEquals(true, settings.state.value.amllEnabled)
        settings.setAmllEnabled(false)
        assertEquals("false", store.getString("settings.amll_enabled"))
        assertEquals(false, settings.state.value.amllEnabled)
        assertEquals(false, AppSettings(store).state.value.amllEnabled)
        settings.setAmllEnabled(true)
        assertEquals(true, AppSettings(store).state.value.amllEnabled)
        store.putString("settings.amll_enabled", "false")
        settings.reload()
        assertEquals(false, settings.state.value.amllEnabled)
        store.putString("settings.amll_enabled", "invalid")
        settings.reload()
        assertEquals(true, settings.state.value.amllEnabled)
    }

    @Test
    fun crossfadePersistsAndReloadRefreshesExistingObservers() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setCrossfadeDuration(1_250L)
        assertEquals("1250", store.getString("settings.crossfade_duration_ms"))
        assertEquals(1_250L, AppSettings(store).state.value.crossfadeDurationMs)
        store.putString("settings.crossfade_duration_ms", "12000")
        settings.reload()
        assertEquals(12_000L, settings.state.value.crossfadeDurationMs)
        store.putString("settings.crossfade_duration_ms", "12001")
        settings.reload()
        assertEquals(0L, settings.state.value.crossfadeDurationMs)
        kotlin.test.assertFailsWith<IllegalArgumentException> { settings.setCrossfadeDuration(-1L) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { settings.setCrossfadeDuration(12_001L) }
        assertEquals(0L, settings.state.value.crossfadeDurationMs)
    }

    @Test
    fun emptyStoreUsesSystemAppearanceAndDefaultAccessibility() {
        assertEquals(AppPreferences(), AppSettings(InMemoryKeyValueStore()).state.value)
    }

    @Test
    fun preferencesSurviveRecreation() {
        val store = InMemoryKeyValueStore()
        AppSettings(store).apply {
            setAppearance(AppearanceMode.Dark)
            setReduceMotion(true)
            setReduceTransparency(true)
        }

        assertEquals(
            AppPreferences(AppearanceMode.Dark, reduceMotion = true, reduceTransparency = true),
            AppSettings(store).state.value,
        )
    }

    @Test
    fun appearanceUsesStableStoredValues() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        val values = mapOf(
            AppearanceMode.System to "system",
            AppearanceMode.Light to "light",
            AppearanceMode.Dark to "dark",
        )

        for ((appearance, storedValue) in values) {
            settings.setAppearance(appearance)
            assertEquals(storedValue, store.getString("settings.appearance"))
            assertEquals(appearance, AppSettings(store).state.value.appearance)
        }
    }

    @Test
    fun resettingPreferencesToDefaultsPersists() {
        val store = InMemoryKeyValueStore()
        AppSettings(store).apply {
            setAppearance(AppearanceMode.Dark)
            setReduceMotion(true)
            setReduceTransparency(true)
            setAppearance(AppearanceMode.System)
            setReduceMotion(false)
            setReduceTransparency(false)
        }

        assertEquals(AppPreferences(), AppSettings(store).state.value)
    }

    @Test
    fun unknownAppearanceFallsBackWithoutChangingAccessibility() {
        for (storedValue in listOf("", "sepia", "DARK", "dark ")) {
            val store = InMemoryKeyValueStore().apply {
                putString("settings.appearance", storedValue)
                putString("settings.reduce_motion", "true")
                putString("settings.reduce_transparency", "true")
            }

            assertEquals(
                AppPreferences(reduceMotion = true, reduceTransparency = true),
                AppSettings(store).state.value,
            )
        }
    }

    @Test
    fun malformedBooleanFallsBackIndependently() {
        for (storedValue in listOf("", "1", "TRUE", "true ", "enabled")) {
            val store = InMemoryKeyValueStore().apply {
                putString("settings.appearance", "light")
                putString("settings.reduce_motion", storedValue)
                putString("settings.reduce_transparency", "true")
            }
            assertEquals(
                AppPreferences(AppearanceMode.Light, reduceMotion = false, reduceTransparency = true),
                AppSettings(store).state.value,
            )

            store.putString("settings.reduce_motion", "true")
            store.putString("settings.reduce_transparency", storedValue)
            assertEquals(
                AppPreferences(AppearanceMode.Light, reduceMotion = true, reduceTransparency = false),
                AppSettings(store).state.value,
            )
        }
    }

    @Test
    fun changingEachSettingPreservesTheOthersAndUnrelatedData() {
        val store = InMemoryKeyValueStore().apply { putString("future.preference", "preserved") }
        Favorites(store).apply { toggle("track-a"); toggle("track-b") }
        val settings = AppSettings(store)

        settings.setReduceTransparency(true)
        assertEquals(AppPreferences(reduceTransparency = true), AppSettings(store).state.value)

        settings.setReduceMotion(true)
        assertEquals(
            AppPreferences(reduceMotion = true, reduceTransparency = true),
            AppSettings(store).state.value,
        )

        settings.setAppearance(AppearanceMode.Light)
        assertEquals(
            AppPreferences(AppearanceMode.Light, reduceMotion = true, reduceTransparency = true),
            AppSettings(store).state.value,
        )

        settings.setReduceTransparency(false)
        assertEquals(
            AppPreferences(AppearanceMode.Light, reduceMotion = true),
            AppSettings(store).state.value,
        )
        settings.setReduceMotion(false)
        assertEquals(AppPreferences(AppearanceMode.Light), AppSettings(store).state.value)

        assertEquals(listOf("track-a", "track-b"), Favorites(store).ids.value)
        assertEquals("preserved", store.getString("future.preference"))
    }

    @Test
    fun stateEmitsChangesAndSuppressesIdenticalValues() = runTest {
        val settings = AppSettings(InMemoryKeyValueStore())
        val observed = mutableListOf<AppPreferences>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            settings.state.collect { observed += it }
        }

        settings.setAppearance(AppearanceMode.Dark)
        settings.setReduceMotion(true)
        settings.setReduceTransparency(true)
        settings.setReduceTransparency(true)

        assertEquals(
            listOf(
                AppPreferences(),
                AppPreferences(AppearanceMode.Dark),
                AppPreferences(AppearanceMode.Dark, reduceMotion = true),
                AppPreferences(AppearanceMode.Dark, reduceMotion = true, reduceTransparency = true),
            ),
            observed,
        )
    }

    @Test
    fun artworkStyleDefaultsToFullBleedAndPersists() {
        val store = InMemoryKeyValueStore()
        assertEquals(ArtworkStyle.FullBleed, AppSettings(store).state.value.artworkStyle)
        AppSettings(store).setArtworkStyle(ArtworkStyle.Square)
        assertEquals(ArtworkStyle.Square, AppSettings(store).state.value.artworkStyle)
        AppSettings(store).setArtworkStyle(ArtworkStyle.FullBleed)
        assertEquals(ArtworkStyle.FullBleed, AppSettings(store).state.value.artworkStyle)
    }

    @Test
    fun unknownArtworkStyleFallsBackToDefault() {
        val store = InMemoryKeyValueStore().apply { putString("settings.artwork_style", "hologram") }
        assertEquals(ArtworkStyle.FullBleed, AppSettings(store).state.value.artworkStyle)
    }

    @Test
    fun interfaceStyleDefaultsToLiquidGlassAndPersists() {
        val store = InMemoryKeyValueStore()
        assertEquals(InterfaceStyle.LiquidGlass, AppSettings(store).state.value.interfaceStyle)
        AppSettings(store).setInterfaceStyle(InterfaceStyle.MaterialGlass)
        assertEquals(InterfaceStyle.MaterialGlass, AppSettings(store).state.value.interfaceStyle)
        store.putString("settings.interface_style", "holo")
        assertEquals(InterfaceStyle.LiquidGlass, AppSettings(store).state.value.interfaceStyle)
    }

    @Test
    fun searchInNavigationBarIsOffByDefaultAndPersists() {
        val store = InMemoryKeyValueStore()
        assertEquals(false, AppSettings(store).state.value.searchInNavigationBar)
        AppSettings(store).setSearchInNavigationBar(true)
        assertEquals(true, AppSettings(store).state.value.searchInNavigationBar)
        store.putString(AppSettings.SEARCH_IN_NAV_KEY, "not a boolean")
        assertEquals(false, AppSettings(store).state.value.searchInNavigationBar)
    }

    @Test
    fun resumeOnReconnectDefaultsToOffAndPersists() {
        val store = dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore()
        kotlin.test.assertFalse(AppSettings(store).state.value.resumeOnReconnect)
        AppSettings(store).setResumeOnReconnect(true)
        kotlin.test.assertTrue(AppSettings(store).state.value.resumeOnReconnect)
    }

    @Test
    fun replayGainDefaultsToOffAndPersists() {
        val store = InMemoryKeyValueStore()
        assertEquals(dev.cmdrchara.songlass.core.player.ReplayGainMode.Off, AppSettings(store).state.value.replayGain)
        AppSettings(store).setReplayGain(dev.cmdrchara.songlass.core.player.ReplayGainMode.Album)
        assertEquals(dev.cmdrchara.songlass.core.player.ReplayGainMode.Album, AppSettings(store).state.value.replayGain)
        store.putString("settings.replay_gain", "loud")
        assertEquals(dev.cmdrchara.songlass.core.player.ReplayGainMode.Off, AppSettings(store).state.value.replayGain)
    }

    @Test
    fun folderSortPersists() {
        val store = InMemoryKeyValueStore()
        AppSettings(store).setFolderSort(dev.cmdrchara.songlass.core.library.FolderSort.MostPlayed)
        assertEquals(dev.cmdrchara.songlass.core.library.FolderSort.MostPlayed, AppSettings(store).state.value.folderSort)
    }

    @Test
    fun minimumRatingIsAnyByDefaultPersistsAndIsRestoredWithTheLibraryPage() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        // A fresh install means "any rating", and an unreadable value keeps that meaning.
        assertEquals(0, settings.state.value.libraryFilter.minimumRating)
        store.putString(AppSettings.MIN_RATING_KEY, "seven")
        settings.reload()
        assertEquals(0, settings.state.value.libraryFilter.minimumRating)
        assertEquals("seven", store.getString(AppSettings.MIN_RATING_KEY))

        settings.setMinimumRating(4)
        assertEquals(4, settings.state.value.libraryFilter.minimumRating)
        assertEquals("4", store.getString(AppSettings.MIN_RATING_KEY))
        assertEquals(4, AppSettings(store).state.value.libraryFilter.minimumRating)
        kotlin.test.assertFailsWith<IllegalArgumentException> { settings.setMinimumRating(6) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { settings.setMinimumRating(-1) }
        assertEquals(4, settings.state.value.libraryFilter.minimumRating)

        // The Library page restores it to "any" and drops the stored value.
        settings.resetToDefaults(SettingsArea.Library)
        assertEquals(0, settings.state.value.libraryFilter.minimumRating)
        assertEquals(null, store.getString(AppSettings.MIN_RATING_KEY))
        assertEquals(settings.state.value, AppSettings(store).state.value)
        assertTrue(AppSettings.MIN_RATING_KEY.startsWith("library."), "travels in the .songlass backup")
    }

    private fun changedSettings(store: InMemoryKeyValueStore) = AppSettings(store).apply {
        setInterfaceStyle(InterfaceStyle.Miuix); setAppearance(AppearanceMode.Dark); setArtworkStyle(ArtworkStyle.Square)
        setReplayGain(dev.cmdrchara.songlass.core.player.ReplayGainMode.Album); setResumeOnReconnect(true)
        setCrossfadeDuration(4_000L); setManualCrossfadeDuration(400L); setResumePerTrack(true); setLongTrackThreshold(1_200_000L)
        setAmllEnabled(false); setLrclibEnabled(false); setReduceMotion(true); setReduceTransparency(true)
        setTextScale(1.15f); setBlurIntensity(BlurIntensity.High); setSearchInNavigationBar(true)
        setPlayCountRule(dev.cmdrchara.songlass.core.history.PlayCountRule.NearlyAll); setAlbumLayout(AlbumLayout.List)
        setMinimumDuration(30_000L); setFolderExcluded("Music/Voice", true)
        setMinimumRating(5)
        setTrackClickAction(TrackClickAction.AddToQueue); setQueueStart(QueueStart.Immediately); setQueueEnd(QueueEnd.Stop)
        setQueueAddOrder(QueueAddOrder.Shuffled); setQueueReplaces(true); setShuffleBias(ShuffleBias.LeastPlayed)
        setKeepShuffleOnSelection(true)
        setHandleAudioFocus(false); setPauseOnDisconnect(false); setHeadsetMultiPress(false)
        setFolderSort(dev.cmdrchara.songlass.core.library.FolderSort.MostPlayed)
    }

    @Test
    fun restoringOnePageDefaultsOnlyItsPreferencesAndSurvivesRestart() {
        val store = InMemoryKeyValueStore().apply { putString("history.counts", "kept") }
        val settings = changedSettings(store)
        val before = settings.state.value
        settings.resetToDefaults(SettingsArea.Sound)
        val after = settings.state.value
        val defaults = AppPreferences()
        assertEquals(defaults.replayGain, after.replayGain)
        assertEquals(0L, after.crossfadeDurationMs)
        assertEquals(0L, after.manualCrossfadeDurationMs)
        assertEquals(true, after.handleAudioFocus)
        // Everything on the other pages is untouched.
        assertEquals(before.copy(replayGain = defaults.replayGain, crossfadeDurationMs = 0L, manualCrossfadeDurationMs = 0L,
            handleAudioFocus = true), after)
        assertEquals(after, AppSettings(store).state.value)
        assertEquals("kept", store.getString("history.counts"))
        assertEquals(null, store.getString(AppSettings.CROSSFADE_KEY))
        assertEquals(null, store.getString(AppSettings.AUDIO_FOCUS_KEY))
    }

    @Test
    fun thePlaybackPageRestoresHeadsetResumeAndLyricsSourcesTogether() {
        val store = InMemoryKeyValueStore()
        val settings = changedSettings(store)
        val before = settings.state.value
        settings.resetToDefaults(SettingsArea.Player)
        val after = settings.state.value
        assertEquals(before.copy(headphones = dev.cmdrchara.songlass.core.playback.HeadphoneSettings(), pauseOnDisconnect = true, headsetMultiPress = true,
            resumePerTrack = false, longTrackThresholdMs = 10 * 60_000L, amllEnabled = true, lrclibEnabled = true), after)
        assertEquals(after, AppSettings(store).state.value)
        for (key in listOf(AppSettings.PAUSE_DISCONNECT_KEY, AppSettings.MULTI_PRESS_KEY, AppSettings.AUDIO_FOCUS_KEY, AppSettings.LRCLIB_KEY))
            assertTrue(key.startsWith("settings."), "$key travels with the other preferences in a backup")
    }

    @Test
    fun everyPageRestoresItsOwnDefaults() {
        for (area in SettingsArea.entries) {
            val store = InMemoryKeyValueStore()
            val settings = changedSettings(store)
            settings.resetToDefaults(area)
            val state = settings.state.value
            val defaults = AppPreferences()
            when (area) {
                SettingsArea.Appearance -> assertEquals(
                    listOf(defaults.interfaceStyle, defaults.searchInNavigationBar, defaults.appearance, defaults.artworkStyle,
                        defaults.reduceMotion, defaults.reduceTransparency, defaults.textScale, defaults.blurIntensity),
                    listOf(state.interfaceStyle, state.searchInNavigationBar, state.appearance, state.artworkStyle,
                        state.reduceMotion, state.reduceTransparency, state.textScale, state.blurIntensity))
                SettingsArea.Queue -> assertEquals(defaults.copy(), defaults.copy(
                    trackClickAction = state.trackClickAction, queueStart = state.queueStart, queueEnd = state.queueEnd,
                    queueAddOrder = state.queueAddOrder, queueReplaces = state.queueReplaces,
                    shuffleBias = state.shuffleBias, keepShuffleOnSelection = state.keepShuffleOnSelection))
                SettingsArea.Library -> {
                    assertEquals(defaults.libraryFilter, state.libraryFilter)
                    assertEquals(defaults.playCountRule, state.playCountRule)
                    assertEquals(defaults.albumLayout, state.albumLayout)
                    // Folder sort is a library-page choice, not part of the settings page.
                    assertEquals(dev.cmdrchara.songlass.core.library.FolderSort.MostPlayed, state.folderSort)
                }
                SettingsArea.Sound -> assertEquals(defaults.handleAudioFocus, state.handleAudioFocus)
                SettingsArea.Player -> assertEquals(
                    listOf(defaults.headsetMultiPress, defaults.longTrackThresholdMs, defaults.amllEnabled, defaults.lrclibEnabled),
                    listOf(state.headsetMultiPress, state.longTrackThresholdMs, state.amllEnabled, state.lrclibEnabled))
            }
            assertEquals(state, AppSettings(store).state.value, area.name)
        }
    }

    @Test
    fun restoringDefaultsIsIgnoredWhileABackupRestoreOwnsTheData() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = dev.cmdrchara.songlass.core.storage.RestoreMutationGate()
        val settings = AppSettings(store, gate)
        settings.setReduceMotion(true)
        gate.restoring { settings.resetToDefaults(SettingsArea.Appearance) }
        assertEquals(true, settings.state.value.reduceMotion)
    }

    @Test
    fun queueRulesPersistAreBackedUpAndUnknownValuesFallBack() {
        val store = InMemoryKeyValueStore()
        changedSettings(store)
        val reloaded = AppSettings(store).state.value
        assertEquals(TrackClickAction.AddToQueue, reloaded.trackClickAction)
        assertEquals(QueueStart.Immediately, reloaded.queueStart)
        assertEquals(QueueEnd.Stop, reloaded.queueEnd)
        assertEquals(QueueAddOrder.Shuffled, reloaded.queueAddOrder)
        assertEquals(true, reloaded.queueReplaces)
        assertEquals(ShuffleBias.LeastPlayed, reloaded.shuffleBias)
        assertEquals(true, reloaded.keepShuffleOnSelection)
        // Backups carry every "settings." key, so these travel with the other preferences.
        val keys = listOf(AppSettings.TRACK_CLICK_KEY, AppSettings.QUEUE_START_KEY, AppSettings.QUEUE_END_KEY,
            AppSettings.QUEUE_ORDER_KEY, AppSettings.QUEUE_REPLACES_KEY, AppSettings.SHUFFLE_BIAS_KEY, AppSettings.KEEP_SHUFFLE_KEY)
        keys.forEach { assertEquals(true, it.startsWith("settings."), it) }
        keys.forEach { store.putString(it, "something from the future") }
        val fallback = AppSettings(store).state.value
        assertEquals(TrackClickAction.PlayList, fallback.trackClickAction)
        assertEquals(QueueStart.AfterCurrent, fallback.queueStart)
        assertEquals(QueueEnd.ReturnToCollection, fallback.queueEnd)
        assertEquals(QueueAddOrder.End, fallback.queueAddOrder)
        assertEquals(false, fallback.queueReplaces)
        assertEquals(ShuffleBias.Random, fallback.shuffleBias)
        assertEquals(false, fallback.keepShuffleOnSelection)
    }

    @Test
    fun restoringTheQueuePageKeepsEveryOtherPreference() {
        val store = InMemoryKeyValueStore()
        val settings = changedSettings(store)
        val before = settings.state.value
        settings.resetToDefaults(SettingsArea.Queue)
        val defaults = AppPreferences()
        assertEquals(before.copy(
            trackClickAction = defaults.trackClickAction, queueStart = defaults.queueStart, queueEnd = defaults.queueEnd,
            queueAddOrder = defaults.queueAddOrder, queueReplaces = defaults.queueReplaces,
            shuffleBias = defaults.shuffleBias, keepShuffleOnSelection = defaults.keepShuffleOnSelection,
        ), settings.state.value)
        assertEquals(settings.state.value, AppSettings(store).state.value)
    }

    @Test
    fun theLauncherIconIsTheSameForEveryStyleUnlessAnotherOneIsChosenAndIsRestoredWithTheAppearancePage() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        settings.setInterfaceStyle(InterfaceStyle.Miuix)
        assertEquals(AppIconChoice.Violet, settings.state.value.appIcon, "the default icon does not depend on the style")
        settings.setAppIcon(AppIconChoice.Night)
        settings.setInterfaceStyle(InterfaceStyle.MaterialGlass)
        assertEquals(AppIconChoice.Night, AppSettings(store).state.value.appIcon)
        assertTrue(AppSettings.APP_ICON_KEY.startsWith("settings."), "travels with the other preferences in a backup")
        store.putString(AppSettings.APP_ICON_KEY, "from-the-future")
        assertEquals(AppIconChoice.Violet, AppSettings(store).state.value.appIcon, "unknown values fall back to the default")
        settings.setAppIcon(AppIconChoice.Night)
        settings.resetToDefaults(SettingsArea.Appearance)
        assertEquals(AppIconChoice.Violet, settings.state.value.appIcon)
        assertEquals(null, store.getString(AppSettings.APP_ICON_KEY))
    }

    @Test
    fun lastFmIsOffByDefaultTrimmedAndForgottenOnlyOnRequest() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        assertEquals(false, settings.state.value.lastFmEnabled)
        assertEquals("", settings.state.value.lastFmApiKey)
        assertEquals("", settings.state.value.lastFmApiSecret)

        settings.setLastFmEnabled(true)
        settings.setLastFmApiCredentials("  pasted-key  ", " pasted-secret ")
        // Trimmed on write, so whitespace from a paste never breaks the signature.
        assertEquals("pasted-key", settings.state.value.lastFmApiKey)
        assertEquals("pasted-secret", settings.state.value.lastFmApiSecret)
        val restored = AppSettings(store).state.value
        assertEquals(true, restored.lastFmEnabled)
        assertEquals("pasted-key", restored.lastFmApiKey)
        assertEquals("pasted-secret", restored.lastFmApiSecret)

        // Credentials, session and queue stay out of the .songlass v1 backup prefixes on purpose.
        for (key in LastFmStorageKeys.ALL) {
            assertTrue(key.startsWith("lastfm."), "$key must not travel in a settings/library/home backup")
        }
        store.putString(LastFmStorageKeys.SESSION_KEY, "session-key")
        store.putString(LastFmStorageKeys.SESSION_USERNAME, "listener")
        store.putString(LastFmStorageKeys.QUEUE, "pending")

        // Resetting the appearance page never touches scrobbling.
        settings.resetToDefaults(SettingsArea.Appearance)
        assertEquals(true, settings.state.value.lastFmEnabled)
        assertEquals("pasted-key", settings.state.value.lastFmApiKey)

        settings.resetLastFm()
        assertEquals(false, settings.state.value.lastFmEnabled)
        assertEquals("", settings.state.value.lastFmApiKey)
        assertEquals("", settings.state.value.lastFmApiSecret)
        for (key in LastFmStorageKeys.ALL) assertEquals(null, store.getString(key), key)
        assertEquals(settings.state.value, AppSettings(store).state.value)
    }

    @Test
    fun theEqualizerBandsAreLockedByDefaultAndTheSoundPageRestoresTheLock() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettings(store)
        // A fresh install starts locked: scrolling the page cannot move the graph.
        assertEquals(true, settings.state.value.equalizerLocked)
        settings.setEqualizerLocked(false)
        assertEquals(false, settings.state.value.equalizerLocked)
        assertEquals(false, AppSettings(store).state.value.equalizerLocked)

        settings.resetToDefaults(SettingsArea.Sound)
        assertEquals(true, settings.state.value.equalizerLocked)
        assertEquals(null, store.getString(AppSettings.EQUALIZER_LOCKED_KEY))

        // The lock is a Sound-page preference and travels with the settings backup.
        assertTrue(AppSettings.EQUALIZER_LOCKED_KEY.startsWith("settings."))
        // A page that does not own it leaves the choice alone.
        settings.setEqualizerLocked(false)
        settings.resetToDefaults(SettingsArea.Queue)
        assertEquals(false, settings.state.value.equalizerLocked)
    }
}
