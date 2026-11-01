package dev.cmdrchara.songlass.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Read by the active word mask during drawing, shared with active-line selection. */
val LocalLyricsPlaybackPosition = staticCompositionLocalOf<(() -> Long)?> { null }

/** Independent reading zoom in font units; system accessibility scaling still applies normally. */
val LocalLyricsTextScale = staticCompositionLocalOf { 1f }

/** Display-only copy of the source word ranges; no source text or persistence lives here. */
@Immutable
data class LyricWordTiming(val start: Int, val end: Int, val startMs: Long, val endMs: Long?) {
    fun progressAt(positionMs: Long): Float = when {
        positionMs < startMs -> 0f
        endMs == null || endMs <= startMs || positionMs >= endMs -> 1f
        else -> ((positionMs - startMs).toDouble() / (endMs - startMs).toDouble()).toFloat()
    }
}

/** Pixel tokens for the fixed-size export, supplied by each look. */
@Immutable
data class LyricsCardStyle(
    val corner: Float = 76f,
    val coverCorner: Float = 32f,
    val verseCorner: Float = 48f,
    val verseInset: Float = 0f,
    val fontSize: Float = 66f,
    val accentMark: Boolean = false,
)

/** Reading rhythm and selection geometry belong to the chosen design language. */
@Immutable
data class LyricsPresentation(
    val fontSize: Float = 34f,
    val lineHeight: Float = 43f,
    val weight: Int = 700,
    val verticalPadding: Float = 14f,
    val selectionCorner: Float = 20f,
)
