package dev.cmdrchara.songlass.core.lyrics

internal const val TTML_NS = "http://www.w3.org/ns/ttml"
internal const val XML_NS = "http://www.w3.org/XML/1998/namespace"
internal const val TTML_PARAMETER_NS = "$TTML_NS#parameter"
internal const val TTML_METADATA_NS = "$TTML_NS#metadata"
internal const val APPLE_INTERNAL_LYRICS_NS = "http://music.apple.com/lyric-ttml-internal"
internal const val ITUNES_LYRICS_NS = "http://itunes.apple.com/lyric-ttml-extensions"

internal class InvalidTtml : Exception()
internal fun ttmlCheck(condition: Boolean) { if (!condition) throw InvalidTtml() }
internal data class XmlName(val namespace: String, val local: String)
internal sealed interface TtmlContent
internal data class TtmlText(val value: String) : TtmlContent
internal data class TtmlElement(
    val name: XmlName,
    val attributes: Map<XmlName, String>,
    val children: MutableList<TtmlContent> = mutableListOf(),
) : TtmlContent {
    fun attribute(local: String, namespace: String = ""): String? = attributes[XmlName(namespace, local)]
}

/** Bounded XML reader for lyric documents. No DTD, custom entities, I/O or resource resolution. */
internal class TtmlXml(private val input: String) {
    private data class Open(val rawName: String, val namespaces: Map<String, String>, val element: TtmlElement)
    private var cursor = 0
    private var nodes = 0
    private var root: TtmlElement? = null
    private val stack = mutableListOf<Open>()

    fun read(): TtmlElement {
        validateCharacters(input)
        while (cursor < input.length) {
            when {
                input.startsWith("<!--", cursor) -> {
                    val end = input.indexOf("-->", cursor + 4)
                    ttmlCheck(end >= 0 && "--" !in input.substring(cursor + 4, end) && !input.substring(cursor + 4, end).endsWith('-'))
                    cursor = end + 3
                }
                input.startsWith("<![CDATA[", cursor) -> {
                    ttmlCheck(stack.isNotEmpty())
                    val end = input.indexOf("]]>", cursor + 9)
                    ttmlCheck(end >= 0)
                    text(input.substring(cursor + 9, end), decode = false)
                    cursor = end + 3
                }
                input.startsWith("<?", cursor) -> {
                    val start = cursor
                    cursor += 2
                    val target = name()
                    val end = input.indexOf("?>", cursor)
                    ttmlCheck(end >= 0)
                    if (target.equals("xml", ignoreCase = true)) {
                        ttmlCheck(start == 0 && target == "xml" &&
                            Regex("""\s+version\s*=\s*(?:"1\.0"|'1\.0')(?:\s+encoding\s*=\s*(?:"[A-Za-z][A-Za-z0-9._-]*"|'[A-Za-z][A-Za-z0-9._-]*'))?(?:\s+standalone\s*=\s*(?:"(?:yes|no)"|'(?:yes|no)'))?\s*""").matches(input.substring(cursor, end)))
                    }
                    cursor = end + 2
                }
                input.startsWith("</", cursor) -> {
                    cursor += 2
                    val raw = name()
                    space()
                    ttmlCheck(stack.isNotEmpty() && stack.last().rawName == raw && take('>'))
                    stack.removeAt(stack.lastIndex)
                }
                input.startsWith("<!", cursor) -> throw InvalidTtml()
                input[cursor] == '<' -> open()
                else -> {
                    val end = input.indexOf('<', cursor).let { if (it < 0) input.length else it }
                    text(input.substring(cursor, end), decode = true)
                    cursor = end
                }
            }
        }
        ttmlCheck(stack.isEmpty())
        return root ?: throw InvalidTtml()
    }

