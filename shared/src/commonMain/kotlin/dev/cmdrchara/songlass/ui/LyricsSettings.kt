package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cmdrchara.songlass.core.lyrics.LyricsPresentation
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.SettingsToggleRow
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LyricsAdjustment(target: SettingsTarget, settings: AppSettings, value: LyricsPresentation) {
    val timing = target == SettingsTarget.LyricsOffset
    val title = stringResource(if (timing) Res.string.lyrics_timing else Res.string.lyrics_text_size)
    val readout = if (timing) lyricsOffsetLabel(value.offsetMs) else "${value.textSizePercent}%"
    val current = if (timing) value.offsetMs else value.textSizePercent
    val initial = if (timing) 0 else 100
    val step = if (timing) LyricsPresentation.OFFSET_STEP_MS else LyricsPresentation.TEXT_SIZE_STEP
    val change: (Int) -> Unit = if (timing) settings::setLyricsOffset else settings::setLyricsTextSize
    val look = LocalGlassLook.current
    look.SettingsAdjustment(
        title, readout, if (timing) stringResource(Res.string.lyrics_timing_hint) else null, true,
        stringResource(if (timing) Res.string.lyrics_earlier else Res.string.lyrics_smaller),
        stringResource(if (timing) Res.string.lyrics_later else Res.string.lyrics_larger),
        stringResource(Res.string.settings_effect_reset),
        { change(current - step) }, { change(current + step) }, { change(initial) }, Modifier,
        resetEnabled = current != initial,
    ) { modifier ->
        val slider = if (timing) (value.offsetMs + 5_000) / 10_000f else (value.textSizePercent - 80) / 80f
        look.ValueSlider(slider, {
            change(if (timing) LyricsPresentation.offsetFromSlider(it) else LyricsPresentation.textSizeFromSlider(it))
        }, title, modifier.semantics { stateDescription = readout })
    }
}

@Composable
internal fun lyricsOffsetLabel(offsetMs: Int): String = stringResource(
    Res.string.lyrics_offset_value, if (offsetMs > 0) "+$offsetMs" else offsetMs.toString(),
)

@Composable
internal fun LyricsOptionsDialog(settings: AppSettings, value: LyricsPresentation, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 720.dp)
            .clip(RoundedCornerShape(24.dp)).background(AppTheme.colors.background).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppText(stringResource(Res.string.lyrics_options), AppTheme.typography.headline, modifier = Modifier.weight(1f))
                IconButton(AppIcons.Close, stringResource(Res.string.action_close), onClose)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SettingsGroup { LyricsAdjustment(SettingsTarget.LyricsOffset, settings, value) }
                SettingsGroup(Modifier.padding(top = 8.dp)) { LyricsAdjustment(SettingsTarget.LyricsSize, settings, value) }
                SettingsGroup(Modifier.padding(top = 8.dp)) {
                    SettingsToggleRow(stringResource(Res.string.lyrics_keep_screen_on), value.keepScreenOn,
                        settings::setLyricsKeepScreenOn, subtitle = stringResource(Res.string.lyrics_keep_screen_hint))
                }
            }
        }
    }
}

/** Owned by the visible, resumed lyrics page; never changes the device's screen timeout. */
@Composable
internal expect fun KeepLyricsScreenAwake(enabled: Boolean)
