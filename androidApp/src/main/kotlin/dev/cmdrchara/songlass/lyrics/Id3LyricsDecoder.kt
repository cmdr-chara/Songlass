package dev.cmdrchara.songlass.lyrics

import dev.cmdrchara.songlass.core.lyrics.Lyrics
import dev.cmdrchara.songlass.core.lyrics.LyricsLine
import dev.cmdrchara.songlass.core.lyrics.LyricsOrigin
import dev.cmdrchara.songlass.core.lyrics.LyricsParser
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset

/** Decodes lyric frame payloads after Media3 has handled ID3 framing and unsynchronization. */
internal object Id3LyricsDecoder {
    private const val MAX_FRAME_BYTES = 512 * 1024
    private const val MAX_LINES = 10_000

    fun decode(id: String, data: ByteArray): Lyrics? {
        if (data.isEmpty() || data.size > MAX_FRAME_BYTES) return null
        val encoding = data[0].toInt() and 0xff
        if (encoding !in 0..3) return null
        return try {
            when (id) {
                "USLT", "ULT" -> unsynchronized(data, encoding)
                "SYLT", "SLT" -> synchronized(data, encoding)
                else -> null
            }
        } catch (_: CharacterCodingException) {
            // Malformed local tags must not suppress a valid online fallback.
            null
        }
    }

    private fun unsynchronized(data: ByteArray, encoding: Int): Lyrics? {
        if (data.size < 5) return null
        val descriptionEnd = terminator(data, 4, encoding) ?: return null
        val textStart = descriptionEnd + delimiterSize(encoding)
        val text = decodeText(data, textStart, data.size, encoding)
        return LyricsParser.parse(text, LyricsOrigin.Embedded)
    }

    private fun synchronized(data: ByteArray, encoding: Int): Lyrics? {
        if (data.size < 7) return null
        // Timestamp format 1 is MPEG frames, which cannot be converted without frame timing.
        if (data[4].toInt() != 2 || data[5].toInt() != 1) return null
        val descriptionEnd = terminator(data, 6, encoding) ?: return null
        var position = descriptionEnd + delimiterSize(encoding)
        val lines = mutableListOf<LyricsLine>()
        while (position < data.size) {
            if (lines.size >= MAX_LINES) return null
            val textEnd = terminator(data, position, encoding) ?: return null
            val timestampStart = textEnd + delimiterSize(encoding)
            if (timestampStart > data.size - 4) return null
            val timestamp = (0 until 4).fold(0L) { value, index ->
                (value shl 8) or (data[timestampStart + index].toLong() and 0xff)
            }
            val text = decodeText(data, position, textEnd, encoding)
                .replace("\r\n", "\n").replace('\r', '\n').trim()
            // SYLT is timed text and may store either whole lines or smaller lyric fragments.
            lines += LyricsLine(timestamp, text)
            position = timestampStart + 4
        }
        if (lines.none { it.text.isNotBlank() }) return null
        return Lyrics(lines.sortedBy { it.timeMs }, LyricsOrigin.Embedded)
    }

    private fun delimiterSize(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun terminator(data: ByteArray, start: Int, encoding: Int): Int? {
        val width = delimiterSize(encoding)
        var index = start
        while (index <= data.size - width) {
            if (data[index] == 0.toByte() && (width == 1 || data[index + 1] == 0.toByte())) {
                return index
            }
            index += width
        }
        return null
    }

    private fun decodeText(data: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end) return ""
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return charset.newDecoder().decode(ByteBuffer.wrap(data, start, end - start))
            .toString().trimEnd('\u0000').removePrefix("\uFEFF")
    }
}
