package dev.cmdrchara.songlass.core.lyrics

import kotlin.math.roundToLong

/** Converts the supported TTML lyric subset to the same model used by Enhanced LRC. */
internal object TtmlParser {
    fun parse(text: String, source: LyricsOrigin): Lyrics? = try {
        val root = TtmlXml(text).read()
        ttmlCheck(root.name.local == "tt" && root.name.namespace in setOf(TTML_NS, ""))
        Document(root).read(source)
    } catch (_: InvalidTtml) { null }

    private data class Interval(val begin: Long, val end: Long?, val timed: Boolean) {
        val active get() = end == null || end > begin
    }
    private data class Cue(val line: LyricsLine, val end: Long?, val timed: Boolean)

    private class Document(private val root: TtmlElement) {
        private val namespace = root.name.namespace
        // AMLL's experimental dialect explicitly marks song-absolute timing at the root.
        // Bare seconds are accepted only for this marker, never inferred from numeric values.
        private val internalApple = namespace == TTML_NS &&
            root.attribute("timing", APPLE_INTERNAL_LYRICS_NS) in setOf("Word", "Line")
        private val time = TimeExpressions(root, bareSeconds = internalApple)
        // Preserve the existing documented iTunes extension dialect independently.
        private val apple = internalApple || hasAppleExtension(root)
        private val cues = mutableListOf<Cue>()
        private var wordCount = 0

        fun read(source: LyricsOrigin): Lyrics? {
            val bodies = root.children.filterIsInstance<TtmlElement>().filter { it.isTag("body") }
            ttmlCheck(bodies.size == 1)
            val preserve = when (root.attribute("space", XML_NS)) {
                null, "default" -> false
                "preserve" -> true
                else -> throw InvalidTtml()
            }
            visit(bodies.single(), Interval(0, null, false), preserve, base = 0)
            if (cues.none { it.line.text.isNotBlank() }) return null
            if (cues.none { it.timed }) return Lyrics(cues.map { it.line.copy(timeMs = null) }, source)
            val sorted = cues.sortedBy { it.line.timeMs }
            val result = mutableListOf<LyricsLine>()
            var coveredUntil: Long? = null
            for ((index, cue) in sorted.withIndex()) {
                result += cue.line
                coveredUntil = if (coveredUntil == null || coveredUntil <= cue.line.timeMs!!) cue.end
                    else cue.end?.let { maxOf(coveredUntil, it) }
                val next = sorted.getOrNull(index + 1)?.line?.timeMs
                // Explicit ends mark vocal gaps, including the last line; no fabricated word end.
                coveredUntil?.let { end ->
                    if (next == null || end < next) result += LyricsLine(end, "")
                }
                ttmlCheck(result.size <= LyricsParser.MAX_LINES)
            }
            return Lyrics(result, source)
        }

        private fun visit(node: TtmlElement, parent: Interval, preserve: Boolean, base: Long): Long? {
            if (node.name.namespace != namespace) return parent.end
            ttmlCheck(node.name.local in setOf("body", "div", "p", "span", "br"))
            val interval = interval(node, parent, base)
            if (!interval.active) return interval.end
            val keep = when (node.attribute("space", XML_NS)) {
                null -> preserve
                "preserve" -> true
                "default" -> false
                else -> throw InvalidTtml()
            }
            if (node.isTag("p")) {
                ttmlCheck(cues.size < LyricsParser.MAX_LINES)
                val builder = LineBuilder()
                collect(node, interval, keep, null, builder)
                wordCount += builder.words.size
                ttmlCheck(wordCount <= LyricsParser.MAX_WORD_ENTRIES)
                cues += Cue(LyricsLine(interval.begin, builder.text.toString(), builder.words), interval.end,
                    interval.timed || builder.words.isNotEmpty())
                return interval.end
            }
            val sequential = sequential(node)
            var cursor = interval.begin
            var sequentialEnd: Long? = interval.begin
            val children = node.children.filterIsInstance<TtmlElement>()
                .filter { it.name.namespace == namespace && !it.isTag("metadata") }
            for ((index, child) in children.withIndex()) {
                ttmlCheck(child.isTag("div") || child.isTag("p"))
                val end = visit(child, interval, keep, if (sequential) cursor else interval.begin)
                if (sequential) {
                    ttmlCheck(end != null || index == children.lastIndex)
                    sequentialEnd = end
                    if (end != null) cursor = end
                }
            }
            return interval.end ?: if (sequential) sequentialEnd else null
        }

