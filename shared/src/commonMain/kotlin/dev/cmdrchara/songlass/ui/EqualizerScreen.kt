package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.designsystem.BackPageTitle

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.equalizer.EqChain
import dev.cmdrchara.songlass.core.equalizer.EqPreset
import dev.cmdrchara.songlass.core.equalizer.Equalizer
import dev.cmdrchara.songlass.core.equalizer.GraphicEq
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.EqBand
import dev.cmdrchara.songlass.designsystem.EqualizerEditor
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SettingsChoiceRow
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.SettingsToggleRow
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.eq_custom
import dev.cmdrchara.songlass.shared.resources.eq_decimal_separator
import dev.cmdrchara.songlass.shared.resources.eq_headphones
import dev.cmdrchara.songlass.shared.resources.eq_headphones_delete
import dev.cmdrchara.songlass.shared.resources.eq_headphones_hint
import dev.cmdrchara.songlass.shared.resources.eq_headphones_import
import dev.cmdrchara.songlass.shared.resources.eq_headphones_none
import dev.cmdrchara.songlass.shared.resources.eq_hint
import dev.cmdrchara.songlass.shared.resources.eq_import_failed
import dev.cmdrchara.songlass.shared.resources.eq_preset_flat
import dev.cmdrchara.songlass.shared.resources.eq_preset_less_bass
import dev.cmdrchara.songlass.shared.resources.eq_preset_less_treble
import dev.cmdrchara.songlass.shared.resources.eq_preset_low_volume
import dev.cmdrchara.songlass.shared.resources.eq_preset_more_bass
import dev.cmdrchara.songlass.shared.resources.eq_preset_more_treble
import dev.cmdrchara.songlass.shared.resources.eq_preset_voice
import dev.cmdrchara.songlass.shared.resources.eq_presets
import dev.cmdrchara.songlass.shared.resources.eq_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/** Graphic equalizer, presets and headphone corrections; values and persistence live in core. */
@Composable
fun EqualizerScreen(equalizer: Equalizer, backLabel: String, onBack: () -> Unit, onOpenAutoEq: () -> Unit, contentPadding: PaddingValues, searchFocus: SettingsFocusRequest? = null) {
    val state by equalizer.state.collectAsState()
    val listState = rememberLazyListState()
    val outputProfilesOffset = if (state.outputProfiles.isNotEmpty()) 1 else 0
    val targetIndex = when (searchFocus?.target) {
        SettingsTarget.Equalizer -> 1
        SettingsTarget.OutputProfiles -> 3
        SettingsTarget.EqualizerPresets -> 8 + outputProfilesOffset
        SettingsTarget.HeadphoneCorrection -> 10 + outputProfilesOffset
        else -> null
    }
    val searchReveal = SettingsSearchScroll(searchFocus, listState, targetIndex)
    var importFailed by remember { mutableStateOf(false) }
    val importer = rememberTextFileImporter(TextFileKind.EqualizerProfile) { fileName, text ->
        importFailed = text == null || equalizer.importProfile(fileName ?: "AutoEQ", text) == null
    }
    val separator = stringResource(Res.string.eq_decimal_separator)
    val bands = remember(state.gains) {
        GraphicEq.frequencies.zip(state.gains) { f, g -> EqBand(f.toFloat(), frequencyLabel(f), g.toFloat()) }
    }
    // The curve the user shapes, without the automatic preamp (it would only shift everything down).
    val curve = remember(state.gains, state.profile) {
        EqChain(state.profile?.filters.orEmpty() + GraphicEq.filters(state.gains), 0.0).curve(CURVE_POINTS).map { it.toFloat() }
    }

    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).then(searchReveal), state = listState, contentPadding = contentPadding) {
        item { BackPageTitle(stringResource(Res.string.eq_title),
            if (searchFocus == null) backLabel else stringResource(Res.string.settings_search_results), onBack) }
        item {
            SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                SettingsSearchAnchor(SettingsTarget.Equalizer, searchFocus) {
                    SettingsToggleRow(stringResource(Res.string.eq_title), state.enabled, equalizer::setEnabled)
                }
            }
        }
        item { SectionTitle(stringResource(Res.string.eq_outputs)) }
        item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
            SettingsSearchAnchor(SettingsTarget.OutputProfiles, searchFocus) {
                SettingsToggleRow(stringResource(Res.string.eq_outputs_auto), state.automaticOutputs, equalizer::setAutomaticOutputs)
            }
            state.output?.let { output ->
                val name = if (output.id.startsWith("builtin:")) stringResource(Res.string.output_this_phone) else output.name
                ListRow(title = name, subtitle = stringResource(if (state.usesOutputProfile) Res.string.eq_outputs_bound else Res.string.eq_outputs_manual), onClick = null)
                ListRow(
                    title = stringResource(if (state.outputProfile == null) Res.string.eq_outputs_save else Res.string.audio_eq_update_binding),
                    subtitle = if (state.automaticOutputs) null else stringResource(Res.string.audio_eq_saved_auto_off),
                    onClick = equalizer::saveForCurrentOutput,
                )
            } ?: ListRow(title = stringResource(Res.string.audio_eq_unknown_route), onClick = null)
            ListRow(
                title = stringResource(Res.string.audio_eq_configured),
                subtitle = equalizerCurveLabel(state.enabled, state.preset, state.profile?.name),
                onClick = null,
            )
            if (state.usesOutputProfile) state.manualCurve.let { manual ->
                ListRow(
                    title = stringResource(Res.string.audio_eq_manual_fallback),
                    subtitle = equalizerCurveLabel(manual.enabled, manual.preset, state.profiles.firstOrNull { it.id == manual.profileId }?.name),
                    onClick = null,
                )
            }
        } }
        if (state.outputProfiles.isNotEmpty()) item { SettingsGroup(Modifier.padding(start = pageInset, end = pageInset, top = 8.dp)) {
            state.outputProfiles.forEach { binding ->
                val active = state.usesOutputProfile && state.output?.id == binding.output.id
                val status = stringResource(if (active) Res.string.audio_eq_saved_active else Res.string.audio_eq_saved_inactive)
                val preset = EqPreset.entries.firstOrNull { it.gains == binding.gains }
                val description = equalizerCurveLabel(binding.enabled, preset, state.profiles.firstOrNull { it.id == binding.profileId }?.name)
                ListRow(title = if (binding.output.id.startsWith("builtin:")) stringResource(Res.string.output_this_phone) else binding.output.name,
                    subtitle = "$status · $description", trailing = {
                    dev.cmdrchara.songlass.designsystem.IconButton(AppIcons.Close, stringResource(Res.string.eq_outputs_remove), { equalizer.removeOutputProfile(binding.output.id) })
                }, onClick = null)
            }
        } }
        item { FeatureHint(stringResource(Res.string.eq_outputs_hint)) }
        item {
            EqualizerEditor(
                bands = bands,
                curve = curve,
                maxGainDb = GraphicEq.MAX_GAIN_DB.toFloat(),
                enabled = state.enabled,
                formatGain = { formatGain(it, separator) },
                onBandChange = { band, gain ->
                    // Shaping the sound means wanting to hear it.
                    if (!state.enabled) equalizer.setEnabled(true)
                    equalizer.setGain(band, gain.toDouble())
                },
                modifier = Modifier.padding(start = pageInset, end = pageInset, top = 20.dp),
            )
        }
        item { Hint(stringResource(Res.string.audio_eq_headroom_hint)) }
        item { SectionTitle(stringResource(Res.string.eq_presets)) }
        item {
            SettingsSearchAnchor(SettingsTarget.EqualizerPresets, searchFocus) {
                SettingsGroup(Modifier.padding(horizontal = pageInset).selectableGroup()) {
                    EqPreset.entries.forEach { preset ->
                        SettingsChoiceRow(stringResource(preset.label), selected = state.preset == preset, onClick = {
                            equalizer.applyPreset(preset)
                            if (preset != EqPreset.Flat) equalizer.setEnabled(true)
                        })
                    }
                    if (state.preset == null) SettingsChoiceRow(stringResource(Res.string.eq_custom), selected = true, onClick = {})
                }
            }
        }
        item { SectionTitle(stringResource(Res.string.eq_headphones)) }
        item {
            SettingsSearchAnchor(SettingsTarget.HeadphoneCorrection, searchFocus) {
                SettingsGroup(Modifier.padding(horizontal = pageInset).selectableGroup()) {
                    SettingsChoiceRow(stringResource(Res.string.eq_headphones_none), selected = state.profileId == null, onClick = { equalizer.selectProfile(null) })
                    state.profiles.forEach { profile ->
                        SettingsChoiceRow(profile.name, selected = state.profileId == profile.id, onClick = { equalizer.selectProfile(profile.id) })
                    }
                }
            }
        }
        item {
            SettingsGroup(Modifier.padding(start = pageInset, end = pageInset, top = 12.dp)) {
                ListRow(
                    title = stringResource(Res.string.autoeq_browse),
                    leading = { AppIcon(AppIcons.Search, contentDescription = null, size = 22.dp, tint = AppTheme.colors.accent) },
                    trailing = { AppIcon(AppIcons.ChevronRight, contentDescription = null, size = 16.dp, tint = AppTheme.colors.tertiaryLabel) },
                    onClick = onOpenAutoEq,
                )
                if (importer != null) {
                    ListRow(
                        title = stringResource(Res.string.eq_headphones_import),
                        leading = { AppIcon(AppIcons.ImportFile, contentDescription = null, size = 22.dp, tint = AppTheme.colors.accent) },
                        onClick = importer,
                    )
                }
                state.profile?.let { profile ->
                    ListRow(
                        title = stringResource(Res.string.eq_headphones_delete, profile.name),
                        leading = { AppIcon(AppIcons.Close, contentDescription = null, size = 22.dp, tint = AppTheme.colors.accent) },
                        onClick = { equalizer.deleteProfile(profile.id) },
                    )
                }
            }
        }
        if (importFailed) item { Hint(stringResource(Res.string.eq_import_failed)) }
        item { Hint(stringResource(Res.string.eq_headphones_hint)) }
    }
}

