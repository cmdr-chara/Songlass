package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.playback.HeadphoneSettings

import dev.cmdrchara.songlass.core.player.AudioFocusSettings
import dev.cmdrchara.songlass.core.player.FocusDucking

import dev.cmdrchara.songlass.core.library.LibraryFilter
import dev.cmdrchara.songlass.core.library.Ratings
import dev.cmdrchara.songlass.core.history.PlayCountRule
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import dev.cmdrchara.songlass.core.library.FolderSort
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.scrobble.LastFmStorageKeys
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

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
        liquidMaterial = LiquidMaterial.fromStorageKey(store.getString(LIQUID_MATERIAL_KEY)),
        searchInNavigationBar = store.getString(SEARCH_IN_NAV_KEY)?.toBooleanStrictOrNull() ?: false,
        textScale = readTextScale(store),
        blurIntensity = BlurIntensity.fromStorageKey(store.getString(BLUR_INTENSITY_KEY)),
        replayGain = when (store.getString(REPLAY_GAIN_KEY)) {
            "track" -> ReplayGainMode.Track
            "album" -> ReplayGainMode.Album
            else -> ReplayGainMode.Off
        },
        replayGainSettings = dev.cmdrchara.songlass.core.player.ReplayGainSettings(
            enabled = store.getString(RG_PROCESSING_KEY)?.toBooleanStrictOrNull() ?: false,
            preampDb = store.getString(RG_PREAMP_KEY)?.toIntOrNull()?.takeIf { it in -20..20 } ?: 0,
            missingPreampDb = store.getString(RG_MISSING_PREAMP_KEY)?.toIntOrNull()?.takeIf { it in -20..20 } ?: 0,
            limiter = store.getString(RG_LIMITER_KEY)?.toBooleanStrictOrNull() ?: true,
        ),
        equalizerTuning = dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.read(store),
        equalizerLocked = store.getString(EQUALIZER_LOCKED_KEY)?.toBooleanStrictOrNull() ?: true,
        audioEffects = dev.cmdrchara.songlass.core.player.AudioEffectsSettings(
            balancePercent = readEffect(store, dev.cmdrchara.songlass.core.player.AudioEffect.Balance),
            stereoExpandPercent = readEffect(store, dev.cmdrchara.songlass.core.player.AudioEffect.StereoExpand),
            tempoPercent = readEffect(store, dev.cmdrchara.songlass.core.player.AudioEffect.Tempo),
            reverbPercent = readEffect(store, dev.cmdrchara.songlass.core.player.AudioEffect.Reverb),
        ),
        folderSort = when (store.getString(FOLDER_SORT_KEY)) {
            "recent" -> FolderSort.RecentlyAdded
            "most_played" -> FolderSort.MostPlayed
            else -> FolderSort.Name
        },
        headphones = HeadphoneSettings.read(store),
        crossfadeDurationMs = store.getString(CROSSFADE_KEY)?.toLongOrNull()?.takeIf { it in 0L..12_000L } ?: 0L,
        manualCrossfadeDurationMs = store.getString(MANUAL_CROSSFADE_KEY)?.let(::parseCrossfadeDurationMs) ?: 0L,
        transportFades = dev.cmdrchara.songlass.core.player.TransportFadeSettings(
            store.getString(PAUSE_FADE_KEY)?.let(::parseTransportFadeDurationMs) ?: 0L,
            store.getString(STOP_FADE_KEY)?.let(::parseTransportFadeDurationMs) ?: 0L,
            store.getString(SEEK_FADE_KEY)?.let(::parseTransportFadeDurationMs) ?: 0L,
        ),
        amllEnabled = store.getString(AMLL_KEY)?.toBooleanStrictOrNull() ?: true,
        lrclibEnabled = store.getString(LRCLIB_KEY)?.toBooleanStrictOrNull() ?: true,
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
        handleAudioFocus = store.getString(AUDIO_FOCUS_KEY)?.toBooleanStrictOrNull() ?: true,
        audioFocus = AudioFocusSettings.read(store),
        pauseOnDisconnect = store.getString(PAUSE_DISCONNECT_KEY)?.toBooleanStrictOrNull() ?: true,
        headsetMultiPress = store.getString(MULTI_PRESS_KEY)?.toBooleanStrictOrNull() ?: true,
        onlineArtworkEnabled = store.getString(ONLINE_ARTWORK_KEY)?.toBooleanStrictOrNull() ?: false,
        onlineArtworkWifiOnly = store.getString(ONLINE_ARTWORK_WIFI_KEY)?.toBooleanStrictOrNull() ?: true,
        lastFmEnabled = store.getString(LASTFM_ENABLED_KEY)?.toBooleanStrictOrNull() ?: false,
        lastFmApiKey = store.getString(LASTFM_API_KEY).orEmpty(),
        lastFmApiSecret = store.getString(LASTFM_API_SECRET).orEmpty(),
        libraryFilter = LibraryFilter(
            excludedFolders = store.getStringList(EXCLUDED_FOLDERS_KEY).map { it.trim('/') }
                .filter { it.isNotBlank() }.toSet(),
            minimumDurationMs = store.getString(MIN_DURATION_KEY)?.toLongOrNull()
                ?.takeIf { it in LibraryFilter.durationChoicesMs } ?: 0,
            minimumRating = store.getString(MIN_RATING_KEY)?.toIntOrNull()
                ?.takeIf { it in 0..Ratings.MAX_STARS } ?: 0,
        ),
    )

    val state: StateFlow<AppPreferences> = _state.asStateFlow()

    /** Stored scales outside the offered choices fall back to 1.0 without rewriting the value. */
    private fun readTextScale(store: KeyValueStore): Float =
        store.getString(TEXT_SCALE_KEY)?.toFloatOrNull()
            ?.let { stored -> TEXT_SCALE_CHOICES.firstOrNull { abs(it - stored) <= TEXT_SCALE_TOLERANCE } }
            ?: 1f

    /** Refreshes the existing observable preferences after a validated backup restore. */
    fun reload() = prepareReload().invoke()

    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = readPreferences(store)
        return { _state.value = loaded }
    }

    fun setEqualizerBass(db: Double) = setEqualizerTuning(dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.BASS_KEY) { copy(bassDb = db) }
    fun setEqualizerTreble(db: Double) = setEqualizerTuning(dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.TREBLE_KEY) { copy(trebleDb = db) }
    fun setEqualizerLayout(layout: dev.cmdrchara.songlass.core.equalizer.GraphicEqLayout) =
        setEqualizerTuning(dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.LAYOUT_KEY) { copy(layout = layout) }
    private fun setEqualizerTuning(key: String, change: dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.() -> dev.cmdrchara.songlass.core.equalizer.EqualizerTuning) {
        if (!mutations.canMutate) return
        val next = state.value.equalizerTuning.change()
        if (next == state.value.equalizerTuning) return
        val value = when (key) {
            dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.BASS_KEY -> next.bassDb.toString()
            dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.TREBLE_KEY -> next.trebleDb.toString()
            else -> next.layout.count.toString()
        }
        store.putString(key, value)
        _state.update { it.copy(equalizerTuning = next) }
    }

    fun setAudioEffect(effect: dev.cmdrchara.songlass.core.player.AudioEffect, value: Int) {
        if (!mutations.canMutate) return
        val next = effect.changing(state.value.audioEffects, value)
        if (next == state.value.audioEffects) return
        store.putString(effectKey(effect), value.toString())
        _state.update { it.copy(audioEffects = next) }
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

    fun setPauseFadeDuration(durationMs: Long) = setTransportFade(PAUSE_FADE_KEY, durationMs) { copy(pauseMs = durationMs) }
    fun setStopFadeDuration(durationMs: Long) = setTransportFade(STOP_FADE_KEY, durationMs) { copy(stopMs = durationMs) }
    fun setSeekFadeDuration(durationMs: Long) = setTransportFade(SEEK_FADE_KEY, durationMs) { copy(seekMs = durationMs) }

    private fun setTransportFade(key: String, durationMs: Long,
        update: dev.cmdrchara.songlass.core.player.TransportFadeSettings.() -> dev.cmdrchara.songlass.core.player.TransportFadeSettings) {
        if (!mutations.canMutate) return
        require(parseTransportFadeDurationMs(durationMs.toString()) != null)
        store.putString(key, durationMs.toString())
        _state.update { it.copy(transportFades = it.transportFades.update()) }
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

    /** Locks or unlocks the graphic equalizer bands; a preset is not affected by the lock. */
    fun setEqualizerLocked(locked: Boolean) {
        if (!mutations.canMutate) return
        if (_state.value.equalizerLocked == locked) return
        store.putString(EQUALIZER_LOCKED_KEY, locked.toString())
        _state.update { it.copy(equalizerLocked = locked) }
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

    fun setLiquidMaterial(material: LiquidMaterial) {
        if (!mutations.canMutate || _state.value.liquidMaterial == material) return
        store.putString(LIQUID_MATERIAL_KEY, material.storageKey)
        _state.update { it.copy(liquidMaterial = material) }
    }

    fun setSearchInNavigationBar(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(SEARCH_IN_NAV_KEY, enabled.toString())
        _state.update { it.copy(searchInNavigationBar = enabled) }
    }

    fun setTextScale(scale: Float) {
        if (!mutations.canMutate) return
        val choice = TEXT_SCALE_CHOICES.firstOrNull { abs(it - scale) <= TEXT_SCALE_TOLERANCE }
        require(choice != null) { "unsupported text scale: $scale" }
        if (choice == state.value.textScale) return
        store.putString(TEXT_SCALE_KEY, choice.toString())
        _state.update { it.copy(textScale = choice) }
    }

    fun setBlurIntensity(intensity: BlurIntensity) {
        if (!mutations.canMutate) return
        if (intensity == state.value.blurIntensity) return
        store.putString(BLUR_INTENSITY_KEY, intensity.storageKey)
        _state.update { it.copy(blurIntensity = intensity) }
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

    fun setReplayGainProcessing(enabled: Boolean) = updateReplayGainSettings(RG_PROCESSING_KEY, enabled.toString()) { copy(enabled = enabled) }
    fun setReplayGainLimiter(enabled: Boolean) = updateReplayGainSettings(RG_LIMITER_KEY, enabled.toString()) { copy(limiter = enabled) }
    fun setReplayGainPreamp(db: Int) {
        require(db in -20..20)
        updateReplayGainSettings(RG_PREAMP_KEY, db.toString()) { copy(preampDb = db) }
    }
    fun setReplayGainMissingPreamp(db: Int) {
        require(db in -20..20)
        updateReplayGainSettings(RG_MISSING_PREAMP_KEY, db.toString()) { copy(missingPreampDb = db) }
    }
    private fun updateReplayGainSettings(key: String, value: String,
        update: dev.cmdrchara.songlass.core.player.ReplayGainSettings.() -> dev.cmdrchara.songlass.core.player.ReplayGainSettings) {
        if (!mutations.canMutate) return
        val current = state.value.replayGainSettings
        if (current.update() == current) return
        store.putString(key, value)
        _state.update { it.copy(replayGainSettings = it.replayGainSettings.update()) }
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
        // Compatibility entry point: a single accepted batch resets both independent overrides.
        store.updateStrings(removing = setOf(HeadphoneSettings.WIRED_KEY,
            HeadphoneSettings.BLUETOOTH_KEY), writing = mapOf(RESUME_KEY to enabled.toString()))
        _state.update { it.copy(headphones = it.headphones.copy(resumeWired = enabled, resumeBluetooth = enabled)) }
    }

    fun setResumeWired(enabled: Boolean) = setHeadphoneSetting(
        HeadphoneSettings.WIRED_KEY, enabled.toString()) { it.copy(resumeWired = enabled) }
    fun setResumeBluetooth(enabled: Boolean) = setHeadphoneSetting(
        HeadphoneSettings.BLUETOOTH_KEY, enabled.toString()) { it.copy(resumeBluetooth = enabled) }
    fun setBluetoothCommandDelay(durationMs: Long) {
        require(durationMs in HeadphoneSettings.delayChoicesMs)
        setHeadphoneSetting(HeadphoneSettings.COMMAND_DELAY_KEY, durationMs.toString()) {
            it.copy(bluetoothCommandDelayMs = durationMs)
        }
    }
    private fun setHeadphoneSetting(key: String, value: String,
        change: (HeadphoneSettings) -> HeadphoneSettings) {
        if (!mutations.canMutate) return
        val next = change(state.value.headphones)
        if (next == state.value.headphones) return
        store.putString(key, value)
        _state.update { it.copy(headphones = next) }
    }

    fun setLrclibEnabled(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(LRCLIB_KEY, enabled.toString())
        _state.update { it.copy(lrclibEnabled = enabled) }
    }

    fun setFocusDucking(value: FocusDucking) = setAudioFocus(
        AudioFocusSettings.DUCKING_KEY, value.name) { it.copy(ducking = value) }
    fun setResumeOnFocusGain(value: Boolean) = setAudioFocus(
        AudioFocusSettings.RESUME_KEY, value.toString()) { it.copy(resumeOnFocusGain = value) }
    fun setResumeAfterCall(value: Boolean) = setAudioFocus(
        AudioFocusSettings.CALL_RESUME_KEY, value.toString()) { it.copy(resumeAfterCall = value) }
    private fun setAudioFocus(key: String, value: String,
        change: (AudioFocusSettings) -> AudioFocusSettings) {
        if (!mutations.canMutate) return
        val next = change(_state.value.audioFocus)
        if (next == _state.value.audioFocus) return
        store.putString(key, value)
        _state.update { it.copy(audioFocus = next) }
    }

    fun setHandleAudioFocus(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(AUDIO_FOCUS_KEY, enabled.toString())
        _state.update { it.copy(handleAudioFocus = enabled) }
    }

    fun setPauseOnDisconnect(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(PAUSE_DISCONNECT_KEY, enabled.toString())
        _state.update { it.copy(pauseOnDisconnect = enabled) }
    }

    fun setHeadsetMultiPress(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(MULTI_PRESS_KEY, enabled.toString())
        _state.update { it.copy(headsetMultiPress = enabled) }
    }

    /** Cover lookup over the network: the switch that has to be on before any request leaves. */
    fun setOnlineArtworkEnabled(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(ONLINE_ARTWORK_KEY, enabled.toString())
        _state.update { it.copy(onlineArtworkEnabled = enabled) }
    }

    /** Keeps downloads off mobile data unless the user turns this off. */
    fun setOnlineArtworkWifiOnly(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(ONLINE_ARTWORK_WIFI_KEY, enabled.toString())
        _state.update { it.copy(onlineArtworkWifiOnly = enabled) }
    }

    /** Scrobbling opt-in. A missing API key pair keeps the feature inactive even when this is on. */
    fun setLastFmEnabled(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString(LASTFM_ENABLED_KEY, enabled.toString())
        _state.update { it.copy(lastFmEnabled = enabled) }
    }

    /**
     * Replaces the Last.fm API key pair. Requests are signed with these values, so an empty pair
     * leaves the feature unable to run; the settings page explains why.
     */
    fun setLastFmApiCredentials(apiKey: String, apiSecret: String) {
        if (!mutations.canMutate) return
        val key = apiKey.trim()
        val secret = apiSecret.trim()
        if (key == state.value.lastFmApiKey && secret == state.value.lastFmApiSecret) return
        store.putStrings(mapOf(LASTFM_API_KEY to key, LASTFM_API_SECRET to secret))
        _state.update { it.copy(lastFmApiKey = key, lastFmApiSecret = secret) }
    }

    /**
     * Removes everything the Last.fm feature stores: the opt-in switch, the API credentials, the
     * session key and the pending queue. They are not preferences alone — the session is an account
     * authorization and the queue holds listens that were never sent — so the settings page pairs this
     * with a disconnect (see [LastFmStorageKeys]).
     */
    fun resetLastFm() {
        if (!mutations.canMutate) return
        store.updateStrings(removing = LastFmStorageKeys.ALL, writing = emptyMap())
        _state.update { it.copy(lastFmEnabled = false, lastFmApiKey = "", lastFmApiSecret = "") }
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
     * Hides the library tracks rated below [stars]: zero means any rating, so a level neither
     * filters nor is written as a rating of its own. The rating itself lives in [Ratings].
     */
    fun setMinimumRating(stars: Int) {
        if (!mutations.canMutate) return
        require(stars in 0..Ratings.MAX_STARS)
        store.putString(MIN_RATING_KEY, stars.toString())
        _state.update { it.copy(libraryFilter = it.libraryFilter.copy(minimumRating = stars)) }
    }

    /**
     * Removes the stored values of one settings page, so every default stays "absent" as for a fresh
     * install. Only that page's preferences change; saved EQ curves/corrections, playlists and history stay.
     * Sound also resets global tone controls/layout, without erasing any saved graphic curve.
     */
    fun resetToDefaults(area: SettingsArea) {
        if (!mutations.canMutate) return
        val keys = when (area) {
            SettingsArea.Appearance -> setOf(INTERFACE_STYLE_KEY, LIQUID_MATERIAL_KEY, SEARCH_IN_NAV_KEY, APPEARANCE_KEY, ARTWORK_STYLE_KEY, APP_ICON_KEY, REDUCE_MOTION_KEY, REDUCE_TRANSPARENCY_KEY,
                TEXT_SCALE_KEY, BLUR_INTENSITY_KEY, ONLINE_ARTWORK_KEY, ONLINE_ARTWORK_WIFI_KEY)
            SettingsArea.Sound -> setOf(REPLAY_GAIN_KEY, CROSSFADE_KEY, MANUAL_CROSSFADE_KEY, AUDIO_FOCUS_KEY, PAUSE_FADE_KEY, STOP_FADE_KEY, SEEK_FADE_KEY, RG_PROCESSING_KEY, RG_PREAMP_KEY, RG_MISSING_PREAMP_KEY, RG_LIMITER_KEY, BALANCE_KEY, STEREO_EXPAND_KEY, TEMPO_KEY, REVERB_KEY,
                dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.BASS_KEY, dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.TREBLE_KEY,
                dev.cmdrchara.songlass.core.equalizer.EqualizerTuning.LAYOUT_KEY, EQUALIZER_LOCKED_KEY) + AudioFocusSettings.keys
            SettingsArea.Player -> setOf(RESUME_KEY, PAUSE_DISCONNECT_KEY, MULTI_PRESS_KEY, PER_TRACK_KEY, LONG_THRESHOLD_KEY, AMLL_KEY, LRCLIB_KEY) + HeadphoneSettings.keys
            SettingsArea.Queue -> setOf(TRACK_CLICK_KEY, QUEUE_START_KEY, QUEUE_END_KEY, QUEUE_ORDER_KEY, QUEUE_REPLACES_KEY, SHUFFLE_BIAS_KEY, KEEP_SHUFFLE_KEY)
            SettingsArea.Library -> setOf(EXCLUDED_FOLDERS_KEY, MIN_DURATION_KEY, MIN_RATING_KEY, PLAY_COUNT_RULE_KEY, ALBUM_LAYOUT_KEY)
        }
        store.updateStrings(removing = keys, writing = emptyMap())
        val defaults = readPreferences()
        _state.update { current ->
            when (area) {
                SettingsArea.Appearance -> current.copy(interfaceStyle = defaults.interfaceStyle,
                    searchInNavigationBar = defaults.searchInNavigationBar, liquidMaterial = defaults.liquidMaterial,
                    appearance = defaults.appearance, artworkStyle = defaults.artworkStyle, appIcon = defaults.appIcon,
                    reduceMotion = defaults.reduceMotion, reduceTransparency = defaults.reduceTransparency,
                    textScale = defaults.textScale, blurIntensity = defaults.blurIntensity,
                    onlineArtworkEnabled = defaults.onlineArtworkEnabled,
                    onlineArtworkWifiOnly = defaults.onlineArtworkWifiOnly)
                SettingsArea.Sound -> current.copy(replayGain = defaults.replayGain, replayGainSettings = defaults.replayGainSettings, audioEffects = defaults.audioEffects, equalizerTuning = defaults.equalizerTuning,
                    equalizerLocked = defaults.equalizerLocked,
                    crossfadeDurationMs = defaults.crossfadeDurationMs, manualCrossfadeDurationMs = defaults.manualCrossfadeDurationMs,
                    handleAudioFocus = defaults.handleAudioFocus, audioFocus = defaults.audioFocus, transportFades = defaults.transportFades)
                SettingsArea.Player -> current.copy(headphones = defaults.headphones,
                    pauseOnDisconnect = defaults.pauseOnDisconnect, headsetMultiPress = defaults.headsetMultiPress,
                    resumePerTrack = defaults.resumePerTrack, longTrackThresholdMs = defaults.longTrackThresholdMs,
                    amllEnabled = defaults.amllEnabled, lrclibEnabled = defaults.lrclibEnabled)
                SettingsArea.Queue -> current.copy(trackClickAction = defaults.trackClickAction, queueStart = defaults.queueStart,
                    queueEnd = defaults.queueEnd, queueAddOrder = defaults.queueAddOrder, queueReplaces = defaults.queueReplaces,
                    shuffleBias = defaults.shuffleBias, keepShuffleOnSelection = defaults.keepShuffleOnSelection)
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
        const val BALANCE_KEY = "settings.balance_percent"
        const val STEREO_EXPAND_KEY = "settings.stereo_expand_percent"
        const val TEMPO_KEY = "settings.tempo_percent"
        const val REVERB_KEY = "settings.reverb_percent"
        private fun effectKey(effect: dev.cmdrchara.songlass.core.player.AudioEffect): String = when (effect) {
            dev.cmdrchara.songlass.core.player.AudioEffect.Balance -> BALANCE_KEY
            dev.cmdrchara.songlass.core.player.AudioEffect.StereoExpand -> STEREO_EXPAND_KEY
            dev.cmdrchara.songlass.core.player.AudioEffect.Tempo -> TEMPO_KEY
            dev.cmdrchara.songlass.core.player.AudioEffect.Reverb -> REVERB_KEY
        }
        private fun readEffect(store: KeyValueStore, effect: dev.cmdrchara.songlass.core.player.AudioEffect): Int =
            store.getString(effectKey(effect))?.toIntOrNull()?.takeIf { it in effect.minimum..effect.maximum } ?: effect.default
        val LONG_TRACK_CHOICES_MS = listOf(60_000L, 300_000L, 600_000L, 1_200_000L, 1_800_000L, 3_600_000L)
        private const val PER_TRACK_KEY = "settings.resume_per_track"
        private const val LONG_THRESHOLD_KEY = "settings.long_track_threshold_ms"
        const val EXCLUDED_FOLDERS_KEY = "library.excluded_folders"
        const val MIN_DURATION_KEY = "library.minimum_duration_ms"
        const val MIN_RATING_KEY = "library.minimum_rating"
        const val APPEARANCE_KEY = "settings.appearance"
        const val REDUCE_MOTION_KEY = "settings.reduce_motion"
        const val REDUCE_TRANSPARENCY_KEY = "settings.reduce_transparency"
        const val ARTWORK_STYLE_KEY = "settings.artwork_style"
        const val INTERFACE_STYLE_KEY = "settings.interface_style"
        const val LIQUID_MATERIAL_KEY = "settings.liquid_material"
        const val SEARCH_IN_NAV_KEY = "settings.search_in_navigation_bar"
        const val RG_PROCESSING_KEY = "settings.replay_gain_processing"
        const val RG_PREAMP_KEY = "settings.replay_gain_preamp_db"
        const val RG_MISSING_PREAMP_KEY = "settings.replay_gain_missing_preamp_db"
        const val RG_LIMITER_KEY = "settings.replay_gain_limiter"
        const val REPLAY_GAIN_KEY = "settings.replay_gain"
        const val FOLDER_SORT_KEY = "library.folder_sort"
        const val RESUME_KEY = "settings.resume_on_reconnect"
        const val CROSSFADE_KEY = "settings.crossfade_duration_ms"
        const val PAUSE_FADE_KEY = "settings.pause_fade_duration_ms"
        const val STOP_FADE_KEY = "settings.stop_fade_duration_ms"
        const val SEEK_FADE_KEY = "settings.seek_fade_duration_ms"
        const val MANUAL_CROSSFADE_KEY = "settings.manual_crossfade_duration_ms"
        const val AMLL_KEY = "settings.amll_enabled"
        const val LRCLIB_KEY = "settings.lrclib_enabled"
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
        const val TEXT_SCALE_KEY = "settings.text_scale"
        const val BLUR_INTENSITY_KEY = "settings.blur_intensity"
        const val EQUALIZER_LOCKED_KEY = "settings.equalizer_locked"
        /** Multipliers offered by the Appearance page; 1.0 is the reference size. */
        val TEXT_SCALE_CHOICES = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)

        /** Stored floats are compared with a tolerance so decimal strings survive a round trip. */
        private const val TEXT_SCALE_TOLERANCE = 0.001f
        const val AUDIO_FOCUS_KEY = "settings.handle_audio_focus"
        const val PAUSE_DISCONNECT_KEY = "settings.pause_on_disconnect"
        const val MULTI_PRESS_KEY = "settings.headset_multi_press"
        const val ONLINE_ARTWORK_KEY = "settings.online_artwork_enabled"
        const val ONLINE_ARTWORK_WIFI_KEY = "settings.online_artwork_wifi_only"

        // Last.fm credentials, session and queue deliberately use the `lastfm.` prefix: the
        // .songlass v1 backup exports only settings.*, library.* and home.*, so no API secret or
        // session ever travels in a shared backup file. See LastFmStorageKeys.
        const val LASTFM_ENABLED_KEY = LastFmStorageKeys.ENABLED
        const val LASTFM_API_KEY = LastFmStorageKeys.API_KEY
        const val LASTFM_API_SECRET = LastFmStorageKeys.API_SECRET
    }
}
