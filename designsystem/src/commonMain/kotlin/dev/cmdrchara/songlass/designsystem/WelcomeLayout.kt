package dev.cmdrchara.songlass.designsystem

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keeps the welcome's footer at the bottom when its content fits. On short screens, the hero,
 * copy and actions form one scrollable page instead of squeezing text into the remaining height.
 * The viewport is bounded; only the page's natural content is measured without a height limit.
 */
@Composable
fun WelcomeLayout(
    step: Int,
    modifier: Modifier = Modifier,
    topSpacing: Dp = 0.dp,
    heroSpacing: Dp = 12.dp,
    minimumHeroHeight: Dp = 96.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    hero: @Composable BoxWithConstraintsScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    BoxWithConstraints(modifier) {
        val viewportHeight = constraints.maxHeight
        Layout(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll),
            content = {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center, content = hero)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = horizontalAlignment, content = content)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = horizontalAlignment, content = footer)
            },
        ) { measurables, constraints ->
            val natural = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val footerPlaceable = measurables[2].measure(natural)
            val contentPlaceable = measurables[1].measure(natural)
            val top = topSpacing.roundToPx()
            val gap = heroSpacing.roundToPx()
            val occupied = top + gap + contentPlaceable.height + footerPlaceable.height
            // Keep ordinary portrait spacing, but let overflowing copy and actions scroll.
            val minimumHero = minimumHeroHeight.roundToPx().coerceAtMost(viewportHeight)
            val heroHeight = (viewportHeight - occupied).coerceAtLeast(minimumHero)
            val heroPlaceable = measurables[0].measure(constraints.copy(minHeight = heroHeight, maxHeight = heroHeight))
            layout(constraints.maxWidth, occupied + heroHeight) {
                heroPlaceable.placeRelative(0, top)
                contentPlaceable.placeRelative(0, top + heroHeight + gap)
                footerPlaceable.placeRelative(0, top + heroHeight + gap + contentPlaceable.height)
            }
        }
    }
}
