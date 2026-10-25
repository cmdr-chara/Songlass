package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.equalizer.*
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.ceil

private data class ReviewedAutoEq(
    val correction: AutoEqCorrection,
    val ticket: dev.cmdrchara.songlass.core.storage.MutationTicket,
)

private sealed interface AutoEqLoad<out T> {
    data object Loading : AutoEqLoad<Nothing>
    data object Failed : AutoEqLoad<Nothing>
    data class Ready<T>(val value: T) : AutoEqLoad<T>
}

@Composable
fun AutoEqCatalogScreen(repository: AutoEqRepository, state: AppState, contentPadding: PaddingValues) {
    var retry by remember { mutableIntStateOf(0) }
    val catalog by produceState<AutoEqLoad<AutoEqCatalog>>(AutoEqLoad.Loading, repository, retry) {
        value = AutoEqLoad.Loading
        value = try { AutoEqLoad.Ready(withContext(Dispatchers.Default) { repository.catalog() }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AutoEqLoad.Failed }
    }
    val current = (catalog as? AutoEqLoad.Ready)?.value
    val results by produceState<List<AutoEqEntry>>(emptyList(), current, state.autoEqQuery) {
        val query = state.autoEqQuery
        if (query.isNotBlank()) delay(120)
        value = withContext(Dispatchers.Default) { current?.search(query).orEmpty() }
    }
    val focus = LocalFocusManager.current
    LocalGlassLook.current.SettingsToolPage {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
            SettingsPageHeader(stringResource(Res.string.autoeq_title), stringResource(Res.string.eq_title), state::goBack)
            SearchField(state.autoEqQuery, { state.autoEqQuery = it },
                stringResource(Res.string.autoeq_search), stringResource(Res.string.search_clear),
                Modifier.padding(horizontal = pageInset, vertical = 8.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = contentPadding) {
                when (catalog) {
                    AutoEqLoad.Loading -> item { FeatureHint(stringResource(Res.string.autoeq_loading_catalog)) }
                    AutoEqLoad.Failed -> item {
                        FeatureHint(stringResource(Res.string.autoeq_catalog_failed))
                        ActionButton(stringResource(Res.string.autoeq_retry), { retry++ }, Modifier.padding(16.dp))
                    }
                    is AutoEqLoad.Ready -> {
                        item { FeatureHint(stringResource(Res.string.autoeq_results, results.size)) }
                        if (results.isEmpty()) item { FeatureHint(stringResource(Res.string.autoeq_no_results)) }
                        items(results, key = { it.id }) { entry ->
                            ListRow(entry.name, subtitle = stringResource(Res.string.autoeq_measured_by, entry.source),
                                trailing = { AppIcon(AppIcons.ChevronRight, null, size = 16.dp, tint = AppTheme.colors.tertiaryLabel) },
                                modifier = Modifier.padding(horizontal = pageInset),
                                onClick = { focus.clearFocus(); state.open(Route.AutoEqDetail(entry)) })
                        }
                        item { FeatureHint(stringResource(Res.string.autoeq_attribution)) }
                    }
                }
            }
        }
    }
}

/** Download/inspect never changes the audible curve. Only the explicit Apply action does. */
@Composable
fun AutoEqDetailScreen(entry: AutoEqEntry, repository: AutoEqRepository, equalizer: Equalizer,
    onBack: () -> Unit, onApplied: () -> Unit, contentPadding: PaddingValues) {
    var retry by remember(entry.id) { mutableIntStateOf(0) }
    val restoration by equalizer.mutations.state.collectAsState()
    val correction by produceState<AutoEqLoad<ReviewedAutoEq>>(AutoEqLoad.Loading, entry.id, retry, restoration) {
        value = AutoEqLoad.Loading
        val ticket = equalizer.mutations.ticket() ?: return@produceState
        value = try {
            val loaded = withContext(Dispatchers.Default) { repository.correction(entry) }
            if (equalizer.mutations.accepts(ticket)) AutoEqLoad.Ready(ReviewedAutoEq(loaded, ticket))
            else AutoEqLoad.Failed
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AutoEqLoad.Failed }
    }
    val eq by equalizer.state.collectAsState()
    val output = eq.output
    val outputName = output?.let { if (it.id.startsWith("builtin:")) stringResource(Res.string.output_this_phone) else it.name }
    var bind by rememberSaveable(entry.id, output?.id) { mutableStateOf(false) }
    var applyFailed by remember(entry.id) { mutableStateOf(false) }
    var saveFailed by remember(entry.id) { mutableStateOf(false) }
    val reviewed = (correction as? AutoEqLoad.Ready)?.value
    val current = reviewed?.correction
    val preview = remember(current) { current?.let { EqChain(it.filters, 0.0).curve(96).map(Double::toFloat) }.orEmpty() }
    val range = remember(preview) { maxOf(12f, ceil((preview.maxOfOrNull { abs(it) } ?: 12f) / 6f) * 6f) }
    val description = stringResource(Res.string.autoeq_curve_description, entry.name)
    LocalGlassLook.current.SettingsToolPage {
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
            item { SettingsPageHeader(entry.name, stringResource(Res.string.autoeq_title), onBack) }
            item { FeatureHint(stringResource(Res.string.autoeq_measured_by, entry.source)) }
            when (correction) {
                AutoEqLoad.Loading -> item { FeatureHint(stringResource(Res.string.autoeq_loading_profile)) }
                AutoEqLoad.Failed -> item {
                    FeatureHint(stringResource(Res.string.autoeq_download_failed))
                    ActionButton(stringResource(Res.string.autoeq_retry), { retry++ }, Modifier.padding(16.dp))
                }
                is AutoEqLoad.Ready -> {
                    item { SettingsPageSection(stringResource(Res.string.autoeq_preview)) }
                    item { EqualizerEditor(emptyList(), preview, range, true, { "$it dB" }, { _, _ -> },
                        Modifier.padding(horizontal = pageInset).semantics { contentDescription = description }) }
                    item { FeatureHint(stringResource(Res.string.autoeq_preview_hint)) }
                    if (outputName != null) item {
                        SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                            SettingsToggleRow(stringResource(Res.string.autoeq_bind, outputName), bind || eq.usesOutputProfile,
                                { bind = it }, enabled = !eq.usesOutputProfile)
                        }
                        FeatureHint(stringResource(Res.string.autoeq_bind_hint))
                    }
                    item { ActionButton(stringResource(Res.string.autoeq_apply), {
                        current?.let {
                            applyFailed = false
                            saveFailed = false
                            val applied = try {
                                equalizer.applyCatalogProfile(it, if (bind || eq.usesOutputProfile) output else null, reviewed.ticket)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { saveFailed = true; false }
                            if (applied) onApplied()
                            else if (!saveFailed) applyFailed = true
                        }
                    }, Modifier.fillMaxWidth().padding(16.dp)) }
                    if (applyFailed) item { FeatureHint(stringResource(Res.string.autoeq_apply_failed)) }
                    if (saveFailed) item {
                        Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            FeatureHint(stringResource(Res.string.ordinary_eq_save_failed))
                        }
                    }
                    item { FeatureHint(stringResource(Res.string.autoeq_attribution)) }
                }
            }
        }
    }
}
