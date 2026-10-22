package dev.cmdrchara.songlass.core.player

import dev.cmdrchara.songlass.core.storage.KeyValueStore

/** Remaining player volume during a short interrupt; independent of user volume and DSP. */
enum class FocusDucking(val volume: Float) {
    Quiet(.1f), Normal(.2f), Soft(.5f), Pause(1f);
}

data class AudioFocusSettings(
    val ducking: FocusDucking = FocusDucking.Normal,
    val resumeOnFocusGain: Boolean = true,
    val resumeAfterCall: Boolean = true,
) {
    companion object {
        const val DUCKING_KEY = "settings.focus_ducking"
        const val RESUME_KEY = "settings.resume_on_focus_gain"
        const val CALL_RESUME_KEY = "settings.resume_after_call"
        val keys = setOf(DUCKING_KEY, RESUME_KEY, CALL_RESUME_KEY)
        fun read(store: KeyValueStore) = AudioFocusSettings(
            FocusDucking.entries.firstOrNull { it.name == store.getString(DUCKING_KEY) } ?: FocusDucking.Normal,
            store.getString(RESUME_KEY)?.toBooleanStrictOrNull() ?: true,
            store.getString(CALL_RESUME_KEY)?.toBooleanStrictOrNull() ?: true,
        )
    }
}

enum class FocusChange { Gain, Duck, TransientLoss, PermanentLoss }
enum class FocusPlaybackAction { None, Pause, Resume }
data class FocusDecision(val action: FocusPlaybackAction = FocusPlaybackAction.None, val gain: Float = 1f)

/**
 * Single-session policy for both voices. It remembers only interruptions of an existing playback
 * intent. User commands, output loss, overlap cancellation and permanent loss invalidate resumption.
 * Call-like audio modes can arrive before or after transient focus loss; both orders are supported.
 */
class AudioFocusPolicy {
    private data class Pending(val call: Boolean, val canResume: Boolean, val allowed: Boolean)
    var settings = AudioFocusSettings()
        private set
    private var pending: Pending? = null
    private var focus = FocusChange.Gain
    var callActive = false
        private set
    var gain = 1f
        private set

    fun configure(value: AudioFocusSettings, playing: Boolean, canResume: Boolean = true): FocusDecision {
        settings = value
        pending = pending?.let { it.copy(allowed = it.allowed && if (it.call) value.resumeAfterCall else value.resumeOnFocusGain) }
        return if (focus == FocusChange.Duck) changed(FocusChange.Duck, playing, canResume) else FocusDecision(gain = gain)
    }

    fun cancelResume() { pending = null }

    /** Explicit Play must acquire native focus first, and never schedules a resume during a call. */
    fun explicitPlay(): Boolean {
        pending = null
        return !callActive
    }

    fun changed(change: FocusChange, playing: Boolean, canResume: Boolean = true): FocusDecision {
        focus = change
        return when (change) {
            FocusChange.Gain -> finishIfPossible()
            FocusChange.PermanentLoss -> {
                pending = null
                gain = 1f
                FocusDecision(FocusPlaybackAction.Pause, gain)
            }
            FocusChange.Duck -> if (!callActive && settings.ducking != FocusDucking.Pause) {
                gain = settings.ducking.volume
                FocusDecision(gain = gain)
            } else pauseForInterrupt(playing, canResume)
            FocusChange.TransientLoss -> pauseForInterrupt(playing, canResume)
        }
    }

    fun callChanged(active: Boolean, playing: Boolean, canResume: Boolean = true): FocusDecision {
        if (active == callActive) return FocusDecision(gain = gain)
        callActive = active
        if (active) {
            val previous = pending
            if (playing || previous != null) pending = Pending(true,
                previous?.canResume ?: canResume, settings.resumeAfterCall)
            gain = 1f
            return FocusDecision(FocusPlaybackAction.Pause, gain)
        }
        return finishIfPossible()
    }

    private fun pauseForInterrupt(playing: Boolean, canResume: Boolean): FocusDecision {
        if (playing && pending == null) pending = Pending(callActive, canResume,
            if (callActive) settings.resumeAfterCall else settings.resumeOnFocusGain)
        gain = 1f
        return FocusDecision(FocusPlaybackAction.Pause, gain)
    }

    private fun finishIfPossible(): FocusDecision {
        if (callActive || focus != FocusChange.Gain) return FocusDecision(gain = gain)
        gain = 1f
        val resume = pending?.let { it.canResume && it.allowed } == true
        pending = null
        return FocusDecision(if (resume) FocusPlaybackAction.Resume else FocusPlaybackAction.None, gain)
    }
}
