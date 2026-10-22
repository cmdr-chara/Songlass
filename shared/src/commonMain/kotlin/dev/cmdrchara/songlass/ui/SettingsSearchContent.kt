package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.equalizer.EqualizerState
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.settings.*
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility
import dev.cmdrchara.songlass.shared.resources.*
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun settingsTargetTitle(target: SettingsTarget): String = stringResource(when (target) {
    SettingsTarget.InterfaceStyle -> Res.string.settings_ui_style
    SettingsTarget.Appearance -> Res.string.settings_appearance
    SettingsTarget.ArtworkStyle -> Res.string.settings_artwork
    SettingsTarget.ResumeOnReconnect -> Res.string.settings_resume_reconnect
    SettingsTarget.ReplayGain -> Res.string.settings_replay_gain
    SettingsTarget.SleepTimer -> Res.string.sleep_title
    SettingsTarget.AppIcon -> Res.string.settings_app_icon
    SettingsTarget.AudioFocus -> Res.string.settings_audio_focus
    SettingsTarget.PauseOnDisconnect -> Res.string.settings_pause_disconnect
    SettingsTarget.HeadsetMultiPress -> Res.string.settings_headset_multi
    SettingsTarget.TrackClickAction -> Res.string.queue_click_section
    SettingsTarget.QueueStart -> Res.string.queue_start_section
    SettingsTarget.QueueAddOrder -> Res.string.queue_order_section
    SettingsTarget.QueueReplaces -> Res.string.queue_replaces
    SettingsTarget.QueueEnd -> Res.string.queue_end_section
    SettingsTarget.ShuffleBias -> Res.string.shuffle_bias_title
    SettingsTarget.KeepShuffle -> Res.string.shuffle_keep
    SettingsTarget.Crossfade -> Res.string.settings_search_crossfade_automatic
    SettingsTarget.CrossfadeDuration -> Res.string.settings_search_crossfade_duration
    SettingsTarget.ManualCrossfadeDuration -> Res.string.settings_search_manual_crossfade_duration
    SettingsTarget.ResumePerTrack -> Res.string.settings_long_resume
    SettingsTarget.LongTrackThreshold -> Res.string.settings_long_threshold
    SettingsTarget.AmllLyrics -> Res.string.settings_amll
    SettingsTarget.LrclibLyrics -> Res.string.settings_lrclib
    SettingsTarget.ReduceMotion -> Res.string.settings_reduce_motion
    SettingsTarget.ReduceTransparency -> Res.string.settings_reduce_transparency
    SettingsTarget.Equalizer -> Res.string.eq_title
    SettingsTarget.EqualizerPresets -> Res.string.eq_presets
    SettingsTarget.OutputProfiles -> Res.string.eq_outputs_auto
    SettingsTarget.HeadphoneCorrection -> Res.string.eq_headphones
    SettingsTarget.MinimumDuration -> Res.string.library_minimum_duration
    SettingsTarget.ExcludedFolders -> Res.string.library_excluded_folders
    SettingsTarget.PlayCountRule -> Res.string.library_counting_title
    SettingsTarget.AlbumLayout -> Res.string.library_album_layout
    SettingsTarget.HomeCustomization -> Res.string.homecfg_title
    SettingsTarget.Backup -> Res.string.backup_title
    SettingsTarget.About -> Res.string.about_title
})

