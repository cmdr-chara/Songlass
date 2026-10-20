package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track

enum class LyricsOrigin { Embedded, File, LrcLib, Amll }

/** UTF-16 text range and original media timestamps, which may describe a word or syllable. */
data class LyricsWord(val start: Int, val end: Int, val startMs: Long, val endMs: Long?) {
    fun progressAt(positionMs: Long): Float = when {
        positionMs < startMs -> 0f
        endMs == null || endMs <= startMs || positionMs >= endMs -> 1f
        else -> ((positionMs - startMs).toDouble() / (endMs - startMs).toDouble()).toFloat()
    }
}

data class LyricsLine(val timeMs: Long?, val text: String, val words: List<LyricsWord> = emptyList())

/** Timed lines are ordered by timestamp; untimed lyrics retain their paragraph breaks. */
data class Lyrics(
    val lines: List<LyricsLine>,
    val source: LyricsOrigin,
    val instrumental: Boolean = false,
) {
    val hasWordTiming: Boolean = lines.any { it.words.isNotEmpty() }
    val hasEstimatedTiming: Boolean = lines.any { it.timeMs != null && it.text.isNotBlank() && it.words.isEmpty() }

    val isSynced: Boolean = lines.isNotEmpty() && lines.all { it.timeMs != null }

    /** Returns the last line at or before playback, including blank lines marking a vocal gap. */
    fun activeLineAt(positionMs: Long): Int {
        if (!isSynced) return -1
        var low = 0
        var high = lines.lastIndex
        var active = -1
        while (low <= high) {
            val middle = low + (high - low) / 2
            val time = lines[middle].timeMs ?: return -1
            if (time <= positionMs) {
                active = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return active
    }

    /**
     * Prepares the next lyric shortly before it starts, after the current estimated reveal finishes.
     * This is only a scroll target: [activeLineAt] still determines which lyric is being sung.
     */
    fun scrollLineAt(positionMs: Long, durationMs: Long): Int {
        val active = activeLineAt(positionMs)
        if (active < 0 || active == lines.lastIndex) return active
        val nextTime = lines[active + 1].timeMs ?: return active
        if (nextTime <= positionMs) return active
        val previewStart = if (nextTime < Long.MIN_VALUE + 800) Long.MIN_VALUE else nextTime - 800
        if (positionMs < previewStart || lineProgressAt(active, positionMs, durationMs) < 1f) return active

        // The last duplicate is the same target that becomes active at the next timestamp.
        val next = activeLineAt(nextTime)
        for (index in active..next) {
            // Blank gaps and symbol-only markers (music notes, dots, etc.) must not pull the list.
            if (lines[index].text.none { it.isLetterOrDigit() }) return active
        }
        return next
    }

    /**
     * Uses real word intervals when present. Otherwise estimates the line's reveal, finishing
     * before the next distinct timestamp,
     * with a small lead and a phrase-length cap so instrumental gaps do not stretch the reveal.
     * The final line uses track duration when available, otherwise its phrase-length estimate.
     */
    fun lineProgressAt(index: Int, positionMs: Long, durationMs: Long): Float {
        if (!isSynced || index !in lines.indices) return 0f
        val start = lines[index].timeMs ?: return 0f
        if (positionMs < start) return 0f
        val timedWords = lines[index].words
        if (timedWords.isNotEmpty()) {
            val total = timedWords.sumOf { it.end - it.start }.coerceAtLeast(1)
            return (timedWords.sumOf { (it.end - it.start) * it.progressAt(positionMs).toDouble() } / total).toFloat()
        }

        var low = index + 1
        var high = lines.size
        while (low < high) {
            val middle = low + (high - low) / 2
            val time = lines[middle].timeMs ?: return 0f
            if (time <= start) low = middle + 1 else high = middle
        }
        val end = if (low < lines.size) lines[low].timeMs ?: return 0f else durationMs

        // Subtract before converting for millisecond precision near Long.MAX_VALUE. A range
        // crossing zero can exceed Long.MAX_VALUE, so only that case uses floating subtraction.
        fun distanceFromStart(time: Long): Double =
            if (start < 0 && time >= 0) time.toDouble() - start.toDouble()
            else (time - start).toDouble()
        var words = 0
        var characters = 0
        var inWord = false
        for (character in lines[index].text) {
            if (character.isWhitespace()) {
                inWord = false
            } else {
                if (!inWord) words++
                characters++
                inWord = true
            }
        }
        // Unspaced languages need a character estimate rather than counting a verse as one word.
        val phraseCapMs = (if (words <= 1) characters * 180.0 else words * 550.0).coerceAtLeast(2_000.0)
        val windowMs = (if (end > start) minOf(distanceFromStart(end) * 0.70, phraseCapMs) else phraseCapMs)
            .coerceAtLeast(1.0)
        return ((distanceFromStart(positionMs) + 150.0) / windowMs).toFloat().coerceIn(0f, 1f)
    }
}

fun interface LyricsSource {
    /** Null means this source has no lyrics; failures should be thrown, not reported as absence. */
    suspend fun load(track: Track): Lyrics?

    companion object {
        val Empty = LyricsSource { null }
    }
}

sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics) : LyricsResult
    data object NotFound : LyricsResult
    data object Failed : LyricsResult
}
