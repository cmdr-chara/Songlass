package dev.cmdrchara.songlass.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpResultTest {
    @Test fun networkAndTimeoutFailuresAreRetryable() {
        assertTrue(HttpResult.Failure(HttpFailureKind.Network, "network request failed").retryable)
        assertTrue(HttpResult.Failure(HttpFailureKind.Timeout, "request timed out").retryable)
    }

    @Test fun httpAndMalformedFailuresAreNotRetriedUnchanged() {
        assertFalse(HttpResult.Failure(HttpFailureKind.HttpError, "HTTP 500").retryable)
        assertFalse(HttpResult.Failure(HttpFailureKind.Malformed, "response body is too large").retryable)
    }

    @Test fun successCarriesStatusAndBodyAndIsNotRetryable() {
        val result = HttpResult.Success(200, "{}")
        assertEquals(200, result.status)
        assertEquals("{}", result.body)
        assertFalse(result.retryable)
    }

    @Test fun completedTwoHundredStatusesBecomeSuccess() {
        for (status in listOf(200, 201, 204, 299)) {
            assertEquals(HttpResult.Success(status, "body"), httpResultOf(status, "body"), "status $status")
        }
    }

    @Test fun otherStatusesBecomeHttpErrorWithoutPayload() {
        for (status in listOf(300, 301, 400, 404, 429, 500, 503)) {
            val result = httpResultOf(status, "provider body with artist=Daft Punk")
            assertEquals(HttpResult.Failure(HttpFailureKind.HttpError, "HTTP $status"), result, "status $status")
            assertFalse(result.retryable, "status $status")
        }
    }

    @Test fun failureDetailIsFixedAndNeverEchoesTheResponseBody() {
        val result = httpResultOf(500, "internal error for artist=Daft Punk")
        assertEquals("HTTP 500", (result as HttpResult.Failure).detail)
    }
}
