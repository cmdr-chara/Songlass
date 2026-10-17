package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.uikit.LocalUIViewController
import dev.cmdrchara.songlass.SonglassViewController

@Composable
actual fun SystemBarAppearance(lightIcons: Boolean) {
    val controller = LocalUIViewController.current.parentViewController as? SonglassViewController
    SideEffect { controller?.updateStatusBarAppearance(lightIcons) }
}
