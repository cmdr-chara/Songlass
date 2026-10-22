package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.cmdrchara.songlass.core.player.Crossfade
import dev.cmdrchara.songlass.core.player.CrossfadeKind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.equalizer.Equalizer
import dev.cmdrchara.songlass.core.playback.SleepTimerState
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.settings.*
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility
import dev.cmdrchara.songlass.shared.resources.*
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import org.jetbrains.compose.resources.stringResource

/** UI navigation only: preference values and persistence remain in core. */
enum class SettingsPane { Overview, Appearance, Playback, Queue, Lyrics, Accessibility }

@Composable
fun SettingsScreen(
    settings: AppSettings,
    preferences: AppPreferences,
    supportsAmllLyrics: Boolean,
    supportsCrossfade: Boolean,
    supportsStyleIcons: Boolean,
    systemAccessibility: UiAccessibility,
    equalizer: Equalizer,
    sleepTimer: SleepTimerState,
    onOpenSleepTimer: () -> Unit,
    onRestoreDefaults: (SettingsArea) -> Unit,
    pane: SettingsPane,
    search: SettingsSearchSession,
    onSearchResult: (SettingsTarget) -> Unit,
    onPaneChanged: (SettingsPane) -> Unit,
    isActive: Boolean,
    onOpenEqualizer: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHomeCustomization: () -> Unit,
    onOpenBackup: () -> Unit,
    backLabel: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    returnToPlayer: Boolean = false,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeDurationEditor() {
        focus.clearFocus(); keyboard?.hide()
        search.durationDraft = null
    }
    fun openDurationEditor(kind: CrossfadeKind) {
        if (!supportsCrossfade) return
        focus.clearFocus(); keyboard?.hide()
        search.durationDraft = SettingsDurationDraft(kind,
            if (kind == CrossfadeKind.Automatic) preferences.crossfadeDurationMs else preferences.manualCrossfadeDurationMs)
    }
    val durationRequest = search.focus?.takeIf { isActive && pane == SettingsPane.Playback }
    LaunchedEffect(durationRequest?.id, supportsCrossfade) {
        val request = durationRequest ?: return@LaunchedEffect
        if (search.openedDurationRequestId == request.id) return@LaunchedEffect
        val kind = when (request.target) {
            SettingsTarget.CrossfadeDuration -> CrossfadeKind.Automatic
            SettingsTarget.ManualCrossfadeDuration -> CrossfadeKind.Manual
            else -> return@LaunchedEffect
        }
        // Consume each request once: Save, Cancel and Back must not immediately reopen the draft.
        search.openedDurationRequestId = request.id
        openDurationEditor(kind)
    }
    fun backFrom(page: SettingsPane) {
        focus.clearFocus(); keyboard?.hide()
        when {
            page == SettingsPane.Playback && search.durationDraft != null -> closeDurationEditor()
            returnToPlayer -> onBack()
            page == SettingsPane.Overview -> { search.updateQuery(""); search.returnToResults() }
            search.resultPane == page -> { search.returnToResults(); onPaneChanged(SettingsPane.Overview) }
            else -> onPaneChanged(SettingsPane.Overview)
        }
    }
    PlatformBackHandler(isActive && (pane != SettingsPane.Overview || search.query.isNotEmpty())) { backFrom(pane) }
    val documents = settingsSearchDocuments()
    val index = remember(documents) { SettingsSearchIndex(documents) }
    val results = remember(index, search.query, supportsAmllLyrics, supportsCrossfade) {
        index.search(search.query, supportsAmllLyrics, supportsCrossfade)
    }
    val eqState by equalizer.state.collectAsState()
    LocalGlassLook.current.SettingsPage {
        ScreenTransition(pane, direction = { _, to ->
            if (to == SettingsPane.Overview) NavigationDirection.Backward else NavigationDirection.Forward
        }) { page ->
            val searching = page == SettingsPane.Overview && search.query.isNotBlank()
            val scroll = if (searching) search.resultsScroll else search.paneScroll.getValue(page)
            val request = search.focus?.takeIf { isActive && page == pane && it.target.settingsPane == page }
            val reveal = SettingsSearchScroll(request, scroll, request?.target?.settingsItemIndex)
            LazyColumn(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding().then(reveal),
                state = scroll,
                contentPadding = contentPadding,
            ) {
                item(key = "title") {
                    val title = when (page) {
                        SettingsPane.Overview -> Res.string.settings_title
                        SettingsPane.Appearance -> Res.string.settings_appearance
                        SettingsPane.Playback -> Res.string.settings_playback
                        SettingsPane.Queue -> Res.string.settings_queue
                        SettingsPane.Lyrics -> Res.string.settings_lyrics
                        SettingsPane.Accessibility -> Res.string.settings_accessibility
                    }
                    LocalGlassLook.current.SettingsPageTitle(stringResource(title),
                        when (page) {
                            SettingsPane.Overview -> if (search.query.isNotEmpty()) stringResource(Res.string.settings_title) else backLabel
                            SettingsPane.Playback -> stringResource(when {
                                search.durationDraft != null -> Res.string.action_cancel
                                returnToPlayer -> Res.string.player_return
                                search.resultPane == page -> Res.string.settings_search_results
                                else -> Res.string.settings_title
                            })
                            else -> if (search.resultPane == page) stringResource(Res.string.settings_search_results) else stringResource(Res.string.settings_title)
                        },
                        if (page == SettingsPane.Overview && search.query.isEmpty()) onBack else ({ backFrom(page) }), Modifier)
                }
                when (page) {
                    SettingsPane.Overview -> {
                        item(key = "settings-search") {
                            SearchField(search.query, search::updateQuery, stringResource(Res.string.settings_search_placeholder),
                                stringResource(Res.string.search_clear), Modifier.padding(horizontal = pageInset, vertical = 8.dp))
                        }
                        if (searching) settingsSearchResults(results, documents, preferences, eqState, systemAccessibility) {
                            focus.clearFocus(); keyboard?.hide(); onSearchResult(it)
                        } else {
                            overviewSettings(preferences, supportsAmllLyrics, onPaneChanged,
                                onOpenLibrary, onOpenHomeCustomization, onOpenBackup, onOpenAbout)
                        }
                    }
                    SettingsPane.Appearance -> appearanceSettings(settings, preferences, request, supportsStyleIcons)
                    SettingsPane.Playback -> playbackSettings(settings, preferences, equalizer, sleepTimer, onOpenSleepTimer,
                        { closeDurationEditor(); onOpenEqualizer() }, request, supportsCrossfade,
                        search.durationDraft, ::openDurationEditor, ::closeDurationEditor)
                    SettingsPane.Queue -> queueSettings(settings, preferences, request)
                    SettingsPane.Lyrics -> if (supportsAmllLyrics) {
                        item {
                            SettingsFormGroup(Modifier.padding(horizontal = pageInset, vertical = 12.dp)) {
                                SettingsSearchAnchor(SettingsTarget.AmllLyrics, request) {
                                    SettingsToggleRow(stringResource(Res.string.settings_amll), preferences.amllEnabled,
                                        settings::setAmllEnabled)
                                }
                            }
                        }
                        item { SettingsHint(stringResource(Res.string.settings_amll_hint)) }
                    }
                    SettingsPane.Accessibility -> accessibilitySettings(settings, preferences, systemAccessibility, request)
                }
                val restoreArea = when (page) {
                    SettingsPane.Overview -> null
                    SettingsPane.Appearance -> SettingsArea.Appearance
                    SettingsPane.Playback -> SettingsArea.Playback
                    SettingsPane.Queue -> SettingsArea.Queue
                    SettingsPane.Lyrics -> if (supportsAmllLyrics) SettingsArea.Lyrics else null
                    SettingsPane.Accessibility -> SettingsArea.Accessibility
                }
                if (restoreArea != null) item(key = "restore-defaults") { RestoreDefaultsButton(restoreArea, onRestoreDefaults) }
                item(key = "end") { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

private fun LazyListScope.overviewSettings(
    preferences: AppPreferences,
    supportsAmll: Boolean,
    open: (SettingsPane) -> Unit,
    onLibrary: () -> Unit,
    onHome: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
) {
    item { SettingsSection(stringResource(Res.string.settings_personalization)) }
    item {
        SettingsDestinations(Modifier.padding(horizontal = pageInset)) {
            SettingsLink(stringResource(Res.string.settings_appearance),
                "${styleName(preferences.interfaceStyle)} · ${appearanceName(preferences.appearance)}", AppIcons.Settings, summaryIsValue = true) {
                open(SettingsPane.Appearance)
            }
            SettingsLink(stringResource(Res.string.settings_home_customization),
                stringResource(Res.string.settings_home_summary), AppIcons.Home, onClick = onHome)
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_music_section)) }
    item {
        SettingsDestinations(Modifier.padding(horizontal = pageInset)) {
            SettingsLink(stringResource(Res.string.settings_playback),
                stringResource(Res.string.settings_playback_summary), AppIcons.Equalizer) { open(SettingsPane.Playback) }
            SettingsLink(stringResource(Res.string.settings_queue),
                stringResource(Res.string.settings_queue_summary), AppIcons.Queue) { open(SettingsPane.Queue) }
            SettingsLink(stringResource(Res.string.library_settings),
                stringResource(Res.string.settings_library_summary), AppIcons.Library, onClick = onLibrary)
            if (supportsAmll) SettingsLink(stringResource(Res.string.settings_lyrics),
                stringResource(Res.string.settings_lyrics_summary), AppIcons.Lyrics) { open(SettingsPane.Lyrics) }
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_app_section)) }
    item {
        SettingsDestinations(Modifier.padding(horizontal = pageInset)) {
            SettingsLink(stringResource(Res.string.settings_accessibility),
                stringResource(Res.string.settings_accessibility_summary), AppIcons.Person) { open(SettingsPane.Accessibility) }
            SettingsLink(stringResource(Res.string.settings_backup),
                stringResource(Res.string.settings_backup_summary), AppIcons.ImportFile, onClick = onBackup)
            SettingsLink(stringResource(Res.string.about_title),
                stringResource(Res.string.settings_about_summary), AppIcons.InfoCircle, onClick = onAbout)
        }
    }
}

@Composable
private fun SettingsLink(title: String, summary: String, icon: ImageVector, summaryIsValue: Boolean = false, onClick: () -> Unit) =
    LocalGlassLook.current.SettingsDestination(title, summary, icon, summaryIsValue, onClick)

@Composable
private fun SettingsFormGroup(modifier: Modifier, content: @Composable () -> Unit) =
    LocalGlassLook.current.SettingsPageGroup(modifier, content)

@Composable
private fun SettingsDestinations(modifier: Modifier, content: @Composable () -> Unit) =
    LocalGlassLook.current.SettingsDestinationGroup(modifier, content)

@Composable
private fun SettingsSection(title: String) = LocalGlassLook.current.SettingsSectionTitle(title)

private fun LazyListScope.appearanceSettings(settings: AppSettings, preferences: AppPreferences, request: SettingsFocusRequest?, supportsStyleIcons: Boolean) {
    item { SettingsSection(stringResource(Res.string.settings_ui_style)) }
    item {
        SettingsSearchAnchor(SettingsTarget.InterfaceStyle, request) {
            PreviewChoices(InterfaceStyle.entries.size) { index, modifier ->
                val style = InterfaceStyle.entries[index]
                LocalGlassLook.current.SettingsPreviewChoice(styleName(style), preferences.interfaceStyle == style,
                    { settings.setInterfaceStyle(style) }, modifier) { StylePreview(style) }
            }
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_appearance)) }
    item {
        SettingsSearchAnchor(SettingsTarget.Appearance, request) {
            SettingsOptions(AppearanceMode.entries.map { appearanceName(it) }, preferences.appearance.ordinal) {
                settings.setAppearance(AppearanceMode.entries[it])
            }
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_artwork)) }
    item {
        SettingsSearchAnchor(SettingsTarget.ArtworkStyle, request) {
            PreviewChoices(ArtworkStyle.entries.size) { index, modifier ->
                val style = ArtworkStyle.entries[index]
                val title = stringResource(if (style == ArtworkStyle.FullBleed) Res.string.settings_artwork_full else Res.string.settings_artwork_square)
                LocalGlassLook.current.SettingsPreviewChoice(title, preferences.artworkStyle == style,
                    { settings.setArtworkStyle(style) }, modifier) { ArtworkPreview(style) }
            }
        }
    }
    item { SettingsHint(stringResource(Res.string.settings_artwork_hint)) }
    if (supportsStyleIcons) item {
        SettingsSearchAnchor(SettingsTarget.AppIcon, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset, vertical = 12.dp)) {
                SettingsToggleRow(stringResource(Res.string.settings_icon_follows_style), preferences.iconFollowsStyle,
                    settings::setIconFollowsStyle, subtitle = stringResource(Res.string.settings_icon_follows_hint))
            }
        }
    }
}

