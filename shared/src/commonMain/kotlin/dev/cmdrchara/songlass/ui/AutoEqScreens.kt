package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.equalizer.*
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
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
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        BackLink(stringResource(Res.string.eq_title), state::goBack)
        AutoEqHeading(stringResource(Res.string.autoeq_title))
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

/** Download/inspect never changes the audible curve. Only the explicit Apply action does. */
@Composable
fun AutoEqDetailScreen(entry: AutoEqEntry, repository: AutoEqRepository, equalizer: Equalizer,
    onBack: () -> Unit, onApplied: () -> Unit, contentPadding: PaddingValues) {
    var retry by remember(entry.id) { mutableIntStateOf(0) }
    val correction by produceState<AutoEqLoad<AutoEqCorrection>>(AutoEqLoad.Loading, entry.id, retry) {
        value = AutoEqLoad.Loading
        value = try { AutoEqLoad.Ready(withContext(Dispatchers.Default) { repository.correction(entry) }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AutoEqLoad.Failed }
    }
    val eq by equalizer.state.collectAsState()
    val output = eq.output
    val outputName = output?.let { if (it.id.startsWith("builtin:")) stringResource(Res.string.output_this_phone) else it.name }
    var bind by rememberSaveable(entry.id, output?.id) { mutableStateOf(false) }
    var applyFailed by remember(entry.id) { mutableStateOf(false) }
    val current = (correction as? AutoEqLoad.Ready)?.value
    val preview = remember(current) { current?.let { EqChain(it.filters, 0.0).curve(96).map(Double::toFloat) }.orEmpty() }
    val range = remember(preview) { maxOf(12f, ceil((preview.maxOfOrNull { abs(it) } ?: 12f) / 6f) * 6f) }
    val description = stringResource(Res.string.autoeq_curve_description, entry.name)
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
        item { BackLink(stringResource(Res.string.autoeq_title), onBack) }
        item { AutoEqHeading(entry.name) }
        item { FeatureHint(stringResource(Res.string.autoeq_measured_by, entry.source)) }
        when (correction) {
            AutoEqLoad.Loading -> item { FeatureHint(stringResource(Res.string.autoeq_loading_profile)) }
            AutoEqLoad.Failed -> item {
                FeatureHint(stringResource(Res.string.autoeq_download_failed))
                ActionButton(stringResource(Res.string.autoeq_retry), { retry++ }, Modifier.padding(16.dp))
            }
            is AutoEqLoad.Ready -> {
                item { SectionTitle(stringResource(Res.string.autoeq_preview)) }
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
                        if (equalizer.applyCatalogProfile(it, if (bind || eq.usesOutputProfile) output else null)) onApplied()
                        else applyFailed = true
                    }
                }, Modifier.fillMaxWidth().padding(16.dp)) }
                if (applyFailed) item { FeatureHint(stringResource(Res.string.autoeq_apply_failed)) }
                item { FeatureHint(stringResource(Res.string.autoeq_attribution)) }
            }
        }
    }
}

/** Secondary-page titles wrap long model/variant names instead of truncating a root app bar. */
@Composable
private fun AutoEqHeading(text: String) {
    AppText(text, AppTheme.typography.largeTitle,
        modifier = Modifier.padding(horizontal = pageInset, vertical = 8.dp).semantics { heading() })
}
