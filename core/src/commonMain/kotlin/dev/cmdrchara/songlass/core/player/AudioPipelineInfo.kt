package dev.cmdrchara.songlass.core.player

import dev.cmdrchara.songlass.core.equalizer.EqOutput
import kotlin.math.log10

/** PCM observed inside Songlass's processor, before Android's mixer and the output device. */
enum class PcmEncoding { Signed16, Float32 }

enum class EqualizerProcessing {
    /** The format was configured, but no buffer has used the current processor yet. */
    AwaitingAudio,
    Bypassed,
    Active,
    /** This processor leaves an unsupported PCM encoding untouched. */
    Unsupported,
}

/** Published only on processor configuration, applied-curve changes and reset, never each buffer. */
data class AudioProcessingInfo(
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: PcmEncoding?,
    val equalizer: EqualizerProcessing,
    /** The attenuation applied before the EQ filters, not a hardware clipping measurement. */
    val headroomDb: Double = 0.0,
    val filterCount: Int = 0,
) {
    init {
        require(sampleRateHz > 0 && channelCount > 0)
        require(headroomDb.isFinite() && headroomDb <= 0.0)
        require(filterCount >= 0)
    }
}

/** ReplayGain's own multiplier, excluding device volume, audio focus and a crossfade envelope. */
data class ReplayGainApplication(val mode: ReplayGainMode, val linearGain: Float) {
    init { require(linearGain.isFinite() && linearGain in 0f..1f) }

    /** Null means the applied multiplier is zero; never expose an infinite numeric dB label. */
    val gainDb: Double? get() = linearGain.takeIf { it > 0f }?.let { 20.0 * log10(it.toDouble()) }

    companion object {
        fun fromTags(tags: ReplayGainTags?, mode: ReplayGainMode) =
            ReplayGainApplication(mode, ReplayGain.volumeFor(tags, mode))
    }
}

enum class AudioOutputKind { BuiltIn, Wired, Bluetooth, Usb, Hdmi, Other }

/** An observed media route. Identity is null when Android cannot identify it for an EQ binding. */
data class AudioOutputDevice(val identity: String?, val name: String?, val kind: AudioOutputKind)

/**
 * Selected routes, never the list of connected devices or their advertised capabilities.
 * Empty means unknown; multiple devices are deliberately not reduced to an arbitrary first one.
 * No sample rate or depth is inferred here: these APIs do not reveal the final DAC format.
 */
data class AudioOutputRoute(val devices: List<AudioOutputDevice> = emptyList()) {
    val singleDevice: AudioOutputDevice? get() = devices.singleOrNull()

    /** Ambiguous or anonymous routing always uses the manual fallback. */
    val equalizerOutput: EqOutput? get() = singleDevice?.let { device ->
        device.identity?.takeIf(String::isNotBlank)?.let { EqOutput(it, device.name?.takeIf(String::isNotBlank) ?: it) }
    }
}
