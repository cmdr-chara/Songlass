package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.core.history.PlayCountRule
import dev.cmdrchara.songlass.core.library.LibraryFilter
import dev.cmdrchara.songlass.core.library.LibraryFolderOption
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.core.settings.AlbumLayout
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibrarySettingsScreen(graph: AppGraph, onBack: () -> Unit, onPickDuration: () -> Unit, onRestoreDefaults: (dev.cmdrchara.songlass.core.settings.SettingsArea) -> Unit, contentPadding: PaddingValues, backLabel: String, searchFocus: SettingsFocusRequest? = null, searchResultOrigin: Boolean = true) {
    val preferences by graph.settings.state.collectAsState()
    val refreshFailure by graph.libraryRefreshFailure.collectAsState()
    val loading by graph.isLibraryLoading.collectAsState()
    val listState = rememberLazyListState()
    val targetIndex = when (searchFocus?.target) {
        SettingsTarget.MinimumDuration -> 3
        SettingsTarget.ExcludedFolders -> 4
        SettingsTarget.PlayCountRule -> 8
        SettingsTarget.AlbumLayout -> 11
        else -> null
    }
    val searchReveal = SettingsSearchScroll(searchFocus, listState, targetIndex)
    val tracks by graph.allTracks.collectAsState()
    val library by graph.library.collectAsState()
    val filter = preferences.libraryFilter
    val folders = remember(tracks, filter.excludedFolders) { LibraryFilter.folderTree(tracks, filter.excludedFolders) }
    var expanded by remember(tracks) { mutableStateOf(folders.map { it.path }.toSet()) }
    val durations = LibraryFilter.durationChoicesMs
    val durationLabels = durations.map { duration ->
        if (duration == 0L) stringResource(Res.string.library_all_durations)
        else stringResource(Res.string.library_duration_seconds, (duration / 1000).toInt())
    }
LocalGlassLook.current.SettingsPage {
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).then(searchReveal), state = listState, contentPadding = contentPadding) {
        item { LocalGlassLook.current.SettingsPageTitle(stringResource(Res.string.library_settings),
            if (searchFocus == null || !searchResultOrigin) backLabel else stringResource(Res.string.settings_search_results), onBack, Modifier) }
        item { LibraryHint(stringResource(Res.string.library_filter_count, library.tracks.size, tracks.size)) }
        item(key = "rescan") {
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
                ListRow(
                    title = stringResource(Res.string.library_rescan),
                    subtitle = if (loading) stringResource(Res.string.library_scanning) else null,
                    leading = { AppIcon(AppIcons.Library, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
                    onClick = if (loading) null else graph::requestLibraryRefresh,
                )
            }
        }
        item {
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 12.dp)) {
                SettingsSearchAnchor(SettingsTarget.MinimumDuration, searchFocus) {
                    ListRow(
                        title = stringResource(Res.string.library_minimum_duration),
                        subtitle = durationLabels[durations.indexOf(filter.minimumDurationMs)],
                        leading = { AppIcon(AppIcons.Clock, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
                        trailing = { AppIcon(AppIcons.ChevronRight, contentDescription = null, size = 16.dp, tint = AppTheme.colors.tertiaryLabel) },
                        onClick = onPickDuration,
                    )
                }
            }
        }
        item {
            SettingsSearchAnchor(SettingsTarget.ExcludedFolders, searchFocus) {
                SettingsPageSection(stringResource(Res.string.library_excluded_folders))
            }
        }
        item { LibraryHint(stringResource(Res.string.library_exclusions_hint)) }
        if (folders.isEmpty()) item { LibraryHint(stringResource(Res.string.library_no_folders)) }
        else item {
            SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                @Composable fun folderRow(folder: LibraryFolderOption, depth: Int) {
                    val path = folder.path
                    val parentExcluded = filter.excludedFolders.any { path != it && path.startsWith("$it/") }
                    val count = pluralStringResource(Res.plurals.songs_count, folder.trackCount, folder.trackCount)
                    val children = if (folder.children.isNotEmpty()) pluralStringResource(Res.plurals.library_subfolders, folder.children.size, folder.children.size) else null
                    val subtitle = if (parentExcluded) stringResource(Res.string.library_parent_excluded)
                        else listOfNotNull(count, children).joinToString(" · ")
                    FolderChoiceRow(
                        FolderChoice(folder.name, subtitle, if (folder.children.isEmpty()) null else path in expanded,
                            parentExcluded || path in filter.excludedFolders, !parentExcluded,
                            stringResource(if (path in expanded) Res.string.library_collapse_folder else Res.string.library_expand_folder, folder.name),
                            stringResource(Res.string.library_exclude_folder, path)),
                        onExpand = { expanded = if (path in expanded) expanded - path else expanded + path },
                        onExcludedChange = { graph.settings.setFolderExcluded(path, it) },
                        modifier = Modifier.padding(start = (depth * 16).coerceAtMost(48).dp),
                    )
                    if (path in expanded) folder.children.forEach { folderRow(it, depth + 1) }
                }
                folders.forEach { folderRow(it, 0) }
            }
        }
        item { SettingsPageSection(stringResource(Res.string.library_counting_title)) }
        item {
            SettingsSearchAnchor(SettingsTarget.PlayCountRule, searchFocus) {
                SettingsGroup(Modifier.padding(horizontal = pageInset).selectableGroup()) {
                    PlayCountRule.entries.forEach { rule ->
                        SettingsChoiceRow(playCountRuleLabel(rule), preferences.playCountRule == rule,
                            { graph.settings.setPlayCountRule(rule) })
                    }
                }
            }
        }
        item { LibraryHint(stringResource(Res.string.library_counting_hint)) }
        item { SettingsPageSection(stringResource(Res.string.library_album_layout)) }
        item {
            SettingsSearchAnchor(SettingsTarget.AlbumLayout, searchFocus) {
                SegmentedControl(
                    AlbumLayout.entries.map { stringResource(if (it == AlbumLayout.Grid) Res.string.library_layout_grid else Res.string.library_layout_list) },
                    preferences.albumLayout.ordinal, { graph.settings.setAlbumLayout(AlbumLayout.entries[it]) },
                    Modifier.padding(horizontal = pageInset).heightIn(min = 48.dp))
            }
        }
        if (refreshFailure != null) item {
            LibraryRefreshNotice(refreshFailure, loading, graph::requestLibraryRefresh)
        }
        item(key = "restore-defaults") { RestoreDefaultsButton(dev.cmdrchara.songlass.core.settings.SettingsArea.Library, onRestoreDefaults) }
    }
    }
}

