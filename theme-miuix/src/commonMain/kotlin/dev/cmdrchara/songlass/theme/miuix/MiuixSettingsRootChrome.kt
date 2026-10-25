package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalAppColors

/** Only the Settings index adopts the native expanded/collapsed title recipe. */
internal val LocalMiuixSettingsRoot = staticCompositionLocalOf { false }
private val ExpandedTitleTop = 72.dp

@Composable
internal fun MiuixSettingsRootChrome(enabled: Boolean, title: String, listState: LazyListState, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    val canvas = colors.background
    val surface = if (colors.isDark) Color(0xFF242424) else Color(0xFFF2F2F2)
    val rootColors = if (enabled) colors.copy(background = canvas, secondaryBackground = surface, fill = surface) else colors
    val collapseOffset = with(LocalDensity.current) { ExpandedTitleTop.roundToPx() }
    val compact by remember(enabled, listState, collapseOffset) {
        derivedStateOf {
            enabled && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset >= collapseOffset)
        }
    }
    Box(Modifier.fillMaxSize().background(canvas)) {
        CompositionLocalProvider(LocalMiuixSettingsRoot provides enabled, LocalAppColors provides rootColors) {
            content()
            if (compact) {
                Box(Modifier.fillMaxWidth().background(canvas)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .heightIn(min = 52.dp)
                    // Rows behind the opaque title must not receive invisible taps.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 26.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center) {
                    AppText(title, typography.title3.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
                        Modifier.fillMaxWidth().semantics { heading() }, colors.label,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
internal fun MiuixSettingsRootTitle(title: String, modifier: Modifier) {
    val colors = AppTheme.colors
    val typography = AppTheme.typography
    AppText(title, typography.largeTitle.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal),
        modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = ExpandedTitleTop, bottom = 14.dp)
            .semantics { heading() }, colors.label)
}
