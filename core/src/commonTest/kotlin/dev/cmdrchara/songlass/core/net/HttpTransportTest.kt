package dev.cmdrchara.songlass.core.net

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ITUNES_SEARCH =
    "https://itunes.apple.com/search?term=daft+punk&entity=album"

class HttpTransportTest {
    @Test fun successfulGetParsesJsonThroughTheSharedHelpers() = runTest {
        val transport = FakeHttpTransport(
            mapOf(
                ITUNES_SEARCH to HttpResult.Success(
                    200,
                    """{"resultCount":1,"results":[{"collectionName":"Discovery"}]}""",
                ),
            ),
        )
        val json = transport.getJsonElement(ITUNES_SEARCH, headers = mapOf("Accept" to "application/json"))
        val results = json.asJsonObjectOrNull()?.arrayOrNull("results")
        assertEquals(1, json.asJsonObjectOrNull()?.intOrNull("resultCount"))
        assertEquals(1, results?.size)
        assertEquals("Discovery", results?.first().asJsonObjectOrNull()?.stringOrNull("collectionName"))
        assertEquals(
            FakeHttpTransport.Request("GET", ITUNES_SEARCH, headers = mapOf("Accept" to "application/json")),
            transport.requests.single(),
        )
    }

    @Test fun serverErrorsSurfaceAsHttpErrorWithoutThrowing() = runTest {
        val transport = FakeHttpTransport(mapOf(ITUNES_SEARCH to httpResultOf(500, "boom")))
        val result = transport.get(ITUNES_SEARCH)
        assertEquals(HttpResult.Failure(HttpFailureKind.HttpError, "HTTP 500"), result)
        assertFalse(result.retryable)
        assertNull(transport.getJsonElement(ITUNES_SEARCH))
    }

    @Test fun timeoutsAreReportedAsRetryableFailures() = runTest {
        val transport = FakeHttpTransport(
            mapOf(ITUNES_SEARCH to HttpResult.Failure(HttpFailureKind.Timeout, "request timed out")),
        )
        val result = transport.get(ITUNES_SEARCH)
        assertEquals(HttpFailureKind.Timeout, (result as HttpResult.Failure).kind)
        assertTrue(result.retryable)
        assertNull(transport.getJsonElement(ITUNES_SEARCH))
    }

    @Test fun nonJsonBodiesBecomeNullInsteadOfAnEmptyTree() = runTest {
        val transport = FakeHttpTransport(
            mapOf(ITUNES_SEARCH to HttpResult.Success(200, "<html>Service Unavailable</html>")),
        )
        assertNull(transport.getJsonElement(ITUNES_SEARCH))
    }

    @Test fun postSendsTheFormAndHeaders() = runTest {
        val transport = FakeHttpTransport(
            mapOf("https://ws.audioscrobbler.com/2.0/" to HttpResult.Success(200, "{}")),
        )
        val result = transport.post(
            "https://ws.audioscrobbler.com/2.0/",
            form = mapOf("method" to "track.scrobble", "api_key" to "synthetic-test-key"),
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        )
        assertEquals(HttpResult.Success(200, "{}"), result)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("track.scrobble", request.form["method"])
        assertEquals("synthetic-test-key", request.form["api_key"])
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
    }

    @Test fun defaultFailureIsReportedWhenNoCannedResponseMatches() = runTest {
        val transport = FakeHttpTransport()
        assertEquals(HttpResult.Failure(HttpFailureKind.Network, "no fake response"), transport.get(ITUNES_SEARCH))
    }
}
