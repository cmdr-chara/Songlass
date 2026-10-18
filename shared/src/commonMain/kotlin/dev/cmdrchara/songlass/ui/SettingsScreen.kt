package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.shared.resources.settings_replay_gain_hint

import dev.cmdrchara.songlass.shared.resources.settings_replay_gain_album

import dev.cmdrchara.songlass.shared.resources.settings_replay_gain_track

import dev.cmdrchara.songlass.shared.resources.settings_replay_gain_off

import dev.cmdrchara.songlass.shared.resources.settings_replay_gain

import dev.cmdrchara.songlass.core.player.ReplayGainMode

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.cmdrchara.songlass.core.equalizer.Equalizer
import dev.cmdrchara.songlass.shared.resources.eq_off
import dev.cmdrchara.songlass.shared.resources.eq_on
import dev.cmdrchara.songlass.shared.resources.eq_title
import dev.cmdrchara.songlass.shared.resources.settings_sound
import dev.cmdrchara.songlass.shared.resources.settings_resume_reconnect
import dev.cmdrchara.songlass.shared.resources.settings_resume_reconnect_hint
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.AppearanceMode
import dev.cmdrchara.songlass.core.settings.ArtworkStyle
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SettingsChoiceRow
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.SettingsToggleRow
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.settings_artwork
import dev.cmdrchara.songlass.shared.resources.settings_artwork_full
import dev.cmdrchara.songlass.shared.resources.settings_artwork_hint
import dev.cmdrchara.songlass.shared.resources.settings_artwork_square
import dev.cmdrchara.songlass.shared.resources.settings_accessibility
import dev.cmdrchara.songlass.shared.resources.settings_accessibility_hint
import dev.cmdrchara.songlass.shared.resources.settings_appearance
import dev.cmdrchara.songlass.shared.resources.settings_appearance_dark
import dev.cmdrchara.songlass.shared.resources.settings_appearance_hint
import dev.cmdrchara.songlass.shared.resources.settings_appearance_light
import dev.cmdrchara.songlass.shared.resources.settings_appearance_system
import dev.cmdrchara.songlass.shared.resources.settings_device_enabled
import dev.cmdrchara.songlass.shared.resources.settings_liquid_glass
import dev.cmdrchara.songlass.shared.resources.settings_reduce_motion
import dev.cmdrchara.songlass.shared.resources.settings_reduce_motion_hint
import dev.cmdrchara.songlass.shared.resources.settings_reduce_transparency
import dev.cmdrchara.songlass.shared.resources.settings_reduce_transparency_hint
import dev.cmdrchara.songlass.shared.resources.settings_title
import dev.cmdrchara.songlass.shared.resources.settings_ui_style
import dev.cmdrchara.songlass.shared.resources.settings_ui_style_hint
import dev.cmdrchara.songlass.shared.resources.settings_material_glass
import dev.cmdrchara.songlass.core.settings.InterfaceStyle
import dev.cmdrchara.songlass.shared.resources.library_settings
import org.jetbrains.compose.resources.stringResource

