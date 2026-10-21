package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.settings.SettingsArea
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** Last item of a settings page; it only asks, [RestoreDefaultsSheet] confirms. */
@Composable
internal fun RestoreDefaultsButton(area: SettingsArea, onAsk: (SettingsArea) -> Unit) {
    ActionButton(stringResource(Res.string.settings_restore_defaults), { onAsk(area) },
        Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 16.dp))
}

/** Hosted above the scaffold, like the other sheets, so the bottom bar cannot cover its actions. */
@Composable
fun RestoreDefaultsSheet(state: AppState) {
    val asked = state.restoreDefaultsArea
    // The sheet animates out after the area is cleared; keep the area it was opened for.
    var area by remember { mutableStateOf(asked) }
    if (asked != null) area = asked
    ActionSheet(
        visible = asked != null,
        title = stringResource(Res.string.settings_restore_title),
        subtitle = stringResource(Res.string.settings_restore_hint),
        actions = listOf(SheetAction(stringResource(Res.string.settings_restore_action), null) {
            area?.let { state.settings.resetToDefaults(it) }
        }),
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = state::closeRestoreDefaults,
    )
}
