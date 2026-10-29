package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.playback.HeadphoneSettings

import dev.cmdrchara.songlass.core.player.FocusDucking

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
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
import androidx.compose.ui.semantics.stateDescription
import dev.cmdrchara.songlass.core.player.Crossfade
import dev.cmdrchara.songlass.core.player.CrossfadeKind
import dev.cmdrchara.songlass.core.player.TransportFadeSettings
import dev.cmdrchara.songlass.core.player.ReplayGainSettings
import dev.cmdrchara.songlass.core.player.AudioEffect
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** UI navigation only: preference values and persistence remain in core. */
enum class SettingsPane { Overview, Appearance, Sound, Player, Queue }

@Composable
fun SettingsScreen(
    settings: AppSettings,
    preferences: AppPreferences,
    supportsAmllLyrics: Boolean,
    supportsOnlineLyrics: Boolean,
    supportsCrossfade: Boolean,
    supportsTransportFades: Boolean,
    supportsReplayGainProcessing: Boolean,
    supportsAudioEffects: Boolean,
    supportsAudioFocusSettings: Boolean,
    supportsHeadphoneSettings: Boolean,
    supportsAppIconChoice: Boolean,
    supportsOnlineArtwork: Boolean,
    supportsScrobbling: Boolean,
    lastFmSummary: String,
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
    onOpenLastFm: () -> Unit,
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
    fun openDurationEditor(target: SettingsTarget) {
        val transportFade = target in transportFadeTargets
        if (if (transportFade) !supportsTransportFades else !supportsCrossfade) return
        focus.clearFocus(); keyboard?.hide()
        val value = when (target) {
            SettingsTarget.CrossfadeDuration -> preferences.crossfadeDurationMs
            SettingsTarget.ManualCrossfadeDuration -> preferences.manualCrossfadeDurationMs
            SettingsTarget.PauseFadeDuration -> preferences.transportFades.pauseMs
            SettingsTarget.StopFadeDuration -> preferences.transportFades.stopMs
            SettingsTarget.SeekFadeDuration -> preferences.transportFades.seekMs
            else -> return
        }
        search.durationDraft = SettingsDurationDraft(target, value)
    }
    val durationRequest = search.focus?.takeIf { isActive && pane == SettingsPane.Sound }
    LaunchedEffect(durationRequest?.id, supportsCrossfade, supportsTransportFades) {
        val request = durationRequest ?: return@LaunchedEffect
        if (search.openedDurationRequestId == request.id) return@LaunchedEffect
        if (request.target !in transportFadeTargets && request.target != SettingsTarget.CrossfadeDuration &&
            request.target != SettingsTarget.ManualCrossfadeDuration) return@LaunchedEffect
        // Consume each request once: Save, Cancel and Back must not immediately reopen the draft.
        search.openedDurationRequestId = request.id
        openDurationEditor(request.target)
    }
    fun backFrom(page: SettingsPane) {
        focus.clearFocus(); keyboard?.hide()
        when {
            page == SettingsPane.Sound && search.durationDraft != null -> closeDurationEditor()
            returnToPlayer -> onBack()
            page == SettingsPane.Overview -> { search.updateQuery(""); search.returnToResults() }
            search.resultPane == page -> { search.returnToResults(); onPaneChanged(SettingsPane.Overview) }
            else -> onPaneChanged(SettingsPane.Overview)
        }
    }
    PlatformBackHandler(isActive && (pane != SettingsPane.Overview || search.query.isNotEmpty())) { backFrom(pane) }
    val documents = settingsSearchDocuments()
    val index = remember(documents) { SettingsSearchIndex(documents) }
    val results = remember(index, search.query, supportsAmllLyrics, supportsCrossfade, supportsOnlineLyrics, supportsTransportFades, supportsReplayGainProcessing, supportsAudioEffects, supportsAudioFocusSettings, supportsHeadphoneSettings, supportsOnlineArtwork, supportsScrobbling) {
        index.search(search.query, supportsAmllLyrics, supportsCrossfade, supportsOnlineLyrics, supportsTransportFades, supportsReplayGainProcessing, supportsAudioEffects, supportsAudioFocusSettings, supportsHeadphoneSettings, supportsOnlineArtwork, supportsScrobbling)
    }
    val eqState by equalizer.state.collectAsState()
    LocalGlassLook.current.SettingsPage {
        ScreenTransition(pane, direction = { _, to ->
            if (to == SettingsPane.Overview) NavigationDirection.Backward else NavigationDirection.Forward
        }) { page ->
            val searching = page == SettingsPane.Overview && search.query.isNotBlank()
            val scroll = if (searching) search.resultsScroll else search.paneScroll.getValue(page)
            val request = search.focus?.takeIf { isActive && page == pane && it.target.settingsPane == page }
            val positions = remember(page) { SettingsItemPositions() }
            val reveal = SettingsSearchScroll(request, scroll, positions)
            val look = LocalGlassLook.current
            val isRoot = page == SettingsPane.Overview && !searching
            look.SettingsRootChrome(isRoot, stringResource(Res.string.settings_title), scroll) {
                LazyColumn(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding().then(reveal),
                    state = scroll,
                    contentPadding = contentPadding,
                ) {
                    val items = SettingsItems(this, positions)
                    items.item(key = "title") {
                        val title = when (page) {
                            SettingsPane.Overview -> Res.string.settings_title
                            SettingsPane.Appearance -> Res.string.settings_appearance
                            SettingsPane.Sound -> Res.string.settings_sound
                            SettingsPane.Player -> Res.string.settings_playback
                            SettingsPane.Queue -> Res.string.settings_queue
                        }
                        val titleBackLabel = when (page) {
                                SettingsPane.Overview -> if (search.query.isNotEmpty()) stringResource(Res.string.settings_title) else backLabel
                                SettingsPane.Sound -> stringResource(when {
                                    search.durationDraft != null -> Res.string.action_cancel
                                    returnToPlayer -> Res.string.player_return
                                    search.resultPane == page -> Res.string.settings_search_results
                                    else -> Res.string.settings_title
                                })
                                else -> if (search.resultPane == page) stringResource(Res.string.settings_search_results) else stringResource(Res.string.settings_title)
                            }
                        val titleBack: () -> Unit = if (isRoot) onBack else ({ backFrom(page) })
                        if (isRoot) look.SettingsRootTitle(stringResource(title), titleBackLabel, titleBack, Modifier)
                        else look.SettingsPageTitle(stringResource(title), titleBackLabel, titleBack, Modifier)
                    }
                    when (page) {
                        SettingsPane.Overview -> {
                            items.item(key = "settings-search") {
                                SearchField(search.query, search::updateQuery, stringResource(Res.string.settings_search_placeholder),
                                    stringResource(Res.string.search_clear), Modifier.padding(horizontal = look.settingsRootHorizontalInset, vertical = 8.dp))
                            }
                            if (searching) settingsSearchResults(results, documents, preferences, eqState, systemAccessibility) {
                                focus.clearFocus(); keyboard?.hide(); onSearchResult(it)
                            } else {
                                items.overviewSettings(preferences, eqState, onPaneChanged, onOpenEqualizer,
                                    onOpenLibrary, onOpenHomeCustomization, onOpenBackup, onOpenAbout,
                                    supportsScrobbling, lastFmSummary, onOpenLastFm)
                            }
                        }
                        SettingsPane.Appearance -> {
                            items.appearanceSettings(settings, preferences, request, supportsAppIconChoice, systemAccessibility)
                            if (supportsOnlineArtwork) items.onlineArtworkSettings(settings, preferences, request)
                        }
                        SettingsPane.Sound -> items.soundSettings(settings, preferences, equalizer, sleepTimer, onOpenSleepTimer,
                            { closeDurationEditor(); onOpenEqualizer() }, request, supportsCrossfade, supportsTransportFades, supportsReplayGainProcessing, supportsAudioEffects, supportsAudioFocusSettings,
                            search.durationDraft, ::openDurationEditor, ::closeDurationEditor)
                        SettingsPane.Player -> items.playerSettings(settings, preferences, request, supportsAmllLyrics, supportsOnlineLyrics, supportsHeadphoneSettings)
                        SettingsPane.Queue -> items.queueSettings(settings, preferences, request)
                    }
                    val restoreArea = when (page) {
                        SettingsPane.Overview -> null
                        SettingsPane.Appearance -> SettingsArea.Appearance
                        SettingsPane.Sound -> SettingsArea.Sound
                        SettingsPane.Player -> SettingsArea.Player
                        SettingsPane.Queue -> SettingsArea.Queue
                    }
                    if (restoreArea != null) items.item(key = "restore-defaults") { RestoreDefaultsButton(restoreArea, onRestoreDefaults) }
                    items.item(key = "end") { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

private fun SettingsItems.overviewSettings(
    preferences: AppPreferences,
    eq: dev.cmdrchara.songlass.core.equalizer.EqualizerState,
    open: (SettingsPane) -> Unit,
    onEqualizer: () -> Unit,
    onLibrary: () -> Unit,
    onHome: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    supportsScrobbling: Boolean,
    lastFmSummary: String,
    onLastFm: () -> Unit,
) {
    item { SettingsSection(stringResource(Res.string.settings_personalization)) }
    item {
        SettingsDestinations(Modifier.padding(horizontal = LocalGlassLook.current.settingsRootHorizontalInset)) {
            SettingsLink(stringResource(Res.string.settings_appearance),
                "${styleName(preferences.interfaceStyle)} · ${appearanceName(preferences.appearance)}", AppIcons.Settings, summaryIsValue = true) {
                open(SettingsPane.Appearance)
            }
            SettingsLink(stringResource(Res.string.settings_home_customization),
                stringResource(Res.string.settings_home_summary), AppIcons.Home, onClick = onHome)
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_audio_section)) }
    item {
        SettingsDestinations(Modifier.padding(horizontal = LocalGlassLook.current.settingsRootHorizontalInset)) {
            SettingsLink(stringResource(Res.string.settings_sound),
                stringResource(Res.string.settings_sound_summary), AppIcons.SpeakerLow) { open(SettingsPane.Sound) }
            SettingsLink(stringResource(Res.string.eq_title),
                if (!eq.enabled) stringResource(Res.string.eq_off) else listOfNotNull(stringResource(Res.string.eq_on), eq.profile?.name).joinToString(" · "),
                AppIcons.Equalizer, summaryIsValue = true, onClick = onEqualizer)
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_music_section)) }
    item {
        SettingsDestinations(Modifier.padding(horizontal = LocalGlassLook.current.settingsRootHorizontalInset)) {
            SettingsLink(stringResource(Res.string.settings_playback),
                stringResource(Res.string.settings_player_summary), AppIcons.AudioOutput) { open(SettingsPane.Player) }
            SettingsLink(stringResource(Res.string.settings_queue),
                stringResource(Res.string.settings_queue_summary), AppIcons.Queue) { open(SettingsPane.Queue) }
            SettingsLink(stringResource(Res.string.library_settings),
                stringResource(Res.string.settings_library_summary), AppIcons.Library, onClick = onLibrary)
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_app_section)) }
    item {
        SettingsDestinations(Modifier.padding(horizontal = LocalGlassLook.current.settingsRootHorizontalInset)) {
            if (supportsScrobbling) SettingsLink(stringResource(Res.string.lastfm_title), lastFmSummary,
                AppIcons.MusicNote, summaryIsValue = true, onClick = onLastFm)
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

/** Independent preference cards have an 8 dp gutter; connected rows use SettingsGroupFragment. */
@Composable
private fun SettingsFormGroup(modifier: Modifier, content: @Composable () -> Unit) =
    LocalGlassLook.current.SettingsPageGroup(modifier.padding(vertical = 4.dp), content)

@Composable
private fun SettingsDestinations(modifier: Modifier, content: @Composable () -> Unit) =
    LocalGlassLook.current.SettingsDestinationGroup(modifier, content)

@Composable
private fun SettingsSection(title: String) = LocalGlassLook.current.SettingsSectionTitle(title)

private fun SettingsItems.appearanceSettings(settings: AppSettings, preferences: AppPreferences, request: SettingsFocusRequest?, supportsAppIconChoice: Boolean, system: UiAccessibility) {
    item { LocalGlassLook.current.SettingsAppearanceSectionTitle(stringResource(Res.string.settings_ui_style)) }
    mark(SettingsTarget.InterfaceStyle)
    item {
        SettingsSearchAnchor(SettingsTarget.InterfaceStyle, request) {
            val choices = InterfaceStyle.entries.map { style ->
                SettingsVisualChoice(styleName(style), preferences.interfaceStyle == style,
                    { settings.setInterfaceStyle(style) }) { StylePreview(style) }
            }
            LocalGlassLook.current.SettingsVisualChoices(SettingsVisualChoiceKind.InterfaceStyle,
                stringResource(Res.string.settings_ui_style), choices, stringResource(Res.string.action_cancel), null,
                lastInGroup = false, modifier = Modifier.padding(horizontal = pageInset))
        }
    }
    item { LocalGlassLook.current.SettingsAppearanceSectionTitle(stringResource(Res.string.settings_appearance)) }
    mark(SettingsTarget.Appearance)
    item {
        SettingsSearchAnchor(SettingsTarget.Appearance, request) {
            val labels = AppearanceMode.entries.map { appearanceName(it) }
            val onSelect: (Int) -> Unit = { index -> AppearanceMode.entries.getOrNull(index)?.let(settings::setAppearance) }
            LocalGlassLook.current.SettingsAppearanceModeOptions(stringResource(Res.string.settings_theme),
                labels, preferences.appearance.ordinal, onSelect, stringResource(Res.string.action_cancel),
                Modifier.padding(horizontal = pageInset)) {
                SettingsOptions(labels, preferences.appearance.ordinal, onSelect = onSelect)
            }
        }
    }
    item { LocalGlassLook.current.SettingsAppearanceSectionTitle(stringResource(Res.string.settings_artwork)) }
    mark(SettingsTarget.ArtworkStyle)
    item {
        SettingsSearchAnchor(SettingsTarget.ArtworkStyle, request) {
            val choices = ArtworkStyle.entries.map { style ->
                val title = stringResource(if (style == ArtworkStyle.FullBleed) Res.string.settings_artwork_full else Res.string.settings_artwork_square)
                SettingsVisualChoice(title, preferences.artworkStyle == style,
                    { settings.setArtworkStyle(style) }) { ArtworkPreview(style) }
            }
            LocalGlassLook.current.SettingsVisualChoices(SettingsVisualChoiceKind.Artwork,
                stringResource(Res.string.settings_artwork), choices, stringResource(Res.string.action_cancel),
                stringResource(Res.string.settings_artwork_hint), lastInGroup = !supportsAppIconChoice,
                modifier = Modifier.padding(horizontal = pageInset))
        }
    }
    item {
        val hint = stringResource(Res.string.settings_artwork_hint)
        LocalGlassLook.current.SettingsAppearanceHint(hint) { SettingsHint(hint) }
    }
    if (supportsAppIconChoice) {
        item { LocalGlassLook.current.SettingsAppearanceSectionTitle(stringResource(Res.string.settings_app_icon)) }
        mark(SettingsTarget.AppIcon)
        item {
            SettingsSearchAnchor(SettingsTarget.AppIcon, request) {
                val dark = AppTheme.colors.isDark
                val choices = listOf(AppIconChoice.Automatic, AppIconChoice.LiquidGlass, AppIconChoice.MaterialGlass,
                    AppIconChoice.Miuix, AppIconChoice.Violet, AppIconChoice.Night).map { icon ->
                    SettingsVisualChoice(appIconName(icon), preferences.appIcon == icon,
                        { settings.setAppIcon(icon) }) { AppIconPreview(icon.resolve(preferences.interfaceStyle, dark)) }
                }
                LocalGlassLook.current.SettingsVisualChoices(SettingsVisualChoiceKind.AppIcon,
                    stringResource(Res.string.settings_app_icon), choices, stringResource(Res.string.action_cancel),
                    launcherIconHint(), lastInGroup = true,
                    modifier = Modifier.padding(horizontal = pageInset))
            }
        }
        item {
            val hint = launcherIconHint()
            LocalGlassLook.current.SettingsAppearanceHint(hint) { SettingsHint(hint) }
        }
    }
    item { SettingsSection(stringResource(Res.string.settings_text_and_background)) }
    mark(SettingsTarget.TextScale, SettingsTarget.BlurIntensity)
    item {
        SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
            SettingsSearchAnchor(SettingsTarget.TextScale, request) {
                val choices = AppSettings.TEXT_SCALE_CHOICES
                LocalGlassLook.current.SettingsValueChoice(stringResource(Res.string.settings_text_scale),
                    choices.map { textScaleLabel(it) }, choices.indexOf(preferences.textScale),
                    { index -> choices.getOrNull(index)?.let(settings::setTextScale) },
                    stringResource(Res.string.action_cancel), Modifier)
            }
            SettingsSearchAnchor(SettingsTarget.BlurIntensity, request) {
                val choices = BlurIntensity.entries
                LocalGlassLook.current.SettingsValueChoice(stringResource(Res.string.settings_blur_intensity),
                    choices.map { blurIntensityLabel(it) }, choices.indexOf(preferences.blurIntensity),
                    { index -> choices.getOrNull(index)?.let(settings::setBlurIntensity) },
                    stringResource(Res.string.action_cancel), Modifier)
            }
        }
    }
    item { SettingsHint(stringResource(Res.string.settings_text_scale_hint)) }
    item { SettingsHint(stringResource(Res.string.settings_blur_hint)) }
    item { SettingsSection(stringResource(Res.string.settings_accessibility)) }
    mark(SettingsTarget.ReduceMotion, SettingsTarget.ReduceTransparency)
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
internal fun appIconName(icon: AppIconChoice): String = when (icon) {
    AppIconChoice.Violet -> stringResource(Res.string.settings_app_icon_violet)
    AppIconChoice.Night -> stringResource(Res.string.settings_app_icon_night)
    AppIconChoice.Automatic -> stringResource(Res.string.settings_app_icon_automatic)
    AppIconChoice.LiquidGlass -> stringResource(Res.string.settings_liquid_glass)
    AppIconChoice.MaterialGlass -> stringResource(Res.string.settings_material_glass)
    AppIconChoice.Miuix -> stringResource(Res.string.settings_miuix)
}

@Composable
private fun AppIconPreview(icon: LauncherIconVariant) {
    val asset = when (icon) {
        LauncherIconVariant.Violet -> Res.drawable.songlass_brand_light
        LauncherIconVariant.Night -> Res.drawable.songlass_icon_night
        LauncherIconVariant.LiquidLight -> Res.drawable.songlass_icon_liquid_light
        LauncherIconVariant.LiquidDark -> Res.drawable.songlass_icon_liquid_dark
        LauncherIconVariant.MaterialLight -> Res.drawable.songlass_icon_material_light
        LauncherIconVariant.MaterialDark -> Res.drawable.songlass_icon_material_dark
        LauncherIconVariant.MiuixLight -> Res.drawable.songlass_icon_miuix_light
        LauncherIconVariant.MiuixDark -> Res.drawable.songlass_icon_miuix_dark
    }
    Image(platformLauncherIconPainter(icon) ?: painterResource(asset), contentDescription = null,
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).clearAndSetSemantics { })
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
private fun <T> SettingsChoices(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val labels = options.map { label(it) }
    LocalGlassLook.current.SettingsChoiceOptions(title, labels, options.indexOf(selected),
        { index -> options.getOrNull(index)?.let(onSelect) }, stringResource(Res.string.action_cancel),
        Modifier.padding(horizontal = pageInset))
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

private fun SettingsItems.queueSettings(settings: AppSettings, preferences: AppPreferences, request: SettingsFocusRequest?) {
    item { SettingsSection(stringResource(Res.string.queue_click_section)) }
    mark(SettingsTarget.TrackClickAction)
    item {
        SettingsSearchAnchor(SettingsTarget.TrackClickAction, request) {
            SettingsChoices(stringResource(Res.string.queue_click_section), TrackClickAction.entries, preferences.trackClickAction, { clickActionLabel(it) }, settings::setTrackClickAction)
        }
    }
    item { SettingsSection(stringResource(Res.string.queue_start_section)) }
    mark(SettingsTarget.QueueStart)
    item {
        SettingsSearchAnchor(SettingsTarget.QueueStart, request) {
            SettingsChoices(stringResource(Res.string.queue_start_section), QueueStart.entries, preferences.queueStart, { queueStartLabel(it) }, settings::setQueueStart)
        }
    }
    item { SettingsSection(stringResource(Res.string.queue_order_section)) }
    mark(SettingsTarget.QueueAddOrder)
    item {
        SettingsSearchAnchor(SettingsTarget.QueueAddOrder, request) {
            SettingsChoices(stringResource(Res.string.queue_order_section), QueueAddOrder.entries, preferences.queueAddOrder, { queueOrderLabel(it) }, settings::setQueueAddOrder)
        }
    }
    mark(SettingsTarget.QueueReplaces)
    item {
        SettingsSearchAnchor(SettingsTarget.QueueReplaces, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset, vertical = 8.dp)) {
                SettingsToggleRow(stringResource(Res.string.queue_replaces), preferences.queueReplaces, settings::setQueueReplaces)
            }
        }
    }
    item { SettingsSection(stringResource(Res.string.queue_end_section)) }
    mark(SettingsTarget.QueueEnd)
    item {
        SettingsSearchAnchor(SettingsTarget.QueueEnd, request) {
            SettingsChoices(stringResource(Res.string.queue_end_section), QueueEnd.entries, preferences.queueEnd, { queueEndLabel(it) }, settings::setQueueEnd)
        }
    }
    item { SettingsSection(stringResource(Res.string.shuffle_section)) }
    mark(SettingsTarget.ShuffleBias)
    item {
        SettingsSearchAnchor(SettingsTarget.ShuffleBias, request) {
            SettingsChoices(stringResource(Res.string.shuffle_section), ShuffleBias.entries, preferences.shuffleBias, { shuffleBiasLabel(it) }, settings::setShuffleBias)
        }
    }
    mark(SettingsTarget.KeepShuffle)
    item {
        SettingsSearchAnchor(SettingsTarget.KeepShuffle, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset, vertical = 8.dp)) {
                SettingsToggleRow(stringResource(Res.string.shuffle_keep), preferences.keepShuffleOnSelection, settings::setKeepShuffleOnSelection)
            }
        }
    }
}

private val transportFadeTargets = listOf(SettingsTarget.PauseFadeDuration, SettingsTarget.StopFadeDuration, SettingsTarget.SeekFadeDuration)
private val crossfadePresets = listOf(0L, 2_000L, 4_000L, 6_000L, 8_000L, 12_000L)
private val manualCrossfadePresets = listOf(0L, 400L, 1_000L, 2_000L)

private fun SettingsItems.soundSettings(
    settings: AppSettings,
    preferences: AppPreferences,
    equalizer: Equalizer,
    sleepTimer: SleepTimerState,
    openSleepTimer: () -> Unit,
    openEq: () -> Unit,
    request: SettingsFocusRequest?,
    supportsCrossfade: Boolean,
    supportsTransportFades: Boolean,
    supportsReplayGainProcessing: Boolean,
    supportsAudioEffects: Boolean,
    supportsAudioFocusSettings: Boolean,
    draft: SettingsDurationDraft?,
    openDuration: (SettingsTarget) -> Unit,
    closeDuration: () -> Unit,
) {
    item { SettingsSection(stringResource(Res.string.settings_sound_tools)) }
    mark(SettingsTarget.SleepTimer)
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
        }
    }
    item(key = "audio-effects-title") { SettingsSection(stringResource(Res.string.settings_effects)) }
    listOf(SettingsTarget.Balance to AudioEffect.Balance, SettingsTarget.StereoExpand to AudioEffect.StereoExpand,
        SettingsTarget.Tempo to AudioEffect.Tempo, SettingsTarget.Reverb to AudioEffect.Reverb).forEach { (target, effect) ->
        mark(target)
        item(key = "audio-effect-${effect.name}") {
            SettingsSearchAnchor(target, request) {
                AudioEffectControl(target, effect, effect.value(preferences.audioEffects), supportsAudioEffects) {
                    settings.setAudioEffect(effect, it)
                }
            }
        }
    }
    if (!supportsAudioEffects) item { SettingsHint(stringResource(Res.string.settings_crossfade_unsupported)) }
    item { SettingsSection(stringResource(Res.string.settings_replay_gain)) }
    mark(SettingsTarget.ReplayGain)
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
    item { SettingsHint(stringResource(if (preferences.replayGainSettings.enabled)
        Res.string.settings_rg_fallback_hint else Res.string.settings_replay_gain_hint)) }
    mark(SettingsTarget.ReplayGainProcessing)
    item(key = "rg-processing") {
        SettingsSearchAnchor(SettingsTarget.ReplayGainProcessing, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
                SettingsToggleRow(stringResource(Res.string.settings_rg_processing), preferences.replayGainSettings.enabled,
                    settings::setReplayGainProcessing, enabled = supportsReplayGainProcessing,
                    subtitle = stringResource(Res.string.settings_rg_processing_short))
            }
        }
    }
    item { SettingsHint(stringResource(Res.string.settings_rg_processing_hint)) }
    mark(SettingsTarget.ReplayGainPreamp)
    item(key = "rg-preamp") {
        SettingsSearchAnchor(SettingsTarget.ReplayGainPreamp, request) {
            ReplayGainPreampControl(stringResource(Res.string.settings_rg_preamp), preferences.replayGainSettings.preampDb,
                supportsReplayGainProcessing, settings::setReplayGainPreamp)
        }
    }
    mark(SettingsTarget.ReplayGainMissingPreamp)
    item(key = "rg-missing-preamp") {
        SettingsSearchAnchor(SettingsTarget.ReplayGainMissingPreamp, request) {
            ReplayGainPreampControl(stringResource(Res.string.settings_rg_missing_preamp), preferences.replayGainSettings.missingPreampDb,
                supportsReplayGainProcessing, settings::setReplayGainMissingPreamp)
        }
    }
    mark(SettingsTarget.ReplayGainLimiter)
    item(key = "rg-limiter") {
        SettingsSearchAnchor(SettingsTarget.ReplayGainLimiter, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
                SettingsToggleRow(stringResource(Res.string.settings_rg_limiter), preferences.replayGainSettings.limiter,
                    settings::setReplayGainLimiter, enabled = supportsReplayGainProcessing,
                    subtitle = stringResource(Res.string.settings_rg_limiter_short))
            }
        }
    }
    item { SettingsHint(stringResource(Res.string.settings_rg_limiter_hint)) }
    if (!supportsReplayGainProcessing) item { SettingsHint(stringResource(Res.string.settings_crossfade_unsupported)) }
    item(key = "crossfade-title") { SettingsSection(stringResource(Res.string.settings_crossfade)) }
    mark(SettingsTarget.Crossfade, SettingsTarget.CrossfadeDuration)
    item(key = "automatic-crossfade") {
        SettingsSearchAnchor(SettingsTarget.Crossfade, request) {
            SettingsSearchAnchor(SettingsTarget.CrossfadeDuration, request) {
                CrossfadeDurationControl(CrossfadeKind.Automatic, preferences.crossfadeDurationMs,
                    supportsCrossfade, draft?.takeIf { it.target == SettingsTarget.CrossfadeDuration },
                    { openDuration(SettingsTarget.CrossfadeDuration) }, closeDuration) { duration ->
                    settings.setCrossfadeDuration(duration)
                    closeDuration()
                }
            }
        }
    }
    mark(SettingsTarget.ManualCrossfadeDuration)
    item(key = "manual-crossfade") {
        SettingsSearchAnchor(SettingsTarget.ManualCrossfadeDuration, request) {
            CrossfadeDurationControl(CrossfadeKind.Manual, preferences.manualCrossfadeDurationMs,
                supportsCrossfade, draft?.takeIf { it.target == SettingsTarget.ManualCrossfadeDuration },
                { openDuration(SettingsTarget.ManualCrossfadeDuration) }, closeDuration) { duration ->
                settings.setManualCrossfadeDuration(duration)
                closeDuration()
            }
        }
    }
    item(key = "manual-crossfade-hint") { SettingsHint(stringResource(Res.string.settings_crossfade_manual_hint)) }
    if (!supportsCrossfade) item(key = "crossfade-unsupported") { SettingsHint(stringResource(Res.string.settings_crossfade_unsupported)) }
    item(key = "transport-fades-title") { SettingsSection(stringResource(Res.string.settings_transport_fades)) }
    transportFadeTargets.forEachIndexed { index, target ->
        mark(target)
        item(key = "transport-fade-${target.name}") {
            val value = when (target) {
                SettingsTarget.PauseFadeDuration -> preferences.transportFades.pauseMs
                SettingsTarget.StopFadeDuration -> preferences.transportFades.stopMs
                else -> preferences.transportFades.seekMs
            }
            SettingsSearchAnchor(target, request) {
                val position = when (index) {
                    0 -> SettingsGroupPosition.First
                    transportFadeTargets.size - 1 -> SettingsGroupPosition.Last
                    else -> SettingsGroupPosition.Middle
                }
                LocalGlassLook.current.SettingsGroupFragment(position, Modifier.padding(horizontal = pageInset)) {
                    if (supportsTransportFades) SettingsLink(settingsTargetTitle(target), crossfadeDurationValue(value), AppIcons.Pause,
                        summaryIsValue = true, onClick = { if (draft?.target == target) closeDuration() else openDuration(target) })
                    else ListRow(settingsTargetTitle(target), subtitle = crossfadeDurationValue(value))
                    if (supportsTransportFades && draft?.target == target) CrossfadeDurationEditor(draft, { duration ->
                        when (target) {
                            SettingsTarget.PauseFadeDuration -> settings.setPauseFadeDuration(duration)
                            SettingsTarget.StopFadeDuration -> settings.setStopFadeDuration(duration)
                            else -> settings.setSeekFadeDuration(duration)
                        }
                        closeDuration()
                    }, closeDuration)
                }
            }
        }
    }
    if (!supportsTransportFades) item(key = "transport-fades-unsupported") { SettingsHint(stringResource(Res.string.settings_crossfade_unsupported)) }
    item { SettingsSection(stringResource(Res.string.settings_interruptions)) }
    mark(SettingsTarget.AudioFocus)
    item {
        LocalGlassLook.current.SettingsGroupFragment(SettingsGroupPosition.First, Modifier.padding(horizontal = pageInset)) {
            SettingsSearchAnchor(SettingsTarget.AudioFocus, request) {
                SettingsToggleRow(stringResource(Res.string.settings_audio_focus), preferences.handleAudioFocus,
                    settings::setHandleAudioFocus, subtitle = stringResource(Res.string.settings_audio_focus_hint))
            }
        }
    }
    mark(SettingsTarget.FocusDucking)
    item(key = "focus-ducking") {
        SettingsSearchAnchor(SettingsTarget.FocusDucking, request) {
            LocalGlassLook.current.SettingsGroupFragment(SettingsGroupPosition.Middle, Modifier.padding(horizontal = pageInset)) {
                val title = stringResource(Res.string.settings_focus_ducking)
                if (supportsAudioFocusSettings && preferences.handleAudioFocus) {
                    val choices = FocusDucking.entries
                    LocalGlassLook.current.SettingsValueChoice(title, choices.map { focusDuckingLabel(it) },
                        choices.indexOf(preferences.audioFocus.ducking),
                        { index -> choices.getOrNull(index)?.let(settings::setFocusDucking) },
                        stringResource(Res.string.action_cancel), Modifier)
                } else ListRow(title, subtitle = focusDuckingLabel(preferences.audioFocus.ducking))
            }
        }
    }
    listOf(SettingsTarget.ResumeOnFocusGain, SettingsTarget.ResumeAfterCall).forEach { target ->
        mark(target)
        item(key = "focus-${target.name}") {
            SettingsSearchAnchor(target, request) {
                LocalGlassLook.current.SettingsGroupFragment(
                    if (target == SettingsTarget.ResumeAfterCall) SettingsGroupPosition.Last else SettingsGroupPosition.Middle,
                    Modifier.padding(horizontal = pageInset)) {
                    val value = if (target == SettingsTarget.ResumeAfterCall) preferences.audioFocus.resumeAfterCall else preferences.audioFocus.resumeOnFocusGain
                    if (supportsAudioFocusSettings && preferences.handleAudioFocus) SettingsToggleRow(settingsTargetTitle(target), value,
                        { if (target == SettingsTarget.ResumeAfterCall) settings.setResumeAfterCall(it) else settings.setResumeOnFocusGain(it) })
                    else ListRow(settingsTargetTitle(target), subtitle = stringResource(if (value) Res.string.eq_on else Res.string.eq_off))
                }
            }
        }
    }
    item(key = "focus-policy-hint") { SettingsHint(stringResource(Res.string.settings_focus_policy_hint)) }
    if (!supportsAudioFocusSettings) item { SettingsHint(stringResource(Res.string.settings_crossfade_unsupported)) }
}

