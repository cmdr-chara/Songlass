package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** Official Miuix overlays hosted by the stable root Scaffold; callbacks stay with the screen. */
@Composable
internal fun MiuixActions(visible: Boolean, title: String, subtitle: String?, artwork: Artwork?, actions: List<SheetAction>, closeLabel: String, dismiss: () -> Unit) {
    OverlayBottomSheet(show = visible, title = title, onDismissRequest = dismiss,
        endAction = { MiuixIconAction(AppIcons.Close, closeLabel, dismiss, false, false, Modifier) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (artwork != null || subtitle != null) MiuixRow(title, subtitle,
                artwork?.let { { AlbumArt(it, Modifier.size(52.dp), cornerRadius = 16.dp) } }, null, false, null, Modifier)
            MiuixGroup({
                actions.forEach { action ->
                    MiuixRow(action.label, null, action.icon?.let { { dev.cmdrchara.songlass.designsystem.foundation.AppIcon(it, null, size = 24.dp, tint = AppTheme.colors.accent) } },
                        null, false, { if (visible) { dismiss(); action.onClick() } }, Modifier)
                }
            }, Modifier)
        }
    }
}

@Composable
internal fun MiuixInfo(visible: Boolean, title: String, rows: List<InfoRow>, closeLabel: String, dismiss: () -> Unit) {
    OverlayBottomSheet(show = visible, title = title, onDismissRequest = dismiss,
        endAction = { MiuixIconAction(AppIcons.Close, closeLabel, dismiss, false, false, Modifier) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Column(Modifier.fillMaxWidth()) {
                    AppText(row.label, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
                    AppText(row.value, AppTheme.typography.body)
                }
            }
        }
    }
}

@Composable
internal fun MiuixPrompt(visible: Boolean, title: String, initialText: String, placeholder: String, confirmLabel: String, cancelLabel: String, confirm: (String) -> Unit, dismiss: () -> Unit) {
    var text by remember(visible, initialText) { mutableStateOf(initialText) }
    val focus = remember { FocusRequester() }
    fun submit() {
        if (visible && text.isNotBlank()) {
            confirm(text.trim())
            dismiss()
        }
    }
    OverlayDialog(show = visible, title = title, onDismissRequest = dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MiuixField(text, { text = it.take(80) }, placeholder, true, Modifier.focusRequester(focus),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixAction(cancelLabel, null, dismiss, Modifier.weight(1f))
                MiuixAction(confirmLabel, null, ::submit, Modifier.weight(1f), primary = true, enabled = text.isNotBlank())
            }
        }
        LaunchedEffect(visible) { if (visible) runCatching { focus.requestFocus() } }
    }
}
