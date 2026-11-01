package dev.cmdrchara.songlass.core.lyrics

import kotlin.math.roundToInt

/** Display-only preferences. Positive offsets delay lyrics without changing the source or audio. */
data class LyricsPresentation(
    val offsetMs: Int = 0,
    val textSizePercent: Int = 100,
    val keepScreenOn: Boolean = false,
) {
    /** Negative display time is intentional: a delayed first line must not light up at media zero. */
    fun displayPosition(mediaPositionMs: Long): Long = shift(mediaPositionMs, -normalizeOffset(offsetMs))

    /** Inverse of the display clock, bounded to the available media when a lyric is tapped. */
    fun seekPosition(lyricsPositionMs: Long, durationMs: Long): Long =
        shift(lyricsPositionMs, normalizeOffset(offsetMs)).coerceAtLeast(0L)
            .let { if (durationMs > 0L) it.coerceAtMost(durationMs) else it }

    companion object {
        const val OFFSET_LIMIT_MS = 5_000
        const val OFFSET_STEP_MS = 50
        const val MIN_TEXT_SIZE = 80
        const val MAX_TEXT_SIZE = 160
        const val TEXT_SIZE_STEP = 5

        fun normalizeOffset(value: Int): Int =
            (value.coerceIn(-OFFSET_LIMIT_MS, OFFSET_LIMIT_MS).toDouble() / OFFSET_STEP_MS).roundToInt() * OFFSET_STEP_MS

        fun normalizeTextSize(value: Int): Int =
            (value.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE).toDouble() / TEXT_SIZE_STEP).roundToInt() * TEXT_SIZE_STEP

        fun offsetFromSlider(position: Float): Int = normalizeOffset(
            if (position.isFinite()) ((position.coerceIn(0f, 1f) * 2 - 1) * OFFSET_LIMIT_MS).roundToInt() else 0,
        )

        fun textSizeFromSlider(position: Float): Int = normalizeTextSize(
            if (position.isFinite()) (MIN_TEXT_SIZE + position.coerceIn(0f, 1f) * (MAX_TEXT_SIZE - MIN_TEXT_SIZE)).roundToInt() else 100,
        )

        private fun shift(value: Long, delta: Int): Long = when {
            delta > 0 && value > Long.MAX_VALUE - delta -> Long.MAX_VALUE
            delta < 0 && value < Long.MIN_VALUE - delta -> Long.MIN_VALUE
            else -> value + delta
        }
    }
}