/** Small related choices fit a native segmented control; large text gets full-width rows. */
@Composable
private fun SettingsOptions(labels: List<String>, selected: Int, stackedLabels: List<String> = labels, onSelect: (Int) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (largeText || maxWidth - pageInset * 2 < 320.dp) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset).selectableGroup()) {
                stackedLabels.forEachIndexed { index, label -> SettingsChoiceRow(label, index == selected, { onSelect(index) }) }
            }
        } else SegmentedControl(labels, selected, onSelect, Modifier.padding(horizontal = pageInset).heightIn(min = 48.dp))
    }
}

/** Three choices at normal text sizes; a vertical layout makes enlarged labels fully readable. */
@Composable
private fun PreviewChoices(count: Int, content: @Composable (Int, Modifier) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val insetForm = LocalGlassLook.current.usesInsetSettingsForm
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = pageInset)
        .then(if (insetForm) Modifier.clip(RoundedCornerShape(26.dp)).background(AppTheme.colors.secondaryBackground) else Modifier)
        .selectableGroup()) {
        if (largeText || maxWidth < 280.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(count) { content(it, Modifier.fillMaxWidth()) }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(count) { content(it, Modifier.weight(1f)) }
        }
    }
}

/** Decorative miniatures describe the layout; the card supplies the accessible radio label. */
@Composable
internal fun StylePreview(style: InterfaceStyle) {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxWidth().height(if (LocalGlassLook.current.usesInsetSettingsForm) 88.dp else 76.dp).clearAndSetSemantics { }
        .clip(RoundedCornerShape(12.dp)).background(colors.background).padding(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(26.dp).height(5.dp).background(colors.label.copy(alpha = .5f), RoundedCornerShape(3.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(24.dp).background(
                    Brush.linearGradient(listOf(colors.accent.copy(alpha = .25f), colors.fill)), RoundedCornerShape(6.dp))) }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(colors.fill).padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                repeat(if (style == InterfaceStyle.LiquidGlass) 2 else 3) { i ->
                    Box(Modifier.size(if (i == 0) 10.dp else 5.dp).background(
                        if (i == 0) colors.accent else colors.secondaryLabel.copy(alpha = .5f), RoundedCornerShape(if (style == InterfaceStyle.MaterialGlass) 3.dp else 5.dp)))
                }
            }
            if (style == InterfaceStyle.LiquidGlass) Box(Modifier.size(18.dp).background(colors.fill, RoundedCornerShape(9.dp)))
        }
    }
}

