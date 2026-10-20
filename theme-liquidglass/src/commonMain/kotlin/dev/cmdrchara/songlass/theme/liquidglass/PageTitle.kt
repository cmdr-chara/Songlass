package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Default root title keeps the established Liquid/Material screen geometry. */
@Composable
internal fun LiquidPageTitle(text: String, trailing: (@Composable () -> Unit)?, modifier: Modifier) {
    Row(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppText(text, AppTheme.typography.largeTitle, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}
