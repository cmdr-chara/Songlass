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

/** Stable platform output identity; never a connection-session id. */
data class EqOutput(val id: String, val name: String)
data class OutputEqProfile(val output: EqOutput, val enabled: Boolean, val gains: List<Double>, val profileId: String?)

data class EqualizerState(
    val enabled: Boolean = false,
    val gains: List<Double> = GraphicEq.flat,
    val profiles: List<HeadphoneProfile> = emptyList(),
    val profileId: String? = null,
    val automaticOutputs: Boolean = false,
    val output: EqOutput? = null,
    val outputProfiles: List<OutputEqProfile> = emptyList(),
) {
    val outputProfile: OutputEqProfile? get() = outputProfiles.firstOrNull { it.output.id == output?.id }
    val usesOutputProfile: Boolean get() = automaticOutputs && outputProfile != null
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

    /**
     * Re-reads the curve, profiles and output bindings from [store], after something else (a
     * backup restore) replaced them. The output currently playing is a fact about the device,
     * not stored data, so it stays and picks its bound curve again.
     */
    fun reload() {
        selectOutputCurve(load().copy(output = _state.value.output))
    }

    fun setEnabled(enabled: Boolean) {
        saveCurve(_state.value.copy(enabled = enabled))
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

    fun catalogCorrection(entry: AutoEqEntry): AutoEqCorrection? =
        _state.value.profiles.firstOrNull { it.id == "autoeq:${entry.id}" }?.let { AutoEqCorrection(entry, it.filters) }

    /** Catalog identity is stable: applying again updates one saved profile instead of duplicating it. */
    fun applyCatalogProfile(correction: AutoEqCorrection, forOutput: EqOutput? = null): Boolean {
        val state = _state.value
        if (forOutput != null && state.output != forOutput) return false
        if (correction.filters.isEmpty() || correction.filters.size > AutoEq.MAX_FILTERS || correction.filters.any {
            !it.frequencyHz.isFinite() || it.frequencyHz !in 10.0..22_000.0 ||
                !it.gainDb.isFinite() || it.gainDb !in -24.0..24.0 || !it.q.isFinite() || it.q !in 0.1..10.0
        }) return false
        val profile = HeadphoneProfile("autoeq:${correction.entry.id}", correction.entry.name, correction.filters)
        val profiles = state.profiles.filterNot { it.id == profile.id } + profile
        store.putString("$PROFILE_PREFIX${profile.id}.name", profile.name)
        store.putString("$PROFILE_PREFIX${profile.id}.filters", encode(profile.filters))
        store.putStringList(PROFILES_KEY, profiles.map { it.id })
        val changed = state.copy(profiles = profiles, profileId = profile.id, enabled = true)
        if (forOutput == null) saveCurve(changed)
        else {
            // Binding is atomic with selection: the manual curve is not overwritten on the way.
            val binding = OutputEqProfile(forOutput, true, state.gains, profile.id)
            val outputs = state.outputProfiles.filterNot { it.output.id == forOutput.id } + binding
            saveOutputs(outputs)
            store.putString("equalizer.outputs.enabled", "true")
            _state.value = changed.copy(automaticOutputs = true, outputProfiles = outputs)
        }
        return true
    }

    /** Uses the headphone profile [id], or none. */
    fun selectProfile(id: String?) {
        val valid = id?.takeIf { wanted -> _state.value.profiles.any { it.id == wanted } }
        saveCurve(_state.value.copy(profileId = valid))
    }

    fun deleteProfile(id: String) {
        val profiles = _state.value.profiles.filterNot { it.id == id }
        store.putStringList(PROFILES_KEY, profiles.map { it.id })
        store.putString("$PROFILE_PREFIX$id.name", null)
        store.putString("$PROFILE_PREFIX$id.filters", null)
        if (store.getString(SELECTED_KEY) == id) store.putString(SELECTED_KEY, null)
        val outputs = _state.value.outputProfiles.map { it.copy(profileId = it.profileId.takeIf { selected -> selected != id }) }
        saveOutputs(outputs)
        _state.update { state -> state.copy(profiles = profiles, outputProfiles = outputs, profileId = state.profileId.takeIf { it != id }) }
    }

    private fun saveGains(gains: List<Double>) {
        saveCurve(_state.value.copy(gains = gains))
    }

    /** Route changes select an existing curve, without writing over the manual settings. */
    fun setOutput(output: EqOutput?) {
        if (_state.value.output == output) return
        selectOutputCurve(_state.value.copy(output = output))
    }

    fun setAutomaticOutputs(enabled: Boolean) {
        store.putString("equalizer.outputs.enabled", enabled.toString())
        selectOutputCurve(_state.value.copy(automaticOutputs = enabled))
    }

    /** Bind the currently audible curve to this output. Further edits update this binding. */
    fun saveForCurrentOutput() {
        val state = _state.value
        val output = state.output ?: return
        val binding = OutputEqProfile(output, state.enabled, state.gains, state.profileId)
        val outputs = state.outputProfiles.filterNot { it.output.id == output.id } + binding
        saveOutputs(outputs)
        _state.value = state.copy(outputProfiles = outputs)
    }

    fun removeOutputProfile(id: String) {
        val outputs = _state.value.outputProfiles.filterNot { it.output.id == id }
        listOf("name", "enabled", "gains", "profile").forEach { store.putString("equalizer.outputs.$id.$it", null) }
        saveOutputs(outputs)
        selectOutputCurve(_state.value.copy(outputProfiles = outputs))
    }

    private fun selectOutputCurve(state: EqualizerState) {
        val bound = state.outputProfile.takeIf { state.automaticOutputs }
        val manual = load()
        _state.value = state.copy(
            enabled = bound?.enabled ?: manual.enabled,
            gains = bound?.gains ?: manual.gains,
            profileId = (bound?.profileId ?: if (bound == null) manual.profileId else null)
                ?.takeIf { id -> state.profiles.any { it.id == id } },
        )
    }

    private fun saveCurve(state: EqualizerState) {
        if (state.usesOutputProfile) {
            val output = state.output!!
            val outputs = state.outputProfiles.map { if (it.output.id == output.id)
                OutputEqProfile(output, state.enabled, state.gains, state.profileId) else it }
            saveOutputs(outputs)
            _state.value = state.copy(outputProfiles = outputs)
        } else {
            store.putString(ENABLED_KEY, state.enabled.toString())
            store.putString(GAINS_KEY, state.gains.joinToString(","))
            store.putString(SELECTED_KEY, state.profileId)
            _state.value = state
        }
    }

    private fun saveOutputs(outputs: List<OutputEqProfile>) {
        store.putStringList("equalizer.outputs.index", outputs.map { it.output.id })
        outputs.forEach { binding ->
            val key = "equalizer.outputs.${binding.output.id}"
            store.putString("$key.name", binding.output.name)
            store.putString("$key.enabled", binding.enabled.toString())
            store.putString("$key.gains", binding.gains.joinToString(","))
            store.putString("$key.profile", binding.profileId)
        }
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
            automaticOutputs = store.getString("equalizer.outputs.enabled") == "true",
            outputProfiles = store.getStringList("equalizer.outputs.index").distinct().mapNotNull { id ->
                val key = "equalizer.outputs.$id"
                val name = store.getString("$key.name") ?: return@mapNotNull null
                val bands = store.getString("$key.gains")?.split(',')?.mapNotNull { it.toDoubleOrNull()?.takeIf(Double::isFinite)?.let(GraphicEq::snap) }
                    ?.takeIf { it.size == GraphicEq.frequencies.size } ?: return@mapNotNull null
                OutputEqProfile(EqOutput(id, name), store.getString("$key.enabled") == "true", bands,
                    store.getString("$key.profile")?.takeIf { wanted -> profiles.any { it.id == wanted } })
            },
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