@Composable
private fun ArtworkPreview(style: ArtworkStyle) {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxWidth().height(76.dp).clearAndSetSemantics { }
        .clip(RoundedCornerShape(12.dp)).background(colors.background), contentAlignment = Alignment.Center) {
        val art = if (style == ArtworkStyle.Square) Modifier.padding(10.dp).size(56.dp) else Modifier.fillMaxSize()
        Box(art.clip(RoundedCornerShape(if (style == ArtworkStyle.Square) 8.dp else 0.dp)).background(
            Brush.linearGradient(listOf(colors.accent.copy(alpha = .7f), colors.fill))))
        if (style == ArtworkStyle.FullBleed) Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).width(34.dp).height(4.dp)
            .background(colors.label.copy(alpha = .65f), RoundedCornerShape(2.dp)))
    }
}

/** One exclusive choice per row, like the other settings lists. */
@Composable
private fun <T> SettingsChoices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) =
    SettingsFormGroup(Modifier.padding(horizontal = pageInset).selectableGroup()) {
        options.forEach { option -> SettingsChoiceRow(label(option), option == selected, { onSelect(option) }) }
    }

@Composable
internal fun clickActionLabel(action: TrackClickAction) = stringResource(when (action) {
    TrackClickAction.PlayList -> Res.string.queue_click_play
    TrackClickAction.PlayListAndOpenPlayer -> Res.string.queue_click_play_open
    TrackClickAction.AddToQueue -> Res.string.queue_click_enqueue
})

