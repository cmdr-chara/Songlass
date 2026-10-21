package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.core.equalizer.EqPreset
import dev.cmdrchara.songlass.core.equalizer.EqualizerState
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.AudioOutputDevice
import dev.cmdrchara.songlass.core.player.AudioOutputKind
import dev.cmdrchara.songlass.core.player.AudioOutputRoute
import dev.cmdrchara.songlass.core.player.CrossfadeKind
import dev.cmdrchara.songlass.core.player.CrossfadeSkipReason
import dev.cmdrchara.songlass.core.player.EqualizerProcessing
import dev.cmdrchara.songlass.core.player.PcmEncoding
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.InfoSheet
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class AudioInformationStage { File, Processing, Output }

/**
 * A short, ordered path with optional details for each stage. Both overlay primitives belong to
 * the active design system, including their scrolling, reduced motion and opaque surfaces.
 */
@Composable
fun AudioInformationSheet(state: AppState, track: Track, player: PlayerState) {
    val equalizer by state.equalizer.state.collectAsState()
    val settings by state.settings.state.collectAsState()
    var stage by remember(track.id) { mutableStateOf<AudioInformationStage?>(null) }
    LaunchedEffect(state.isSongInfoVisible) {
        if (!state.isSongInfoVisible) stage = null
    }
    fun showDetails(value: AudioInformationStage) {
        // ActionSheet dismisses before invoking an action. Keep this single logical overlay open.
        stage = value
        state.showSongInfo()
    }
    val file = stringResource(Res.string.audio_info_file)
    val processing = stringResource(Res.string.audio_info_processing)
    val output = stringResource(Res.string.audio_info_output)
    ActionSheet(
        visible = state.isSongInfoVisible && stage == null,
        title = stringResource(Res.string.audio_info_title),
        subtitle = track.title,
        artwork = rememberTrackArtwork(track),
        actions = listOf(
            SheetAction(file, AppIcons.MusicNote) { showDetails(AudioInformationStage.File) },
            SheetAction(processing, AppIcons.Equalizer) { showDetails(AudioInformationStage.Processing) },
            SheetAction(output, AppIcons.AudioOutput) { showDetails(AudioInformationStage.Output) },
        ),
        cancelLabel = stringResource(Res.string.action_close),
        onDismiss = state::closeSongInfo,
    )
    val selectedStage = stage
    val rows = when (selectedStage) {
        AudioInformationStage.File -> fileInformationRows(track, player)
        AudioInformationStage.Processing -> processingInformationRows(player, equalizer, settings)
        AudioInformationStage.Output -> outputInformationRows(player.outputRoute)
        null -> emptyList()
    }
    InfoSheet(
        visible = state.isSongInfoVisible && selectedStage != null,
        title = when (selectedStage) {
            AudioInformationStage.File -> file
            AudioInformationStage.Processing -> processing
            AudioInformationStage.Output -> output
            null -> stringResource(Res.string.audio_info_title)
        },
        rows = rows,
        closeLabel = stringResource(Res.string.action_close),
        onDismiss = state::closeSongInfo,
    )
}

@Composable
private fun fileInformationRows(track: Track, player: PlayerState): List<InfoRow> {
    val source = player.audioFormat
    val unknown = stringResource(Res.string.audio_unknown)
    return listOfNotNull(
        InfoRow(stringResource(Res.string.playback_info_title), track.title),
        InfoRow(stringResource(Res.string.playback_info_artist), artistName(track.artist)),
        track.album?.let { InfoRow(stringResource(Res.string.playback_info_album), it) },
        track.albumArtist?.let { InfoRow(stringResource(Res.string.audio_album_artist), it) },
        InfoRow(stringResource(Res.string.playback_info_duration), formatDuration(player.durationMs.takeIf { it > 0 } ?: track.durationMs)),
        InfoRow(stringResource(Res.string.audio_source_codec), source?.codec ?: unknown),
        InfoRow(stringResource(Res.string.audio_source_rate), source?.sampleRateHz?.let { stringResource(Res.string.audio_rate_hz, it) } ?: unknown),
        InfoRow(stringResource(Res.string.audio_source_depth), source?.bitDepth?.let { stringResource(Res.string.audio_integer_depth, it) } ?: unknown),
        InfoRow(stringResource(Res.string.audio_channels_label), source?.channelCount?.let { pluralStringResource(Res.plurals.audio_channels, it, it) } ?: unknown),
        source?.bitRate?.let { InfoRow(stringResource(Res.string.playback_info_bitrate), stringResource(Res.string.audio_bitrate_bps, it)) },
        track.path?.let { InfoRow(stringResource(Res.string.audio_file_path), it) },
        if (track.path == null) track.folder?.let { InfoRow(stringResource(Res.string.playback_info_folder), it) } else null,
        if (!track.uri.startsWith("demo:")) InfoRow(stringResource(Res.string.playback_info_storage), stringResource(Res.string.playback_local_file)) else null,
        InfoRow(stringResource(Res.string.audio_source_properties), stringResource(Res.string.audio_source_note)),
    )
}

