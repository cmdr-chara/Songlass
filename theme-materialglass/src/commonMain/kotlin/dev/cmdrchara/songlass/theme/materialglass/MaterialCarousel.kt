package dev.cmdrchara.songlass.theme.materialglass

import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.indication
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.ShelfItem
import dev.cmdrchara.songlass.designsystem.ShelfStyle
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun MaterialArtworkShelf(items: List<ShelfItem>, style: ShelfStyle, itemSize: Dp, modifier: Modifier) {
    if (style == ShelfStyle.Pins) return ShapedPins(items, itemSize, modifier)
    // Actions (New playlist…) are tonal buttons above the carousel, so covers keep the large slots.
    val actions = items.filter { it.icon != null }
    val covers = items.filter { it.icon == null }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { TonalButton(it) }
            }
        }
        if (covers.isNotEmpty()) MultiBrowseCarousel(covers, itemSize * 1.2f, Modifier)
    }
}

/** Material filled tonal button: icon and label on a pill in the secondary container. */
@Composable
private fun TonalButton(item: ShelfItem) {
    val palette = currentPalette()
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(palette.primaryContainer)
            .prefetchArtworkOnPress(item.artwork)
            .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = item.onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { AppIcon(it, contentDescription = null, size = 18.dp, tint = palette.onPrimaryContainer) }
        Spacer(Modifier.width(8.dp))
        AppText(item.title, AppTheme.typography.footnote.copy(fontWeight = FontWeight.Bold), color = palette.onPrimaryContainer, maxLines = 1)
    }
}

/** Pins as covers cut into expressive shapes, each a different one, with the name below. */
@Composable
private fun ShapedPins(items: List<ShelfItem>, itemSize: Dp, modifier: Modifier) {
    if (items.size == 1) {
        val item = items.single()
        val palette = currentPalette()
        Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(MaterialShapes.LargeIncreased))
                .background(palette.container)
                .prefetchArtworkOnPress(item.artwork)
                .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = item.onClick)
                .heightIn(min = 88.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(64.dp).clip(if (item.circular) CircleShape else RoundedCornerShape(MaterialShapes.Medium))
                .background(palette.primaryContainer), contentAlignment = Alignment.Center) {
                val artwork = item.artwork
                if (artwork != null) AlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
                else item.icon?.let { AppIcon(it, null, size = 24.dp, tint = palette.onPrimaryContainer) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(item.title, AppTheme.typography.headline, color = palette.label, maxLines = 2)
                item.subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = palette.secondaryLabel, maxLines = 2) }
            }
        }
        return
    }
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            val shape = if (item.circular) ExpressiveShapes.Cookie else ExpressiveShapes.sequence[index % ExpressiveShapes.sequence.size]
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier.width(itemSize).prefetchArtworkOnPress(item.artwork).clickable(interaction, indication = null, role = Role.Button, onClick = item.onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The ripple follows the shape, not the whole tile.
                Box(Modifier.size(itemSize).clip(shape).background(currentPalette().container).indication(interaction, MaterialRipple)) {
                    item.artwork?.let { AlbumArt(it, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.height(8.dp))
                AppText(item.title, AppTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, textAlign = TextAlign.Center)
                item.subtitle?.let {
                    AppText(it, AppTheme.typography.caption1, color = AppTheme.colors.secondaryLabel,
                        maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * Material 3 multi-browse carousel: large items, then a medium and a small one at the end that
 * grow as they scroll in, while the item leaving at the start shrinks away. Items are masks over
 * a fixed-size cover (the cover never stretches), with the title on the cover fading as the mask
 * narrows. Flings settle on an item.
 */
@Composable
private fun MultiBrowseCarousel(items: List<ShelfItem>, large: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val geometry = remember(constraints.maxWidth, large, density) {
            with(density) { CarouselGeometry.of(constraints.maxWidth.toFloat(), large.toPx(), SmallItem.toPx(), Gap.toPx()) }
        }
        var scroll by remember { mutableFloatStateOf(0f) }
        val maxScroll = (items.size - geometry.largeCount).coerceAtLeast(0) * geometry.unit
        if (scroll > maxScroll) scroll = maxScroll
        val scrollState = rememberScrollableState { delta ->
            val old = scroll
            scroll = (scroll - delta).coerceIn(0f, maxScroll)
            old - scroll
        }
        val fling = remember(geometry, maxScroll, density) { SnapFling(geometry.unit, { scroll }, maxScroll, density) }
        Layout(
            content = {
                items.forEach { item -> CarouselItem(item, geometry.large) { scroll } }
            },
            modifier = Modifier
                // Items leaving at the start shrink against the edge, never into the page margin.
                .clipToBounds()
                .scrollable(scrollState, Orientation.Horizontal, flingBehavior = fling)
                .semantics {
                    horizontalScrollAxisRange = ScrollAxisRange({ scroll }, { maxScroll })
                    scrollBy { x, _ -> scope.launch { scrollState.scrollBy(-x) }; true }
                },
        ) { measurables, constraints ->
            val height = geometry.large.roundToInt()
            val placements = measurables.mapIndexed { index, measurable ->
                val slot = geometry.slot(index * geometry.unit - scroll) ?: return@mapIndexed null
                val width = slot.width.roundToInt().coerceAtLeast(1)
                measurable.measure(Constraints.fixed(width, height)) to slot.x.roundToInt()
            }
            layout(constraints.maxWidth, height) {
                placements.forEach { placed -> placed?.let { (placeable, x) -> placeable.place(x, 0) } }
            }
        }
    }
}

private val SmallItem = 40.dp
private val Gap = 8.dp
private val ItemShape = RoundedCornerShape(28.dp)

/** One item: a mask the width the carousel gives it, over a cover the size of a large item. */
@Composable
private fun CarouselItem(item: ShelfItem, largePx: Float, scroll: () -> Float) {
    val palette = currentPalette()
    val sizeDp = with(LocalDensity.current) { largePx.toDp() }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(ItemShape)
            .background(if (item.icon != null) palette.primaryContainer else palette.container)
            .prefetchArtworkOnPress(item.artwork)
            .clickable(interaction, indication = MaterialRipple, role = Role.Button, onClick = item.onClick),
        contentAlignment = Alignment.Center,
    ) {
        val artwork = item.artwork
        if (item.icon == null && artwork != null) {
            AlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.requiredSize(sizeDp))
        } else if (item.icon != null) {
            AppIcon(item.icon!!, contentDescription = null, size = 32.dp, tint = palette.onPrimaryContainer)
        }
        // The title lives on the cover and fades as the mask narrows.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = ((size.width / largePx - 0.55f) / 0.45f).coerceIn(0f, 1f)
            },
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(Modifier.fillMaxWidth()
                .then(if (item.icon == null) Modifier.materialArtworkCaptionScrim() else Modifier)
                .padding(start = 14.dp, end = 14.dp, top = MaterialArtworkCaptionFadeHeight, bottom = 12.dp)) {
                val ink = if (item.icon != null) palette.onPrimaryContainer else Color.White
                AppText(item.title, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Bold), color = ink, maxLines = 1)
                item.subtitle?.let { AppText(it, AppTheme.typography.caption1, color = ink.copy(alpha = 0.82f), maxLines = 1) }
            }
        }
    }
}

