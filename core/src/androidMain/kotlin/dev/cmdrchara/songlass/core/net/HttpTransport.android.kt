package dev.cmdrchara.songlass.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.utils.io.cancel
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Android transport: one Ktor client with the OkHttp engine, created lazily and shared by the whole
 * process. The client owns the connection pool, so it must not be created per request.
 *
 * Behaviour:
 * - connection 10 s, whole request 20 s, socket 20 s ([HttpTimeout]);
 * - response bodies are bounded to 1 MiB and a larger body cancels the call instead of buffering it;
 * - redirects are disabled (Ktor 2.3.2's HttpRedirect has no redirect cap, so following them could
 *   loop forever); a 3xx surfaces as [HttpFailureKind.HttpError];
 * - no automatic retry: `retryOnConnectionFailure` is off and [HttpResult.retryable] only advises
 *   future callers;
 * - HTTPS-only, plus a fixed, identifiable User-Agent with no install or device identifiers.
 *
 * Failures are classified into [HttpFailureKind] without logging or echoing the URL.
 */
actual fun platformHttpTransport(): HttpTransport? = AndroidHttpTransport

private object AndroidHttpTransport : HttpTransport {
    private val client: HttpClient by lazy { createClient() }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        execute(HttpMethod.Get, url, form = null, headers = headers)

    override suspend fun post(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): HttpResult = execute(HttpMethod.Post, url, form = form, headers = headers)

    private suspend fun execute(
        httpMethod: HttpMethod,
        url: String,
        form: Map<String, String>?,
        headers: Map<String, String>,
    ): HttpResult {
        // Refused before building a request: metadata never travels over cleartext.
        if (!isSecureHttpUrl(url)) {
            return HttpResult.Failure(HttpFailureKind.Malformed, "only absolute https URLs are supported")
        }
        return try {
            val response = client.request(url) {
                this.method = httpMethod
                if (headers.keys.none { it.equals(HttpHeaders.UserAgent, ignoreCase = true) }) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                headers.forEach { (name, value) -> header(name, value) }
                if (form != null) {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                form.forEach { (name, value) -> append(name, value) }
                            },
                        ),
                    )
                }
            }
            val body = readBoundedBody(response)
                ?: return HttpResult.Failure(HttpFailureKind.Malformed, "response body is too large")
            httpResultOf(response.status.value, body)
        } catch (e: CancellationException) {
            // Coroutine cancellation is not a transport failure: keep structured concurrency intact.
            throw e
        } catch (e: Exception) {
            // Cancellation can also surface as a socket/IO error; never mask it as a result.
            currentCoroutineContext().ensureActive()
            classifyFailure(e)
        }
    }

    private fun createClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        engine {
            config {
                retryOnConnectionFailure(false)
                followRedirects(false)
                followSslRedirects(false)
            }
        }
    }

    private fun classifyFailure(cause: Exception): HttpResult = when (cause) {
        is HttpRequestTimeoutException,
        is SocketTimeoutException,
        is ConnectTimeoutException,
        is InterruptedIOException,
        -> HttpResult.Failure(HttpFailureKind.Timeout, "request timed out")
        is IOException -> HttpResult.Failure(HttpFailureKind.Network, "network request failed")
        // Invalid arguments, TLS/configuration errors and similar: retrying unchanged will not help.
        else -> HttpResult.Failure(HttpFailureKind.Malformed, "unexpected response")
    }

    /** Reads at most [MAX_RESPONSE_BYTES]; a larger body cancels the call instead of buffering it. */
    private suspend fun readBoundedBody(response: HttpResponse): String? {
        val channel = response.bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read < 0) break
            if (read > 0) output.write(buffer, 0, read)
            if (output.size() > MAX_RESPONSE_BYTES) {
                channel.cancel()
                return null
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    // Identifiable but coarse: no install id, device model or user data. Keep in sync with the app
    // version in androidApp/build.gradle.kts.
    private const val USER_AGENT = "Songlass/0.1.0 (Android)"
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val REQUEST_TIMEOUT_MS = 20_000L
    private const val SOCKET_TIMEOUT_MS = 20_000L
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
    private const val READ_BUFFER_BYTES = 16 * 1024
}
