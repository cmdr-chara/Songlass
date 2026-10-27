package dev.cmdrchara.songlass.core.net

/**
 * Maps a completed HTTP exchange to [HttpResult].
 *
 * Only 2xx responses carry a body forward. Redirects are disabled in the Android transport (Ktor
 * 2.3.2 has no redirect cap) and every other status becomes an [HttpFailureKind.HttpError] whose
 * detail contains just the status code, never the URL or the provider payload.
 */
fun httpResultOf(status: Int, body: String): HttpResult =
    if (status in 200..299) {
        HttpResult.Success(status, body)
    } else {
        HttpResult.Failure(HttpFailureKind.HttpError, "HTTP $status")
    }