@Composable
internal fun focusDuckingLabel(value: FocusDucking): String =
    if (value == FocusDucking.Pause) stringResource(Res.string.settings_focus_pause)
    else "${(value.volume * 100).toInt()}%"

@Composable
internal fun bluetoothCommandDelayLabel(ms: Long): String =
    stringResource(if (ms == 0L) Res.string.settings_bluetooth_filter_off else Res.string.settings_bluetooth_filter_seconds, (ms / 1000).toString())

private fun SettingsItems.playerSettings(
    settings: AppSettings,
    preferences: AppPreferences,
    request: SettingsFocusRequest?,
    supportsAmll: Boolean,
    supportsOnline: Boolean,
    supportsHeadphones: Boolean,
) {
    item { SettingsSection(stringResource(Res.string.settings_headset)) }
    mark(SettingsTarget.ResumeOnReconnect, SettingsTarget.ResumeBluetooth, SettingsTarget.PauseOnDisconnect, SettingsTarget.HeadsetMultiPress)
    item {
        SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
            SettingsSearchAnchor(SettingsTarget.ResumeOnReconnect, request) {
                if (supportsHeadphones) SettingsToggleRow(stringResource(Res.string.settings_resume_wired), preferences.headphones.resumeWired,
                    settings::setResumeWired)
                else ListRow(stringResource(Res.string.settings_resume_wired), subtitle = stringResource(if (preferences.headphones.resumeWired) Res.string.eq_on else Res.string.eq_off))
            }
            SettingsSearchAnchor(SettingsTarget.ResumeBluetooth, request) {
                if (supportsHeadphones) SettingsToggleRow(stringResource(Res.string.settings_resume_bluetooth), preferences.headphones.resumeBluetooth,
                    settings::setResumeBluetooth)
                else ListRow(stringResource(Res.string.settings_resume_bluetooth), subtitle = stringResource(if (preferences.headphones.resumeBluetooth) Res.string.eq_on else Res.string.eq_off))
            }
            SettingsSearchAnchor(SettingsTarget.PauseOnDisconnect, request) {
                SettingsToggleRow(stringResource(Res.string.settings_pause_disconnect), preferences.pauseOnDisconnect,
                    settings::setPauseOnDisconnect)
            }
            SettingsSearchAnchor(SettingsTarget.HeadsetMultiPress, request) {
                SettingsToggleRow(stringResource(Res.string.settings_headset_multi), preferences.headsetMultiPress,
                    settings::setHeadsetMultiPress, subtitle = stringResource(Res.string.settings_headset_multi_hint))
            }
        }
    }
    mark(SettingsTarget.BluetoothCommandDelay)
    item(key = "bluetooth-command-delay") {
        SettingsSearchAnchor(SettingsTarget.BluetoothCommandDelay, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    AppText(stringResource(Res.string.settings_bluetooth_command_filter), AppTheme.typography.body, modifier = Modifier.padding(horizontal = 16.dp))
                    val choices = HeadphoneSettings.delayChoicesMs
                    if (supportsHeadphones) FilterChips(choices.map { bluetoothCommandDelayLabel(it) },
                        choices.indexOf(preferences.headphones.bluetoothCommandDelayMs), { settings.setBluetoothCommandDelay(choices[it]) })
                    else ListRow(bluetoothCommandDelayLabel(preferences.headphones.bluetoothCommandDelayMs))
                }
            }
        }
    }
    item(key = "bluetooth-filter-hint") { SettingsHint(stringResource(Res.string.settings_bluetooth_command_filter_hint)) }
    if (!supportsHeadphones) item { SettingsHint(stringResource(Res.string.settings_crossfade_unsupported)) }
    item(key = "long-tracks-title") { SettingsSection(stringResource(Res.string.settings_long_section)) }
    mark(SettingsTarget.ResumePerTrack)
    item(key = "per-track-resume") {
        SettingsSearchAnchor(SettingsTarget.ResumePerTrack, request) {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
                SettingsToggleRow(stringResource(Res.string.settings_long_resume), preferences.resumePerTrack,
                    settings::setResumePerTrack)
            }
        }
    }
    item(key = "long-threshold-title") { SettingsSection(stringResource(Res.string.settings_long_threshold)) }
    mark(SettingsTarget.LongTrackThreshold)
    item(key = "long-duration-threshold") {
        SettingsSearchAnchor(SettingsTarget.LongTrackThreshold, request) {
            val choices = AppSettings.LONG_TRACK_CHOICES_MS
            FilterChips(choices.map { stringResource(Res.string.settings_long_minutes, (it / 60_000L).toInt()) },
                choices.indexOf(preferences.longTrackThresholdMs).coerceAtLeast(0),
                { settings.setLongTrackThreshold(choices[it]) }, Modifier.padding(vertical = 4.dp))
        }
    }
    item(key = "long-tracks-hint") { SettingsHint(stringResource(Res.string.settings_long_hint)) }
    if (supportsAmll || supportsOnline) {
        item { SettingsSection(stringResource(Res.string.settings_online_lyrics)) }
        mark(SettingsTarget.AmllLyrics, SettingsTarget.LrclibLyrics)
        item {
            SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
                if (supportsAmll) SettingsSearchAnchor(SettingsTarget.AmllLyrics, request) {
                    SettingsToggleRow(stringResource(Res.string.settings_amll), preferences.amllEnabled,
                        settings::setAmllEnabled, subtitle = stringResource(Res.string.settings_amll_hint))
                }
                if (supportsOnline) SettingsSearchAnchor(SettingsTarget.LrclibLyrics, request) {
                    SettingsToggleRow(stringResource(Res.string.settings_lrclib), preferences.lrclibEnabled,
                        settings::setLrclibEnabled, subtitle = stringResource(Res.string.settings_lrclib_hint))
                }
            }
        }
    }
}

