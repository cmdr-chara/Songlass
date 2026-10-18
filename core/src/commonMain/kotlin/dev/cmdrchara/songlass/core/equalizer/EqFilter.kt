package dev.cmdrchara.songlass.core.equalizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType { Peaking, LowShelf, HighShelf }

/** One second-order filter of an equalizer, as AutoEQ and most parametric equalizers describe it. */
data class EqFilter(val type: FilterType, val frequencyHz: Double, val gainDb: Double, val q: Double) {
    /** Biquad coefficients for [sampleRate] (Robert Bristow-Johnson's Audio EQ Cookbook), normalised by a0. */
    fun coefficients(sampleRate: Int): Biquad {
        // A filter above Nyquist is meaningless: keep it just below.
        val f = frequencyHz.coerceIn(1.0, sampleRate * 0.45)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.05))
        return when (type) {
            FilterType.Peaking -> Biquad.normalised(
                b0 = 1 + alpha * a, b1 = -2 * cosW, b2 = 1 - alpha * a,
                a0 = 1 + alpha / a, a1 = -2 * cosW, a2 = 1 - alpha / a,
            )
            FilterType.LowShelf -> {
                val s = 2 * sqrt(a) * alpha
                Biquad.normalised(
                    b0 = a * ((a + 1) - (a - 1) * cosW + s),
                    b1 = 2 * a * ((a - 1) - (a + 1) * cosW),
                    b2 = a * ((a + 1) - (a - 1) * cosW - s),
                    a0 = (a + 1) + (a - 1) * cosW + s,
                    a1 = -2 * ((a - 1) + (a + 1) * cosW),
                    a2 = (a + 1) + (a - 1) * cosW - s,
                )
            }
            FilterType.HighShelf -> {
                val s = 2 * sqrt(a) * alpha
                Biquad.normalised(
                    b0 = a * ((a + 1) + (a - 1) * cosW + s),
                    b1 = -2 * a * ((a - 1) + (a + 1) * cosW),
                    b2 = a * ((a + 1) + (a - 1) * cosW - s),
                    a0 = (a + 1) - (a - 1) * cosW + s,
                    a1 = 2 * ((a - 1) - (a + 1) * cosW),
                    a2 = (a + 1) - (a - 1) * cosW - s,
                )
            }
        }
    }
}

/** Coefficients of y = b0·x + b1·x₁ + b2·x₂ − a1·y₁ − a2·y₂. */
data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    /** Gain in dB at [frequencyHz]. */
    fun responseDb(frequencyHz: Double, sampleRate: Int): Double {
        val w = 2.0 * PI * frequencyHz / sampleRate
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val numRe = b0 + b1 * c1 + b2 * c2
        val numIm = -(b1 * s1 + b2 * s2)
        val denRe = 1 + a1 * c1 + a2 * c2
        val denIm = -(a1 * s1 + a2 * s2)
        val power = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm)
        return 10.0 * log10(max(power, 1e-12))
    }

    companion object {
        fun normalised(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}

/**
 * What the player applies: [filters] in series after a [preampDb] attenuation that leaves room
 * for the boosts, so the result never clips.
 */
data class EqChain(val filters: List<EqFilter>, val preampDb: Double) {
    val isFlat: Boolean get() = filters.all { it.gainDb == 0.0 } && preampDb == 0.0

    /** Gain of the whole chain at [frequencyHz], preamp included. */
    fun responseDb(frequencyHz: Double, sampleRate: Int = REFERENCE_RATE): Double =
        filtersResponseDb(filters.map { it.coefficients(sampleRate) }, frequencyHz, sampleRate) + preampDb

    /** [points] values of [responseDb] spaced evenly on a log scale from 20 Hz to 20 kHz. */
    fun curve(points: Int): List<Double> = logFrequencies(points).let { freqs ->
        val biquads = filters.map { it.coefficients(REFERENCE_RATE) }
        freqs.map { filtersResponseDb(biquads, it, REFERENCE_RATE) + preampDb }
    }

    companion object {
        val Flat = EqChain(emptyList(), 0.0)
        const val REFERENCE_RATE = 48_000
        const val MIN_HZ = 20.0
        const val MAX_HZ = 20_000.0

        /** A chain of [filters] with the preamp that keeps its loudest boost at 0 dB. */
        fun withHeadroom(filters: List<EqFilter>): EqChain {
            val active = filters.filter { it.gainDb != 0.0 }
            if (active.isEmpty()) return Flat
            val biquads = active.map { it.coefficients(REFERENCE_RATE) }
            val peak = logFrequencies(HEADROOM_POINTS).maxOf { filtersResponseDb(biquads, it, REFERENCE_RATE) }
            return EqChain(active, if (peak > 0.0) -peak else 0.0)
        }

        fun logFrequencies(points: Int): List<Double> = List(points) { i ->
            MIN_HZ * (MAX_HZ / MIN_HZ).pow(i / (points - 1).toDouble())
        }

        private const val HEADROOM_POINTS = 256

        private fun filtersResponseDb(biquads: List<Biquad>, frequencyHz: Double, sampleRate: Int) =
            biquads.sumOf { it.responseDb(frequencyHz, sampleRate) }
    }
}