        private fun collect(node: TtmlElement, parent: Interval, preserve: Boolean, word: Interval?, builder: LineBuilder) {
            val sequential = sequential(node)
            var cursor = parent.begin
            for (child in node.children) when (child) {
                is TtmlText -> builder.append(child.value, preserve, word)
                is TtmlElement -> {
                    if (child.name.namespace != namespace || child.isTag("metadata")) continue
                    if (internalApple && child.attribute("role", TTML_METADATA_NS)?.trim()?.split(Regex("\\s+"))
                            ?.any { it == "x-translation" || it == "x-roman" } == true) continue
                    ttmlCheck(child.isTag("span") || child.isTag("br"))
                    val interval = interval(child, parent, if (sequential) cursor else parent.begin)
                    val keep = when (child.attribute("space", XML_NS)) {
                        null -> preserve
                        "preserve" -> true
                        "default" -> false
                        else -> throw InvalidTtml()
                    }
                    if (interval.active) {
                        if (child.isTag("br")) builder.newline()
                        else collect(child, interval, keep,
                            if (hasTiming(child)) interval else word?.let { interval }, builder)
                    }
                    if (sequential) { cursor = interval.end ?: throw InvalidTtml() }
                }
            }
        }

        private fun sequential(node: TtmlElement): Boolean = when (node.attribute("timeContainer")) {
            null, "par" -> false
            "seq" -> { ttmlCheck(!apple); true }
            else -> throw InvalidTtml()
        }

        private fun interval(node: TtmlElement, parent: Interval, base: Long): Interval {
            val syncBase = if (apple) 0L else base
            val begin = node.attribute("begin")?.let { add(syncBase, time.parse(it)) } ?: base
            var end = node.attribute("end")?.let { add(syncBase, time.parse(it)) }
            node.attribute("dur")?.let { duration ->
                val fromDuration = add(begin, time.parse(duration))
                end = end?.let { minOf(it, fromDuration) } ?: fromDuration
            }
            ttmlCheck(end == null || end >= begin)
            val clippedEnd = parent.end?.let { p -> end?.let { minOf(p, it) } ?: p } ?: end
            return Interval(maxOf(parent.begin, begin), clippedEnd, parent.timed || hasTiming(node))
        }

        private fun TtmlElement.isTag(local: String) = name == XmlName(namespace, local)
        private fun hasTiming(node: TtmlElement) = listOf("begin", "end", "dur").any { node.attribute(it) != null }
        private fun hasAppleExtension(node: TtmlElement): Boolean = node.attributes.keys.any { it.namespace == ITUNES_LYRICS_NS } ||
            node.children.filterIsInstance<TtmlElement>().any(::hasAppleExtension)

        private class LineBuilder {
            val text = StringBuilder()
            val words = mutableListOf<LyricsWord>()
            private var pendingSpace = false

            fun append(value: String, preserve: Boolean, interval: Interval?) {
                for (c in value) {
                    if (!preserve && xmlSpace(c)) { pendingSpace = text.isNotEmpty() && text.last() != '\n'; continue }
                    if (pendingSpace) { text.append(' '); pendingSpace = false }
                    val start = text.length
                    text.append(c)
                    if (interval != null && !xmlSpace(c)) {
                        val previous = words.lastOrNull()
                        if (previous != null && previous.end == start && previous.startMs == interval.begin && previous.endMs == interval.end)
                            words[words.lastIndex] = previous.copy(end = text.length)
                        else {
                            ttmlCheck(words.size < LyricsParser.MAX_WORD_ENTRIES)
                            words += LyricsWord(start, text.length, interval.begin, interval.end)
                        }
                    }
                }
            }
            fun newline() { pendingSpace = false; text.append('\n') }
        }
    }

