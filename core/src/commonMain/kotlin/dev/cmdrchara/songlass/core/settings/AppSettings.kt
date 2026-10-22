package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.library.LibraryFilter
import dev.cmdrchara.songlass.core.history.PlayCountRule
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import dev.cmdrchara.songlass.core.library.FolderSort
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Persistent preferences shared by all interfaces and platforms. */
class AppSettings(
    private val store: KeyValueStore,
    private val mutations: RestoreMutationGate = RestoreMutationGate(),
) {
    private val _state = MutableStateFlow(readPreferences())

    private fun readPreferences(store: KeyValueStore = this.store): AppPreferences = AppPreferences(
        appearance = when (store.getString(APPEARANCE_KEY)) {
            "light" -> AppearanceMode.Light
            "dark" -> AppearanceMode.Dark
            else -> AppearanceMode.System
        },
        reduceMotion = store.getString(REDUCE_MOTION_KEY)?.toBooleanStrictOrNull() ?: false,
        reduceTransparency = store.getString(REDUCE_TRANSPARENCY_KEY)?.toBooleanStrictOrNull() ?: false,
        artworkStyle = when (store.getString(ARTWORK_STYLE_KEY)) {
            "square" -> ArtworkStyle.Square
            else -> ArtworkStyle.FullBleed
        },
        interfaceStyle = when (store.getString(INTERFACE_STYLE_KEY)) {
            "material_glass" -> InterfaceStyle.MaterialGlass
            "miuix" -> InterfaceStyle.Miuix
            else -> InterfaceStyle.LiquidGlass
        },
        replayGain = when (store.getString(REPLAY_GAIN_KEY)) {
            "track" -> ReplayGainMode.Track
            "album" -> ReplayGainMode.Album
            else -> ReplayGainMode.Off
        },
        folderSort = when (store.getString(FOLDER_SORT_KEY)) {
            "recent" -> FolderSort.RecentlyAdded
            "most_played" -> FolderSort.MostPlayed
            else -> FolderSort.Name
        },
        resumeOnReconnect = store.getString(RESUME_KEY)?.toBooleanStrictOrNull() ?: false,
        crossfadeDurationMs = store.getString(CROSSFADE_KEY)?.toLongOrNull()?.takeIf { it in 0L..12_000L } ?: 0L,
        manualCrossfadeDurationMs = store.getString(MANUAL_CROSSFADE_KEY)?.let(::parseCrossfadeDurationMs) ?: 0L,
        amllEnabled = store.getString(AMLL_KEY)?.toBooleanStrictOrNull() ?: true,
        playCountRule = PlayCountRule.fromStorage(store.getString(PLAY_COUNT_RULE_KEY)),
        albumLayout = if (store.getString(ALBUM_LAYOUT_KEY) == "list") AlbumLayout.List else AlbumLayout.Grid,
        resumePerTrack = store.getString(PER_TRACK_KEY)?.toBooleanStrictOrNull() ?: false,
        longTrackThresholdMs = store.getString(LONG_THRESHOLD_KEY)?.toLongOrNull()?.takeIf { it in LONG_TRACK_CHOICES_MS } ?: 10 * 60_000L,
        trackClickAction = TrackClickAction.fromStorage(store.getString(TRACK_CLICK_KEY)),
        queueStart = QueueStart.fromStorage(store.getString(QUEUE_START_KEY)),
        queueEnd = QueueEnd.fromStorage(store.getString(QUEUE_END_KEY)),
        queueAddOrder = QueueAddOrder.fromStorage(store.getString(QUEUE_ORDER_KEY)),
        queueReplaces = store.getString(QUEUE_REPLACES_KEY)?.toBooleanStrictOrNull() ?: false,
        shuffleBias = ShuffleBias.fromStorage(store.getString(SHUFFLE_BIAS_KEY)),
        keepShuffleOnSelection = store.getString(KEEP_SHUFFLE_KEY)?.toBooleanStrictOrNull() ?: false,
        appIcon = AppIconChoice.fromStorageKey(store.getString(APP_ICON_KEY)),
        libraryFilter = LibraryFilter(
            excludedFolders = store.getStringList(EXCLUDED_FOLDERS_KEY).map { it.trim('/') }
                .filter { it.isNotBlank() }.toSet(),
            minimumDurationMs = store.getString(MIN_DURATION_KEY)?.toLongOrNull()
                ?.takeIf { it in LibraryFilter.durationChoicesMs } ?: 0,
        ),
    )

    val state: StateFlow<AppPreferences> = _state.asStateFlow()

    /** Refreshes the existing observable preferences after a validated backup restore. */
    fun reload() = prepareReload().invoke()

    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = readPreferences(store)
        return { _state.value = loaded }
    }

    fun setCrossfadeDuration(durationMs: Long) {
        if (!mutations.canMutate) return
        require(durationMs in 0L..12_000L)
        store.putString(CROSSFADE_KEY, durationMs.toString())
        _state.update { it.copy(crossfadeDurationMs = durationMs) }
    }

    fun setManualCrossfadeDuration(durationMs: Long) {
        if (!mutations.canMutate) return
        require(parseCrossfadeDurationMs(durationMs.toString()) != null)
        store.putString(MANUAL_CROSSFADE_KEY, durationMs.toString())
        _state.update { it.copy(manualCrossfadeDurationMs = durationMs) }
    }

    fun setAppearance(appearance: AppearanceMode) {
        if (!mutations.canMutate) return
        val storedValue = when (appearance) {
            AppearanceMode.System -> "system"
            AppearanceMode.Light -> "light"
            AppearanceMode.Dark -> "dark"
        }
        store.putString(APPEARANCE_KEY, storedValue)
        _state.update { it.copy(appearance = appearance) }
    }

    fun setReduceMotion(reduceMotion: Boolean) {
        if (!mutations.canMutate) return
        store.putString(REDUCE_MOTION_KEY, reduceMotion.toString())
        _state.update { it.copy(reduceMotion = reduceMotion) }
    }

    fun setReduceTransparency(reduceTransparency: Boolean) {
        if (!mutations.canMutate) return
        store.putString(REDUCE_TRANSPARENCY_KEY, reduceTransparency.toString())
        _state.update { it.copy(reduceTransparency = reduceTransparency) }
    }

    fun setArtworkStyle(style: ArtworkStyle) {
        if (!mutations.canMutate) return
        store.putString(
            ARTWORK_STYLE_KEY,
            when (style) {
                ArtworkStyle.FullBleed -> "full_bleed"
                ArtworkStyle.Square -> "square"
            },
        )
        _state.update { it.copy(artworkStyle = style) }
    }

    fun setInterfaceStyle(style: InterfaceStyle) {
        if (!mutations.canMutate) return
        store.putString(
            INTERFACE_STYLE_KEY,
            when (style) {
                InterfaceStyle.LiquidGlass -> "liquid_glass"
                InterfaceStyle.MaterialGlass -> "material_glass"
                InterfaceStyle.Miuix -> "miuix"
            },
        )
        _state.update { it.copy(interfaceStyle = style) }
    }

    fun setReplayGain(mode: ReplayGainMode) {
        if (!mutations.canMutate) return
        store.putString(
            REPLAY_GAIN_KEY,
            when (mode) {
                ReplayGainMode.Off -> "off"
                ReplayGainMode.Track -> "track"
                ReplayGainMode.Album -> "album"
            },
        )
        _state.update { it.copy(replayGain = mode) }
    }

    fun setFolderSort(sort: FolderSort) {
        if (!mutations.canMutate) return
        store.putString(
            FOLDER_SORT_KEY,
            when (sort) {
                FolderSort.Name -> "name"
                FolderSort.RecentlyAdded -> "recent"
                FolderSort.MostPlayed -> "most_played"
            },
        )
        _state.update { it.copy(folderSort = sort) }
    }

    fun setResumeOnReconnect(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(RESUME_KEY, enabled.toString())
        _state.update { it.copy(resumeOnReconnect = enabled) }
    }

    fun setResumePerTrack(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(PER_TRACK_KEY, enabled.toString())
        _state.update { it.copy(resumePerTrack = enabled) }
    }

    fun setLongTrackThreshold(durationMs: Long) {
        if (!mutations.canMutate) return
        require(durationMs in LONG_TRACK_CHOICES_MS)
        store.putString(LONG_THRESHOLD_KEY, durationMs.toString())
        _state.update { it.copy(longTrackThresholdMs = durationMs) }
    }

    fun setTrackClickAction(action: TrackClickAction) {
        if (!mutations.canMutate) return
        store.putString(TRACK_CLICK_KEY, action.storageKey)
        _state.update { it.copy(trackClickAction = action) }
    }

    fun setQueueStart(start: QueueStart) {
        if (!mutations.canMutate) return
        store.putString(QUEUE_START_KEY, start.storageKey)
        _state.update { it.copy(queueStart = start) }
    }

    fun setQueueEnd(end: QueueEnd) {
        if (!mutations.canMutate) return
        store.putString(QUEUE_END_KEY, end.storageKey)
        _state.update { it.copy(queueEnd = end) }
    }

    fun setQueueAddOrder(order: QueueAddOrder) {
        if (!mutations.canMutate) return
        store.putString(QUEUE_ORDER_KEY, order.storageKey)
        _state.update { it.copy(queueAddOrder = order) }
    }

    fun setQueueReplaces(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(QUEUE_REPLACES_KEY, enabled.toString())
        _state.update { it.copy(queueReplaces = enabled) }
    }

    fun setShuffleBias(bias: ShuffleBias) {
        if (!mutations.canMutate) return
        store.putString(SHUFFLE_BIAS_KEY, bias.storageKey)
        _state.update { it.copy(shuffleBias = bias) }
    }

    fun setAppIcon(icon: AppIconChoice) {
        if (!mutations.canMutate) return
        store.putString(APP_ICON_KEY, icon.storageKey)
        _state.update { it.copy(appIcon = icon) }
    }

    fun setKeepShuffleOnSelection(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(KEEP_SHUFFLE_KEY, enabled.toString())
        _state.update { it.copy(keepShuffleOnSelection = enabled) }
    }

    fun setAmllEnabled(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(AMLL_KEY, enabled.toString())
        _state.update { it.copy(amllEnabled = enabled) }
    }

    fun setPlayCountRule(rule: PlayCountRule) {
        if (!mutations.canMutate) return
        store.putString(PLAY_COUNT_RULE_KEY, rule.storageKey)
        _state.update { it.copy(playCountRule = rule) }
    }

    fun setAlbumLayout(layout: AlbumLayout) {
        if (!mutations.canMutate) return
        store.putString(ALBUM_LAYOUT_KEY, if (layout == AlbumLayout.List) "list" else "grid")
        _state.update { it.copy(albumLayout = layout) }
    }

    fun setMinimumDuration(durationMs: Long) {
        if (!mutations.canMutate) return
        require(durationMs in LibraryFilter.durationChoicesMs)
        store.putString(MIN_DURATION_KEY, durationMs.toString())
        _state.update { it.copy(libraryFilter = it.libraryFilter.copy(minimumDurationMs = durationMs)) }
    }

    /**
     * Removes the stored values of one settings page, so every default stays "absent" as for a fresh
     * install. Only that page's preferences change: other settings, playlists, history and the equalizer are kept.
     */
    fun resetToDefaults(area: SettingsArea) {
        if (!mutations.canMutate) return
        val keys = when (area) {
            SettingsArea.Appearance -> setOf(INTERFACE_STYLE_KEY, APPEARANCE_KEY, ARTWORK_STYLE_KEY, APP_ICON_KEY)
            SettingsArea.Playback -> setOf(REPLAY_GAIN_KEY, RESUME_KEY, CROSSFADE_KEY, MANUAL_CROSSFADE_KEY, PER_TRACK_KEY, LONG_THRESHOLD_KEY)
            SettingsArea.Queue -> setOf(TRACK_CLICK_KEY, QUEUE_START_KEY, QUEUE_END_KEY, QUEUE_ORDER_KEY, QUEUE_REPLACES_KEY, SHUFFLE_BIAS_KEY, KEEP_SHUFFLE_KEY)
            SettingsArea.Lyrics -> setOf(AMLL_KEY)
            SettingsArea.Accessibility -> setOf(REDUCE_MOTION_KEY, REDUCE_TRANSPARENCY_KEY)
            SettingsArea.Library -> setOf(EXCLUDED_FOLDERS_KEY, MIN_DURATION_KEY, PLAY_COUNT_RULE_KEY, ALBUM_LAYOUT_KEY)
        }
        store.updateStrings(removing = keys, writing = emptyMap())
        val defaults = readPreferences()
        _state.update { current ->
            when (area) {
                SettingsArea.Appearance -> current.copy(interfaceStyle = defaults.interfaceStyle,
                    appearance = defaults.appearance, artworkStyle = defaults.artworkStyle, appIcon = defaults.appIcon)
                SettingsArea.Playback -> current.copy(replayGain = defaults.replayGain, resumeOnReconnect = defaults.resumeOnReconnect,
                    crossfadeDurationMs = defaults.crossfadeDurationMs, manualCrossfadeDurationMs = defaults.manualCrossfadeDurationMs,
                    resumePerTrack = defaults.resumePerTrack, longTrackThresholdMs = defaults.longTrackThresholdMs)
                SettingsArea.Queue -> current.copy(trackClickAction = defaults.trackClickAction, queueStart = defaults.queueStart,
                    queueEnd = defaults.queueEnd, queueAddOrder = defaults.queueAddOrder, queueReplaces = defaults.queueReplaces,
                    shuffleBias = defaults.shuffleBias, keepShuffleOnSelection = defaults.keepShuffleOnSelection)
                SettingsArea.Lyrics -> current.copy(amllEnabled = defaults.amllEnabled)
                SettingsArea.Accessibility -> current.copy(reduceMotion = defaults.reduceMotion,
                    reduceTransparency = defaults.reduceTransparency)
                SettingsArea.Library -> current.copy(libraryFilter = defaults.libraryFilter,
                    playCountRule = defaults.playCountRule, albumLayout = defaults.albumLayout)
            }
        }
    }

    fun setFolderExcluded(path: String, excluded: Boolean) {
        if (!mutations.canMutate) return
        val folder = path.trim('/')
        if (folder.isBlank()) return
        val current = state.value.libraryFilter
        val folders = if (excluded) current.excludedFolders + folder else current.excludedFolders - folder
        store.putStringList(EXCLUDED_FOLDERS_KEY, folders.sorted())
        _state.update { it.copy(libraryFilter = it.libraryFilter.copy(excludedFolders = folders)) }
    }

    companion object {
        val LONG_TRACK_CHOICES_MS = listOf(60_000L, 300_000L, 600_000L, 1_200_000L, 1_800_000L, 3_600_000L)
        private const val PER_TRACK_KEY = "settings.resume_per_track"
        private const val LONG_THRESHOLD_KEY = "settings.long_track_threshold_ms"
        const val EXCLUDED_FOLDERS_KEY = "library.excluded_folders"
        const val MIN_DURATION_KEY = "library.minimum_duration_ms"
        const val APPEARANCE_KEY = "settings.appearance"
        const val REDUCE_MOTION_KEY = "settings.reduce_motion"
        const val REDUCE_TRANSPARENCY_KEY = "settings.reduce_transparency"
        const val ARTWORK_STYLE_KEY = "settings.artwork_style"
        const val INTERFACE_STYLE_KEY = "settings.interface_style"
        const val REPLAY_GAIN_KEY = "settings.replay_gain"
        const val FOLDER_SORT_KEY = "library.folder_sort"
        const val RESUME_KEY = "settings.resume_on_reconnect"
        const val CROSSFADE_KEY = "settings.crossfade_duration_ms"
        const val MANUAL_CROSSFADE_KEY = "settings.manual_crossfade_duration_ms"
        const val AMLL_KEY = "settings.amll_enabled"
        const val PLAY_COUNT_RULE_KEY = "settings.play_count_rule"
        const val ALBUM_LAYOUT_KEY = "library.album_layout"
        const val TRACK_CLICK_KEY = "settings.track_click_action"
        const val QUEUE_START_KEY = "settings.queue_start"
        const val QUEUE_END_KEY = "settings.queue_end"
        const val QUEUE_ORDER_KEY = "settings.queue_add_order"
        const val QUEUE_REPLACES_KEY = "settings.queue_replaces"
        const val SHUFFLE_BIAS_KEY = "settings.shuffle_bias"
        const val KEEP_SHUFFLE_KEY = "settings.keep_shuffle"
        const val APP_ICON_KEY = "settings.app_icon"
    }
}
