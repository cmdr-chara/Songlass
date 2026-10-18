package dev.cmdrchara.songlass.core.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Playback pauses at [endsAtMs] (same clock as the timer's). */
    data class Until(val endsAtMs: Long) : SleepTimerState

    /** Playback pauses when the current track ends. */
    data object EndOfTrack : SleepTimerState
}

/** Pauses playback after a while or at the end of the current track. Lives as long as playback. */
class SleepTimer(
    private val controller: PlaybackController,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val timed = MutableStateFlow<SleepTimerState.Until?>(null)
    private var job: Job? = null

    val state: StateFlow<SleepTimerState> = combine(timed, controller.stopAfterCurrent) { until, endOfTrack ->
        when {
            endOfTrack -> SleepTimerState.EndOfTrack
            until != null -> until
            else -> SleepTimerState.Off
        }
    }.stateIn(scope, SharingStarted.Eagerly, SleepTimerState.Off)

    fun start(minutes: Int) {
        cancel()
        val duration = minutes * 60_000L
        timed.value = SleepTimerState.Until(clock() + duration)
        job = scope.launch {
            delay(duration)
            controller.pause()
            timed.value = null
        }
    }

    fun endOfTrack() {
        cancel()
        controller.setStopAfterCurrent(true)
    }

    fun cancel() {
        job?.cancel()
        job = null
        timed.value = null
        controller.setStopAfterCurrent(false)
    }
}
