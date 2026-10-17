package dev.cmdrchara.songlass.core.lyrics

/** Parses local text and LRC without inventing lyrics or treating metadata as song text. */
object LyricsParser {
    const val MAX_CHARACTERS = 128 * 1024
    const val MAX_LINES = 5_000

    private val timestamp = Regex("([0-9]+):([0-9]{2})(?:\\.([0-9]{1,3}))?")
    private val wordTag = Regex("[<\\[]([0-9]+:[0-9]{2}(?:\\.[0-9]{1,3})?)[>\\]]")
    private val metadataKeys = setOf(
        "ar", "al", "ti", "au", "lr", "length", "by", "offset", "re", "tool", "ve", "la", "id", "#",
    )

    /** Oversized input is rejected as a whole, so a truncated lyric is never silently presented. */
    fun parse(text: String, source: LyricsOrigin): Lyrics? {
        if (text.length > MAX_CHARACTERS || text.any { character ->
                (character.code < 32 && character != '\t' && character != '\r' && character != '\n') ||
                    character == '\u007F'
            }
        ) return null
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return null
        val timed = mutableListOf<LyricsLine>()
        val plain = mutableListOf<LyricsLine>()
        var offsetMs = 0L
        var lineCount = 0
        for (rawLine in normalized.lineSequence()) {
            if (++lineCount > MAX_LINES) return null
            val line = rawLine.trim()
            val times = mutableListOf<Long>()
            var contentStart = 0
            var metadata = false
            while (contentStart < line.length && line[contentStart] == '[') {
                val end = line.indexOf(']', contentStart + 1)
                if (end < 0) break
                val tag = line.substring(contentStart + 1, end)
                val time = parseTimestamp(tag)
                val key = tag.substringBefore(':').lowercase()
                if (time != null) {
                    times += time
                    if (times.size + timed.size > MAX_LINES) return null
                } else if (':' in tag && key in metadataKeys) {
                    metadata = true
                    if (key == "offset") {
                        tag.substringAfter(':').trim().toLongOrNull()?.let { offsetMs = it }
                    }
                } else {
                    break
                }
                contentStart = end + 1
                while (contentStart < line.length && line[contentStart].isWhitespace()) contentStart++
            }
            val content = line.substring(contentStart)
            if (times.isNotEmpty()) {
                // Enhanced LRC word times are not line boundaries. Keep the actual sung words.
                val words = wordTag.replace(content) { match ->
                    val paired = (match.value.first() == '<' && match.value.last() == '>') ||
                        (match.value.first() == '[' && match.value.last() == ']')
                    if (paired && parseTimestamp(match.groupValues[1]) != null) "" else match.value
                }.trim()
                times.forEach { timed += LyricsLine(it, words) }
            } else if (!metadata || content.isNotEmpty()) {
                plain += LyricsLine(null, content)
            }
        }
        if (timed.isNotEmpty()) {
            if (timed.none { it.text.isNotBlank() }) return null
            val lines = timed.map { line ->
                line.copy(timeMs = applyOffset(requireNotNull(line.timeMs), offsetMs))
            }.sortedBy { it.timeMs }
            return Lyrics(lines, source)
        }
        val lines = plain.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
        return lines.takeIf { it.isNotEmpty() }?.let { Lyrics(it, source) }
    }

    private fun parseTimestamp(value: String): Long? {
        val match = timestamp.matchEntire(value) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toInt()
        if (seconds > 59) return null
        val fraction = match.groupValues[3]
        val remainder = seconds * 1_000L + if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').toLong()
        if (minutes > (Long.MAX_VALUE - remainder) / 60_000) return null
        return minutes * 60_000 + remainder
    }

    private fun applyOffset(timeMs: Long, offsetMs: Long): Long = when {
        // LRC positive offsets make lyrics appear sooner; negative offsets delay them.
        offsetMs >= 0 -> (timeMs - offsetMs).coerceAtLeast(0)
        offsetMs == Long.MIN_VALUE || timeMs > Long.MAX_VALUE + offsetMs -> Long.MAX_VALUE
        else -> timeMs - offsetMs
    }
}
