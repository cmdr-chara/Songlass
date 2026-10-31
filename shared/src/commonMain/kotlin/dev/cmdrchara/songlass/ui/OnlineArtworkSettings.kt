package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SettingsToggleRow
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.settings_online_artwork
import dev.cmdrchara.songlass.shared.resources.settings_online_artwork_automatic_hint
import dev.cmdrchara.songlass.shared.resources.settings_online_artwork_wifi
import dev.cmdrchara.songlass.shared.resources.settings_online_artwork_wifi_hint
import dev.cmdrchara.songlass.shared.resources.settings_online_artwork_stored
import dev.cmdrchara.songlass.shared.resources.settings_online_artwork_clear
import dev.cmdrchara.songlass.shared.resources.settings_online_artwork_clear_hint
import dev.cmdrchara.songlass.shared.resources.artwork_folder_title
import dev.cmdrchara.songlass.shared.resources.artwork_folder_hint
import dev.cmdrchara.songlass.shared.resources.artwork_folder_remove
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Appearance page, "Online covers": automatic missing-cover lookup and the rule that
 * requests/downloads wait for Wi-Fi. An explicit disabled preference is always respected.
 *
 * Shown only where the platform can actually reach a provider (the caller decides), so the switch
 * never promises a feature that cannot run. Resetting the Appearance page clears both values.
 */
internal fun SettingsItems.onlineArtworkSettings(
    settings: AppSettings,
    preferences: AppPreferences,
    request: SettingsFocusRequest?,
) {
    item {
        val picker = rememberArtworkFolderPicker() ?: return@item
        LocalGlassLook.current.SettingsSectionTitle(stringResource(Res.string.artwork_folder_title))
        LocalGlassLook.current.SettingsPageGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
            ListRow(title = picker.name ?: stringResource(Res.string.artwork_folder_title),
                subtitle = stringResource(Res.string.artwork_folder_hint), fullText = true, onClick = picker.open)
            if (picker.name != null) ListRow(title = stringResource(Res.string.artwork_folder_remove),
                fullText = true, onClick = picker.clear)
        }
    }
    item { LocalGlassLook.current.SettingsSectionTitle(stringResource(Res.string.settings_online_artwork)) }
    mark(SettingsTarget.OnlineArtworkEnabled, SettingsTarget.OnlineArtworkWifiOnly)
    item {
        LocalGlassLook.current.SettingsPageGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
            SettingsSearchAnchor(SettingsTarget.OnlineArtworkEnabled, request) {
                SettingsToggleRow(
                    stringResource(Res.string.settings_online_artwork),
                    preferences.onlineArtworkEnabled,
                    settings::setOnlineArtworkEnabled,
                    subtitle = stringResource(Res.string.settings_online_artwork_automatic_hint),
                )
            }
            SettingsSearchAnchor(SettingsTarget.OnlineArtworkWifiOnly, request) {
                SettingsToggleRow(
                    stringResource(Res.string.settings_online_artwork_wifi),
                    preferences.onlineArtworkWifiOnly,
                    settings::setOnlineArtworkWifiOnly,
                    enabled = preferences.onlineArtworkEnabled,
                    subtitle = stringResource(Res.string.settings_online_artwork_wifi_hint),
                )
            }
        }
    }
    item {
        // The cache is bounded by core; this is the explicit "clear the space" command.
        val controller = LocalOnlineArtwork.current ?: return@item
        val covers by controller.downloaded.state.collectAsState()
        if (covers.isEmpty()) return@item
        val scope = rememberCoroutineScope()
        LocalGlassLook.current.SettingsPageGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
            ListRow(
                title = stringResource(Res.string.settings_online_artwork_stored, covers.size.toString()),
                subtitle = stringResource(Res.string.settings_online_artwork_clear_hint),
                fullText = true,
                trailing = {
                    AppText(stringResource(Res.string.settings_online_artwork_clear),
                        AppTheme.typography.footnote, color = AppTheme.colors.accent)
                },
                onClick = { scope.launch { controller.downloaded.clear() } },
            )
        }
    }
}