@Composable
internal fun queueStartLabel(start: QueueStart) = stringResource(when (start) {
    QueueStart.AfterCurrent -> Res.string.queue_start_after_current
    QueueStart.AfterCollection -> Res.string.queue_start_after_collection
    QueueStart.Immediately -> Res.string.queue_start_immediately
})

@Composable
internal fun queueOrderLabel(order: QueueAddOrder) = stringResource(
    if (order == QueueAddOrder.End) Res.string.queue_order_end else Res.string.queue_order_shuffled)

@Composable
internal fun queueEndLabel(end: QueueEnd) = stringResource(
    if (end == QueueEnd.ReturnToCollection) Res.string.queue_end_return else Res.string.queue_end_stop)

@Composable
internal fun shuffleBiasLabel(bias: ShuffleBias) = stringResource(
    if (bias == ShuffleBias.Random) Res.string.shuffle_bias_random else Res.string.shuffle_bias_least_played)

private fun LazyListScope.queueSettings(settings: AppSettings, preferences: AppPreferences, request: SettingsFocusRequest?) {
    item { SettingsSection(stringResource(Res.string.queue_click_section)) }
    item {
        SettingsSearchAnchor(SettingsTarget.TrackClickAction, request) {
            SettingsChoices(TrackClickAction.entries, preferences.trackClickAction, { clickActionLabel(it) }, settings::setTrackClickAction)
        }
    }
    item { SettingsSection(stringResource(Res.string.queue_start_section)) }
    item {
        SettingsSearchAnchor(SettingsTarget.QueueStart, request) {
            SettingsChoices(QueueStart.entries, preferences.queueStart, { queueStartLabel(it) }, settings::setQueueStart)
        }
    }
    item { SettingsSection(stringResource(Res.string.queue_order_section)) }
    item {
        SettingsSearchAnchor(SettingsTarget.QueueAddOrder, request) {
            SettingsChoices(QueueAddOrder.entries, preferences.queueAddOrder, { queueOrderLabel(it) }, settings::setQueueAddOrder)
        }
    }
    item {
        SettingsSearchAnchor(SettingsTarget.QueueReplaces, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset, vertical = 12.dp)) {
                SettingsToggleRow(stringResource(Res.string.queue_replaces), preferences.queueReplaces, settings::setQueueReplaces)
            }
        }
    }
    item { SettingsSection(stringResource(Res.string.queue_end_section)) }
    item {
        SettingsSearchAnchor(SettingsTarget.QueueEnd, request) {
            SettingsChoices(QueueEnd.entries, preferences.queueEnd, { queueEndLabel(it) }, settings::setQueueEnd)
        }
    }
    item { SettingsSection(stringResource(Res.string.shuffle_section)) }
    item {
        SettingsSearchAnchor(SettingsTarget.ShuffleBias, request) {
            SettingsChoices(ShuffleBias.entries, preferences.shuffleBias, { shuffleBiasLabel(it) }, settings::setShuffleBias)
        }
    }
    item {
        SettingsSearchAnchor(SettingsTarget.KeepShuffle, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset, vertical = 12.dp)) {
                SettingsToggleRow(stringResource(Res.string.shuffle_keep), preferences.keepShuffleOnSelection, settings::setKeepShuffleOnSelection)
            }
        }
    }
}

