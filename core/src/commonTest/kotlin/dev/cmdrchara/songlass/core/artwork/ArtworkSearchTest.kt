package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.net.FakeHttpTransport
import dev.cmdrchara.songlass.core.net.HttpFailureKind
import dev.cmdrchara.songlass.core.net.HttpResult
import dev.cmdrchara.songlass.core.net.httpResultOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DISCOVERY_RESPONSE = """
{"resultCount":1,"results":[{"wrapperType":"collection","artistName":"Daft Punk",
 "collectionName":"Discovery","collectionId":61715512,
 "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music/a/100x100bb.jpg"}]}
"""

class ArtworkSearchTest {
    private fun search(transport: dev.cmdrchara.songlass.core.net.HttpTransport) = ArtworkSearch(transport)

    @Test
    fun foundCandidatesComeFromTheSharedTransport() = runTest {
        val url = ITunesArtwork.searchUrl("Daft Punk", "Discovery")
        val transport = FakeHttpTransport(mapOf(url to HttpResult.Success(200, DISCOVERY_RESPONSE)))
        val result = search(transport).search("Discovery", "Daft Punk")
        assertTrue(result is ArtworkSearchResult.Found, "expected candidates, got $result")
        assertEquals("Discovery", (result as ArtworkSearchResult.Found).candidates.single().album)
        // Only the artist and album tags leave the device, in the search term.
        val requested = transport.requests.single().url
        assertEquals(url, requested)
        assertTrue("term=Daft%20Punk%20Discovery" in requested)
        assertTrue("entity=album" in requested && "media=music" in requested)
    }

    @Test
    fun anEmptyResultListIsNoMatchNotAnError() = runTest {
        val url = ITunesArtwork.searchUrl("Daft Punk", "Discovery")
        val transport = FakeHttpTransport(mapOf(url to HttpResult.Success(200, """{"resultCount":0,"results":[]}""")))
        assertEquals(ArtworkSearchResult.NoMatch, search(transport).search("Discovery", "Daft Punk"))
    }

    @Test
    fun httpErrorStatusesAreReportedAsProviderErrors() = runTest {
        val url = ITunesArtwork.searchUrl(null, "Discovery")
        for (status in listOf(400, 403, 404, 429, 500, 503)) {
            val transport = FakeHttpTransport(mapOf(url to httpResultOf(status, "")))
            assertEquals(
                ArtworkSearchResult.Failed(ArtworkSearchFailure.ProviderError),
                search(transport).search("Discovery", null),
                "HTTP $status",
            )
        }
    }

    @Test
    fun timeoutsAndOfflineFailuresStayDistinguishable() = runTest {
        val url = ITunesArtwork.searchUrl(null, "Discovery")
        assertEquals(
            ArtworkSearchResult.Failed(ArtworkSearchFailure.Timeout),
            search(FakeHttpTransport(mapOf(url to HttpResult.Failure(HttpFailureKind.Timeout, "timeout"))))
                .search("Discovery", null),
        )
        assertEquals(
            ArtworkSearchResult.Failed(ArtworkSearchFailure.Offline),
            search(FakeHttpTransport(mapOf(url to HttpResult.Failure(HttpFailureKind.Network, "network"))))
                .search("Discovery", null),
        )
        assertEquals(
            ArtworkSearchResult.Failed(ArtworkSearchFailure.Malformed),
            search(FakeHttpTransport(mapOf(url to HttpResult.Failure(HttpFailureKind.Malformed, "body"))))
                .search("Discovery", null),
        )
    }

    @Test
    fun aBodyThatIsNotJsonIsMalformedAndNotACrash() = runTest {
        val url = ITunesArtwork.searchUrl(null, "Discovery")
        val transport = FakeHttpTransport(mapOf(url to HttpResult.Success(200, "<html>blocked</html>")))
        assertEquals(ArtworkSearchResult.NoMatch, search(transport).search("Discovery", null))
    }

    @Test
    fun noRequestIsMadeWithoutAnAlbumTag() = runTest {
        val transport = FakeHttpTransport()
        assertEquals(ArtworkSearchResult.NoMatch, search(transport).search("", "Daft Punk"))
        assertTrue(transport.requests.isEmpty(), "nothing may leave the device without an album tag")
    }

    @Test
    fun theCountryIsConfigurableAndDefaultsToTheStoreCountry() = runTest {
        val url = ITunesArtwork.searchUrl("Daft Punk", "Discovery", country = "US")
        val transport = FakeHttpTransport(mapOf(url to HttpResult.Success(200, DISCOVERY_RESPONSE)))
        val result = ArtworkSearch(transport, country = "US").search("Discovery", "Daft Punk")
        assertTrue(result is ArtworkSearchResult.Found)
        assertTrue("country=US" in transport.requests.single().url)
    }
}
