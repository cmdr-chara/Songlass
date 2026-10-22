package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.designsystem.BackPageTitle

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.equalizer.EqualizerTuning
import dev.cmdrchara.songlass.core.equalizer.GraphicEqLayout
import dev.cmdrchara.songlass.core.equalizer.gainsFor
import dev.cmdrchara.songlass.designsystem.FilterChips
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.equalizer.EqChain
import dev.cmdrchara.songlass.core.equalizer.EqPreset
import dev.cmdrchara.songlass.core.equalizer.Equalizer
import dev.cmdrchara.songlass.core.equalizer.EqualizerProfileImport
import dev.cmdrchara.songlass.core.equalizer.EqualizerProfileImportResult
import dev.cmdrchara.songlass.core.equalizer.GraphicEq
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.EqBand
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.EqualizerEditor
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.InfoSheet
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/** Graphic equalizer, presets and headphone corrections; values and persistence live in core. */
@Composable
fun EqualizerScreen(equalizer: Equalizer, settings: AppSettings, backLabel: String, onBack: () -> Unit, onOpenAutoEq: () -> Unit, contentPadding: PaddingValues, searchFocus: SettingsFocusRequest? = null, returnToPlayer: Boolean = false, supportsTuning: Boolean = true) {
    val state by equalizer.state.collectAsState()
    val preferences by settings.state.collectAsState()
    val listState = rememberLazyListState()
    val outputProfilesOffset = if (state.outputProfiles.isNotEmpty()) 1 else 0
    val targetIndex = when (searchFocus?.target) {
        SettingsTarget.Equalizer -> 1
        SettingsTarget.OutputProfiles -> 3
        SettingsTarget.EqualizerBass -> 7 + outputProfilesOffset
        SettingsTarget.EqualizerTreble -> 8 + outputProfilesOffset
        SettingsTarget.EqualizerBands -> 9 + outputProfilesOffset
        SettingsTarget.EqualizerPresets -> 12 + outputProfilesOffset
        SettingsTarget.HeadphoneCorrection -> 14 + outputProfilesOffset
        else -> null
    }
    val searchReveal = SettingsSearchScroll(searchFocus, listState, targetIndex)
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var importFailure by remember { mutableStateOf<ProfileImportFailure?>(null) }
    var importPreview by remember { mutableStateOf<EqualizerProfileImport?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    fun saveChange(change: () -> Unit) {
        try { change(); saveFailed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { saveFailed = true }
    }
    val restoration by equalizer.mutations.state.collectAsState()
    val importTicket = remember(restoration) { equalizer.mutations.ticket() }
    LaunchedEffect(restoration.revision) {
        if (importPreview != null) { importPreview = null; importFailure = ProfileImportFailure.Changed }
    }
    val importer = key(importTicket) { rememberTextFileImporter(TextFileKind.EqualizerProfile) { fileName, text ->
        importFailure = null
        if (!equalizer.mutations.accepts(importTicket)) importFailure = ProfileImportFailure.Changed
        else if (text == null) importFailure = ProfileImportFailure.Invalid
        else if (!importing) {
            // The parser only sees this immutable revision. Persisting/applying stays on the
            // owner thread after a separate, explicit review of the destination and filters.
            val expectedState = equalizer.state.value
            importing = true
            scope.launch {
                try {
                    val parsed = withContext(Dispatchers.Default) {
                        equalizer.prepareProfileImport(fileName ?: "AutoEQ", text, expectedState, importTicket)
                    }
                    if (!equalizer.mutations.accepts(importTicket)) importFailure = ProfileImportFailure.Changed
                    else if (parsed == null) importFailure = ProfileImportFailure.Invalid
                    else importPreview = parsed
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { importFailure = ProfileImportFailure.Failed }
                finally { importing = false }
            }
        }
    } }
    val separator = stringResource(Res.string.eq_decimal_separator)
    val bands = remember(state.gains, state.tuning.layout, separator) {
        state.tuning.layout.frequencies.zip(state.gains) { f, g -> EqBand(f.toFloat(), frequencyLabel(f, separator), g.toFloat()) }
    }
    // The curve the user shapes, without the automatic preamp (it would only shift everything down).
    val curve = remember(state.gains, state.profile, state.tuning) {
        EqChain(state.profile?.filters.orEmpty() + GraphicEq.filters(state.gains, state.tuning.layout) + state.tuning.toneFilters, 0.0).curve(CURVE_POINTS).map { it.toFloat() }
    }

    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).then(searchReveal), state = listState, contentPadding = contentPadding) {
        item { BackPageTitle(stringResource(Res.string.eq_title),
            if (returnToPlayer || searchFocus == null) backLabel else stringResource(Res.string.settings_search_results), onBack) }
        item {
            SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                SettingsSearchAnchor(SettingsTarget.Equalizer, searchFocus) {
                    SettingsToggleRow(stringResource(Res.string.eq_title), state.enabled, { saveChange { equalizer.setEnabled(it) } })
                }
            }
        }
        item { SectionTitle(stringResource(Res.string.eq_outputs)) }
        item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
            SettingsSearchAnchor(SettingsTarget.OutputProfiles, searchFocus) {
                SettingsToggleRow(stringResource(Res.string.eq_outputs_auto), state.automaticOutputs, { saveChange { equalizer.setAutomaticOutputs(it) } })
            }
            state.output?.let { output ->
                val name = if (output.id.startsWith("builtin:")) stringResource(Res.string.output_this_phone) else output.name
                ListRow(title = name, subtitle = stringResource(if (state.usesOutputProfile) Res.string.eq_outputs_bound else Res.string.eq_outputs_manual), onClick = null)
                ListRow(
                    title = stringResource(if (state.outputProfile == null) Res.string.eq_outputs_save else Res.string.audio_eq_update_binding),
                    subtitle = if (state.automaticOutputs) null else stringResource(Res.string.audio_eq_saved_auto_off),
                    onClick = { saveChange(equalizer::saveForCurrentOutput) },
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
                    subtitle = equalizerCurveLabel(manual.enabled, EqPreset.entries.firstOrNull { it.gainsFor(state.tuning.layout) == manual.gainsFor(state.tuning.layout) }, state.profiles.firstOrNull { it.id == manual.profileId }?.name),
                    onClick = null,
                )
            }
        } }
        if (state.outputProfiles.isNotEmpty()) item { SettingsGroup(Modifier.padding(start = pageInset, end = pageInset, top = 8.dp)) {
            state.outputProfiles.forEach { binding ->
                val active = state.usesOutputProfile && state.output?.id == binding.output.id
                val status = stringResource(if (active) Res.string.audio_eq_saved_active else Res.string.audio_eq_saved_inactive)
                val preset = EqPreset.entries.firstOrNull { it.gainsFor(state.tuning.layout) == binding.gainsFor(state.tuning.layout) }
                val description = equalizerCurveLabel(binding.enabled, preset, state.profiles.firstOrNull { it.id == binding.profileId }?.name)
                ListRow(title = if (binding.output.id.startsWith("builtin:")) stringResource(Res.string.output_this_phone) else binding.output.name,
                    subtitle = "$status · $description", trailing = {
                    dev.cmdrchara.songlass.designsystem.IconButton(AppIcons.Close, stringResource(Res.string.eq_outputs_remove), { saveChange { equalizer.removeOutputProfile(binding.output.id) } })
                }, onClick = null)
            }
        } }
        item { FeatureHint(stringResource(Res.string.eq_outputs_hint)) }
        item {
            key(state.tuning.layout) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(start = pageInset, end = pageInset, top = 20.dp)) {
                    // Dense layouts keep readable labels and touch targets by scrolling horizontally.
                    val width = if (bands.size > 10) maxOf(maxWidth, (bands.size * 48 + 50).dp) else maxWidth
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        EqualizerEditor(bands, curve, GraphicEq.MAX_GAIN_DB.toFloat(), state.enabled,
                            { formatGain(it, separator) }, { band, gain ->
                                saveChange { equalizer.setGain(band, gain.toDouble(), enable = true) }
                            }, Modifier.width(width))
                    }
                }
            }
        }
        item { Hint(stringResource(Res.string.audio_eq_headroom_hint)) }
        item(key = "tone-bass") {
            SettingsSearchAnchor(SettingsTarget.EqualizerBass, searchFocus) {
                ToneControl(stringResource(Res.string.eq_tone_bass), preferences.equalizerTuning.bassDb, separator, supportsTuning) {
                    saveChange { settings.setEqualizerBass(it) }
                }
            }
        }
        item(key = "tone-treble") {
            SettingsSearchAnchor(SettingsTarget.EqualizerTreble, searchFocus) {
                ToneControl(stringResource(Res.string.eq_tone_treble), preferences.equalizerTuning.trebleDb, separator, supportsTuning) {
                    saveChange { settings.setEqualizerTreble(it) }
                }
            }
        }
        item(key = "band-count") {
            SettingsSearchAnchor(SettingsTarget.EqualizerBands, searchFocus) {
                LocalGlassLook.current.SettingsPageGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        AppText(stringResource(Res.string.eq_band_count), AppTheme.typography.body, modifier = Modifier.padding(horizontal = 16.dp))
                        if (supportsTuning) FilterChips(GraphicEqLayout.entries.map { it.count.toString() },
                            GraphicEqLayout.entries.indexOf(preferences.equalizerTuning.layout), {
                                saveChange { settings.setEqualizerLayout(GraphicEqLayout.entries[it]) }
                            })
                        else AppText(preferences.equalizerTuning.layout.count.toString(), AppTheme.typography.body, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        item { Hint(stringResource(Res.string.eq_tuning_hint)) }
        item { SectionTitle(stringResource(Res.string.eq_presets)) }
        item {
            SettingsSearchAnchor(SettingsTarget.EqualizerPresets, searchFocus) {
                SettingsGroup(Modifier.padding(horizontal = pageInset).selectableGroup()) {
                    EqPreset.entries.forEach { preset ->
                        SettingsChoiceRow(stringResource(preset.label), selected = state.preset == preset, onClick = {
                            saveChange { equalizer.applyPreset(preset, enable = preset != EqPreset.Flat) }
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
                    SettingsChoiceRow(stringResource(Res.string.eq_headphones_none), selected = state.profileId == null, onClick = { saveChange { equalizer.selectProfile(null) } })
                    state.profiles.forEach { profile ->
                        SettingsChoiceRow(profile.name, selected = state.profileId == profile.id, onClick = { saveChange { equalizer.selectProfile(profile.id) } })
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
                        onClick = if (importing) null else ({
                            if (equalizer.mutations.accepts(importTicket)) { importFailure = null; importer() }
                            else importFailure = ProfileImportFailure.Changed
                        }),
                    )
                }
                state.profile?.let { profile ->
                    ListRow(
                        title = stringResource(Res.string.eq_headphones_delete, profile.name),
                        leading = { AppIcon(AppIcons.Close, contentDescription = null, size = 22.dp, tint = AppTheme.colors.accent) },
                        onClick = { saveChange { equalizer.deleteProfile(profile.id) } },
                    )
                }
            }
        }
        if (importing) item { Hint(stringResource(Res.string.eq_import_reading)) }
        importFailure?.let { failure -> item { Hint(stringResource(when (failure) {
            ProfileImportFailure.Invalid -> Res.string.eq_import_failed
            ProfileImportFailure.Changed -> Res.string.eq_import_changed
            ProfileImportFailure.Failed -> Res.string.eq_import_save_failed
        }), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
        item { Hint(stringResource(Res.string.eq_headphones_hint)) }
    }
    importPreview?.let { preview ->
        ProfileImportReview(preview, onDismiss = { importPreview = null }, onConfirm = {
            importFailure = when (equalizer.confirmProfileImport(preview)) {
                is EqualizerProfileImportResult.Saved -> null
                EqualizerProfileImportResult.Changed -> ProfileImportFailure.Changed
                EqualizerProfileImportResult.Invalid -> ProfileImportFailure.Invalid
                EqualizerProfileImportResult.Failed -> ProfileImportFailure.Failed
            }
            importPreview = null
        })
    }
    InfoSheet(visible = saveFailed, title = stringResource(Res.string.ordinary_save_failed_title),
        rows = listOf(InfoRow(stringResource(Res.string.eq_title), stringResource(Res.string.ordinary_eq_save_failed))),
        closeLabel = stringResource(Res.string.action_close), onDismiss = { saveFailed = false })
}

private enum class ProfileImportFailure { Invalid, Changed, Failed }

/** A platform dialog sits above navigation; every visible control still uses the active look. */
@Composable
private fun ProfileImportReview(preview: EqualizerProfileImport, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val output = preview.output
    val outputName = output?.let { if (it.id.startsWith("builtin:")) stringResource(Res.string.output_this_phone) else it.name }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(pageInset), contentAlignment = Alignment.Center) {
            SettingsGroup(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppText(stringResource(Res.string.eq_import_review), AppTheme.typography.title3,
                        modifier = Modifier.semantics { heading() })
                    AppText(preview.name, AppTheme.typography.headline)
                    AppText(stringResource(Res.string.eq_import_filter_summary, preview.filters.size, preview.skipped),
                        AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel)
                    AppText(if (outputName == null) stringResource(Res.string.eq_import_manual_target)
                        else stringResource(Res.string.eq_import_output_target, outputName), AppTheme.typography.body)
                    AppText(stringResource(Res.string.eq_import_review_hint), AppTheme.typography.subheadline,
                        color = AppTheme.colors.secondaryLabel)
                    ActionButton(stringResource(Res.string.eq_import_confirm), onConfirm, Modifier.fillMaxWidth())
                    ActionButton(stringResource(Res.string.action_cancel), onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
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
private fun frequencyLabel(hz: Double, separator: String): String {
    val value = if (hz >= 1000) hz / 1000 else hz
    return value.toString().removeSuffix(".0").replace(".", separator) + if (hz >= 1000) "k" else ""
}

/** "+3.5 dB", "−2 dB", "0 dB", with the locale's decimal separator. */
internal fun formatGain(db: Float, separator: String): String {
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
private fun ToneControl(title: String, db: Double, separator: String, enabled: Boolean, change: (Double) -> Unit) {
    val value = formatGain(db.toFloat(), separator)
    val lessLabel = stringResource(Res.string.eq_tone_less)
    val moreLabel = stringResource(Res.string.eq_tone_more)
    LocalGlassLook.current.SettingsPageGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(title, AppTheme.typography.body)
            AppText(value, AppTheme.typography.body, color = AppTheme.colors.accent)
            if (enabled) {
                LocalGlassLook.current.ValueSlider(((db + 12) / 24).toFloat(), { change(EqualizerTuning.gainFromSlider(it)) }, title,
                    Modifier.fillMaxWidth().semantics { stateDescription = value })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(stringResource(Res.string.eq_tone_less_short), { change((db - .5).coerceAtLeast(-12.0)) }, Modifier.weight(1f).semantics { contentDescription = lessLabel })
                    ActionButton(stringResource(Res.string.settings_effect_reset), { change(0.0) }, Modifier.weight(1.5f))
                    ActionButton(stringResource(Res.string.eq_tone_more_short), { change((db + .5).coerceAtMost(12.0)) }, Modifier.weight(1f).semantics { contentDescription = moreLabel })
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    AppText(
        text,
        AppTheme.typography.footnote,
        color = AppTheme.colors.label.copy(alpha = 0.7f),
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
