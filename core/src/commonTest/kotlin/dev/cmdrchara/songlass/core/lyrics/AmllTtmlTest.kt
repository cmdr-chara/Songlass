package dev.cmdrchara.songlass.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AmllTtmlTest {
    private fun xml(body: String, timing: String = "Word", namespace: String = "http://music.apple.com/lyric-ttml-internal") =
        """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:i="$namespace" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" i:timing="$timing">$body</tt>"""
    private fun parse(value: String) = LyricsParser.parse(value, LyricsOrigin.Amll)

    @Test fun internalWordDialectKeepsAbsoluteSecondsAndExcludesSupplementaryText() {
        // Entirely self-authored representative structure, without provider lyric content.
        val lyrics = assertNotNull(parse(xml("""<body dur="3:14.571"><div begin="27.173" end="3:14.571">
            <p begin="27.173" end="28.516"><span begin="27.173" end="27.407">Silver</span> <span begin="27.407" end="28.516">window</span><span ttm:role="x-translation">Translated suffix</span><span ttm:role="x-roman">Roman suffix</span></p>
            <p begin="3:10.000" end="3:11.000"><span begin="3:10.000" end="3:11.000">Evening</span></p>
            </div></body>""")))
        val sung = lyrics.lines.filter { it.text.isNotBlank() }
        assertEquals(listOf("Silver window", "Evening"), sung.map { it.text })
        assertEquals(listOf(27_173L, 190_000L), sung.map { it.timeMs })
        assertEquals(listOf(27_173L, 27_407L), sung.first().words.map { it.startMs })
        assertEquals(listOf(27_407L, 28_516L), sung.first().words.map { it.endMs })
    }

    @Test fun internalLineDialectKeepsAbsoluteTimes() {
        val lyrics = assertNotNull(parse(xml("""<body><div begin="10" end="20"><p begin="12.5" end="14">Quiet lantern</p></div></body>""", timing = "Line")))
        assertEquals(12_500L, lyrics.lines.first().timeMs)
        assertEquals(emptyList(), lyrics.lines.first().words)
        val longer = assertNotNull(parse(xml("""<body><p begin="72.5" end="73">Long offset</p></body>""", timing = "Line")))
        assertEquals(72_500L, longer.lines.first().timeMs)
    }

    @Test fun backgroundWordsRemainTimed() {
        val lyrics = assertNotNull(parse(xml("""<body><p begin="2" end="4"><span ttm:role="x-bg"><span begin="2.5" end="3">Echo</span></span></p></body>""")))
        assertEquals("Echo", lyrics.lines.first().text)
        assertEquals(2_500L, lyrics.lines.first().words.single().startMs)
        assertEquals(3_000L, lyrics.lines.first().words.single().endMs)
    }

    @Test fun standardTtmlRemainsRelative() {
        val value = """<tt xmlns="http://www.w3.org/ns/ttml"><body begin="5s"><div begin="2s"><p begin="1s"><span begin="0.5s" end="1s">Relative</span></p></div></body></tt>"""
        val line = assertNotNull(parse(value)).lines.first()
        assertEquals(8_000L, line.timeMs)
        assertEquals(8_500L, line.words.single().startMs)
    }

    @Test fun bareSecondsRequireExactRootDialectMarker() {
        val body = """<body><p begin="2.5">No guess</p></body>"""
        assertNull(parse("""<tt xmlns="http://www.w3.org/ns/ttml">$body</tt>"""))
        assertNull(parse(xml(body, namespace = "http://forged.invalid/internal")))
        assertNull(parse(xml(body, timing = "word")))
        assertNull(parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:i="http://itunes.apple.com/lyric-ttml-extensions" i:song-part="Verse">$body</tt>"""))
        assertNull(parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:i="http://music.apple.com/lyric-ttml-internal"><body i:timing="Word"><p begin="2.5">Nested marker</p></body></tt>"""))
    }

    @Test fun songAbsoluteDialectRejectsSequentialContainers() {
        assertNull(parse(xml("""<body timeContainer="seq"><p begin="2" end="3">No sequence</p></body>""")))
    }
}
