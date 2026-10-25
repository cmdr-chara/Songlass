package dev.cmdrchara.songlass.theme.liquidglass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun SheetWindowHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val sourceWindow = activityWindow(view.context)
        SideEffect {
            window?.apply {
                // The existing animated Compose scrim is the only dim layer.
                val current = attributes
                // Compose owns sheet motion, including the app Reduce Motion setting.
                if (current.windowAnimations != 0) setWindowAnimations(0)
                if (current.dimAmount != 0f) setDimAmount(0f)
                if ((current.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }
                val appearanceMask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                val sourceAppearance = sourceWindow?.insetsController?.systemBarsAppearance
                val controller = insetsController
                if (sourceAppearance != null && controller != null &&
                    (controller.systemBarsAppearance and appearanceMask) != (sourceAppearance and appearanceMask)) {
                    controller.setSystemBarsAppearance(sourceAppearance and appearanceMask, appearanceMask)
                }
                val matchParent = WindowManager.LayoutParams.MATCH_PARENT
                if (current.width != matchParent || current.height != matchParent) {
                    setLayout(matchParent, matchParent)
                }
            }
        }
        content()
    }
}

private fun activityWindow(context: Context): Window? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current.window
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return null
}
