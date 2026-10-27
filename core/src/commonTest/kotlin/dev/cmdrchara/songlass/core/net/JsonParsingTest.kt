package dev.cmdrchara.songlass.core.net

import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JsonParsingTest {
    @Test fun parsesObjectsArraysAndScalars() {
        val objectTree = parseJsonOrNull("""{"a":1}""")
        assertEquals(1, objectTree.asJsonObjectOrNull()?.intOrNull("a"))
        val arrayTree = parseJsonOrNull("""[1,2,3]""")
        assertEquals(3, arrayTree.asJsonArrayOrNull()?.size)
        assertEquals(2, parseJsonOrNull("2")?.asIntOrNull())
        assertEquals(JsonNull, parseJsonOrNull("null"))
    }

    @Test fun malformedBodiesReturnNullInsteadOfThrowing() {
        for (body in listOf(
            "",
            " ",
            "{",
            "{\"a\":}",
            "[1,",
            "\"unterminated",
            "{\"a\":1} trailing",
            "not json",
            "{'a':1}",
            "{\"a\":1}{\"b\":2}",
        )) {
            assertNull(parseJsonOrNull(body), body)
        }
    }

    @Test fun singleBareTokenIsALiteralButNeverAnObjectOrArray() {
        // kotlinx's tree reader accepts a bare top-level token (numbers, true/false, unquoted text).
        // Shape checks stay the caller's job: a body that is not JSON at all becomes a literal.
        val bare = parseJsonOrNull("<html></html>")
        assertNotNull(bare)
        assertNull(bare.asJsonObjectOrNull())
        assertNull(bare.asJsonArrayOrNull())
    }

    @Test fun wrongTypesReturnNull() {
        val tree = parseJsonOrNull(
            """{"object":{"k":"v"},"array":[1],"number":4,"string":"s","nothing":null}""",
        ).asJsonObjectOrNull()!!
        assertNull(tree.asJsonArrayOrNull())
        assertNull(tree.objectOrNull("array"))
        assertNull(tree.arrayOrNull("object"))
        assertNull(tree.stringOrNull("number"))
        assertNull(tree.intOrNull("string"))
        assertNull(tree.booleanOrNull("number"))
    }

    @Test fun missingKeysReturnNull() {
        val tree = parseJsonOrNull("{}").asJsonObjectOrNull()!!
        assertNull(tree.elementOrNull("missing"))
        assertNull(tree.objectOrNull("missing"))
        assertNull(tree.arrayOrNull("missing"))
        assertNull(tree.stringOrNull("missing"))
        assertNull(tree.intOrNull("missing"))
        assertNull(tree.longOrNull("missing"))
        assertNull(tree.doubleOrNull("missing"))
        assertNull(tree.booleanOrNull("missing"))
    }

    @Test fun primitivesAreNotCoercedBetweenTypes() {
        val tree = parseJsonOrNull("""{"s":"5","n":5,"b":true,"z":null}""").asJsonObjectOrNull()!!
        assertEquals("5", tree.stringOrNull("s"))
        assertNull(tree.stringOrNull("n"))
        assertNull(tree.stringOrNull("b"))
        assertNull(tree.stringOrNull("z"))
        assertEquals(true, tree.booleanOrNull("b"))
        assertNull(tree.booleanOrNull("s"))
        assertNull(tree.booleanOrNull("n"))
        assertNull(tree.intOrNull("b"))
        assertNull(tree.doubleOrNull("z"))
    }

    @Test fun numbersAreStrictlyTypedAndBounded() {
        val tree = parseJsonOrNull(
            """{"i":42,"l":9007199254740993,"d":1.5,"overflow":1e400,"neg":-7,"text":"42"}""",
        ).asJsonObjectOrNull()!!
        assertEquals(42, tree.intOrNull("i"))
        assertEquals(-7, tree.intOrNull("neg"))
        assertNull(tree.intOrNull("d"))
        assertNull(tree.intOrNull("text"))
        assertEquals(9007199254740993L, tree.longOrNull("l"))
        assertEquals(1.5, tree.doubleOrNull("d"))
        assertEquals(42.0, tree.doubleOrNull("i"))
        assertNull(tree.doubleOrNull("overflow"))
        assertNull(tree.longOrNull("overflow"))
    }

    @Test fun unicodeAndEscapesArePreserved() {
        val tree = parseJsonOrNull(
            """{"title":"Città \u00e8 東京 😀","escaped":"line\nbreak"}""",
        ).asJsonObjectOrNull()!!
        assertEquals("Città è 東京 😀", tree.stringOrNull("title"))
        assertEquals("line\nbreak", tree.stringOrNull("escaped"))
    }

    @Test fun arraysKeepOrderAndIndexAccessIsSafe() {
        val array = parseJsonOrNull("""[{"n":1},{"n":2}]""").asJsonArrayOrNull()!!
        assertEquals(listOf(1, 2), array.map { it.asJsonObjectOrNull()?.intOrNull("n") })
        assertEquals(2, array.elementAtOrNull(1).asJsonObjectOrNull()?.intOrNull("n"))
        assertNull(array.elementAtOrNull(2))
    }

    @Test fun scalarRootIsNeitherObjectNorArray() {
        val scalar = parseJsonOrNull("42")
        assertNull(scalar.asJsonObjectOrNull())
        assertNull(scalar.asJsonArrayOrNull())
        assertEquals(42, scalar.asIntOrNull())
        assertNull(parseJsonOrNull("\"text\"").asJsonObjectOrNull())
    }
}