@Composable
internal fun playCountRuleLabel(rule: PlayCountRule): String = stringResource(when (rule) {
    PlayCountRule.ThirtySecondsOrHalf -> Res.string.library_counting_default
    PlayCountRule.Half -> Res.string.library_counting_half
    PlayCountRule.NearlyAll -> Res.string.library_counting_nearly_all
})

/** Hosted above the scaffold so the bottom bar cannot cover sheet actions. */
@Composable
fun LibraryDurationMenu(state: AppState) {
    val preferences by state.settings.state.collectAsState()
    val durations = LibraryFilter.durationChoicesMs
    val durationLabels = durations.map { duration ->
        if (duration == 0L) stringResource(Res.string.library_all_durations)
        else stringResource(Res.string.library_duration_seconds, (duration / 1000).toInt())
    }
    ActionSheet(
        visible = state.isLibraryDurationPickerOpen,
        title = stringResource(Res.string.library_minimum_duration),
        subtitle = stringResource(Res.string.library_duration_hint),
        artwork = null,
        actions = durations.mapIndexed { index, duration ->
            SheetAction(durationLabels[index], if (preferences.libraryFilter.minimumDurationMs == duration) AppIcons.Checkmark else null) {
                state.settings.setMinimumDuration(duration)
            }
        },
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = state::closeLibraryDurationPicker,
    )
}

@Composable
private fun LibraryHint(text: String) {
    AppText(text, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}
