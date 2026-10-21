package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.restore_progress_body
import dev.cmdrchara.songlass.shared.resources.restore_progress_title
import org.jetbrains.compose.resources.stringResource

/** Live checks also consume input in the frame before the modal is recomposed. */
internal fun Modifier.restoreInputBarrier(gate: RestoreMutationGate): Modifier =
    onPreviewKeyEvent { !gate.canMutate }.pointerInput(gate) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!gate.canMutate) event.changes.forEach { it.consume() }
            }
        }
    }

/** Placed after the other root hosts so its window stays above their pending dialogs. */
@Composable
internal fun RestoreProgressHost(restoring: Boolean) {
    val focus = LocalFocusManager.current
    LaunchedEffect(restoring) { if (restoring) focus.clearFocus(force = true) }
    if (!restoring) return
    val title = stringResource(Res.string.restore_progress_title)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(pageInset),
            contentAlignment = Alignment.Center) {
            SettingsGroup(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(20.dp).semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppText(title, AppTheme.typography.title3, modifier = Modifier.semantics { heading() })
                    AppText(stringResource(Res.string.restore_progress_body), AppTheme.typography.body,
                        color = AppTheme.colors.secondaryLabel)
                }
            }
        }
    }
}
