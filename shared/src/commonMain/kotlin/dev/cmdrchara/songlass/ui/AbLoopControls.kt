package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.formatDuration
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AbLoopControls(controller: PlaybackController, trackId: String, player: PlayerState, durationMs: Long) {
    val loop by controller.abLoop.collectAsState()
    var expanded by remember(trackId) { mutableStateOf(false) }
    val current = loop?.takeIf { it.trackId == trackId }
    val ready = player.status == PlayerStatus.Ready && durationMs >= PlaybackController.MIN_LOOP_MS
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val title = when {
            current?.endMs != null -> stringResource(Res.string.ab_loop_active, formatDuration(current.startMs), formatDuration(current.endMs!!))
            current != null -> stringResource(Res.string.ab_loop_waiting, formatDuration(current.startMs))
            else -> stringResource(Res.string.ab_loop_title)
        }
        ActionButton(title, { expanded = !expanded }, Modifier.fillMaxWidth(), icon = AppIcons.Repeat)
        if (expanded) {
            AppText(stringResource(Res.string.ab_loop_hint), AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
            ActionButton(stringResource(Res.string.ab_loop_set_a), { controller.markLoopStart(trackId) }, Modifier.fillMaxWidth(),
                enabled = ready && player.positionMs in 0..(durationMs - PlaybackController.MIN_LOOP_MS))
            ActionButton(stringResource(Res.string.ab_loop_set_b), { controller.markLoopEnd(trackId) }, Modifier.fillMaxWidth(),
                enabled = ready && current != null && player.positionMs <= durationMs && player.positionMs - current.startMs >= PlaybackController.MIN_LOOP_MS)
            if (current != null) ActionButton(stringResource(Res.string.ab_loop_clear), controller::clearAbLoop, Modifier.fillMaxWidth())
        }
    }
}
