package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/** Ten octave bands, as on most graphic equalizers. */
object GraphicEq {
    val frequencies: List<Double> = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0)
    const val MAX_GAIN_DB = 12.0

    /** Gain changes in steps of half a decibel, finer than the ear notices. */
    const val STEP_DB = 0.5

    /** One-octave bandwidth. */
    private const val Q = 1.41

    val flat: List<Double> = List(frequencies.size) { 0.0 }

    fun filters(gains: List<Double>): List<EqFilter> =
        frequencies.zip(gains) { f, g -> EqFilter(FilterType.Peaking, f, g, Q) }

    fun snap(gainDb: Double): Double =
        ((gainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB) / STEP_DB).roundToInt() * STEP_DB)
}

/** Ready-made curves for the graphic equalizer. */
enum class EqPreset(val gains: List<Double>) {
    Flat(GraphicEq.flat),
    MoreBass(listOf(6.0, 5.0, 3.5, 1.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    LessBass(listOf(-6.0, -5.0, -3.5, -1.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    MoreTreble(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 3.0, 4.5, 5.5)),
    LessTreble(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, -3.0, -4.5, -5.5)),
    Voice(listOf(-2.0, -2.0, -1.0, 0.0, 1.5, 3.0, 3.0, 2.0, 0.0, -1.0)),

    /** Ears hear less bass and treble at low volume: this brings them back. */
    LowVolume(listOf(5.0, 4.0, 2.0, 0.0, -1.0, 0.0, 0.0, 1.0, 3.0, 4.0)),
}

/** A correction for one pair of headphones, usually imported from AutoEQ. */
data class HeadphoneProfile(val id: String, val name: String, val filters: List<EqFilter>)

data class EqualizerState(
    val enabled: Boolean = false,
    val gains: List<Double> = GraphicEq.flat,
    val profiles: List<HeadphoneProfile> = emptyList(),
    val profileId: String? = null,
) {
    val profile: HeadphoneProfile? get() = profiles.firstOrNull { it.id == profileId }

    /** The preset the bands match, or null for a custom curve. */
    val preset: EqPreset? get() = EqPreset.entries.firstOrNull { it.gains == gains }

    /** Headphone correction first, then the user's taste on top; flat when switched off. */
    val chain: EqChain
        get() = if (!enabled) EqChain.Flat
        else EqChain.withHeadroom(profile?.filters.orEmpty() + GraphicEq.filters(gains))
}

/** Equalizer settings: on/off, the graphic bands and the headphone profiles, saved in [store]. */
class Equalizer(
    private val store: KeyValueStore = InMemoryKeyValueStore(),
    private val clock: () -> Long = { 0L },
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        store.putString(ENABLED_KEY, enabled.toString())
        _state.update { it.copy(enabled = enabled) }
    }

    fun setGain(band: Int, gainDb: Double) {
        val gains = _state.value.gains
        if (band !in gains.indices) return
        saveGains(gains.toMutableList().also { it[band] = GraphicEq.snap(gainDb) })
    }

    fun applyPreset(preset: EqPreset) = saveGains(preset.gains)

    /**
     * Adds the parametric equalizer file [text] as a headphone profile named after [fileName],
     * selects it and switches the equalizer on. Returns null when the file has no usable filter.
     */
    fun importProfile(fileName: String, text: String): HeadphoneProfile? {
        val parsed = AutoEq.parse(text)
        if (parsed.filters.isEmpty()) return null
        val base = "h${clock()}"
        var id = base
        var n = 1
        while (_state.value.profiles.any { it.id == id }) id = "$base-${n++}"
        val profile = HeadphoneProfile(id, AutoEq.nameFromFile(fileName), parsed.filters)
        store.putString("$PROFILE_PREFIX$id.name", profile.name)
        store.putString("$PROFILE_PREFIX$id.filters", encode(profile.filters))
        val profiles = _state.value.profiles + profile
        store.putStringList(PROFILES_KEY, profiles.map { it.id })
        _state.update { it.copy(profiles = profiles) }
        selectProfile(id)
        setEnabled(true)
        return profile
    }

    /** Uses the headphone profile [id], or none. */
    fun selectProfile(id: String?) {
        val valid = id?.takeIf { wanted -> _state.value.profiles.any { it.id == wanted } }
        store.putString(SELECTED_KEY, valid)
        _state.update { it.copy(profileId = valid) }
    }

    fun deleteProfile(id: String) {
        val profiles = _state.value.profiles.filterNot { it.id == id }
        store.putStringList(PROFILES_KEY, profiles.map { it.id })
        store.putString("$PROFILE_PREFIX$id.name", null)
        store.putString("$PROFILE_PREFIX$id.filters", null)
        if (_state.value.profileId == id) store.putString(SELECTED_KEY, null)
        _state.update { state -> state.copy(profiles = profiles, profileId = state.profileId.takeIf { it != id }) }
    }

    private fun saveGains(gains: List<Double>) {
        store.putString(GAINS_KEY, gains.joinToString(","))
        _state.update { it.copy(gains = gains) }
    }

    private fun load(): EqualizerState {
        val gains = store.getString(GAINS_KEY)
            ?.split(',')
            ?.mapNotNull { it.toDoubleOrNull()?.let(GraphicEq::snap) }
            ?.takeIf { it.size == GraphicEq.frequencies.size }
            ?: GraphicEq.flat
        val profiles = store.getStringList(PROFILES_KEY).mapNotNull { id ->
            val name = store.getString("$PROFILE_PREFIX$id.name") ?: return@mapNotNull null
            val filters = decode(store.getString("$PROFILE_PREFIX$id.filters")).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            HeadphoneProfile(id, name, filters)
        }
        return EqualizerState(
            enabled = store.getString(ENABLED_KEY)?.toBooleanStrictOrNull() ?: false,
            gains = gains,
            profiles = profiles,
            profileId = store.getString(SELECTED_KEY)?.takeIf { id -> profiles.any { it.id == id } },
        )
    }

    private companion object {
        const val ENABLED_KEY = "equalizer.enabled"
        const val GAINS_KEY = "equalizer.gains"
        const val SELECTED_KEY = "equalizer.profile"
        const val PROFILES_KEY = "equalizer.profiles"
        const val PROFILE_PREFIX = "equalizer.profiles."

        fun encode(filters: List<EqFilter>): String =
            filters.joinToString(";") { "${it.type.name}:${it.frequencyHz}:${it.gainDb}:${it.q}" }

        fun decode(text: String?): List<EqFilter> = text.orEmpty().split(';').mapNotNull { item ->
            val parts = item.split(':')
            if (parts.size != 4) return@mapNotNull null
            val type = FilterType.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            EqFilter(type, parts[1].toDoubleOrNull() ?: return@mapNotNull null, parts[2].toDoubleOrNull() ?: return@mapNotNull null, parts[3].toDoubleOrNull() ?: return@mapNotNull null)
        }
    }
}
