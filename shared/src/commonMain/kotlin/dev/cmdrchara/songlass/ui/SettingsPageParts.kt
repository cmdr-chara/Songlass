package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.cmdrchara.songlass.designsystem.BackPageTitle
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook

/** Material tools share the preferences rhythm; other looks keep their original composition. */
@Composable
internal fun SettingsPageHeader(text: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    legacy: (@Composable () -> Unit)? = null) =
    LocalGlassLook.current.SettingsToolPageTitle(text, backLabel, onBack, modifier) {
        if (legacy != null) legacy() else BackPageTitle(text, backLabel, onBack, modifier)
    }

@Composable
internal fun SettingsPageSection(text: String) = LocalGlassLook.current.SettingsToolSectionTitle(text) { SectionTitle(text) }