private val crossfadePresets = listOf(0L, 2_000L, 4_000L, 6_000L, 8_000L, 12_000L)
private val manualCrossfadePresets = listOf(0L, 400L, 1_000L, 2_000L)

private fun LazyListScope.playbackSettings(
    settings: AppSettings,
    preferences: AppPreferences,
    equalizer: Equalizer,
    sleepTimer: SleepTimerState,
    openSleepTimer: () -> Unit,
    openEq: () -> Unit,
    request: SettingsFocusRequest?,
    supportsCrossfade: Boolean,
    draft: SettingsDurationDraft?,
    openDuration: (CrossfadeKind) -> Unit,
    closeDuration: () -> Unit,
) {
    item { SettingsSection(stringResource(Res.string.settings_sound)) }
    item {
        val eq by equalizer.state.collectAsState()
        SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
            SettingsLink(stringResource(Res.string.eq_title),
                if (!eq.enabled) stringResource(Res.string.eq_off) else listOfNotNull(stringResource(Res.string.eq_on), eq.profile?.name).joinToString(" · "),
                AppIcons.Equalizer, summaryIsValue = true, onClick = openEq)
            SettingsSearchAnchor(SettingsTarget.SleepTimer, request) {
                val minutesLeft = sleepMinutesLeft(sleepTimer)
                SettingsLink(stringResource(Res.string.sleep_title), when {
                    sleepTimer is SleepTimerState.EndOfTrack -> stringResource(Res.string.player_timer_track_end)
                    minutesLeft != null -> stringResource(Res.string.player_timer_minutes, minutesLeft)
                    else -> stringResource(Res.string.eq_off)
                }, AppIcons.Timer, summaryIsValue = true, onClick = openSleepTimer)
            }
            SettingsSearchAnchor(SettingsTarget.ResumeOnReconnect, request) {
                SettingsToggleRow(stringResource(Res.string.settings_resume_reconnect), preferences.resumeOnReconnect,
                    settings::setResumeOnReconnect, subtitle = stringResource(Res.string.settings_resume_reconnect_hint))
            }
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_replay_gain)) }
    item {
        val labels = ReplayGainMode.entries.map { mode ->
            stringResource(when (mode) {
                ReplayGainMode.Off -> Res.string.settings_replay_gain_off
                ReplayGainMode.Track -> Res.string.settings_replay_gain_track
                ReplayGainMode.Album -> Res.string.settings_replay_gain_album
            })
        }
        val shortLabels = listOf(stringResource(Res.string.settings_replay_gain_off),
            stringResource(Res.string.settings_replay_gain_track_short), stringResource(Res.string.settings_replay_gain_album_short))
        SettingsSearchAnchor(SettingsTarget.ReplayGain, request) {
            SettingsOptions(shortLabels, preferences.replayGain.ordinal, labels) { settings.setReplayGain(ReplayGainMode.entries[it]) }
        }
    }
    item { SettingsHint(stringResource(Res.string.settings_replay_gain_hint)) }
    item(key = "long-tracks-title") { SettingsSection(stringResource(Res.string.settings_long_section)) }
    item(key = "per-track-resume") {
        SettingsSearchAnchor(SettingsTarget.ResumePerTrack, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
                SettingsToggleRow(stringResource(Res.string.settings_long_resume), preferences.resumePerTrack,
                    settings::setResumePerTrack, subtitle = stringResource(Res.string.settings_long_resume_hint))
            }
        }
    }
    item(key = "long-duration-threshold") {
        SettingsSearchAnchor(SettingsTarget.LongTrackThreshold, request) {
            // A gap, not a seam: two rounded cards touching leave dark wedges at their corners.
            SettingsFormGroup(Modifier.padding(horizontal = pageInset).padding(top = 12.dp)) {
                AppText(stringResource(Res.string.settings_long_threshold), AppTheme.typography.subheadline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                AppSettings.LONG_TRACK_CHOICES_MS.forEach { duration ->
                    SettingsChoiceRow(stringResource(Res.string.settings_long_minutes, (duration / 60_000L).toInt()),
                        preferences.longTrackThresholdMs == duration, { settings.setLongTrackThreshold(duration) })
                }
            }
        }
    }
    item(key = "long-tracks-hint") { SettingsHint(stringResource(Res.string.settings_long_hint)) }
    item(key = "crossfade-title") { SettingsSection(stringResource(Res.string.settings_crossfade)) }
    item(key = "automatic-crossfade") {
        SettingsSearchAnchor(SettingsTarget.Crossfade, request) {
            SettingsSearchAnchor(SettingsTarget.CrossfadeDuration, request) {
                CrossfadeDurationControl(CrossfadeKind.Automatic, preferences.crossfadeDurationMs,
                    supportsCrossfade, draft?.takeIf { it.kind == CrossfadeKind.Automatic },
                    { openDuration(CrossfadeKind.Automatic) }, closeDuration) { duration ->
                    settings.setCrossfadeDuration(duration)
                    closeDuration()
                }
            }
        }
    }
    item(key = "automatic-crossfade-hint") { SettingsHint(stringResource(Res.string.settings_crossfade_automatic_hint)) }
    item(key = "manual-crossfade") {
        SettingsSearchAnchor(SettingsTarget.ManualCrossfadeDuration, request) {
            CrossfadeDurationControl(CrossfadeKind.Manual, preferences.manualCrossfadeDurationMs,
                supportsCrossfade, draft?.takeIf { it.kind == CrossfadeKind.Manual },
                { openDuration(CrossfadeKind.Manual) }, closeDuration) { duration ->
                settings.setManualCrossfadeDuration(duration)
                closeDuration()
            }
        }
    }
    item(key = "manual-crossfade-hint") { SettingsHint(stringResource(Res.string.settings_crossfade_manual_hint)) }
    if (!supportsCrossfade) item(key = "crossfade-unsupported") { SettingsHint(stringResource(Res.string.settings_crossfade_unsupported)) }
}

