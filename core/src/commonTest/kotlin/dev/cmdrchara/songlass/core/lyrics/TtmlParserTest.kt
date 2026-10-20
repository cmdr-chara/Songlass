package dev.cmdrchara.songlass.core.lyrics

import kotlin.test.*

class TtmlParserTest {
    private fun xml(body: String, attributes: String = "") =
        """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttp="http://www.w3.org/ns/ttml#parameter" $attributes>$body</tt>"""
    private fun parse(value: String) = assertNotNull(LyricsParser.parse(value, LyricsOrigin.File))
    private fun verses(value: String) = parse(value).lines.filter { it.text.isNotBlank() }

    @Test fun nestedStandardTimesAreRelativeAndWordDurationsRemainOriginal() {
        val result = parse(xml("""<body begin="5s"><div begin="2s"><p begin="1s" dur="3s"><span begin="0s" end="1s">Neon</span> <span begin="1s" dur="1s">skies</span></p></div></body>"""))
        val line = result.lines.first()
        assertEquals(8000L, line.timeMs)
        assertEquals("Neon skies", line.text)
        assertEquals(listOf(LyricsWord(0, 4, 8000, 9000), LyricsWord(5, 10, 9000, 10000)), line.words)
        assertEquals(LyricsLine(11000, ""), result.lines.last())
        assertEquals(.5f, line.words.last().progressAt(9500))
        assertEquals(0f, line.words.last().progressAt(8500)) // A backward seek uses the same clock.
        assertEquals(LyricsOrigin.File, result.source)
    }

