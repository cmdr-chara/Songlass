package dev.cmdrchara.songlass.core.net

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureHttpUrlTest {
    @Test fun absoluteHttpsUrlsAreAccepted() {
        for (url in listOf(
            "https://itunes.apple.com/search?term=daft+punk",
            "https://ws.audioscrobbler.com/2.0/",
            "https://example.com:8443/path#fragment",
            "HTTPS://EXAMPLE.COM/",
        )) {
            assertTrue(isSecureHttpUrl(url), url)
        }
    }

    @Test fun cleartextMalformedAndCredentialedUrlsAreRejected() {
        for (url in listOf(
            "http://itunes.apple.com/search",
            "ftp://example.com/file",
            "",
            "https://",
            "https:///path",
            "https://exa mple.com/",
            "https://example.com/\n",
            "https://user:pass@example.com/",
            "itunes.apple.com/search",
        )) {
            assertFalse(isSecureHttpUrl(url), url)
        }
    }
}