@Composable
private fun processingInformationRows(player: PlayerState, equalizer: EqualizerState, settings: AppPreferences): List<InfoRow> {
    val unknown = stringResource(Res.string.audio_unknown)
    val decimal = stringResource(Res.string.eq_decimal_separator)
    val pcm = player.processing
    val pcmFormat = pcm?.let {
        listOfNotNull(
            stringResource(Res.string.audio_rate_hz, it.sampleRateHz),
            pluralStringResource(Res.plurals.audio_channels, it.channelCount, it.channelCount),
            when (it.encoding) {
                PcmEncoding.Signed16 -> stringResource(Res.string.audio_integer_depth, 16)
                PcmEncoding.Float32 -> stringResource(Res.string.audio_float_depth)
                null -> null
            },
        ).joinToString(" · ")
    }
    val observedEq = stringResource(when (pcm?.equalizer) {
        EqualizerProcessing.AwaitingAudio -> Res.string.audio_eq_pending
        EqualizerProcessing.Bypassed -> Res.string.audio_eq_flat
        EqualizerProcessing.Active -> Res.string.audio_eq_active
        EqualizerProcessing.Unsupported -> Res.string.audio_eq_unsupported
        null -> Res.string.audio_unknown
    })
    val application = player.replayGainApplication
    val mode = application?.mode ?: settings.replayGain
    val replayGainMode = stringResource(when (mode) {
        ReplayGainMode.Off -> Res.string.settings_replay_gain_off
        ReplayGainMode.Track -> Res.string.audio_rg_track
        ReplayGainMode.Album -> Res.string.audio_rg_album
    })
    val adjustment = when {
        application == null -> unknown
        mode != ReplayGainMode.Off && player.replayGain?.isEmpty != false -> stringResource(Res.string.audio_rg_no_tags)
        else -> application.gainDb?.let { audioGainLabel(it, decimal) } ?: stringResource(Res.string.audio_rg_zero)
    }
    val transition = player.crossfadeStatus
    val transitionResult = transition?.let {
        val reason = it.skipReason
        when {
            reason != null -> stringResource(when (reason) {
                CrossfadeSkipReason.Disabled -> Res.string.settings_crossfade_reason_disabled
                CrossfadeSkipReason.Unsupported -> Res.string.settings_crossfade_reason_unsupported
                CrossfadeSkipReason.NotPlaying -> Res.string.settings_crossfade_reason_not_playing
                CrossfadeSkipReason.SameTrack -> Res.string.settings_crossfade_reason_same_track
                CrossfadeSkipReason.UnknownDuration -> Res.string.settings_crossfade_reason_unknown_duration
                CrossfadeSkipReason.TooShort -> Res.string.settings_crossfade_reason_too_short
                CrossfadeSkipReason.NotReady -> Res.string.settings_crossfade_reason_not_ready
                CrossfadeSkipReason.PrepareFailed -> Res.string.settings_crossfade_reason_prepare_failed
                CrossfadeSkipReason.Interrupted -> Res.string.settings_crossfade_reason_interrupted
            })
            it.effectiveDurationMs > 0 -> stringResource(Res.string.audio_transition_applied, it.requestedDurationMs, it.effectiveDurationMs)
            else -> stringResource(Res.string.audio_transition_no_overlap)
        }
    }
    return listOfNotNull(
        InfoRow(stringResource(Res.string.audio_processor_format), pcmFormat ?: unknown),
        InfoRow(stringResource(Res.string.audio_eq_observed), observedEq),
        InfoRow(stringResource(Res.string.audio_eq_configured), equalizerCurveLabel(equalizer.enabled, equalizer.preset, equalizer.profile?.name)),
        InfoRow(stringResource(Res.string.eq_outputs), stringResource(if (equalizer.usesOutputProfile) Res.string.eq_outputs_bound else Res.string.eq_outputs_manual)),
        pcm?.takeIf { it.equalizer == EqualizerProcessing.Active }?.let {
            InfoRow(stringResource(Res.string.audio_eq_headroom), audioGainLabel(it.headroomDb, decimal))
        },
        InfoRow(stringResource(Res.string.settings_replay_gain), replayGainMode),
        InfoRow(stringResource(Res.string.audio_rg_applied), adjustment),
        player.replayGain?.trackGainDb?.takeIf(Float::isFinite)?.let { InfoRow(stringResource(Res.string.audio_rg_track_tag), audioGainLabel(it.toDouble(), decimal)) },
        player.replayGain?.albumGainDb?.takeIf(Float::isFinite)?.let { InfoRow(stringResource(Res.string.audio_rg_album_tag), audioGainLabel(it.toDouble(), decimal)) },
        InfoRow(stringResource(Res.string.audio_rg_note), stringResource(Res.string.audio_rg_behavior)),
        InfoRow(stringResource(Res.string.audio_transition_automatic), stringResource(Res.string.audio_transition_duration, settings.crossfadeDurationMs)),
        InfoRow(stringResource(Res.string.audio_transition_manual), stringResource(Res.string.audio_transition_duration, settings.manualCrossfadeDurationMs)),
        transitionResult?.let {
            InfoRow(stringResource(if (transition.kind == CrossfadeKind.Automatic) Res.string.settings_crossfade_last_automatic else Res.string.settings_crossfade_last_manual), it)
        },
        InfoRow(stringResource(Res.string.audio_transition_last), stringResource(Res.string.audio_transition_note)),
        InfoRow(stringResource(Res.string.audio_source_properties), stringResource(Res.string.audio_processor_note)),
    )
}

