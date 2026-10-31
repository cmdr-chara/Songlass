package dev.cmdrchara.songlass.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/** Physical window geometry in logical points/dp, never screen pixels or device dimensions. */
data class WindowRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val valid: Boolean get() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() && right > left && bottom > top
    val area: Double get() = width.toDouble() * height

    fun intersection(other: WindowRect): WindowRect? = WindowRect(
        maxOf(left, other.left), maxOf(top, other.top), minOf(right, other.right), minOf(bottom, other.bottom),
    ).takeIf { valid && other.valid && it.valid }

    fun relativeTo(viewport: WindowRect) = WindowRect(left - viewport.left, top - viewport.top, right - viewport.left, bottom - viewport.top)
}

data class WindowPoint(val x: Float, val y: Float)

enum class WindowBarEdge { Bottom, Leading, Trailing }
enum class WindowRegionKind { Occlusion, Division }
enum class WindowDivision { Vertical, Horizontal }

/** Native frames already include the system's clearance margins. */
data class WindowReservedRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val kind: WindowRegionKind = WindowRegionKind.Occlusion,
) {
    val bounds: WindowRect get() = WindowRect(left, top, right, bottom)
}

data class WindowPanePlan(
    val first: WindowRect,
    val second: WindowRect? = null,
    val division: WindowDivision? = null,
) {
    val paired: Boolean get() = second != null
}

data class WindowChromePlan(val pane: WindowRect, val content: WindowRect, val rail: WindowRect? = null)

/**
 * One window's current geometry. The empty value keeps the existing phone layout unchanged.
 * Only active reserved regions are supplied by the platform. Changing this data does not own
 * navigation, scroll, the queue, or playback and must never recreate their state.
 */
