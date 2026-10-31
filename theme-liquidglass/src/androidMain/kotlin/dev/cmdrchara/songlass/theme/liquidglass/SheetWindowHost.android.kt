package dev.cmdrchara.songlass.theme.liquidglass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun SheetWindowHost(onDismiss: () -> Unit, navigationLightIcons: Boolean?, content: @Composable () -> Unit) {
    // Capture the caller's Activity before entering the dialog's own view hierarchy.
    val sourceWindow = activityWindow(LocalView.current.context)
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val syncSystemBars = remember(view, sourceWindow, navigationLightIcons) {
            Runnable {
                if (view.isAttachedToWindow) {
                    dialogWindow(view)?.configureSheetWindow(sourceWindow, navigationLightIcons)
                }
            }
        }
        DisposableEffect(view, syncSystemBars) {
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.removeCallbacks(syncSystemBars)
                    view.post(syncSystemBars)
                }

                override fun onViewDetachedFromWindow(view: View) {
                    view.removeCallbacks(syncSystemBars)
                }
            }
            // A layout follows the initial insets dispatch and also a keyboard resize.
            // Requeue one callback; do not replace Compose's own insets listener.
            val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                view.removeCallbacks(syncSystemBars)
                view.post(syncSystemBars)
            }
            view.addOnAttachStateChangeListener(listener)
            view.addOnLayoutChangeListener(layoutListener)
            onDispose {
                view.removeOnAttachStateChangeListener(listener)
                view.removeOnLayoutChangeListener(layoutListener)
                view.removeCallbacks(syncSystemBars)
            }
        }
        SideEffect {
            dialogWindow(view)?.configureSheetWindow(sourceWindow, navigationLightIcons)
            // Dialog composition can precede attachment; retry after its window setup.
            view.removeCallbacks(syncSystemBars)
            view.post(syncSystemBars)
        }
        content()
    }
}

private fun Window.configureSheetWindow(sourceWindow: Window?, navigationLightIcons: Boolean?) {
    // Compose owns dimming and motion, including the app Reduce Motion setting.
    val current = attributes
    if (current.windowAnimations != 0) setWindowAnimations(0)
    if (current.dimAmount != 0f) setDimAmount(0f)
    if ((current.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
    val matchParent = WindowManager.LayoutParams.MATCH_PARENT
    if (current.width != matchParent || current.height != matchParent) {
        setLayout(matchParent, matchParent)
    }
    configureSystemBars(sourceWindow, navigationLightIcons)
}

@Suppress("DEPRECATION")
private fun Window.configureSystemBars(sourceWindow: Window?, navigationLightIcons: Boolean?) {
    // These public flags are required for transparent bar colors to take effect on API 33.
    val translucent = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
    if ((attributes.flags and translucent) != 0) clearFlags(translucent)
    if ((attributes.flags and WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0) {
        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }
    // Compose paints the scrim and the themed surface beneath the bars. A second native
    // contrast scrim would change their colors independently of the selected app theme.
    if (statusBarColor != Color.TRANSPARENT) statusBarColor = Color.TRANSPARENT
    if (navigationBarColor != Color.TRANSPARENT) navigationBarColor = Color.TRANSPARENT
    if (navigationBarDividerColor != Color.TRANSPARENT) navigationBarDividerColor = Color.TRANSPARENT
    if (isStatusBarContrastEnforced) isStatusBarContrastEnforced = false
    if (isNavigationBarContrastEnforced) isNavigationBarContrastEnforced = false
    val appearanceMask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
    val controller = insetsController ?: return
    val inheritedAppearance = sourceWindow?.insetsController?.systemBarsAppearance ?: controller.systemBarsAppearance
    val navigationAppearance = when (navigationLightIcons) {
        true -> 0
        false -> WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        null -> inheritedAppearance and WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
    }
    val appearance = (inheritedAppearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS) or navigationAppearance
    if ((controller.systemBarsAppearance and appearanceMask) != appearance) {
        controller.setSystemBarsAppearance(appearance, appearanceMask)
    }
}

private fun dialogWindow(view: View): Window? {
    var current: View? = view
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent as? View
    }
    return null
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
