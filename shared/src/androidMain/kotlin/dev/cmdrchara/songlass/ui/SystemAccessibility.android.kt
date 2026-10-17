package dev.cmdrchara.songlass.ui

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility

@Composable
actual fun rememberSystemAccessibility(): UiAccessibility {
    var reduceMotion by remember { mutableStateOf(ValueAnimator.getDurationScale() <= 0f) }
    DisposableEffect(Unit) {
        val listener = ValueAnimator.DurationScaleChangeListener { scale ->
            reduceMotion = scale <= 0f
        }
        ValueAnimator.registerDurationScaleChangeListener(listener)
        // Read again after registering so a change between composition and this effect is kept.
        reduceMotion = ValueAnimator.getDurationScale() <= 0f
        onDispose { ValueAnimator.unregisterDurationScaleChangeListener(listener) }
    }
    // Transparency reduction is controlled by Songlass on Android.
    return UiAccessibility(reduceMotion = reduceMotion)
}