private const val CURVE_POINTS = 96

internal val EqPreset.label: StringResource
    get() = when (this) {
        EqPreset.Flat -> Res.string.eq_preset_flat
        EqPreset.MoreBass -> Res.string.eq_preset_more_bass
        EqPreset.LessBass -> Res.string.eq_preset_less_bass
        EqPreset.MoreTreble -> Res.string.eq_preset_more_treble
        EqPreset.LessTreble -> Res.string.eq_preset_less_treble
        EqPreset.Voice -> Res.string.eq_preset_voice
        EqPreset.LowVolume -> Res.string.eq_preset_low_volume
    }

/** 31 → "31", 1000 → "1k", 16000 → "16k". */
private fun frequencyLabel(hz: Double): String =
    if (hz >= 1000) "${(hz / 1000).roundToInt()}k" else hz.roundToInt().toString()

/** "+3.5 dB", "−2 dB", "0 dB", with the locale's decimal separator. */
private fun formatGain(db: Float, separator: String): String {
    val halves = (db * 2).roundToInt()
    val whole = abs(halves) / 2
    val number = if (halves % 2 == 0) "$whole" else "$whole${separator}5"
    val sign = when {
        halves > 0 -> "+"
        halves < 0 -> "−"
        else -> ""
    }
    return "$sign$number dB"
}

@Composable
private fun Hint(text: String) {
    AppText(
        text,
        AppTheme.typography.footnote,
        color = AppTheme.colors.label.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
