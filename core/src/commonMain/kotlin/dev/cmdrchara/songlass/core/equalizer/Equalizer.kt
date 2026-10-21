package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.MutationTicket
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
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
        if (!gainDb.isFinite()) 0.0
        else ((gainDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB) / STEP_DB).roundToInt() * STEP_DB)
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

/** Parsed on a worker; its immutable state snapshot is the revision reviewed by the user. */
class EqualizerProfileImport internal constructor(
    val name: String,
    val filters: List<EqFilter>,
    val skipped: Int,
    internal val expectedState: EqualizerState,
    internal val mutationTicket: MutationTicket?,
) {
    /** Only an existing automatic binding is edited; otherwise the manual fallback is selected. */
    val output: EqOutput? get() = expectedState.output.takeIf { expectedState.usesOutputProfile }
}

sealed interface EqualizerProfileImportResult {
    data class Saved(val profile: HeadphoneProfile) : EqualizerProfileImportResult
    data object Changed : EqualizerProfileImportResult
    data object Invalid : EqualizerProfileImportResult
    data object Failed : EqualizerProfileImportResult
}

/** Stable platform output identity; never a connection-session id. */
data class EqOutput(val id: String, val name: String)
data class OutputEqProfile(val output: EqOutput, val enabled: Boolean, val gains: List<Double>, val profileId: String?)

/** The persistent manual fallback, shown without switching away from a bound output curve. */
data class ManualEqCurve(
    val enabled: Boolean = false,
    val gains: List<Double> = GraphicEq.flat,
    val profileId: String? = null,
) {
    val preset: EqPreset? get() = EqPreset.entries.firstOrNull { it.gains == gains }
}

