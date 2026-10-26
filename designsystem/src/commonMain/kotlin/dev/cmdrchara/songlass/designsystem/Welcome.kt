package dev.cmdrchara.songlass.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** One of the interface styles offered on the first step; [preview] is a small drawing of what it looks like. */
@Stable
data class WelcomeChoice(val label: String, val selected: Boolean, val onSelect: () -> Unit, val preview: @Composable (Modifier) -> Unit)

/**
 * One page of the first-run welcome. The screen owns the steps, the permission and the clock;
 * each look decides how a page is drawn, so the three styles are three different designs.
 */
@Stable
data class WelcomeModel(
    val step: Int,
    val stepCount: Int,
    val title: String,
    val body: String,
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val secondaryLabel: String? = null,
    val onSecondary: (() -> Unit)? = null,
    /** Offered on the first step only. */
    val choices: List<WelcomeChoice> = emptyList(),
    /** A live line, such as the number of songs found. */
    val status: String? = null,
    /** "Step 1 of 3", for screen readers. */
    val stepDescription: String = "",
    /** Real covers from the person's library, on the last page only; the looks show them in place of the mark. */
    val covers: List<Artwork> = emptyList(),
    /** Visible return action on every page after the first; independent of permission skipping. */
    val backLabel: String? = null,
    val onBack: (() -> Unit)? = null,
)

@Composable
fun Welcome(model: WelcomeModel) = LocalUiStyle.current.Welcome(model)

/**
 * The page content takes only the height it needs, so the illustration above it can take the rest.
 * Pages slide and fade into each other, in the direction of travel. A page that is leaving keeps the
 * content it had, so it does not change into the next one while it moves out. Reduced motion: a quick fade.
 */
@Composable
fun WelcomePages(model: WelcomeModel, modifier: Modifier = Modifier, content: @Composable (WelcomeModel) -> Unit) {
    val reduce = AppTheme.accessibility.reduceMotion
    val pages = remember { HashMap<Int, WelcomeModel>() }
    pages[model.step] = model
    AnimatedContent(
        targetState = model.step,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState >= initialState
            if (reduce) fadeIn(tween(100)) togetherWith fadeOut(tween(100))
            else (slideInHorizontally(tween(420, easing = Emphasized)) { w -> if (forward) w / 5 else -w / 5 } + fadeIn(tween(320, 80))) togetherWith
                (slideOutHorizontally(tween(260, easing = Emphasized)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(160)))
        },
        label = "welcomePages",
    ) { step ->
        Column(Modifier.fillMaxWidth()) { content(pages[step] ?: model) }
    }
}

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** 0..1, looping every few seconds; a still value with reduced motion. Read it while drawing, not while composing. */
@Composable
fun rememberWelcomeBeat(periodMs: Int = 2600): State<Float> {
    if (AppTheme.accessibility.reduceMotion) return remember { mutableStateOf(0f) }
    return rememberInfiniteTransition(label = "welcomeBeat").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
}

/** Fades and floats an element in, [index] steps after the first; instant with reduced motion. */
@Composable
fun Modifier.welcomeEnter(index: Int = 0): Modifier {
    val reduce = AppTheme.accessibility.reduceMotion
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(index * 80L)
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 320f))
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        scaleX = 0.86f + 0.14f * p
        scaleY = 0.86f + 0.14f * p
        translationY = (1f - p) * 28.dp.toPx()
    }
}
