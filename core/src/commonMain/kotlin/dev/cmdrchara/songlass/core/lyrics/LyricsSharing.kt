package dev.cmdrchara.songlass.core.lyrics

/** A small, ordered excerpt; limits are about legibility, never silent truncation. */
object LyricsSharing {
    const val MAX_LINES = 4
    const val MAX_CHARACTERS = 240

    fun excerpt(lyrics: Lyrics, selected: Set<Int>): List<String> {
        val lines = selected.sorted().mapNotNull { lyrics.lines.getOrNull(it)?.text?.takeIf(String::isNotBlank) }
        return lines.takeIf { it.isNotEmpty() && it.size <= MAX_LINES && characterCount(it) <= MAX_CHARACTERS }.orEmpty()
    }

    fun toggle(lyrics: Lyrics, selected: Set<Int>, index: Int): Set<Int> {
        if (index in selected) return selected - index
        if (lyrics.lines.getOrNull(index)?.text.isNullOrBlank()) return selected
        val next = selected + index
        return if (excerpt(lyrics, next).isEmpty()) selected else next
    }

    private fun characterCount(lines: List<String>): Int = lines.sumOf { text ->
        var count = 0
        var offset = 0
        while (offset < text.length) {
            count++
            offset += if (text[offset].isHighSurrogate() && offset + 1 < text.length && text[offset + 1].isLowSurrogate()) 2 else 1
        }
        count
    }
}