    private class TimeExpressions(private val root: TtmlElement, private val bareSeconds: Boolean) {
        private fun parameter(name: String) = root.attribute(name, TTML_PARAMETER_NS)
        private val nominal = positive(parameter("frameRate") ?: "30")
        private val subFrames = positive(parameter("subFrameRate") ?: "1")
        private val rate: Double
        private val ticks: Double

        init {
            ttmlCheck(parameter("timeBase") in listOf(null, "media"))
            ttmlCheck(parameter("dropMode") in listOf(null, "nonDrop"))
            val multiplier = parameter("frameRateMultiplier")?.trim()?.split(Regex("\\s+"))
            ttmlCheck(multiplier == null || multiplier.size == 2)
            rate = if (multiplier == null) nominal else nominal * positive(multiplier[0]) / positive(multiplier[1])
            ticks = parameter("tickRate")?.let(::positive) ?: if (parameter("frameRate") == null) 1.0 else rate * subFrames
        }

        fun parse(raw: String): Long {
            val value = raw.trim()
            if (bareSeconds && decimalSeconds.matches(value)) {
                return milliseconds(value.toDoubleOrNull() ?: throw InvalidTtml())
            }
            offset.matchEntire(value)?.let { match ->
                val amount = match.groupValues[1].toDoubleOrNull() ?: throw InvalidTtml()
                val seconds = when (match.groupValues[2]) {
                    "h" -> amount * 3600; "m" -> amount * 60; "s" -> amount
                    "ms" -> amount / 1000; "f" -> amount / rate; "t" -> amount / ticks
                    else -> throw InvalidTtml()
                }
                return milliseconds(seconds)
            }
            frames.matchEntire(value)?.let { match ->
                val hours = match.groupValues[1].toDoubleOrNull() ?: throw InvalidTtml()
                val minutes = match.groupValues[2].toInt()
                val seconds = match.groupValues[3].toInt()
                val frame = match.groupValues[4].toIntOrNull() ?: throw InvalidTtml()
                val subFrame = match.groupValues[5].ifEmpty { "0" }.toIntOrNull() ?: throw InvalidTtml()
                ttmlCheck(minutes < 60 && seconds < 60 && frame < nominal && subFrame < subFrames)
                return milliseconds(hours * 3600 + minutes * 60 + seconds + (frame + subFrame / subFrames) / rate)
            }
            clock.matchEntire(value)?.let { match ->
                val hours = match.groupValues[1].ifEmpty { "0" }.toDoubleOrNull() ?: throw InvalidTtml()
                val minutes = match.groupValues[2].toDoubleOrNull() ?: throw InvalidTtml()
                val seconds = match.groupValues[3].toDoubleOrNull() ?: throw InvalidTtml()
                ttmlCheck(seconds < 60 && (match.groupValues[1].isEmpty() || minutes < 60))
                return milliseconds(hours * 3600 + minutes * 60 + seconds)
            }
            throw InvalidTtml()
        }

        private fun positive(raw: String): Double {
            ttmlCheck(raw.isNotEmpty() && raw.all { it in '0'..'9' })
            return (raw.toLongOrNull() ?: throw InvalidTtml()).toDouble().also { ttmlCheck(it > 0) }
        }
        private fun milliseconds(seconds: Double): Long {
            val value = seconds * 1000
            ttmlCheck(value.isFinite() && value >= 0 && value < Long.MAX_VALUE.toDouble())
            return value.roundToLong()
        }
    }

    private fun add(base: Long, offset: Long): Long {
        ttmlCheck(base <= Long.MAX_VALUE - offset)
        return base + offset
    }
    private val decimalSeconds = Regex("[0-9]+(?:\\.[0-9]+)?")
    private val offset = Regex("([0-9]+(?:\\.[0-9]+)?)(ms|h|m|s|f|t)")
    private val frames = Regex("([0-9]{2,}):([0-9]{2}):([0-9]{2}):([0-9]+)(?:\\.([0-9]+))?")
    private val clock = Regex("(?:([0-9]{2,}):)?([0-9]+):([0-9]{2}(?:\\.[0-9]+)?)")
}
