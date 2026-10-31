package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
    OverlayBottomSheet(show = visible, title = null, onDismissRequest = dismiss,
        endAction = { MiuixSheetHeading(title, closeLabel, dismiss) }) {
        // Register inside the popup, after its dismiss handler, so nested Back stays in this sheet.
        val backAction = actions.firstOrNull { it.isBackAction }
        MiuixNestedBackHandler(enabled = visible && backAction != null) {
            if (visible) backAction?.onClick?.invoke()
        }
        val scroll = rememberScrollState()
        LaunchedEffect(title, visible, actions.firstOrNull()?.isBackAction) {
            if (visible) scroll.scrollTo(0)
        }
        // The sheet is drawn edge to edge: keep the last row clear of the gesture bar and let long lists scroll.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 480.dp).verticalScroll(scroll).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The sheet header already carries the title; repeat it only next to an artwork.
            if (artwork != null) MiuixRow(title, subtitle,
                { AlbumArt(artwork, Modifier.size(52.dp), cornerRadius = 16.dp) }, null, false, null, Modifier)
            else if (subtitle != null) AppText(subtitle, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 4.dp))
            MiuixGroup({
                actions.forEach { action ->
                    MiuixRow(action.label, null, action.icon?.let { { dev.cmdrchara.songlass.designsystem.foundation.AppIcon(it, null, size = 24.dp, tint = AppTheme.colors.accent) } },
                        null, false, { if (visible) { if (action.dismissOnClick) dismiss(); action.onClick() } }, Modifier, fullText = true)
                }
            }, Modifier)
        }
    }
}

@Composable
internal fun MiuixInfo(visible: Boolean, title: String, rows: List<InfoRow>, closeLabel: String, dismiss: () -> Unit) {
    OverlayBottomSheet(show = visible, title = null, onDismissRequest = dismiss,
        endAction = { MiuixSheetHeading(title, closeLabel, dismiss) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Column(Modifier.fillMaxWidth()) {
                    AppText(row.label, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
                    AppText(row.value, AppTheme.typography.body)
                }
            }
        }
    }
}

/** Match the playback-tool header: reserve the close target before measuring the title. */
@Composable
internal fun MiuixSheetHeading(title: String, closeLabel: String, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(48.dp))
        AppText(title, AppTheme.typography.title3.copy(fontWeight = FontWeight.Medium),
            color = AppTheme.colors.label, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() })
        MiuixIconAction(AppIcons.Close, closeLabel, dismiss, false, false, Modifier.size(48.dp))
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
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MiuixField(text, { text = it.take(80) }, placeholder, true, Modifier.focusRequester(focus),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val style = AppTheme.typography.headline
                val minimumActionWidth = with(density) {
                    maxOf(measurer.measure(cancelLabel, style, softWrap = false, maxLines = 1).size.width,
                        measurer.measure(confirmLabel, style, softWrap = false, maxLines = 1).size.width).toDp() + 32.dp
                }
                if ((maxWidth - 12.dp) / 2 >= minimumActionWidth) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiuixAction(cancelLabel, null, dismiss, Modifier.weight(1f))
                        MiuixAction(confirmLabel, null, ::submit, Modifier.weight(1f), primary = true, enabled = text.isNotBlank())
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiuixAction(cancelLabel, null, dismiss, Modifier.fillMaxWidth())
                        MiuixAction(confirmLabel, null, ::submit, Modifier.fillMaxWidth(), primary = true, enabled = text.isNotBlank())
                    }
                }
            }
        }
        LaunchedEffect(visible) { if (visible) runCatching { focus.requestFocus() } }
    }
}