/** The index receives current localized labels, while core supplies cross-language synonyms. */
@Composable
internal fun settingsSearchDocuments(): List<SettingsSearchDocument> {
    val root = stringResource(Res.string.settings_title)
    val appearance = stringResource(Res.string.settings_appearance)
    val sound = stringResource(Res.string.settings_sound)
    val playback = stringResource(Res.string.settings_playback)
    val equalizer = stringResource(Res.string.eq_title)
    val queue = stringResource(Res.string.settings_queue)
    val library = stringResource(Res.string.library_settings)
    val lyrics = stringResource(Res.string.settings_lyrics)
    val accessibility = stringResource(Res.string.settings_accessibility)
    return SettingsTarget.entries.map { target ->
        val section = when (target) {
            SettingsTarget.InterfaceStyle, SettingsTarget.Appearance, SettingsTarget.ArtworkStyle, SettingsTarget.AppIcon -> appearance
            SettingsTarget.ReplayGain, SettingsTarget.Crossfade, SettingsTarget.SleepTimer, SettingsTarget.AudioFocus,
            SettingsTarget.Equalizer -> sound
            SettingsTarget.ResumeOnReconnect, SettingsTarget.PauseOnDisconnect, SettingsTarget.HeadsetMultiPress,
            SettingsTarget.ResumePerTrack, SettingsTarget.LongTrackThreshold,
            SettingsTarget.AmllLyrics, SettingsTarget.LrclibLyrics -> playback
            SettingsTarget.TrackClickAction, SettingsTarget.QueueStart, SettingsTarget.QueueAddOrder, SettingsTarget.QueueReplaces,
            SettingsTarget.QueueEnd, SettingsTarget.ShuffleBias, SettingsTarget.KeepShuffle -> queue
            SettingsTarget.CrossfadeDuration, SettingsTarget.ManualCrossfadeDuration -> "$sound › ${stringResource(Res.string.settings_crossfade)}"
            SettingsTarget.EqualizerPresets, SettingsTarget.OutputProfiles, SettingsTarget.HeadphoneCorrection -> "$sound › $equalizer"
            SettingsTarget.MinimumDuration, SettingsTarget.ExcludedFolders,
            SettingsTarget.PlayCountRule, SettingsTarget.AlbumLayout -> library
            SettingsTarget.ReduceMotion, SettingsTarget.ReduceTransparency -> "$appearance › ${stringResource(Res.string.settings_accessibility)}"
            else -> null
        }
        SettingsSearchDocument(target, settingsTargetTitle(target), listOfNotNull(root, section).joinToString(" › "))
    }
}