/** App-wide preferences; values and persistence live in core. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    preferences: AppPreferences,
    systemAccessibility: UiAccessibility,
    equalizer: Equalizer,
    onOpenEqualizer: () -> Unit,
    onOpenLibrary: () -> Unit,
    backLabel: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
        item { BackLink(backLabel, onBack) }
        item {
            AppText(
                stringResource(Res.string.settings_title),
                AppTheme.typography.largeTitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
        }
        item { SectionTitle(stringResource(Res.string.settings_ui_style)) }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp).selectableGroup()) {
                InterfaceStyle.entries.forEach { style ->
                    val label = when (style) {
                        InterfaceStyle.LiquidGlass -> Res.string.settings_liquid_glass
                        InterfaceStyle.MaterialGlass -> Res.string.settings_material_glass
                    }
                    SettingsChoiceRow(
                        title = stringResource(label),
                        selected = preferences.interfaceStyle == style,
                        onClick = { settings.setInterfaceStyle(style) },
                    )
                }
            }
        }
        item { SettingsHint(stringResource(Res.string.settings_ui_style_hint)) }
        item { SectionTitle(stringResource(Res.string.settings_appearance)) }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp).selectableGroup()) {
                AppearanceMode.entries.forEach { mode ->
                    val label = when (mode) {
                        AppearanceMode.System -> Res.string.settings_appearance_system
                        AppearanceMode.Light -> Res.string.settings_appearance_light
                        AppearanceMode.Dark -> Res.string.settings_appearance_dark
                    }
                    SettingsChoiceRow(
                        title = stringResource(label),
                        selected = preferences.appearance == mode,
                        onClick = { settings.setAppearance(mode) },
                    )
                }
            }
        }
        item { SettingsHint(stringResource(Res.string.settings_appearance_hint)) }
        item { SectionTitle(stringResource(Res.string.settings_artwork)) }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp).selectableGroup()) {
                ArtworkStyle.entries.forEach { style ->
                    val label = when (style) {
                        ArtworkStyle.FullBleed -> Res.string.settings_artwork_full
                        ArtworkStyle.Square -> Res.string.settings_artwork_square
                    }
                    SettingsChoiceRow(
                        title = stringResource(label),
                        selected = preferences.artworkStyle == style,
                        onClick = { settings.setArtworkStyle(style) },
                    )
                }
            }
        }
        item { SettingsHint(stringResource(Res.string.settings_artwork_hint)) }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                ListRow(
                    title = stringResource(Res.string.library_settings),
                    trailing = { AppIcon(AppIcons.ChevronRight, contentDescription = null, size = 16.dp) },
                    onClick = onOpenLibrary,
                )
            }
        }
        item { SectionTitle(stringResource(Res.string.settings_sound)) }
        item {
            val eq by equalizer.state.collectAsState()
            SettingsGroup(Modifier.padding(horizontal = 16.dp)) {
                ListRow(
                    title = stringResource(Res.string.eq_title),
                    subtitle = when {
                        !eq.enabled -> stringResource(Res.string.eq_off)
                        else -> listOfNotNull(stringResource(Res.string.eq_on), eq.profile?.name).joinToString(" · ")
                    },
                    leading = { AppIcon(AppIcons.Equalizer, contentDescription = null, size = 22.dp, tint = AppTheme.colors.accent) },
                    trailing = { AppIcon(AppIcons.ChevronRight, contentDescription = null, size = 16.dp, tint = AppTheme.colors.tertiaryLabel) },
                    onClick = onOpenEqualizer,
                )
                SettingsToggleRow(
                    title = stringResource(Res.string.settings_resume_reconnect),
                    subtitle = stringResource(Res.string.settings_resume_reconnect_hint),
                    checked = preferences.resumeOnReconnect,
                    onCheckedChange = settings::setResumeOnReconnect,
                )
            }
        }
        item { SectionTitle(stringResource(Res.string.settings_replay_gain)) }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp).selectableGroup()) {
                ReplayGainMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ReplayGainMode.Off -> Res.string.settings_replay_gain_off
                        ReplayGainMode.Track -> Res.string.settings_replay_gain_track
                        ReplayGainMode.Album -> Res.string.settings_replay_gain_album
                    }
                    SettingsChoiceRow(
                        title = stringResource(label),
                        selected = preferences.replayGain == mode,
                        onClick = { settings.setReplayGain(mode) },
                    )
                }
            }
        }
        item { SettingsHint(stringResource(Res.string.settings_replay_gain_hint)) }
        item { SectionTitle(stringResource(Res.string.settings_accessibility)) }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = stringResource(Res.string.settings_reduce_motion),
                    subtitle = stringResource(
                        if (systemAccessibility.reduceMotion) Res.string.settings_device_enabled
                        else Res.string.settings_reduce_motion_hint,
                    ),
                    checked = preferences.reduceMotion || systemAccessibility.reduceMotion,
                    enabled = !systemAccessibility.reduceMotion,
                    onCheckedChange = settings::setReduceMotion,
                )
                SettingsToggleRow(
                    title = stringResource(Res.string.settings_reduce_transparency),
                    subtitle = stringResource(
                        if (systemAccessibility.reduceTransparency) Res.string.settings_device_enabled
                        else Res.string.settings_reduce_transparency_hint,
                    ),
                    checked = preferences.reduceTransparency || systemAccessibility.reduceTransparency,
                    enabled = !systemAccessibility.reduceTransparency,
                    onCheckedChange = settings::setReduceTransparency,
                )
            }
        }
        item { SettingsHint(stringResource(Res.string.settings_accessibility_hint)) }
    }
}

@Composable
private fun SettingsHint(text: String) {
    AppText(
        text,
        AppTheme.typography.footnote,
        color = AppTheme.colors.label.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