@Composable
private fun AudioEffectControl(target: SettingsTarget, effect: AudioEffect, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val title = settingsTargetTitle(target)
    val readout = audioEffectValue(effect, value)
    SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
        LocalGlassLook.current.SettingsAdjustment(
            title, readout, stringResource(when (effect) {
                AudioEffect.Balance -> Res.string.settings_balance_hint
                AudioEffect.StereoExpand -> Res.string.settings_stereo_expand_hint
                AudioEffect.Tempo -> Res.string.settings_tempo_hint
                AudioEffect.Reverb -> Res.string.settings_reverb_hint
            }), enabled,
            stringResource(Res.string.settings_effect_less), stringResource(Res.string.settings_effect_more),
            stringResource(Res.string.settings_effect_reset),
            { onChange((value - 5).coerceAtLeast(effect.minimum)) },
            { onChange((value + 5).coerceAtMost(effect.maximum)) }, { onChange(effect.default) }, Modifier,
        ) { modifier ->
            LocalGlassLook.current.ValueSlider(effect.sliderPosition(value), { onChange(effect.fromSlider(it)) }, title,
                modifier.semantics { stateDescription = readout })
        }
    }
}

@Composable
internal fun audioEffectValue(effect: AudioEffect, value: Int): String = when {
    effect != AudioEffect.Balance -> "$value%"
    value == 0 -> stringResource(Res.string.settings_balance_center)
    value < 0 -> stringResource(Res.string.settings_balance_left, "${-value}%")
    else -> stringResource(Res.string.settings_balance_right, "$value%")
}