/** Values are read from the current observable state, never from a search-index snapshot. */
@Composable
private fun settingsCurrentValue(target: SettingsTarget, preferences: AppPreferences, eq: EqualizerState, system: UiAccessibility): String? {
    @Composable fun enabled(value: Boolean) = stringResource(if (value) Res.string.eq_on else Res.string.eq_off)
    return when (target) {
        SettingsTarget.InterfaceStyle -> stringResource(when (preferences.interfaceStyle) {
            InterfaceStyle.LiquidGlass -> Res.string.settings_liquid_glass
            InterfaceStyle.MaterialGlass -> Res.string.settings_material_glass
            InterfaceStyle.Miuix -> Res.string.settings_miuix
        })
        SettingsTarget.Appearance -> stringResource(when (preferences.appearance) {
            AppearanceMode.System -> Res.string.settings_appearance_system
            AppearanceMode.Light -> Res.string.settings_appearance_light
            AppearanceMode.Dark -> Res.string.settings_appearance_dark
        })
        SettingsTarget.ArtworkStyle -> stringResource(if (preferences.artworkStyle == ArtworkStyle.FullBleed) Res.string.settings_artwork_full else Res.string.settings_artwork_square)
        SettingsTarget.ResumeOnReconnect -> enabled(preferences.resumeOnReconnect)
        SettingsTarget.ResumePerTrack -> enabled(preferences.resumePerTrack)
        SettingsTarget.LongTrackThreshold -> stringResource(Res.string.settings_long_minutes,
            (preferences.longTrackThresholdMs / 60_000L).toInt())
        SettingsTarget.ReplayGain -> stringResource(when (preferences.replayGain) {
            ReplayGainMode.Off -> Res.string.settings_replay_gain_off
            ReplayGainMode.Track -> Res.string.settings_replay_gain_track_short
            ReplayGainMode.Album -> Res.string.settings_replay_gain_album_short
        })
        SettingsTarget.Crossfade, SettingsTarget.CrossfadeDuration -> if (preferences.crossfadeDurationMs == 0L) stringResource(Res.string.settings_crossfade_off_ms, 0L)
            else stringResource(Res.string.settings_search_duration_value, preferences.crossfadeDurationMs)
        SettingsTarget.ManualCrossfadeDuration -> if (preferences.manualCrossfadeDurationMs == 0L) stringResource(Res.string.settings_crossfade_off_ms, 0L)
            else stringResource(Res.string.settings_search_duration_value, preferences.manualCrossfadeDurationMs)
        SettingsTarget.AmllLyrics -> enabled(preferences.amllEnabled)
        SettingsTarget.LrclibLyrics -> enabled(preferences.lrclibEnabled)
        SettingsTarget.ReduceMotion -> if (system.reduceMotion) stringResource(Res.string.settings_device_enabled) else enabled(preferences.reduceMotion)
        SettingsTarget.ReduceTransparency -> if (system.reduceTransparency) stringResource(Res.string.settings_device_enabled) else enabled(preferences.reduceTransparency)
        SettingsTarget.Equalizer -> enabled(eq.enabled)
        SettingsTarget.EqualizerPresets -> stringResource(eq.preset?.label ?: Res.string.eq_custom)
        SettingsTarget.OutputProfiles -> enabled(eq.automaticOutputs)
        SettingsTarget.HeadphoneCorrection -> eq.profile?.name ?: stringResource(Res.string.eq_headphones_none)
        SettingsTarget.MinimumDuration -> if (preferences.libraryFilter.minimumDurationMs == 0L) stringResource(Res.string.library_all_durations)
            else stringResource(Res.string.library_duration_seconds, (preferences.libraryFilter.minimumDurationMs / 1_000).toInt())
        SettingsTarget.ExcludedFolders -> pluralStringResource(Res.plurals.settings_search_excluded_count,
            preferences.libraryFilter.excludedFolders.size, preferences.libraryFilter.excludedFolders.size)
        SettingsTarget.PlayCountRule -> playCountRuleLabel(preferences.playCountRule)
        SettingsTarget.AlbumLayout -> stringResource(if (preferences.albumLayout == AlbumLayout.List) Res.string.library_layout_list else Res.string.library_layout_grid)
        SettingsTarget.TrackClickAction -> clickActionLabel(preferences.trackClickAction)
        SettingsTarget.QueueStart -> queueStartLabel(preferences.queueStart)
        SettingsTarget.QueueAddOrder -> queueOrderLabel(preferences.queueAddOrder)
        SettingsTarget.QueueReplaces -> enabled(preferences.queueReplaces)
        SettingsTarget.QueueEnd -> queueEndLabel(preferences.queueEnd)
        SettingsTarget.ShuffleBias -> shuffleBiasLabel(preferences.shuffleBias)
        SettingsTarget.KeepShuffle -> enabled(preferences.keepShuffleOnSelection)
        SettingsTarget.AppIcon -> stringResource(if (preferences.appIcon == AppIconChoice.Violet) Res.string.settings_app_icon_violet else Res.string.settings_app_icon_night)
        SettingsTarget.AudioFocus -> enabled(preferences.handleAudioFocus)
        SettingsTarget.PauseOnDisconnect -> enabled(preferences.pauseOnDisconnect)
        SettingsTarget.HeadsetMultiPress -> enabled(preferences.headsetMultiPress)
        SettingsTarget.HomeCustomization, SettingsTarget.Backup, SettingsTarget.About, SettingsTarget.SleepTimer -> null
    }
}

internal fun LazyListScope.settingsSearchResults(
    results: List<SettingsTarget>,
    documents: List<SettingsSearchDocument>,
    preferences: AppPreferences,
    equalizer: EqualizerState,
    system: UiAccessibility,
    onSelect: (SettingsTarget) -> Unit,
) {
    item(key = "search-count") {
        Column(Modifier.fillMaxWidth().padding(horizontal = pageInset + 4.dp, vertical = 12.dp)) {
            AppText(stringResource(Res.string.settings_search_results), AppTheme.typography.headline,
                modifier = Modifier.semantics { heading() })
            AppText(pluralStringResource(Res.plurals.settings_search_result_count, results.size, results.size),
                AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (results.isEmpty()) item(key = "search-empty") {
        Column(Modifier.fillMaxWidth().padding(horizontal = pageInset + 4.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(stringResource(Res.string.settings_search_empty), AppTheme.typography.headline)
            AppText(stringResource(Res.string.settings_search_empty_hint), AppTheme.typography.body,
                color = AppTheme.colors.secondaryLabel)
        }
    }
    items(results, key = { "search-${it.name}" }) { target ->
        val document = documents.first { it.target == target }
        val current = settingsCurrentValue(target, preferences, equalizer, system)
        LocalGlassLook.current.SettingsSearchResult(document.title, document.path,
            current?.let { stringResource(Res.string.settings_search_current, it) }, { onSelect(target) },
            Modifier.padding(horizontal = pageInset, vertical = 4.dp))
    }
}
