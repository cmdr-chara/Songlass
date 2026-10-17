package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import dev.cmdrchara.songlass.designsystem.NavigationDirection
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

@Composable
internal fun <T> LiquidGlassScreenTransition(
    targetState: T,
    direction: (T, T) -> NavigationDirection,
    modifier: Modifier,
    content: @Composable (T) -> Unit,
) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(
        targetState = targetState,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            val navigation = direction(initialState, targetState)
            val duration = if (reduceMotion) LiquidGlassMotion.ReducedMillis else LiquidGlassMotion.FadeMillis
            val fades = fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            if (reduceMotion || navigation == NavigationDirection.Crossfade) {
                fades.using(null)
            } else {
                val sign = if (navigation == NavigationDirection.Forward) forward else -forward
                val motion = tween<androidx.compose.ui.unit.IntOffset>(
                    LiquidGlassMotion.NavigationMillis, easing = LiquidGlassMotion.Emphasized,
                )
                // A modest shared translation establishes direction without moving the glass bars.
                ((slideInHorizontally(motion) { sign * it / 5 } + fadeIn(tween(LiquidGlassMotion.FadeMillis))) togetherWith
                    (slideOutHorizontally(motion) { -sign * it / 5 } + fadeOut(tween(LiquidGlassMotion.FadeMillis))))
                    .using(null)
            }
        },
        label = "screen destination",
    ) { destination ->
        // Outgoing pages remain visible, but must not expose duplicate actions to touch or readers.
        val active = destination == targetState
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { !active }.then(
                if (active) Modifier else Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
            ),
        ) {
            content(destination)
        }
    }
}