    private fun open() {
        cursor++
        val rawName = name()
        val rawAttributes = linkedMapOf<String, String>()
        var closed = false
        while (true) {
            val separated = space()
            if (take('>')) break
            if (take('/')) { ttmlCheck(take('>')); closed = true; break }
            ttmlCheck(separated && rawAttributes.size < 64)
            val key = name()
            space()
            ttmlCheck(take('='))
            space()
            val quote = input.getOrNull(cursor++)
            ttmlCheck(quote == '\'' || quote == '"')
            val end = input.indexOf(quote!!, cursor)
            ttmlCheck(end >= 0)
            val raw = input.substring(cursor, end)
            ttmlCheck('<' !in raw && key !in rawAttributes)
            rawAttributes[key] = decode(raw.replace('\t', ' ').replace('\n', ' '))
            cursor = end + 1
        }
        val namespaces = (stack.lastOrNull()?.namespaces ?: mapOf("xml" to XML_NS)).toMutableMap()
        for ((key, value) in rawAttributes) {
            if (key == "xmlns" || key.startsWith("xmlns:")) {
                val prefix = if (key == "xmlns") "" else key.substringAfter(':')
                ttmlCheck(prefix != "xmlns" && value != "http://www.w3.org/2000/xmlns/" &&
                    (prefix == "xml") == (value == XML_NS) && (prefix.isEmpty() || value.isNotEmpty()))
                namespaces[prefix] = value
            }
        }
        val attributes = linkedMapOf<XmlName, String>()
        for ((key, value) in rawAttributes) {
            if (key == "xmlns" || key.startsWith("xmlns:")) continue
            val resolved = resolve(key, namespaces, attribute = true)
            ttmlCheck(resolved !in attributes)
            attributes[resolved] = value
        }
        ttmlCheck(++nodes <= 25_000 && stack.size < 32)
        val element = TtmlElement(resolve(rawName, namespaces, attribute = false), attributes)
        if (stack.isEmpty()) { ttmlCheck(root == null); root = element }
        else stack.last().element.children += element
        if (!closed) stack += Open(rawName, namespaces, element)
    }

    private fun text(raw: String, decode: Boolean) {
        ttmlCheck("]]>" !in raw || !decode)
        val value = if (decode) decode(raw) else raw.also { validateCharacters(it) }
        if (stack.isEmpty()) ttmlCheck(value.all(::xmlSpace))
        else if (value.isNotEmpty()) {
            ttmlCheck(++nodes <= 25_000)
            stack.last().element.children += TtmlText(value)
        }
    }

    private fun name(): String {
        val start = cursor
        fun first(c: Char) = c == '_' || c.isLetter()
        ttmlCheck(input.getOrNull(cursor)?.let(::first) == true)
        cursor++
        while (cursor < input.length && input[cursor].let { first(it) || it.isDigit() || it == '-' || it == '.' || it == ':' }) cursor++
        val value = input.substring(start, cursor)
        val pieces = value.split(':')
        ttmlCheck(pieces.size <= 2 && pieces.all { it.firstOrNull()?.let(::first) == true })
        return value
    }

    private fun resolve(raw: String, namespaces: Map<String, String>, attribute: Boolean): XmlName {
        val prefix = raw.substringBefore(':', "")
        val uri = if (':' in raw) namespaces[prefix] ?: throw InvalidTtml()
            else if (attribute) "" else namespaces[""] ?: ""
        return XmlName(uri, raw.substringAfter(':'))
    }

    private fun space(): Boolean {
        val start = cursor
        while (cursor < input.length && xmlSpace(input[cursor])) cursor++
        return cursor != start
    }
    private fun take(value: Char): Boolean = if (input.getOrNull(cursor) == value) { cursor++; true } else false

    private fun decode(raw: String): String {
        val result = StringBuilder()
        var at = 0
        while (at < raw.length) {
            if (raw[at] != '&') { result.append(raw[at++]); continue }
            val end = raw.indexOf(';', at + 1)
            ttmlCheck(end >= 0 && end - at <= 16)
            when (val entity = raw.substring(at + 1, end)) {
                "amp" -> result.append('&')
                "lt" -> result.append('<')
                "gt" -> result.append('>')
                "quot" -> result.append('"')
                "apos" -> result.append('\'')
                else -> {
                    val hex = entity.startsWith("#x")
                    ttmlCheck(entity.startsWith('#'))
                    val digits = entity.substring(if (hex) 2 else 1)
                    ttmlCheck(digits.isNotEmpty() && digits.all { if (hex) it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' else it in '0'..'9' })
                    val code = digits.toIntOrNull(if (hex) 16 else 10) ?: throw InvalidTtml()
                    ttmlCheck(validCodePoint(code))
                    if (code <= 0xffff) result.append(code.toChar())
                    else { result.append((0xd800 + ((code - 0x10000) shr 10)).toChar()); result.append((0xdc00 + ((code - 0x10000) and 1023)).toChar()) }
                }
            }
            at = end + 1
        }
        return result.toString().also(::validateCharacters)
    }

    private fun validateCharacters(value: String) {
        var at = 0
        while (at < value.length) {
            val c = value[at++].code
            if (c in 0xd800..0xdbff) { ttmlCheck(value.getOrNull(at)?.let { it.code in 0xdc00..0xdfff } == true); at++ }
            else ttmlCheck(validCodePoint(c))
        }
    }
    private fun validCodePoint(c: Int) = c == 9 || c == 10 || c == 13 || c in 32..0xd7ff || c in 0xe000..0xfffd || c in 0x10000..0x10ffff
}

internal fun xmlSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'