@Composable
private fun crossfadeDurationValue(durationMs: Long): String =
    stringResource(if (durationMs == 0L) Res.string.settings_crossfade_off_ms else Res.string.settings_search_duration_value, durationMs)

@Composable
private fun CrossfadeDurationControl(
    kind: CrossfadeKind,
    currentMs: Long,
    enabled: Boolean,
    draft: SettingsDurationDraft?,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSave: (Long) -> Unit,
) {
    val title = stringResource(if (kind == CrossfadeKind.Automatic) Res.string.settings_crossfade_automatic else Res.string.settings_crossfade_manual)
    val value = crossfadeDurationValue(currentMs)
    SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
        if (enabled) {
            SettingsLink(title, value, if (kind == CrossfadeKind.Automatic) AppIcons.Queue else AppIcons.Next,
                summaryIsValue = true, onClick = { if (draft == null) onEdit() else onCancel() })
        } else {
            ListRow(title, subtitle = value)
        }
        if (enabled && draft != null) CrossfadeDurationEditor(draft, onSave, onCancel)
    }
}

/** Drafts do not change audio or persistence until explicitly saved. */
@Composable
private fun CrossfadeDurationEditor(draft: SettingsDurationDraft, onSave: (Long) -> Unit, onCancel: () -> Unit) {
    val validation = stringResource(Res.string.settings_crossfade_ms_invalid, Crossfade.MIN_EFFECTIVE_MS, Crossfade.MAX_DURATION_MS)
    fun save() {
        val value = parseCrossfadeDurationMs(draft.text)
        if (value != null) onSave(value) else draft.invalid = true
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LocalGlassLook.current.SettingsDurationField(draft.text, { draft.text = it; draft.invalid = false },
            stringResource(Res.string.settings_crossfade_ms_label), ::save,
            Modifier.fillMaxWidth().semantics { if (draft.invalid) error(validation) })
        AppText(stringResource(Res.string.settings_crossfade_ms_hint, Crossfade.MIN_EFFECTIVE_MS, Crossfade.MAX_DURATION_MS),
            AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
        if (draft.invalid) AppText(validation, AppTheme.typography.footnote, color = AppTheme.colors.label,
            modifier = Modifier.semantics {
                error(validation); liveRegion = LiveRegionMode.Polite
            })
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = if (draft.kind == CrossfadeKind.Automatic) crossfadePresets else manualCrossfadePresets
            presets.forEach { duration ->
                ActionButton(crossfadeDurationValue(duration), { draft.text = duration.toString(); draft.invalid = false })
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (LocalDensity.current.fontScale > 1.3f || maxWidth < 260.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(stringResource(Res.string.playlist_save), ::save, Modifier.fillMaxWidth(), AppIcons.Checkmark)
                    ActionButton(stringResource(Res.string.action_cancel), onCancel, Modifier.fillMaxWidth())
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(Res.string.action_cancel), onCancel, Modifier.weight(1f))
                ActionButton(stringResource(Res.string.playlist_save), ::save, Modifier.weight(1f), AppIcons.Checkmark)
            }
        }
    }
}

