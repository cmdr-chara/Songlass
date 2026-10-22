package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.math.ln
import kotlin.math.pow

/** Stable layouts. Ten retains the exact original Songlass frequencies and Q. */
enum class GraphicEqLayout(val frequencies: List<Double>, val q: Double) {
    Five(listOf(62.0, 250.0, 1000.0, 4000.0, 16000.0), 1.0 / (2.0 - .5)),
    Ten(listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0), 1.41),
    Fifteen(listOf(25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0, 1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0),
        1.0 / (2.0.pow(1.0 / 3) - 2.0.pow(-1.0 / 3))),
    ThirtyOne(listOf(20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
        800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0),
        1.0 / (2.0.pow(1.0 / 6) - 2.0.pow(-1.0 / 6)));
    val count: Int get() = frequencies.size
    companion object { fun fromCount(count: Int?): GraphicEqLayout? = entries.firstOrNull { it.count == count } }
}

/** Global tone and selected layout; stored graphic curves stay independent per layout/output. */
data class EqualizerTuning(val bassDb: Double = 0.0, val trebleDb: Double = 0.0, val layout: GraphicEqLayout = GraphicEqLayout.Ten) {
    init { require(listOf(bassDb, trebleDb).all { it.isFinite() && it in -12.0..12.0 && GraphicEq.snap(it) == it }) }
    val toneFilters: List<EqFilter> get() = listOf(
        EqFilter(FilterType.LowShelf, 100.0, bassDb, .7071067811865476),
        EqFilter(FilterType.HighShelf, 10_000.0, trebleDb, .7071067811865476),
    )
    companion object {
        const val BASS_KEY = "settings.eq_bass_db"
        const val TREBLE_KEY = "settings.eq_treble_db"
        const val LAYOUT_KEY = "settings.eq_band_count"
        fun read(store: KeyValueStore) = EqualizerTuning(
            readGain(store.getString(BASS_KEY)), readGain(store.getString(TREBLE_KEY)),
            GraphicEqLayout.fromCount(store.getString(LAYOUT_KEY)?.toIntOrNull()) ?: GraphicEqLayout.Ten,
        )
        private fun readGain(value: String?): Double = value?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in -12.0..12.0 }?.let(GraphicEq::snap) ?: 0.0
        fun gainFromSlider(value: Float): Double {
            require(value.isFinite())
            return GraphicEq.snap(value.coerceIn(0f, 1f) * 24.0 - 12.0)
        }
    }
}

/** Used only to seed an untouched layout from the legacy ten-band curve; never overwrites it. */
fun GraphicEq.resample(gains: List<Double>, from: GraphicEqLayout, to: GraphicEqLayout): List<Double> {
    require(gains.size == from.count && gains.all(Double::isFinite))
    if (from == to) return gains.toList()
    return to.frequencies.map { frequency ->
        val upper = from.frequencies.indexOfFirst { it >= frequency }
        when {
            upper < 0 -> gains.last()
            upper == 0 -> gains.first()
            else -> {
                val lower = upper - 1
                val ratio = ln(frequency / from.frequencies[lower]) / ln(from.frequencies[upper] / from.frequencies[lower])
                snap(gains[lower] + (gains[upper] - gains[lower]) * ratio)
            }
        }
    }
}
fun EqPreset.gainsFor(layout: GraphicEqLayout): List<Double> = GraphicEq.resample(gains, GraphicEqLayout.Ten, layout)