    @Test fun appleMusicDialectUsesAbsoluteCoordinatesWithoutGuessingFromValues() {
        val result = parse(xml("""<body dur="02:00.000"><div begin="00:10.000" end="00:20.000" i:song-part="Verse"><p begin="00:12.000" end="00:15.000"><span begin="00:12.300" end="00:13.000">Neon</span> <span begin="00:13.000" end="00:14.000">skies</span></p></div></body>""",
            """xmlns:i="http://itunes.apple.com/lyric-ttml-extensions""""))
        assertEquals(12000L, result.lines.first().timeMs)
        assertEquals(listOf(12300L, 13000L), result.lines.first().words.map { it.startMs })
        // Merely declaring the namespace must not change a standard document's timing.
        val standard = verses(xml("""<body begin="10s"><p begin="12s">Still relative</p></body>""",
            """xmlns:i="http://itunes.apple.com/lyric-ttml-extensions""""))
        assertEquals(22000L, standard.single().timeMs)
    }

    @Test fun namespaceAliasesMetadataEntitiesCdataAndBreaksKeepUnicodeRanges() {
        val result = parse("""<?xml version="1.0"?>
            <t:tt xmlns:t="http://www.w3.org/ns/ttml" xmlns:m="http://www.w3.org/ns/ttml#metadata">
              <t:head><t:metadata><m:title>Not a lyric</m:title></t:metadata></t:head>
              <t:body><t:p begin="1s" end="4s">
                <t:span begin="0s" end="1s">Neon &amp; &#x6C34;&#128512;</t:span><t:br/><t:span begin="1s" end="2s"><![CDATA[<skies>]]></t:span>
              </t:p></t:body>
            </t:tt>""".trimIndent())
        val line = result.lines.first()
        assertEquals("Neon & 水😀\n<skies>", line.text)
        assertEquals(listOf("Neon", "&", "水😀", "<skies>"), line.words.map { line.text.substring(it.start, it.end) })
        assertEquals(listOf(1000L, 1000L, 1000L, 2000L), line.words.map { it.startMs })
        assertTrue(result.hasWordTiming)
    }

    @Test fun xmlSpaceIsInheritedAndSyllablesAreNotJoinedWithInventedSpaces() {
        val lines = verses(xml("""<body><p begin="1s"><span begin="0s">star</span><span begin="1s">light</span></p><p begin="3s" xml:space="preserve">  keep  spaces </p></body>"""))
        assertEquals("starlight", lines[0].text)
        assertEquals(listOf("star", "light"), lines[0].words.map { lines[0].text.substring(it.start, it.end) })
        assertEquals("  keep  spaces ", lines[1].text)
        assertEquals("  root  ", verses(xml("<body><p>  root  </p></body>", "xml:space=\"preserve\"")).single().text)
    }

    @Test fun clockOffsetFrameSubFrameAndTickExpressionsAreRespected() {
        val cases = mapOf("01:02:03.456" to 3723456L, "02:03.456" to 123456L,
            "1.5h" to 5400000L, "2m" to 120000L, "1.25s" to 1250L, "1250ms" to 1250L)
        cases.forEach { (expression, expected) ->
            assertEquals(expected, verses(xml("<body><p begin=\"$expression\">Neon</p></body>")).single().timeMs, expression)
        }
        val parameters = """ttp:frameRate="25" ttp:frameRateMultiplier="1000 1001" ttp:subFrameRate="4" ttp:tickRate="100""""
        for ((expression, expected) in mapOf("25f" to 1001L, "00:00:01:12.2" to 1501L, "100t" to 1000L))
            assertEquals(expected, verses(xml("<body><p begin=\"$expression\">Neon</p></body>", parameters)).single().timeMs)
        assertEquals(1000L, verses(xml("<body><p begin=\"1t\">Neon</p></body>")).single().timeMs)
        assertEquals(1000L, verses(xml("<body><p begin=\"50t\">Neon</p></body>", "ttp:frameRate=\"25\" ttp:subFrameRate=\"2\"")).single().timeMs)
    }

    @Test fun sequentialContainersRespectDelaysAndDurations() {
        val result = parse(xml("""<body begin="2s" timeContainer="seq"><p begin="1s" dur="2s">First</p><p begin="1s" dur="1s">Second</p></body>"""))
        assertEquals(listOf(3000L, 6000L), result.lines.filter { it.text.isNotBlank() }.map { it.timeMs })
        assertEquals(listOf(LyricsLine(5000, ""), LyricsLine(7000, "")), result.lines.filter { it.text.isEmpty() })
        assertNull(LyricsParser.parse(xml("<body timeContainer=\"seq\"><p>Unknown end</p><p>Next</p></body>"), LyricsOrigin.File))
        assertNull(LyricsParser.parse(xml("<body timeContainer=\"seq\"><div timeContainer=\"seq\"><p>Unknown end</p></div><p>Next</p></body>"), LyricsOrigin.File))
        assertNotNull(LyricsParser.parse(xml("<body timeContainer=\"seq\"><p>Last</p><metadata/></body>"), LyricsOrigin.File))
    }

    @Test fun parentEndClipsWordsAndExplicitLineEndsCreateRealVocalGaps() {
        val result = parse(xml("""<body end="10s"><p begin="1s" end="8s" dur="3s"><span begin="0s" end="7s">Neon</span></p><p begin="7s" end="12s">Skies</p></body>"""))
        assertEquals(listOf(LyricsLine(1000, "Neon", listOf(LyricsWord(0, 4, 1000, 4000))), LyricsLine(4000, ""),
            LyricsLine(7000, "Skies"), LyricsLine(10000, "")), result.lines)
        assertEquals(1, result.activeLineAt(5000))
        assertEquals(3, result.activeLineAt(11000))
        val unknown = verses(xml("""<body><p begin="1s"><span begin="0s">Open end</span></p></body>""")).single()
        assertTrue(unknown.words.all { it.endMs == null })
    }

    @Test fun plainTtmlAndNestedStylingDoNotInventWordTimestamps() {
        val plain = parse(xml("<body><p>First <span>verse</span></p><p>Second verse</p></body>"))
        assertFalse(plain.isSynced)
        assertFalse(plain.hasWordTiming)
        assertEquals(listOf("First verse", "Second verse"), plain.lines.map { it.text })
        val timed = verses(xml("""<body><p begin="1s" end="3s">Line <span>timing only</span></p></body>""")).single()
        assertTrue(timed.words.isEmpty())
        val nested = verses(xml("""<body><p begin="1s"><span begin="0s" end="2s">Keep <span>nested</span> text</span></p></body>""")).single()
        assertEquals("Keep nested text", nested.text)
        assertTrue(nested.words.all { it.startMs == 1000L && it.endMs == 3000L })
    }

    @Test fun invalidXmlAndUnsupportedTimingAreRejectedRatherThanDisplayedAsMarkup() {
        val invalid = listOf(
            "<!DOCTYPE tt [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + xml("<body><p>&x;</p></body>"),
            xml("<body><p>&unknown;</p></body>"), xml("<body><p>&#0;</p></body>"),
            xml("<body><p>&#xD800;</p></body>"), xml("<body><p>&#x110000;</p></body>"),
            xml("<body><p>Broken</body>"), xml("<body><p begin=1s>Neon</p></body>"),
            xml("<body><p begin=\"1s\" begin=\"2s\">Neon</p></body>"),
            xml("<body><p begin=\"2s\" end=\"1s\">Reversed</p></body>"),
            xml("<body><p begin=\"wallclock(2026-10-20)\">Neon</p></body>"),
            xml("<body><p begin=\"00:99:01\">Neon</p></body>"),
            xml("<body><p begin=\"1s\">Neon</p></body>", "ttp:timeBase=\"smpte\""),
            xml("<body><p>Neon</p></body>", "ttp:tickRate=\"0\""),
            xml("<body><p begin=\"9999999999999999999999999h\">Neon</p></body>"),
            xml("<body><x:p>Unbound prefix</x:p></body>"),
            "<tt xmlns=\"urn:wrong\"><body><p>Neon</p></body></tt>",
            xml("<body><p>Neon</p></body>") + "<extra/>",
            "<html><body>Not lyrics</body></html>", "<?xml version=\"1.0\"?><tt",
            "<?xml version=\"1.0\" garbage?>" + xml("<body><p>Neon</p></body>"),
            "<!--broken--->" + xml("<body><p>Neon</p></body>"),
        )
        invalid.forEach { assertNull(LyricsParser.parse(it, LyricsOrigin.File), it) }
    }

    @Test fun xmlDepthLineAndWordBudgetsRejectWholeDocument() {
        val deep = xml("<body>" + "<div>".repeat(32) + "<p>Neon</p>" + "</div>".repeat(32) + "</body>")
        val tooManyLines = xml("<body>" + "<p>a</p>".repeat(LyricsParser.MAX_LINES + 1) + "</body>")
        val tooManyWords = xml("<body><p><span begin=\"0s\">" + "a ".repeat(LyricsParser.MAX_WORD_ENTRIES + 1) + "</span></p></body>")
        for (value in listOf(deep, tooManyLines, tooManyWords)) {
            assertTrue(value.length < LyricsParser.MAX_CHARACTERS)
            assertNull(LyricsParser.parse(value, LyricsOrigin.File))
        }
    }
}