data class WindowLayout(
    val width: Float = 0f,
    val height: Float = 0f,
    val safeLeft: Float = 0f,
    val safeTop: Float = 0f,
    val safeRight: Float = 0f,
    val safeBottom: Float = 0f,
    val barEdge: WindowBarEdge = WindowBarEdge.Bottom,
    val regions: List<WindowReservedRegion> = emptyList(),
) {
    val specified: Boolean get() = width.isFinite() && height.isFinite() && width > 0f && height > 0f
    val bounds: WindowRect get() = WindowRect(0f, 0f, width, height)

    private fun inset(value: Float, limit: Float) = value.takeIf { it.isFinite() }?.coerceIn(0f, limit) ?: 0f

    val safeBounds: WindowRect
        get() = WindowRect(inset(safeLeft, width), inset(safeTop, height),
            width - inset(safeRight, width), height - inset(safeBottom, height))

    val hasReservedContent: Boolean
        get() = specified && regions.any { it.bounds.intersection(safeBounds) != null }

    /** Breakpoints describe usable player content, not a particular phone or display. */
    val requiresAdaptivePlayer: Boolean
        get() = specified && (barEdge != WindowBarEdge.Bottom || hasReservedContent ||
            (safeBounds.width >= 688f && safeBounds.height >= 280f && safeBounds.width >= safeBounds.height - 44f))

    /** Converts native window points through the actual view transform, including a morph scale. */
    fun inLocalCoordinates(localWidth: Float, localHeight: Float, toLocal: (Float, Float) -> WindowPoint): WindowLayout {
        fun convert(rect: WindowRect): WindowRect {
            val points = listOf(toLocal(rect.left, rect.top), toLocal(rect.right, rect.top),
                toLocal(rect.left, rect.bottom), toLocal(rect.right, rect.bottom))
            return WindowRect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
        }
        val safe = convert(safeBounds)
        return WindowLayout(localWidth, localHeight, safe.left.coerceAtLeast(0f), safe.top.coerceAtLeast(0f),
            (localWidth - safe.right).coerceAtLeast(0f), (localHeight - safe.bottom).coerceAtLeast(0f), barEdge,
            regions.map { region ->
                val bounds = convert(region.bounds)
                WindowReservedRegion(bounds.left, bounds.top, bounds.right, bounds.bottom, region.kind)
            })
    }

    /** Semantic bar edges become physical coordinates only after applying layout direction. */
    fun chrome(viewport: WindowRect, requestedRailWidth: Float = 72f, rtl: Boolean = false): WindowChromePlan? {
        val pane = panes(viewport, paired = false)?.first ?: return null
        if (barEdge == WindowBarEdge.Bottom) return WindowChromePlan(pane, pane)
        if (pane.width <= 1f || pane.height <= 1f) return null
        val railWidth = requestedRailWidth.takeIf { it.isFinite() }?.coerceIn(0f, pane.width / 3f)?.takeIf { it > 0f } ?: return null
        val margin = minOf(4f, pane.width / 12f)
        val verticalMargin = minOf(8f, pane.height / 4f)
        val reserve = minOf(railWidth + margin * 3f, pane.width - 1f)
        val left = (barEdge == WindowBarEdge.Leading) != rtl
        val railLeft = if (left) pane.left + margin else pane.right - margin - railWidth
        val rail = WindowRect(railLeft, pane.top + verticalMargin, railLeft + railWidth, pane.bottom - verticalMargin)
        val content = if (left) WindowRect(pane.left + reserve, pane.top, pane.right, pane.bottom)
            else WindowRect(pane.left, pane.top, pane.right - reserve, pane.bottom)
        return WindowChromePlan(pane, content, rail)
    }

    /** A view's own window-space bounds are required: sheets and player content have offsets. */
    fun panes(viewport: WindowRect, paired: Boolean, allowWidePair: Boolean = false): WindowPanePlan? {
        if (!specified || !viewport.valid) return null
        val safe = safeBounds.intersection(viewport) ?: return null
        val normalized = regions.take(MAX_REGIONS).mapNotNull { region ->
            region.bounds.intersection(safe)?.let { region.kind to it }
        }
        val divisions = normalized.filter { it.first == WindowRegionKind.Division }.map { it.second }
        val fold = divisions.maxByOrNull { it.area }
        val occlusions = normalized.filter { it.first == WindowRegionKind.Occlusion }.map { it.second } + divisions.filter { it != fold }
        val split = fold?.let { division ->
            if (division.height >= division.width) Triple(
                WindowRect(safe.left, safe.top, division.left, safe.bottom),
                WindowRect(division.right, safe.top, safe.right, safe.bottom), WindowDivision.Vertical,
            ) else Triple(
                WindowRect(safe.left, safe.top, safe.right, division.top),
                WindowRect(safe.left, division.bottom, safe.right, safe.bottom), WindowDivision.Horizontal,
            )
        } ?: if (allowWidePair && safe.width >= 640f && safe.height >= 280f && safe.width >= safe.height) {
            val middle = (safe.left + safe.right) / 2f
            Triple(WindowRect(safe.left, safe.top, middle - 12f, safe.bottom),
                WindowRect(middle + 12f, safe.top, safe.right, safe.bottom), WindowDivision.Vertical)
        } else null
        val first = clearOcclusions(split?.first ?: safe, occlusions)
        val second = split?.second?.let { clearOcclusions(it, occlusions) }
        if (paired && first != null && second != null) return WindowPanePlan(
            first.relativeTo(viewport), second.relativeTo(viewport), split?.third,
        )
        val available = listOfNotNull(first, second).maxByOrNull { it.area } ?: return null
        return WindowPanePlan(available.relativeTo(viewport))
    }

    /** Avoids cameras without adding their margins for a second time. */
    private fun clearOcclusions(initial: WindowRect, occlusions: List<WindowRect>): WindowRect? {
        if (!initial.valid) return null
        val blocked = occlusions.mapNotNull { it.intersection(initial) }
        if (blocked.isEmpty()) return initial
        // A maximal free rectangle ends at a window or obstruction edge. Enumerating the
        // bounded horizontal intervals avoids greedy dead ends and exponential partitioning.
        val edges = (listOf(initial.left, initial.right) + blocked.flatMap { listOf(it.left, it.right) }).distinct().sorted()
        var best: WindowRect? = null
        fun consider(left: Float, top: Float, right: Float, bottom: Float) {
            val candidate = WindowRect(left, top, right, bottom)
            if (candidate.valid && candidate.area > (best?.area ?: 0.0)) best = candidate
        }
        for (start in 0 until edges.lastIndex) for (end in start + 1..edges.lastIndex) {
            val left = edges[start]
            val right = edges[end]
            val spans = blocked.filter { it.left < right && it.right > left }.sortedBy { it.top }
            var top = initial.top
            for (span in spans) {
                consider(left, top, right, span.top)
                top = maxOf(top, span.bottom)
            }
            consider(left, top, right, initial.bottom)
        }
        return best
    }

    companion object {
        const val MAX_REGIONS = 32

        /** Small versionless numeric wire format from the Swift UIKit overlay, not user data. */
        fun decodeRegions(encoded: String): List<WindowReservedRegion> = encoded.take(16_384).split(';').take(MAX_REGIONS).mapNotNull { item ->
            val fields = item.split(',')
            if (fields.size != 5) return@mapNotNull null
            val kind = when (fields[0]) { "o" -> WindowRegionKind.Occlusion; "d" -> WindowRegionKind.Division; else -> return@mapNotNull null }
            val numbers = fields.drop(1).map { it.toFloatOrNull() ?: return@mapNotNull null }
            WindowReservedRegion(numbers[0], numbers[1], numbers[2], numbers[3], kind).takeIf { it.bounds.valid }
        }
    }
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout() }
