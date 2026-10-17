@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UIAccessibilityReduceTransparencyStatusDidChangeNotification

@Composable
actual fun rememberSystemAccessibility(): UiAccessibility {
    var accessibility by remember { mutableStateOf(readSystemAccessibility()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(
            UIAccessibilityReduceMotionStatusDidChangeNotification,
            UIAccessibilityReduceTransparencyStatusDidChangeNotification,
        ).map { name ->
            center.addObserverForName(
                name = name,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { accessibility = readSystemAccessibility() }
        }
        // Read again after registering to include changes since the initial composition.
        accessibility = readSystemAccessibility()
        onDispose { observers.forEach { center.removeObserver(it) } }
    }
    return accessibility
}

private fun readSystemAccessibility() = UiAccessibility(
    reduceMotion = UIAccessibilityIsReduceMotionEnabled(),
    reduceTransparency = UIAccessibilityIsReduceTransparencyEnabled(),
)
