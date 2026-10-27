package dev.cmdrchara.songlass.core.net

/**
 * Why a request did not produce a usable response.
 *
 * [retryable] is advisory: this foundation never retries on its own, it only records whether
 * repeating the same request could plausibly change the outcome.
 */
enum class HttpFailureKind(val retryable: Boolean) {
    /** The request never reached the server: no connectivity, DNS failure or connection reset. */
    Network(retryable = true),

    /** Connection, request or socket exceeded the configured time budget. */
    Timeout(retryable = true),

    /** The server answered, but not with a success status. */
    HttpError(retryable = false),

    /** The response cannot be used as sent: unsupported URL, oversized body or unexpected failure. */
    Malformed(retryable = false),
}

/**
 * Result of one HTTP request.
 *
 * A transport maps every failure to [Failure]; nothing is thrown for transport failures, HTTP error
 * statuses or malformed payloads. Only coroutine cancellation propagates, so structured concurrency
 * keeps working.
 */
sealed interface HttpResult {
    /** Whether repeating the same request could plausibly succeed. Never implies an automatic retry. */
    val retryable: Boolean

    /** A 2xx response with [body] decoded as UTF-8 text. */
    data class Success(val status: Int, val body: String) : HttpResult {
        override val retryable: Boolean get() = false
    }

    /**
     * A failed request. [detail] is a fixed, human-readable reason: it never contains URLs, query
     * values, headers or exception messages, so it is safe to surface in logs and diagnostics.
     */
    data class Failure(val kind: HttpFailureKind, val detail: String) : HttpResult {
        override val retryable: Boolean get() = kind.retryable
    }
}
