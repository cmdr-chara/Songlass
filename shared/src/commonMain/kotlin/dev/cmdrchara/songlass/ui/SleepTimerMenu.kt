package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.core.playback.SleepTimerState
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.action_cancel
import dev.cmdrchara.songlass.shared.resources.sleep_end_of_track
import dev.cmdrchara.songlass.shared.resources.sleep_minutes
import dev.cmdrchara.songlass.shared.resources.sleep_off
import dev.cmdrchara.songlass.shared.resources.sleep_remaining
import dev.cmdrchara.songlass.shared.resources.sleep_title
import dev.cmdrchara.songlass.shared.resources.sleep_until_end
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Minutes left on a running timer, rounded up; null when no timed timer runs. */
@Composable
internal fun sleepMinutesLeft(state: SleepTimerState): Int? {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(state) {
        while (state is SleepTimerState.Until) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(15_000)
        }
    }
    return (state as? SleepTimerState.Until)?.let { ((it.endsAtMs - now + 59_999) / 60_000).toInt().coerceAtLeast(1) }
}

@Composable
fun SleepTimerMenu(state: AppState) {
    val timer by state.sleepTimer.state.collectAsState()
    val minutesLeft = sleepMinutesLeft(timer)
    ActionSheet(
        visible = state.isSleepMenuOpen,
        title = stringResource(Res.string.sleep_title),
        subtitle = when {
            timer is SleepTimerState.EndOfTrack -> stringResource(Res.string.sleep_until_end)
            minutesLeft != null -> pluralStringResource(Res.plurals.sleep_remaining, minutesLeft, minutesLeft)
            else -> null
        },
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = state::closeSleepMenu,
        actions = buildList {
            for (minutes in listOf(15, 30, 45, 60)) {
                add(SheetAction(pluralStringResource(Res.plurals.sleep_minutes, minutes, minutes), AppIcons.Timer) { state.sleepTimer.start(minutes) })
            }
            add(SheetAction(stringResource(Res.string.sleep_end_of_track), AppIcons.Next) { state.sleepTimer.endOfTrack() })
            if (timer != SleepTimerState.Off) add(SheetAction(stringResource(Res.string.sleep_off), AppIcons.Close) { state.sleepTimer.cancel() })
        },
    )
}