@Composable
private fun ReplayGainPreampControl(title: String, db: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val value = stringResource(Res.string.settings_rg_db, db)
    SettingsFormGroup(Modifier.padding(horizontal = pageInset)) {
        LocalGlassLook.current.SettingsAdjustment(
            title, value, null, enabled,
            stringResource(Res.string.settings_rg_less), stringResource(Res.string.settings_rg_more), null,
            { onChange((db - 1).coerceAtLeast(-20)) }, { onChange((db + 1).coerceAtMost(20)) }, null, Modifier,
        ) { modifier ->
            LocalGlassLook.current.VolumeSlider((db + 20) / 40f,
                { onChange(ReplayGainSettings.preampFromSlider(it)) }, title,
                modifier.semantics { stateDescription = value })
        }
    }
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
    val transportFade = draft.target in transportFadeTargets
    val validation = if (transportFade) stringResource(Res.string.settings_transport_fades_invalid, TransportFadeSettings.MAX_DURATION_MS)
        else stringResource(Res.string.settings_crossfade_ms_invalid, Crossfade.MIN_EFFECTIVE_MS, Crossfade.MAX_DURATION_MS)
    fun save() {
        val value = if (transportFade) parseTransportFadeDurationMs(draft.text) else parseCrossfadeDurationMs(draft.text)
        if (value != null) onSave(value) else draft.invalid = true
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LocalGlassLook.current.SettingsDurationField(draft.text, { draft.text = it; draft.invalid = false },
            stringResource(Res.string.settings_crossfade_ms_label), ::save,
            Modifier.fillMaxWidth().semantics { if (draft.invalid) error(validation) })
        AppText(if (transportFade) stringResource(Res.string.settings_transport_fades_range, TransportFadeSettings.MAX_DURATION_MS)
            else stringResource(Res.string.settings_crossfade_ms_hint, Crossfade.MIN_EFFECTIVE_MS, Crossfade.MAX_DURATION_MS),
            AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
        if (draft.invalid) AppText(validation, AppTheme.typography.footnote, color = AppTheme.colors.label,
            modifier = Modifier.semantics {
                error(validation); liveRegion = LiveRegionMode.Polite
            })
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = when {
                transportFade -> listOf(0L, 100L, 250L, 500L, 1_000L)
                draft.target == SettingsTarget.CrossfadeDuration -> crossfadePresets
                else -> manualCrossfadePresets
            }
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

/** The offered scales are discrete, so thresholds keep labels stable for stored floats. */
@Composable
internal fun textScaleLabel(scale: Float): String = stringResource(when {
    scale < 0.95f -> Res.string.settings_text_scale_small
    scale < 1.1f -> Res.string.settings_text_scale_default
    scale < 1.25f -> Res.string.settings_text_scale_large
    scale < 1.45f -> Res.string.settings_text_scale_larger
    else -> Res.string.settings_text_scale_largest
})

@Composable
internal fun blurIntensityLabel(intensity: BlurIntensity): String = stringResource(when (intensity) {
    BlurIntensity.Off -> Res.string.settings_blur_off
    BlurIntensity.Low -> Res.string.settings_blur_low
    BlurIntensity.Default -> Res.string.settings_blur_default
    BlurIntensity.High -> Res.string.settings_blur_high
})

/** Where each searchable control sits in its page's lazy list, recorded while the page is built. */
internal class SettingsItemPositions {
    val byTarget = HashMap<SettingsTarget, Int>()
}

/**
 * Wraps a lazy list so that pages can say which items hold which searchable controls: [mark] records the
 * index of the next item. Search then scrolls by key, not by a hand-kept list of numbers.
 */
internal class SettingsItems(private val scope: LazyListScope, private val positions: SettingsItemPositions) {
    private var count = 0

    fun mark(vararg targets: SettingsTarget) {
        targets.forEach { positions.byTarget[it] = count }
    }

    fun item(key: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
        count++
        scope.item(key = key, content = content)
    }
}
