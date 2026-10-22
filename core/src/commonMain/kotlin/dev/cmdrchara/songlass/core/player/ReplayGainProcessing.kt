package dev.cmdrchara.songlass.core.player

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Explicit opt-in preserves the previous attenuation-only behaviour after an upgrade. */
data class ReplayGainSettings(
    val enabled: Boolean = false,
    val preampDb: Int = 0,
    val missingPreampDb: Int = 0,
    val limiter: Boolean = true,
) {
    init { require(preampDb in -20..20 && missingPreampDb in -20..20) }

    companion object {
        fun preampFromSlider(position: Float): Int {
            require(position.isFinite())
            return (position.coerceIn(0f, 1f) * 40f).roundToInt() - 20
        }
    }
}

/** The static multiplier before the linked sample-peak limiter, excluding output volume. */
data class ReplayGainPlan(val mode: ReplayGainMode, val gain: Double, val preampDb: Int = 0, val limiter: Boolean = false) {
    init { require(gain.isFinite() && gain in 0.0..MAX_GAIN && preampDb in -20..20) }
    val application: ReplayGainApplication get() = ReplayGainApplication(mode, gain.toFloat(), preampDb, limiter)

    companion object {
        // Bad/outlier metadata must never request an unbounded PCM gain.
        const val MAX_GAIN = 100.0
        fun from(tags: ReplayGainTags?, mode: ReplayGainMode, settings: ReplayGainSettings): ReplayGainPlan {
            if (mode == ReplayGainMode.Off) return ReplayGainPlan(mode, 1.0)
            if (!settings.enabled) return ReplayGainPlan(mode, ReplayGain.volumeFor(tags, mode).toDouble())
            val track = tags?.trackGainDb?.takeIf { it.isFinite() }
            val album = tags?.albumGainDb?.takeIf { it.isFinite() }
            val db = if (mode == ReplayGainMode.Album) album ?: track else track ?: album
            val peak = (if (mode == ReplayGainMode.Album) {
                if (album != null) tags.albumPeak else tags?.trackPeak
            } else if (track != null) tags.trackPeak else tags?.albumPeak)?.takeIf { it.isFinite() && it > 0f }
            val preamp = if (db == null) settings.missingPreampDb else settings.preampDb
            var gain = 10.0.pow(((db?.toDouble() ?: 0.0) + preamp).coerceIn(-200.0, 40.0) / 20.0)
            if (settings.limiter && peak != null) gain = minOf(gain, 1.0 / peak)
            return ReplayGainPlan(mode, gain.coerceIn(0.0, MAX_GAIN), preamp, settings.limiter)
        }
    }
}

/**
 * Per-decoder PCM gain and stereo/multichannel-linked sample-peak limiter. Instant attack,
 * exponential 50 ms release; no lookahead or inter-sample/true-peak claim. Called on a complete
 * frame after EQ and before integer conversion. No allocation in the audio loop.
 */
class ReplayGainDsp(sampleRateHz: Int) {
    init { require(sampleRateHz > 0) }
    private val release = exp(-1.0 / (sampleRateHz * 0.05))
    var reduction = 1.0
        private set

    fun processFrame(frame: DoubleArray, plan: ReplayGainPlan) {
        var peak = 0.0
        for (i in frame.indices) {
            val sample = frame[i]
            val amplified = if (sample.isFinite()) sample * plan.gain else 0.0
            frame[i] = if (amplified.isFinite()) amplified else 0.0
            peak = maxOf(peak, abs(frame[i]))
        }
        if (!plan.limiter) { reduction = 1.0; return }
        val target = if (peak > 1.0) 1.0 / peak else 1.0
        reduction = minOf(target, 1.0 - (1.0 - reduction) * release)
        for (i in frame.indices) frame[i] = (frame[i] * reduction).coerceIn(-1.0, 1.0)
    }

    fun reset() { reduction = 1.0 }
}
