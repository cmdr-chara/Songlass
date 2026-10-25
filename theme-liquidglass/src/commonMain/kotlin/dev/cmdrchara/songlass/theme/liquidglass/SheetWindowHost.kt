package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.runtime.Composable

/** A real modal window above app navigation; the caller retains animation and scrim ownership. */
@Composable
expect fun SheetWindowHost(onDismiss: () -> Unit, content: @Composable () -> Unit)
