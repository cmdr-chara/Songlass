package dev.cmdrchara.songlass.core.lyrics

/** Parses local text, LRC and TTML without inventing lyrics or treating metadata as song text. */
object LyricsParser {
    const val MAX_CHARACTERS = 128 * 1024
    const val MAX_LINES = 5_000
    const val MAX_WORD_ENTRIES = 20_000

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
        val markup = normalized.trimStart()
        if (markup.startsWith("<") && markup.getOrNull(1)?.let { it.isLetter() || it == '_' || it == '?' || it == '!' || it == '/' } == true)
            return TtmlParser.parse(markup, source)
        val timed = mutableListOf<LyricsLine>()
        val plain = mutableListOf<LyricsLine>()
        var offsetMs = 0L
        var lineCount = 0
        var wordCount = 0
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
                val parsed = parseWords(content, times.first())
                val expansion = parsed.second.size.toLong() * times.size
                if (expansion > MAX_WORD_ENTRIES - wordCount) return null
                wordCount += expansion.toInt()
                times.forEach { time ->
                    val words = parsed.second.map { word ->
                        word.copy(startMs = relocate(word.startMs, times.first(), time),
                            endMs = word.endMs?.let { relocate(it, times.first(), time) })
                    }
                    timed += LyricsLine(time, parsed.first, words)
                }
            } else if (!metadata || content.isNotEmpty()) {
                plain += LyricsLine(null, content)
            }
        }
        if (timed.isNotEmpty()) {
            if (timed.none { it.text.isNotBlank() }) return null
            val lines = timed.map { line ->
                line.copy(
                    timeMs = applyOffset(requireNotNull(line.timeMs), offsetMs),
                    words = line.words.map { word -> word.copy(
                        startMs = applyOffset(word.startMs, offsetMs),
                        endMs = word.endMs?.let { applyOffset(it, offsetMs) },
                    ) },
                )
            }.sortedBy { it.timeMs }
            // A word crossing the next distinct lyric is inconsistent: keep readable line timing.
            var nextTime: Long? = null
            var groupTime: Long? = null
            val checked = lines.asReversed().map { line ->
                if (line.timeMs != groupTime) { nextTime = groupTime; groupTime = line.timeMs }
                if (nextTime != null && line.words.any { it.startMs >= nextTime!! || (it.endMs ?: it.startMs) > nextTime!! })
                    line.copy(words = emptyList()) else line
            }.asReversed()
            return Lyrics(checked, source)
        }
        val lines = plain.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
        return lines.takeIf { it.isNotEmpty() }?.let { Lyrics(it, source) }
    }

    /** Invalid word timing never makes the text disappear or creates fabricated timestamps. */
    private fun parseWords(content: String, lineTime: Long): Pair<String, List<LyricsWord>> {
        val text = StringBuilder()
        val words = mutableListOf<LyricsWord>()
        var cursor = 0
        var anchor = lineTime
        var valid = true
        var tagged = false
        for (match in wordTag.findAll(content)) {
            val paired = (match.value.first() == '<' && match.value.last() == '>') ||
                (match.value.first() == '[' && match.value.last() == ']')
            val time = if (paired) parseTimestamp(match.groupValues[1]) else null
            if (time == null) continue
            tagged = true
            val piece = content.substring(cursor, match.range.first)
            val start = text.length
            text.append(piece)
            if (piece.isNotBlank()) words += LyricsWord(start, text.length, anchor, time)
            if (time < anchor) valid = false
            anchor = time
            cursor = match.range.last + 1
        }
        val start = text.length
        val tail = content.substring(cursor)
        text.append(tail)
        if (tail.isNotBlank()) words += LyricsWord(start, text.length, anchor, null)
        val raw = text.toString()
        val left = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val clean = raw.trim()
        val ranges = if (!tagged || !valid || words.size > 2_048) emptyList() else words.mapNotNull { word ->
            val from = maxOf(word.start, left) - left
            val to = minOf(word.end, left + clean.length) - left
            if (from >= to) null else word.copy(start = from, end = to)
        }
        return clean to ranges
    }

    private fun relocate(time: Long, from: Long, to: Long): Long {
        val delta = (time - from).coerceAtLeast(0)
        return if (to > Long.MAX_VALUE - delta) Long.MAX_VALUE else to + delta
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
