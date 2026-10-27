package dev.cmdrchara.songlass.core.net

/**
 * Minimal HTTP surface shared by the network features (cover lookup, scrobbling, ...).
 *
 * Implementations must never throw for transport failures, timeouts, HTTP error statuses or
 * malformed payloads: they return [HttpResult.Failure] instead. Only coroutine cancellation is
 * rethrown. No provider-specific logic lives here: callers build URLs and parse JSON themselves.
 *
 * Implementations are safe to call from the main thread; they must not perform blocking work on the
 * calling thread.
 */
interface HttpTransport {
    /** Performs a GET request. [headers] are added after the transport defaults. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult

    /** Performs a form-encoded POST (`application/x-www-form-urlencoded`). */
    suspend fun post(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult
}

/**
 * Process-wide transport for the current platform, or null when this platform has no tested engine.
 *
 * - Android: Ktor client with the OkHttp engine, see `HttpTransport.android.kt`.
 * - iOS: null. The iPhone build is still a mute demo with no library, player or network features,
 *   and no HTTP engine has been exercised on device. Callers must degrade gracefully (hide cover
 *   lookup / scrobbling) instead of assuming a transport exists.
 */
expect fun platformHttpTransport(): HttpTransport?
