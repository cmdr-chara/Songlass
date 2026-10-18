package dev.cmdrchara.songlass.core.player

import kotlin.math.pow

/** Which ReplayGain value evens out loudness. */
enum class ReplayGainMode {
    Off,

    /** Every track at the same loudness (good for shuffle). */
    Track,

    /** Whole albums at the same loudness, keeping the differences between their tracks. */
    Album,
}

/** ReplayGain tags of a file: gains in dB, peaks as linear sample values (1.0 = full scale). */
data class ReplayGainTags(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
) {
    val isEmpty: Boolean get() = trackGainDb == null && albumGainDb == null

    companion object {
        /**
         * Reads REPLAYGAIN_* tags from [entries] (key → value, any case, as found in Vorbis
         * comments, ID3 TXXX frames or MP4 freeform atoms). Values such as "-6.48 dB" or
         * "0.988" are accepted; unreadable ones are ignored. Null when there is no gain.
         */
        fun parse(entries: Map<String, String>): ReplayGainTags? {
            val byKey = entries.mapKeys { it.key.trim().lowercase() }
            fun number(key: String): Float? = byKey[key]
                ?.trim()
                ?.removeSuffix("dB")?.removeSuffix("db")?.removeSuffix("DB")
                ?.trim()
                ?.replace(',', '.')
                ?.toFloatOrNull()
                ?.takeIf { it.isFinite() }
            val tags = ReplayGainTags(
                trackGainDb = number("replaygain_track_gain"),
                trackPeak = number("replaygain_track_peak")?.takeIf { it > 0f },
                albumGainDb = number("replaygain_album_gain"),
                albumPeak = number("replaygain_album_peak")?.takeIf { it > 0f },
            )
            return tags.takeUnless { it.isEmpty }
        }
    }
}

object ReplayGain {
    /**
     * Player volume (0..1) for [tags] in [mode]. Album falls back to track values and vice versa.
     * A player can only attenuate, so positive gains are capped at 1; when the peak is known,
     * the volume is also lowered so the loudest sample does not clip.
     */
    fun volumeFor(tags: ReplayGainTags?, mode: ReplayGainMode): Float {
        if (mode == ReplayGainMode.Off || tags == null) return 1f
        val (gain, peak) = when (mode) {
            ReplayGainMode.Album -> (tags.albumGainDb ?: tags.trackGainDb) to (tags.albumPeak ?: tags.trackPeak)
            else -> (tags.trackGainDb ?: tags.albumGainDb) to (tags.trackPeak ?: tags.albumPeak)
        }
        if (gain == null) return 1f
        var linear = 10f.pow(gain / 20f)
        if (peak != null && linear * peak > 1f) linear = 1f / peak
        return linear.coerceIn(0f, 1f)
    }
}
