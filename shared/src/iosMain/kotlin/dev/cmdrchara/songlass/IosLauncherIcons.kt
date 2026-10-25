@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass

import dev.cmdrchara.songlass.core.settings.AppIconSelection
import dev.cmdrchara.songlass.core.settings.LauncherIconVariant
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Process-lived bridge. All mutable state and UIKit requests are confined to the main queue. */
internal class IosLauncherIcons {
    private var selection: AppIconSelection? = null
    private var systemDark = false
    private var appearanceKnown = false
    private var inFlight = false
    private var failedVariant: LauncherIconVariant? = null
    private var desiredVariant: LauncherIconVariant? = null
    private var observing = false

    fun select(value: AppIconSelection) = onMain {
        selection = value
        refresh()
    }

    fun updateSystemAppearance(dark: Boolean) = onMain {
        systemDark = dark
        appearanceKnown = true
        if (!observing) {
            observing = true
            // This bridge lives as long as the process, as does its observer.
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { refresh() }
        }
        refresh()
    }

    private fun refresh() {
        if (!appearanceKnown) return
        val variant = selection?.resolve(systemDark) ?: return
        if (desiredVariant != variant) {
            desiredVariant = variant
            failedVariant = null
        }
        val application = UIApplication.sharedApplication
        if (inFlight || failedVariant == variant || !application.supportsAlternateIcons ||
            application.applicationState != UIApplicationState.UIApplicationStateActive
        ) return
        val name = variant.alternateIconName()
        if (application.alternateIconName == name) return
        inFlight = true
        application.setAlternateIconName(name) { error ->
            // UIKit explicitly does not guarantee its completion queue.
            onMain {
                inFlight = false
                if (error != null) {
                    if (desiredVariant == variant) failedVariant = variant
                    // Avoid localized error text, which can contain user or device details.
                    println("Songlass: alternate app icon update failed (code=${error.code}).")
                }
                // Only a newer choice may follow a failed request; the failed choice is blocked.
                refresh()
            }
        }
    }
}

private fun LauncherIconVariant.alternateIconName(): String? = when (this) {
    LauncherIconVariant.Violet -> null
    LauncherIconVariant.Night -> "AppIconNight"
    LauncherIconVariant.LiquidLight -> "AppIconLiquidLight"
    LauncherIconVariant.LiquidDark -> "AppIconLiquidDark"
    LauncherIconVariant.MaterialLight -> "AppIconMaterialLight"
    LauncherIconVariant.MaterialDark -> "AppIconMaterialDark"
    LauncherIconVariant.MiuixLight -> "AppIconMiuixLight"
    LauncherIconVariant.MiuixDark -> "AppIconMiuixDark"
}

private fun onMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}
