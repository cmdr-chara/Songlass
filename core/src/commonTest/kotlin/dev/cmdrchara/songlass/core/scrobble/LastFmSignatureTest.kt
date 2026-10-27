package dev.cmdrchara.songlass.core.scrobble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Signing is the part Last.fm rejects silently when it is wrong, so it is pinned to known MD5
 * vectors and to the exact canonical string the API documents.
 */
class LastFmSignatureTest {
    @Test
    fun md5MatchesPublishedVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
        // UTF-8 bytes, not UTF-16 units: Last.fm signs the encoded payload it receives.
        assertEquals("07117fe4a1ebd544965dc19573183da2", md5Hex("café"))
    }

    @Test
    fun signatureSortsNamesAppendsSecretAndExcludesFormat() {
        val params = mapOf(
            "token" to "mytoken123",
            "api_key" to "0123456789abcdef0123456789abcdef",
            "method" to "auth.getSession",
            "format" to "json",
        )
        // api_key…method…token + secret, in that order.
        assertEquals("1e0cc6f0101ab3101945ef9ea17bf963", lastFmSignature(params, "secret"))
        val withoutFormat = params - "format"
        assertEquals(lastFmSignature(withoutFormat, "secret"), lastFmSignature(params, "secret"))
    }

    @Test
    fun signatureChangesWithTheSecretAndIsLowercaseHex() {
        val params = mapOf("artist" to "Synthetic Artist")
        val signature = lastFmSignature(params, "secret")
        assertEquals(32, signature.length)
        assertTrue(signature.all { it in "0123456789abcdef" })
        assertNotEquals(signature, lastFmSignature(params, "other-secret"))
    }

    @Test
    fun signedFormCarriesApiSigFormatAndSession() {
        val form = signedLastFmForm(
            credentials = testCredentials(),
            method = "track.updateNowPlaying",
            params = mapOf("artist" to "Synthetic Artist", "track" to "Synthetic Song"),
            sessionKey = "0123456789abcdef0123456789abcdef",
        )
        assertEquals("json", form["format"])
        assertEquals("0123456789abcdef0123456789abcdef", form["api_key"])
        assertEquals("0123456789abcdef0123456789abcdef", form["sk"])
        // md5(api_key…artist…method…sk…track… + secret), with format and api_sig excluded.
        assertEquals("693cefd43eb726a83bad2b63c608b14e", form["api_sig"])
    }

    @Test
    fun signedFormOmitsSessionKeyForUnauthenticatedCalls() {
        val form = signedLastFmForm(testCredentials(), "auth.getToken", emptyMap())
        assertTrue("sk" !in form)
        assertEquals("json", form["format"])
    }

    @Test
    fun authorizationUrlPercentEncodesItsParts() {
        assertEquals(
            "https://www.last.fm/api/auth/?api_key=abc&token=to%20ken",
            lastFmAuthorizationUrl("abc", "to ken"),
        )
    }
}