/** Used by Now Playing as well, so its label cannot drift from the EQ route observation. */
@Composable
internal fun audioOutputName(route: AudioOutputRoute): String = when {
    route.devices.isEmpty() -> stringResource(Res.string.audio_output_unknown)
    route.devices.size > 1 -> stringResource(Res.string.audio_output_multiple)
    else -> outputDeviceName(route.devices.single())
}

@Composable
private fun outputDeviceName(device: AudioOutputDevice): String =
    if (device.kind == AudioOutputKind.BuiltIn) stringResource(Res.string.output_this_phone)
    else device.name ?: outputKindName(device.kind)

@Composable
private fun outputKindName(kind: AudioOutputKind): String = stringResource(when (kind) {
    AudioOutputKind.BuiltIn -> Res.string.audio_output_builtin
    AudioOutputKind.Wired -> Res.string.audio_output_wired
    AudioOutputKind.Bluetooth -> Res.string.audio_output_bluetooth
    AudioOutputKind.Usb -> Res.string.audio_output_usb
    AudioOutputKind.Hdmi -> Res.string.audio_output_hdmi
    AudioOutputKind.Other -> Res.string.audio_output_other
})

@Composable
private fun outputInformationRows(route: AudioOutputRoute?): List<InfoRow> = buildList {
    add(InfoRow(stringResource(Res.string.audio_output_selected), route?.let { audioOutputName(it) } ?: stringResource(Res.string.audio_output_unknown)))
    route?.singleDevice?.let { add(InfoRow(stringResource(Res.string.audio_output_type), outputKindName(it.kind))) }
    if (route != null && route.devices.size > 1) route.devices.forEachIndexed { index, device ->
        add(InfoRow(stringResource(Res.string.audio_output_device, index + 1), "${outputDeviceName(device)} · ${outputKindName(device.kind)}"))
    }
    add(InfoRow(stringResource(Res.string.audio_output_dac), stringResource(Res.string.audio_unknown)))
    add(InfoRow(stringResource(Res.string.audio_output_limits), stringResource(Res.string.audio_output_note)))
}

@Composable
internal fun equalizerCurveLabel(enabled: Boolean, preset: EqPreset?, profileName: String?): String = listOfNotNull(
    stringResource(if (enabled) Res.string.eq_on else Res.string.eq_off),
    stringResource(preset?.label ?: Res.string.eq_custom),
    profileName,
).joinToString(" · ")

private fun audioGainLabel(db: Double, decimal: String): String {
    if (abs(db) >= 1_000.0) return "${if (db > 0) "+" else ""}${db.toString().replace(".", decimal)} dB"
    val tenths = (db * 10).roundToInt()
    val number = "${abs(tenths) / 10}" + if (tenths % 10 == 0) "" else "$decimal${abs(tenths) % 10}"
    val sign = if (tenths > 0) "+" else if (tenths < 0) "−" else ""
    return "$sign$number dB"
}
