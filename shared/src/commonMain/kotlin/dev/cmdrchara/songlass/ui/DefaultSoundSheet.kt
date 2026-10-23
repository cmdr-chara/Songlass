package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.core.sound.DefaultSoundTarget
import dev.cmdrchara.songlass.core.model.isSameFileAs
import dev.cmdrchara.songlass.designsystem.PlaybackToolGroup
import dev.cmdrchara.songlass.designsystem.PlaybackToolRow
import dev.cmdrchara.songlass.designsystem.PlaybackToolsSheet
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** Uses each look's existing audio-tool sheet so explanatory text remains readable and scrollable. */
@Composable
internal fun DefaultSoundSheet(state: AppState): Boolean {
    var operationRequest by remember { mutableStateOf<DefaultSoundRequest?>(null) }
    var result by remember { mutableStateOf<DefaultSoundResult?>(null) }
    val request = state.defaultSoundRequest
    val platform = rememberDefaultSoundActions(
        isSourceCurrent = { track ->
            request != null && state.defaultSoundRequest === request &&
                request.track.isSameFileAs(track) && state.isDefaultSoundSourceCurrent(track)
        },
        onResult = { outcome ->
            if (request != null && operationRequest === request && state.finishDefaultSound(request)) {
                result = outcome
                operationRequest = null
            }
        },
    )
    val loading by state.isLibraryLoading.collectAsState()
    val restoring by state.isRestoringBackup.collectAsState()
    val access by state.libraryAccess.collectAsState()
    val complete by state.hasCompleteLibraryScan.collectAsState()
    val failure by state.libraryRefreshFailure.collectAsState()
    val tracks by state.allTracks.collectAsState()
    LaunchedEffect(request) { result = null }
    LaunchedEffect(request, loading, restoring, access, complete, failure, tracks) {
        if (request != null && !state.isDefaultSoundSourceCurrent(request.track)) state.invalidateDefaultSound(request)
    }
    if (request == null || platform == null) return platform != null
    val ringtone = request.target == DefaultSoundTarget.Ringtone
    val busy = state.defaultSoundApplying || platform.isApplying
    val invalid = state.defaultSoundInvalidated || result == DefaultSoundResult.SourceChanged
    val applied = result == DefaultSoundResult.Applied
    val title = stringResource(if (ringtone) Res.string.default_sound_ringtone_confirm else Res.string.default_sound_alarm_confirm)
    val description = stringResource(if (ringtone) Res.string.default_sound_ringtone_copy else Res.string.default_sound_alarm_copy)
    val rows = mutableListOf(
        PlaybackToolRow(request.track.title, artistName(request.track.artist)),
        PlaybackToolRow(stringResource(Res.string.default_sound_copy_title), description),
    )
    if (result == DefaultSoundResult.Failed) rows += PlaybackToolRow(stringResource(Res.string.default_sound_notice), stringResource(Res.string.default_sound_failed))
    when {
        applied -> rows += PlaybackToolRow(stringResource(Res.string.default_sound_success_title),
            stringResource(if (ringtone) Res.string.default_sound_ringtone_applied else Res.string.default_sound_alarm_applied))
        invalid -> rows += PlaybackToolRow(stringResource(Res.string.default_sound_notice), stringResource(Res.string.default_sound_source_changed))
        busy -> rows += PlaybackToolRow(stringResource(Res.string.default_sound_applying), "")
        !platform.permissionGranted -> {
            rows += PlaybackToolRow(stringResource(Res.string.default_sound_permission_title), stringResource(Res.string.default_sound_permission_body))
            rows += PlaybackToolRow(title = stringResource(Res.string.default_sound_open_settings), value = "",
                icon = AppIcons.Settings, prominent = true, onClick = {
                    if (state.defaultSoundRequest === request && !state.defaultSoundApplying && !platform.isApplying) {
                        operationRequest = request
                        result = null
                        platform.openSettings()
                    }
            })
        }
        else -> {
            rows += PlaybackToolRow(title = stringResource(Res.string.default_sound_set), value = "",
                icon = AppIcons.MusicNote, prominent = true, onClick = {
                    // The backend rechecks both permission and source before it writes.
                    if (state.defaultSoundRequest === request && !platform.isApplying && platform.permissionGranted && state.beginDefaultSound(request)) {
                        result = null
                        operationRequest = request
                        platform.apply(request.track, request.target)
                    }
            })
        }
    }
    PlaybackToolsSheet(
        visible = true,
        title = title,
        subtitle = null,
        groups = listOf(PlaybackToolGroup(null, rows)),
        closeLabel = stringResource(if (applied) Res.string.action_close else Res.string.action_cancel),
        backLabel = null,
        onBack = null,
        onDismiss = { if (!busy && state.defaultSoundRequest === request) state.closeDefaultSound() },
    )
    return true
}
