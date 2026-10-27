package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.library.ResumeOverride
import dev.cmdrchara.songlass.core.library.ResumeOverrideSummary
import dev.cmdrchara.songlass.core.library.ResumeOverrides
import dev.cmdrchara.songlass.core.library.ResumePolicy
import dev.cmdrchara.songlass.core.library.resumePolicy
import dev.cmdrchara.songlass.core.playlists.SmartPlaylist
import dev.cmdrchara.songlass.core.playlists.UserPlaylist
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.action_cancel
import dev.cmdrchara.songlass.shared.resources.back
import dev.cmdrchara.songlass.shared.resources.resume_override_action_disable
import dev.cmdrchara.songlass.shared.resources.resume_override_action_enable
import dev.cmdrchara.songlass.shared.resources.resume_override_empty
import dev.cmdrchara.songlass.shared.resources.resume_override_global_threshold
import dev.cmdrchara.songlass.shared.resources.resume_override_hint
import dev.cmdrchara.songlass.shared.resources.resume_override_playlist
import dev.cmdrchara.songlass.shared.resources.resume_override_remove
import dev.cmdrchara.songlass.shared.resources.resume_override_remove_all
import dev.cmdrchara.songlass.shared.resources.resume_override_settings_hint
import dev.cmdrchara.songlass.shared.resources.resume_override_settings_title
import dev.cmdrchara.songlass.shared.resources.resume_override_state_global
import dev.cmdrchara.songlass.shared.resources.resume_override_state_off
import dev.cmdrchara.songlass.shared.resources.resume_override_state_on
import dev.cmdrchara.songlass.shared.resources.resume_override_threshold
import dev.cmdrchara.songlass.shared.resources.resume_override_threshold_global
import dev.cmdrchara.songlass.shared.resources.resume_override_threshold_hint
import dev.cmdrchara.songlass.shared.resources.resume_override_threshold_short
import dev.cmdrchara.songlass.shared.resources.resume_override_threshold_title
import dev.cmdrchara.songlass.shared.resources.resume_override_title
import dev.cmdrchara.songlass.shared.resources.resume_override_unavailable
import dev.cmdrchara.songlass.shared.resources.resume_override_use_global
import dev.cmdrchara.songlass.shared.resources.settings_long_minutes
import org.jetbrains.compose.resources.stringResource

/**
 * Header action of a folder or playlist page: the sheet edits this collection's resumption
 * exception without touching the global settings. The sheet is drawn by the shared looks, so all
 * three styles get their own material and actions.
 */
@Composable
internal fun ResumeOverrideButton(
    state: AppState,
    context: PlayContext,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val preferences by state.settings.state.collectAsState()
    val overrides = state.controller.resumeOverrides
    val entries by overrides.entries.collectAsState()
    var visible by remember(context) { mutableStateOf(false) }
    val hasOverride = entries.any { it.context.key == context.key }
    IconButton(AppIcons.Clock, stringResource(Res.string.resume_override_title), { visible = true },
        modifier, selected = hasOverride, filled = filled)
    ResumeOverrideSheet(overrides, context, preferences, visible) { visible = false }
}

/**
 * Two-step sheet: the main page toggles resume and opens the threshold list; the nested page
 * returns through its own back action. No host is replaced and no coefficient is animated.
 */
@Composable
private fun ResumeOverrideSheet(
    overrides: ResumeOverrides,
    context: PlayContext,
    preferences: AppPreferences,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val entries by overrides.entries.collectAsState()
    val stored = entries.firstOrNull { it.context.key == context.key }?.override
    val effective = preferences.resumePolicy(stored)
    val effectiveThreshold = minutesLabel(effective.longTrackThresholdMs)
    var choosingThreshold by remember(visible) { mutableStateOf(false) }
    ActionSheet(
        visible = visible,
        title = stringResource(if (choosingThreshold) Res.string.resume_override_threshold_title
            else Res.string.resume_override_title),
        subtitle = stringResource(if (choosingThreshold) Res.string.resume_override_threshold_hint
            else Res.string.resume_override_hint),
        actions = if (choosingThreshold) buildList {
            add(SheetAction(stringResource(Res.string.back), AppIcons.ChevronLeft, {
                choosingThreshold = false
            }, dismissOnClick = false, isBackAction = true))
            addAll(thresholdActions(effective) { index ->
                overrides.set(context, (stored ?: ResumeOverride()).copy(
                    longTrackThresholdMs = AppSettings.LONG_TRACK_CHOICES_MS[index],
                ))
                choosingThreshold = false
            })
            if (stored?.longTrackThresholdMs != null) add(SheetAction(
                stringResource(Res.string.resume_override_global_threshold, minutesLabel(preferences.longTrackThresholdMs)),
                AppIcons.Close,
            ) {
                overrides.set(context, stored.copy(longTrackThresholdMs = null))
                choosingThreshold = false
            })
        } else buildList {
            add(SheetAction(
                stringResource(if (effective.resumePerTrack) Res.string.resume_override_action_disable
                    else Res.string.resume_override_action_enable),
                null,
            ) {
                overrides.set(context, (stored ?: ResumeOverride()).copy(resumePerTrack = !effective.resumePerTrack))
            })
            add(SheetAction(stringResource(Res.string.resume_override_threshold, effectiveThreshold),
                AppIcons.Clock, { choosingThreshold = true }, dismissOnClick = false))
            if (stored != null) add(SheetAction(stringResource(Res.string.resume_override_use_global),
                AppIcons.Close) { overrides.remove(context) })
        },
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = { choosingThreshold = false; onDismiss() },
    )
}

