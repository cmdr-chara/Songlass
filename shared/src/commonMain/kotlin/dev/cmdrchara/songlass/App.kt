package dev.cmdrchara.songlass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.app_name
import org.jetbrains.compose.resources.stringResource

/** Root composable shared by Android and iOS. Placeholder until the design system exists. */
@Composable
fun App() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(text = stringResource(Res.string.app_name))
    }
}
