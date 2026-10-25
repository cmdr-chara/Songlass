package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun SheetWindowHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        content()
    }
}