/** One action per global threshold choice; the checkmark shows the effective value. */
@Composable
private fun thresholdActions(
    effective: ResumePolicy,
    onSelect: (Int) -> Unit,
): List<SheetAction> = AppSettings.LONG_TRACK_CHOICES_MS.mapIndexed { index, thresholdMs ->
    SheetAction(minutesLabel(thresholdMs),
        if (thresholdMs == effective.longTrackThresholdMs) AppIcons.Checkmark else null,
        onClick = { onSelect(index) })
}

/** Minutes as the settings page shows them, e.g. "10 min". */
@Composable
private fun minutesLabel(durationMs: Long): String =
    stringResource(Res.string.settings_long_minutes, (durationMs / 60_000L).toInt())

/**
 * Settings → Library: every stored exception, resolved against the current folders and playlists.
 * A collection that no longer exists stays listed as unavailable, with its own removal.
 */
@Composable
internal fun ResumeOverridesSettingsSection(
    graph: AppGraph,
    preferences: AppPreferences,
    searchFocus: SettingsFocusRequest?,
) {
    val entries by graph.resumeOverrides.entries.collectAsState()
    val library by graph.library.collectAsState()
    val userPlaylists by graph.userPlaylists.playlists.collectAsState()
    val smartPlaylists by graph.smartPlaylists.playlists.collectAsState()
    val summaries = entries.map { entry ->
        ResumeOverrideSummary(entry.context, resolvedCollectionName(entry.context, library, userPlaylists, smartPlaylists), entry.override)
    }
    SettingsSearchAnchor(SettingsTarget.ResumeOverrides, searchFocus) {
        Column {
            SettingsPageSection(stringResource(Res.string.resume_override_settings_title))
            if (summaries.isEmpty()) {
                AppText(stringResource(Res.string.resume_override_empty), AppTheme.typography.footnote,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = pageInset + 4.dp, vertical = 8.dp))
            } else {
                SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                    summaries.forEach { summary ->
                        ListRow(
                            title = summary.name ?: fallbackCollectionName(summary.context),
                            subtitle = summarySubtitle(summary, preferences),
                            leading = { AppIcon(AppIcons.Clock, null, size = 24.dp, tint = AppTheme.colors.accent) },
                            trailing = {
                                IconButton(AppIcons.Close, stringResource(Res.string.resume_override_remove),
                                    { graph.resumeOverrides.remove(summary.context) })
                            },
                        )
                    }
                    ListRow(stringResource(Res.string.resume_override_remove_all),
                        leading = { AppIcon(AppIcons.Close, null, size = 24.dp, tint = AppTheme.colors.accent) },
                        onClick = { graph.resumeOverrides.clearAll() })
                }
            }
            AppText(stringResource(Res.string.resume_override_settings_hint), AppTheme.typography.footnote,
                color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = pageInset + 4.dp, vertical = 8.dp))
        }
    }
}

/** The name of a collection, or null when the current data no longer holds it. */
private fun resolvedCollectionName(
    context: PlayContext,
    library: MusicLibrary,
    userPlaylists: List<UserPlaylist>,
    smartPlaylists: List<SmartPlaylist>,
): String? = when (context) {
    is PlayContext.Playlist -> library.folder(context.id)?.name
    is PlayContext.UserPlaylist -> userPlaylists.firstOrNull { it.id == context.id }?.name
    is PlayContext.SmartPlaylist -> smartPlaylists.firstOrNull { it.id == context.id }?.name
    else -> null
}

@Composable
private fun fallbackCollectionName(context: PlayContext): String = when (context) {
    is PlayContext.Playlist -> context.id.trim('/').substringAfterLast('/')
    else -> stringResource(Res.string.resume_override_playlist)
}

/** The stored choices, with inherited fields shown as the global value they fall back to. */
@Composable
private fun summarySubtitle(summary: ResumeOverrideSummary, preferences: AppPreferences): String {
    val resume = stringResource(when (summary.override.resumePerTrack) {
        true -> Res.string.resume_override_state_on
        false -> Res.string.resume_override_state_off
        null -> Res.string.resume_override_state_global
    })
    val threshold = stringResource(
        if (summary.override.longTrackThresholdMs != null) Res.string.resume_override_threshold_short
        else Res.string.resume_override_threshold_global,
        minutesLabel(summary.override.longTrackThresholdMs ?: preferences.longTrackThresholdMs),
    )
    return listOfNotNull(resume, threshold, if (summary.available) null else stringResource(Res.string.resume_override_unavailable))
        .joinToString(" · ")
}
