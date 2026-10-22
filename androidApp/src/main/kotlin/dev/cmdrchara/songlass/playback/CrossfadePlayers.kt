package dev.cmdrchara.songlass.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.ReplayGainTags

/** Attributes shared by every decoder and the session focus request. */
val PlaybackAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()

/**
 * One ExoPlayer with its own audio sink and [EqualizerProcessor], plus what the crossfade needs to
 * know about the track it plays. [Media3AudioPlayer] keeps two of them when crossfade is on: the
 * active one plays the current track, the other holds the next track prepared (or is fading out).
 *
 * Every voice runs the full equalizer in double precision on its own audio; nothing is bypassed
 * during an overlap.
 *
 * Native audio focus belongs to the session. [ownsFocus] only identifies the current logical
 * owner for transition diagnostics; no voice requests focus independently.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerVoice(
    val player: ExoPlayer,
    val equalizer: EqualizerProcessor?,
    focusOwner: Boolean = false,
) {
    var track: Track? = null
    var replayGain: ReplayGainTags? = null

    /** The track's own loudness (ReplayGain), independent of the overlap. */
    var baseVolume = 1f

    /** Overlap gain, 0..1; 1 outside an overlap. */
    var envelope = 1f

    /** User transport envelope, multiplied independently of ReplayGain and track overlap. */
    var transportGain = 1f

    /** Session focus attenuation, identical on both voices and independent of every envelope. */
    var focusGain = 1f

    var ownsFocus = focusOwner
        private set

    val volume: Float get() = (baseVolume * envelope * transportGain * focusGain).coerceIn(0f, 1f)

    fun applyVolume() {
        player.volume = volume
    }

    fun setFocusOwner(owner: Boolean) { ownsFocus = owner }

    fun applyInterruptionPolicy(pauseOnDisconnect: Boolean) {
        player.setAudioAttributes(PlaybackAudioAttributes, false)
        player.setHandleAudioBecomingNoisy(pauseOnDisconnect)
    }

    /** Unloads the track and releases its file. The player stays usable. */
    fun reset() {
        track = null
        replayGain = null
        equalizer?.setReplayGain(dev.cmdrchara.songlass.core.player.ReplayGainPlan(dev.cmdrchara.songlass.core.player.ReplayGainMode.Off, 1.0))
        envelope = 1f
        transportGain = 1f
        player.pause()
        player.stop()
        player.clearMediaItems()
    }
}

enum class CrossfadeStage { Off, Idle, Armed, PreparingManual, Fading }

/**
 * Snapshot for runtime checks (debug probes, tests on a device). Holds no track names or other
 * user data and is never logged by the player itself.
 */
data class CrossfadeDiagnostics(
    val configuredMs: Long,
    val stage: CrossfadeStage,
    val secondPlayerCreated: Boolean,
    /** The prepared next track is ready to start. */
    val standbyReady: Boolean,
    val overlapsStarted: Int,
    val overlapsCompleted: Int,
    /** The next track started right as the current one ended, without overlap. */
    val handOversWithoutOverlap: Int,
    /** The next track was joined gaplessly although crossfade was on. */
    val gaplessFallbacks: Int,
    /** Why the last fallback happened: "plan", "standby-failed" or "standby-late"; null if none yet. */
    val lastFallback: String?,
    /** 0..1 while stage is [CrossfadeStage.Fading]. */
    val progress: Float,
    val outgoingVolume: Float,
    val incomingVolume: Float,
    val manualConfiguredMs: Long = 0,
)
