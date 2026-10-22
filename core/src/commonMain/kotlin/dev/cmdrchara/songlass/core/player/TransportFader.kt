package dev.cmdrchara.songlass.core.player

/** Independent, opt-in transport fades. Durations are plans, not measured acoustic timings. */
data class TransportFadeSettings(val pauseMs: Long = 0, val stopMs: Long = 0, val seekMs: Long = 0) {
    init { require(listOf(pauseMs, stopMs, seekMs).all { it in 0..MAX_DURATION_MS }) }

    companion object { const val MAX_DURATION_MS = 3_000L }
}

sealed interface TransportAction {
    data object Pause : TransportAction
    data object Stop : TransportAction
    data class Seek(val positionMs: Long) : TransportAction
}

/** One native action at most. The adapter applies gain before executing the action. */
data class TransportFadeStep(val gain: Float, val action: TransportAction? = null)

/**
 * Main-thread state machine, independent of any decoder or scheduler. A newer command cancels
 * the old action, starting at the last applied gain. System interruptions call [cancel] and
 * pause immediately. Seek uses half its duration before the seek and half after READY; buffer
 * time is excluded. A missing READY eventually restores gain without issuing Play.
 */
class TransportFader {
    private enum class Phase { Out, WaitingForSeek, In }
    private var phase: Phase? = null
    private var startedAtMs = 0L
    private var durationMs = 0L
    private var startGain = 1f
    private var seekInMs = 0L
    var gain = 1f
        private set
    var pendingAction: TransportAction? = null
        private set
    var revision = 0L
        private set
    val isRunning: Boolean get() = phase != null
    val awaitingSeek: Boolean get() = phase == Phase.WaitingForSeek
    val blocksAdvance: Boolean get() = phase == Phase.Out || awaitingSeek

    fun begin(action: TransportAction, durationMs: Long, audible: Boolean, nowMs: Long): TransportFadeStep {
        require(durationMs in 0..TransportFadeSettings.MAX_DURATION_MS)
        require(action !is TransportAction.Seek || action.positionMs >= 0)
        if (action == pendingAction && phase == Phase.Out) return TransportFadeStep(gain)
        revision++
        pendingAction = action
        if (!audible || durationMs == 0L) {
            phase = null
            pendingAction = null
            gain = 1f
            return TransportFadeStep(gain, action)
        }
        phase = Phase.Out
        startedAtMs = nowMs
        startGain = gain
        seekInMs = if (action is TransportAction.Seek) durationMs - durationMs / 2 else 0
        this.durationMs = if (action is TransportAction.Seek) durationMs / 2 else durationMs
        return tick(nowMs)
    }

    /** Called before native Play, so a paused decoder cannot emit a full-volume first buffer. */
    fun resume(durationMs: Long, nowMs: Long, fromSilence: Boolean = false): TransportFadeStep {
        require(durationMs in 0..TransportFadeSettings.MAX_DURATION_MS)
        revision++
        pendingAction = null
        if (fromSilence && durationMs > 0) gain = 0f
        phase = if (durationMs > 0 && gain < 1f) Phase.In else null
        startedAtMs = nowMs
        this.durationMs = durationMs
        startGain = gain
        if (phase == null) gain = 1f
        return TransportFadeStep(gain)
    }

    fun seekReady(nowMs: Long): TransportFadeStep {
        if (!awaitingSeek) return TransportFadeStep(gain)
        phase = Phase.In
        startedAtMs = nowMs
        durationMs = seekInMs
        startGain = gain
        return tick(nowMs)
    }

    fun tick(nowMs: Long): TransportFadeStep {
        val current = phase ?: return TransportFadeStep(gain)
        // Monotonic clock regressions and pathological elapsed values never execute old intent.
        val elapsed = nowMs - startedAtMs
        if (elapsed < 0) return cancel()
        if (current == Phase.WaitingForSeek) {
            return if (elapsed >= SEEK_TIMEOUT_MS) cancel() else TransportFadeStep(gain)
        }
        val progress = if (durationMs == 0L) 1f else (elapsed.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
        gain = if (current == Phase.Out) startGain * (1f - progress) else startGain + (1f - startGain) * progress
        if (progress < 1f) return TransportFadeStep(gain)
        val action = pendingAction
        pendingAction = null
        phase = if (action is TransportAction.Seek) Phase.WaitingForSeek else null
        startedAtMs = nowMs
        return TransportFadeStep(gain, action)
    }

    /** Used when the file ends before a pending fade can finish. Never advances the queue. */
    fun finishNow(): TransportFadeStep {
        val action = pendingAction
        cancel()
        return TransportFadeStep(gain, action)
    }

    fun cancel(): TransportFadeStep {
        revision++
        phase = null
        pendingAction = null
        gain = 1f
        return TransportFadeStep(gain)
    }

    companion object { const val SEEK_TIMEOUT_MS = 5_000L }
}