/** Where an item sits ([x]) and how wide its mask is, for a position along the strip. */
private class Slot(val x: Float, val width: Float)

/**
 * Keylines of the multi-browse layout. An item's "position" is where it would start if every
 * item were large; it maps piecewise linearly onto (x, width): shrinking off the start, large
 * through the first [largeCount] places, then one medium and one small at the end.
 */
private class CarouselGeometry(val large: Float, val largeCount: Int, private val keys: List<Triple<Float, Float, Float>>, val unit: Float) {
    fun slot(position: Float): Slot? {
        if (position < keys.first().first || position > keys.last().first) return null
        for (i in 0 until keys.lastIndex) {
            val (p0, x0, w0) = keys[i]
            val (p1, x1, w1) = keys[i + 1]
            if (position in p0..p1 && p1 > p0) {
                val t = (position - p0) / (p1 - p0)
                return Slot(x0 + (x1 - x0) * t, w0 + (w1 - w0) * t)
            }
        }
        return null
    }

    companion object {
        fun of(width: Float, wantedLarge: Float, small: Float, gap: Float): CarouselGeometry {
            val large = wantedLarge.coerceAtMost(width - small - gap * 2)
            val unit = large + gap
            var n = floor((width - small - gap) / unit).toInt().coerceAtLeast(1)
            var medium = width - n * unit - small - gap
            if (medium < small * 1.5f && n > 1) {
                n -= 1
                medium = width - n * unit - small - gap
            }
            medium = medium.coerceIn(small, large)
            val end = n * unit
            val keys = buildList {
                add(Triple(-unit, -small - gap, small))
                add(Triple(0f, 0f, large))
                if (n > 1) add(Triple((n - 1) * unit, (n - 1) * unit, large))
                add(Triple(end, end, medium))
                add(Triple(end + unit, end + medium + gap, small))
                add(Triple(end + unit * 2, end + medium + gap + small + gap, small))
            }
            return CarouselGeometry(large, n, keys, unit)
        }
    }
}

/** Fling that settles with an item at the start, keeping the fling's momentum. */
private class SnapFling(
    private val unit: Float,
    private val current: () -> Float,
    private val maxScroll: Float,
    density: Density,
) : FlingBehavior {
    private val decay = splineBasedDecay<Float>(density)

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = current()
        val projected = start - decay.calculateTargetValue(0f, initialVelocity)
        val target = ((projected / unit).roundToInt() * unit).coerceIn(0f, maxScroll)
        var last = start
        AnimationState(start, -initialVelocity).animateTo(target, spring(dampingRatio = 0.9f, stiffness = 260f)) {
            scrollBy(-(value - last))
            last = value
        }
        return 0f
    }
}
