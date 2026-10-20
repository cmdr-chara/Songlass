package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import dev.cmdrchara.songlass.designsystem.Artwork
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Starts decoding [artwork] at [sizePx] as soon as a finger touches the element (the cover of
 * the page it opens), so the page usually finds it in memory. The touch is not consumed; a
 * gesture that turns into a scroll cancels the request, so flinging over cards decodes nothing.
 */
fun Modifier.prefetchArtworkOnPress(artwork: Artwork?, sizePx: Int = LARGE_ARTWORK_PX): Modifier {
    val key = artwork?.imageKey ?: return this
    return this then PrefetchOnPressElement(key, sizeBucket(sizePx))
}

private data class PrefetchOnPressElement(val key: String, val bucket: Int) : ModifierNodeElement<PrefetchOnPressNode>() {
    override fun create() = PrefetchOnPressNode(key, bucket)
    override fun update(node: PrefetchOnPressNode) {
        node.key = key
        node.bucket = bucket
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "prefetchArtworkOnPress"
        properties["key"] = key
    }
}

private class PrefetchOnPressNode(var key: String, var bucket: Int) : DelegatingNode(), CompositionLocalConsumerModifierNode {
    init {
        delegate(SuspendingPointerInputModifierNode {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val loader = currentValueOf(LocalArtworkImageLoader)
                val job: Job? = if (loader != null && loader.cached(key, bucket) == null) loader.prefetch(key, bucket) else null
                if (job == null) return@awaitEachGesture
                val slop = viewConfiguration.touchSlop
                var moved = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: break
                    moved += change.positionChange().getDistance()
                    if (moved > slop) {
                        job.cancel()
                        break
                    }
                    if (!change.pressed) break // A tap: the decode keeps going in the loader's scope.
                }
            }
        })
    }
}

/**
 * Loads the covers of the rows just beyond the visible part of a list, mostly in the direction
 * it scrolls, so they are in memory when they appear. [keys] are the rows' lazy item keys in
 * order and [artworkAt] their covers; other items (headers, grids) are ignored. The window grows
 * with the scroll speed; a few requests run at a time, and those not started yet are dropped
 * when the visible range moves. Same size as the rows, within the loader's memory cache.
 */
@Composable
fun PrefetchListArtwork(
    state: LazyListState,
    keys: List<Any>,
    artworkAt: (Int) -> Artwork?,
    sizePx: Int = SMALL_ARTWORK_PX,
) {
    val loader = LocalArtworkImageLoader.current ?: return
    val bucket = sizeBucket(sizePx)
    val indexOfKey = remember(keys) { keys.withIndex().associate { (index, key) -> key to index } }
    val currentArtworkAt by rememberUpdatedState(artworkAt)
    LaunchedEffect(state, indexOfKey, loader, bucket) {
        val permits = Semaphore(PREFETCH_PARALLELISM)
        var previousFirst = -1
        var previousMark = TimeSource.Monotonic.markNow()
        var rowsPerSecond = 0f
        snapshotFlow {
            val visible = state.layoutInfo.visibleItemsInfo.mapNotNull { indexOfKey[it.key] }
            if (visible.isEmpty()) null else visible.min() to visible.max()
        }.distinctUntilChanged().collectLatest { range ->
            if (range == null) return@collectLatest
            val (first, last) = range
            val forward = previousFirst < 0 || first >= previousFirst
            if (previousFirst >= 0) {
                val seconds = previousMark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1) / 1000f
                // Smoothed: one range change per frame is noisy.
                rowsPerSecond = rowsPerSecond * 0.6f + abs(first - previousFirst) / seconds * 0.4f
            }
            previousFirst = first
            previousMark = TimeSource.Monotonic.markNow()
            val ahead = prefetchAhead(rowsPerSecond)
            val artworks = prefetchOrder(first, last, keys.size, ahead, PREFETCH_BEHIND, forward).mapNotNull { currentArtworkAt(it) }
            coroutineScope {
                // Launched in priority order; the fair semaphore keeps that order.
                for (key in prefetchKeys(artworks)) {
                    if (loader.cached(key, bucket) != null) continue
                    launch { permits.withPermit { if (loader.cached(key, bucket) == null) loader.load(key, bucket) } }
                }
            }
        }
    }
}

/**
 * Keys to load for [artworks] (nearest first): the fallback covers first, one per album, which
 * rows draw at once as stand-ins, then each row's own cover. Duplicates are removed.
 */
internal fun prefetchKeys(artworks: List<Artwork>): List<String> =
    (artworks.mapNotNull { it.fallbackImageKey } + artworks.mapNotNull { it.imageKey }).distinct()

/** Rows to load ahead: about [PREFETCH_SECONDS] of scrolling at [rowsPerSecond], bounded. */
internal fun prefetchAhead(rowsPerSecond: Float): Int =
    (rowsPerSecond * PREFETCH_SECONDS).roundToInt().coerceIn(PREFETCH_AHEAD, PREFETCH_MAX_AHEAD)

/**
 * Row indexes to load around the visible rows [first]..[last] of [size]: [ahead] rows in the
 * scroll direction first, nearest first, then [behind] rows on the other side.
 */
internal fun prefetchOrder(first: Int, last: Int, size: Int, ahead: Int, behind: Int, forward: Boolean): List<Int> {
    if (size <= 0 || first > last) return emptyList()
    val after = (last + 1..minOf(size - 1, last + if (forward) ahead else behind)).toList()
    val before = (first - 1 downTo maxOf(0, first - if (forward) behind else ahead)).toList()
    return if (forward) after + before else before + after
}

private const val PREFETCH_AHEAD = 10
private const val PREFETCH_MAX_AHEAD = 60
private const val PREFETCH_BEHIND = 3
private const val PREFETCH_SECONDS = 0.75f
/** Leaves decode slots in the loader for the rows on screen. */
private const val PREFETCH_PARALLELISM = 3