data class EqualizerState(
    val enabled: Boolean = false,
    val gains: List<Double> = GraphicEq.flat,
    val profiles: List<HeadphoneProfile> = emptyList(),
    val profileId: String? = null,
    val automaticOutputs: Boolean = false,
    val output: EqOutput? = null,
    val outputProfiles: List<OutputEqProfile> = emptyList(),
    val manualCurve: ManualEqCurve = ManualEqCurve(),
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
    val mutations: RestoreMutationGate = RestoreMutationGate(),
    private val clock: () -> Long = { 0L },
) {
    constructor(store: KeyValueStore, clock: () -> Long) : this(store, RestoreMutationGate(), clock)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    /**
     * Re-reads the curve, profiles and output bindings from [store], after something else (a
     * backup restore) replaced them. The output currently playing is a fact about the device,
     * not stored data, so it stays and picks its bound curve again.
     */
    fun reload() = prepareReload().invoke()

    /** Decode off Main; the live device route is sampled only when publishing after commit. */
    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = load(store)
        return { selectOutputCurve(loaded.copy(output = _state.value.output), manual = loaded) }
    }

    fun setEnabled(enabled: Boolean) {
        saveCurve(_state.value.copy(enabled = enabled))
    }

    fun setGain(band: Int, gainDb: Double) {
        val gains = _state.value.gains
        if (band !in gains.indices || !gainDb.isFinite()) return
        saveGains(gains.toMutableList().also { it[band] = GraphicEq.snap(gainDb) })
    }

    fun applyPreset(preset: EqPreset) = saveGains(preset.gains)

    /** Compatibility entry point; UI uses prepare/confirm so file parsing never runs on Main. */
    fun importProfile(fileName: String, text: String): HeadphoneProfile? {
        val prepared = prepareProfileImport(fileName, text) ?: return null
        return (confirmProfileImport(prepared) as? EqualizerProfileImportResult.Saved)?.profile
    }

    /** Pure parsing: capture [expectedState] on the owner thread before calling this on a worker. */
    fun prepareProfileImport(
        fileName: String,
        text: String,
        expectedState: EqualizerState = _state.value,
        mutationTicket: MutationTicket? = mutations.ticket(),
    ): EqualizerProfileImport? {
        if (fileName.length > AutoEq.MAX_FILE_NAME_CHARS) return null
        val parsed = AutoEq.parse(text)
        if (parsed.filters.isEmpty()) return null
        val name = AutoEq.nameFromFile(fileName).takeIf { it.isNotBlank() } ?: return null
        return EqualizerProfileImport(name, parsed.filters.toList(), parsed.skipped, expectedState, mutationTicket)
    }

    /**
     * Owner-thread confirmation. A route/curve/profile change invalidates the reviewed snapshot,
     * including A→B→A changes: StateFlow's new immutable state is a different object. No file is
     * reparsed here, and the persisted key/value schema remains compatible with existing backups.
     */
    fun confirmProfileImport(prepared: EqualizerProfileImport): EqualizerProfileImportResult {
        val state = _state.value
        if (!mutations.accepts(prepared.mutationTicket) || prepared.expectedState !== state) {
            return EqualizerProfileImportResult.Changed
        }
        if (prepared.name.isBlank() || prepared.name.length > AutoEq.MAX_FILE_NAME_CHARS ||
            prepared.filters.isEmpty() || prepared.filters.size > AutoEq.MAX_FILTERS ||
            prepared.filters.any { !it.isSupported }) return EqualizerProfileImportResult.Invalid
        val base = "h${clock()}"
        var id = base
        var n = 1
        while (state.profiles.any { it.id == id }) id = "$base-${n++}"
        val profile = HeadphoneProfile(id, prepared.name, prepared.filters.toList())
        val profiles = state.profiles + profile
        val writing = mutableMapOf(
            "$PROFILE_PREFIX$id.name" to profile.name,
            "$PROFILE_PREFIX$id.filters" to encode(profile.filters),
            PROFILES_KEY to profiles.joinToString("\u001F") { it.id },
        )
        val selected = state.copy(profiles = profiles, profileId = id, enabled = true)
        val next = if (state.usesOutputProfile) {
            val output = state.output!!
            val key = "equalizer.outputs.${output.id}"
            writing["$key.enabled"] = "true"
            writing["$key.profile"] = id
            selected.copy(outputProfiles = state.outputProfiles.map { binding ->
                if (binding.output.id == output.id) binding.copy(enabled = true, profileId = id) else binding
            })
        } else {
            writing[ENABLED_KEY] = "true"
            writing[GAINS_KEY] = state.gains.joinToString(",")
            writing[SELECTED_KEY] = id
            selected.copy(manualCurve = ManualEqCurve(true, state.gains, id))
        }
        return try {
            // Native preference batching publishes the related values together without a
            // synchronous disk commit on the owner/UI thread. This is not a durability barrier.
            store.putStrings(writing)
            _state.value = next
            EqualizerProfileImportResult.Saved(profile)
        } catch (_: Exception) {
            EqualizerProfileImportResult.Failed
        }
    }

    fun catalogCorrection(entry: AutoEqEntry): AutoEqCorrection? =
        _state.value.profiles.firstOrNull { it.id == "autoeq:${entry.id}" }?.let { AutoEqCorrection(entry, it.filters) }

    /** Catalog identity is stable: applying again updates one saved profile instead of duplicating it. */
    fun applyCatalogProfile(
        correction: AutoEqCorrection, forOutput: EqOutput? = null,
        mutationTicket: MutationTicket? = mutations.ticket(),
    ): Boolean {
        if (!mutations.accepts(mutationTicket)) return false
        val state = _state.value
        if (forOutput != null && state.output != forOutput) return false
        if (correction.filters.isEmpty() || correction.filters.size > AutoEq.MAX_FILTERS ||
            correction.filters.any { !it.isSupported }) return false
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
        if (!mutations.canMutate) return
        val profiles = _state.value.profiles.filterNot { it.id == id }
        store.putStringList(PROFILES_KEY, profiles.map { it.id })
        store.putString("$PROFILE_PREFIX$id.name", null)
        store.putString("$PROFILE_PREFIX$id.filters", null)
        if (store.getString(SELECTED_KEY) == id) store.putString(SELECTED_KEY, null)
        val outputs = _state.value.outputProfiles.map { it.copy(profileId = it.profileId.takeIf { selected -> selected != id }) }
        saveOutputs(outputs)
        _state.update { state -> state.copy(
            profiles = profiles, outputProfiles = outputs, profileId = state.profileId.takeIf { it != id },
            manualCurve = state.manualCurve.copy(profileId = state.manualCurve.profileId.takeIf { it != id }),
        ) }
    }

    private fun saveGains(gains: List<Double>) {
        saveCurve(_state.value.copy(gains = gains))
    }

    /** Route changes select an existing curve, without writing over the manual settings. */
    fun setOutput(output: EqOutput?) {
        if (_state.value.output == output) return
        if (!mutations.canMutate) {
            // Device facts still change during restore. Keep the audible curve until the
            // prepared replacement can choose its binding, without reading mixed store data.
            _state.update { it.copy(output = output) }
        } else selectOutputCurve(_state.value.copy(output = output))
    }

    /** Finish a restore attempt, successful or failed, using only already accepted state. */
    fun reconcileCurrentOutput() {
        val current = _state.value
        val manual = current.manualCurve
        selectOutputCurve(current, current.copy(
            enabled = manual.enabled, gains = manual.gains, profileId = manual.profileId,
        ))
    }

    fun setAutomaticOutputs(enabled: Boolean) {
        if (!mutations.canMutate) return
        store.putString("equalizer.outputs.enabled", enabled.toString())
        selectOutputCurve(_state.value.copy(automaticOutputs = enabled))
    }

    /** Bind the currently audible curve to this output. Further edits update this binding. */
    fun saveForCurrentOutput() {
        if (!mutations.canMutate) return
        val state = _state.value
        val output = state.output ?: return
        val binding = OutputEqProfile(output, state.enabled, state.gains, state.profileId)
        val outputs = state.outputProfiles.filterNot { it.output.id == output.id } + binding
        saveOutputs(outputs)
        _state.value = state.copy(outputProfiles = outputs)
    }

    fun removeOutputProfile(id: String) {
        if (!mutations.canMutate) return
        val outputs = _state.value.outputProfiles.filterNot { it.output.id == id }
        listOf("name", "enabled", "gains", "profile").forEach { store.putString("equalizer.outputs.$id.$it", null) }
        saveOutputs(outputs)
        selectOutputCurve(_state.value.copy(outputProfiles = outputs))
    }

    private fun selectOutputCurve(state: EqualizerState, manual: EqualizerState = load()) {
        val bound = state.outputProfile.takeIf { state.automaticOutputs }
        _state.value = state.copy(
            enabled = bound?.enabled ?: manual.enabled,
            gains = bound?.gains ?: manual.gains,
            profileId = (bound?.profileId ?: if (bound == null) manual.profileId else null)
                ?.takeIf { id -> state.profiles.any { it.id == id } },
            manualCurve = manual.manualCurve,
        )
    }

    private fun saveCurve(state: EqualizerState) {
        if (!mutations.canMutate) return
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
            _state.value = state.copy(manualCurve = ManualEqCurve(state.enabled, state.gains, state.profileId))
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

    private fun load(store: KeyValueStore = this.store): EqualizerState {
        val gains = decodeGains(store.getString(GAINS_KEY)) ?: GraphicEq.flat
        val profiles = store.getStringList(PROFILES_KEY).distinct().mapNotNull { id ->
            val name = store.getString("$PROFILE_PREFIX$id.name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val filters = decode(store.getString("$PROFILE_PREFIX$id.filters")).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            HeadphoneProfile(id, name, filters)
        }
        val enabled = store.getString(ENABLED_KEY)?.toBooleanStrictOrNull() ?: false
        val profileId = store.getString(SELECTED_KEY)?.takeIf { id -> profiles.any { it.id == id } }
        return EqualizerState(
            enabled = enabled,
            gains = gains,
            profiles = profiles,
            profileId = profileId,
            manualCurve = ManualEqCurve(enabled, gains, profileId),
            automaticOutputs = store.getString("equalizer.outputs.enabled") == "true",
            outputProfiles = store.getStringList("equalizer.outputs.index").distinct().mapNotNull { id ->
                val key = "equalizer.outputs.$id"
                val name = store.getString("$key.name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val bands = decodeGains(store.getString("$key.gains")) ?: return@mapNotNull null
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

        fun decodeGains(text: String?): List<Double>? {
            if (text == null || text.length > GraphicEq.frequencies.size * 64) return null
            val values = text.split(',', limit = GraphicEq.frequencies.size + 1)
            if (values.size != GraphicEq.frequencies.size) return null
            return values.map { value ->
                // Do not discard one malformed band and shift the remaining frequencies.
                val gain = value.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
                GraphicEq.snap(gain)
            }
        }

        fun decode(text: String?): List<EqFilter> {
            // Imported profiles contain at most twenty filters. Reject a damaged correction as
            // a whole instead of silently applying a different, partial headphone correction.
            if (text.isNullOrBlank() || text.length > AutoEq.MAX_FILTERS * 128) return emptyList()
            val items = text.split(';')
            if (items.size > AutoEq.MAX_FILTERS) return emptyList()
            return items.map { item ->
                val parts = item.split(':')
                if (parts.size != 4) return emptyList()
                val type = FilterType.entries.firstOrNull { it.name == parts[0] } ?: return emptyList()
                val filter = EqFilter(type,
                    parts[1].toDoubleOrNull() ?: return emptyList(),
                    parts[2].toDoubleOrNull() ?: return emptyList(),
                    parts[3].toDoubleOrNull() ?: return emptyList())
                if (!filter.isSupported) return emptyList()
                filter
            }
        }
    }
}