private fun LazyListScope.accessibilitySettings(settings: AppSettings, preferences: AppPreferences, system: UiAccessibility, request: SettingsFocusRequest?) {
    item { SettingsSection(stringResource(Res.string.settings_accessibility)) }
    item {
        SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
            SettingsSearchAnchor(SettingsTarget.ReduceMotion, request) {
                SettingsToggleRow(stringResource(Res.string.settings_reduce_motion), preferences.reduceMotion || system.reduceMotion,
                    settings::setReduceMotion, enabled = !system.reduceMotion,
                    subtitle = stringResource(if (system.reduceMotion) Res.string.settings_device_enabled else Res.string.settings_reduce_motion_hint))
            }
            SettingsSearchAnchor(SettingsTarget.ReduceTransparency, request) {
                SettingsToggleRow(stringResource(Res.string.settings_reduce_transparency), preferences.reduceTransparency || system.reduceTransparency,
                    settings::setReduceTransparency, enabled = !system.reduceTransparency,
                    subtitle = stringResource(if (system.reduceTransparency) Res.string.settings_device_enabled else Res.string.settings_reduce_transparency_hint))
            }
        }
    }
}

@Composable
private fun SettingsHint(text: String) = AppText(text, AppTheme.typography.footnote,
    color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(horizontal = pageInset + 4.dp, vertical = 8.dp))

@Composable
internal fun styleName(style: InterfaceStyle): String = stringResource(when (style) {
    InterfaceStyle.LiquidGlass -> Res.string.settings_liquid_glass
    InterfaceStyle.MaterialGlass -> Res.string.settings_material_glass
    InterfaceStyle.Miuix -> Res.string.settings_miuix
})

@Composable
private fun appearanceName(mode: AppearanceMode): String = stringResource(when (mode) {
    AppearanceMode.System -> Res.string.settings_appearance_system
    AppearanceMode.Light -> Res.string.settings_appearance_light
    AppearanceMode.Dark -> Res.string.settings_appearance_dark
})
