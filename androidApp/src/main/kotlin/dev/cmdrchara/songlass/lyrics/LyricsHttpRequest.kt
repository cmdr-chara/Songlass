package dev.cmdrchara.songlass.lyrics

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal data class LyricsHttpResponse(val status: Int, val body: String, val retryAfter: String? = null)

internal fun interface LyricsHttpRequest {
    suspend fun get(url: URL): LyricsHttpResponse
}

/** Bounded AMLL transport. Rate limiting and the overall lookup deadline belong to the source. */
internal class NativeLyricsHttpRequest(
    private val maxResponseBytes: Int = 512 * 1024,
) : LyricsHttpRequest {
    init {
        require(maxResponseBytes > 0)
    }

    override suspend fun get(url: URL): LyricsHttpResponse = withContext(Dispatchers.IO) {
        if (!url.protocol.equals("https", ignoreCase = true) ||
            !url.host.equals("api.amll.dev", ignoreCase = true) ||
            url.port !in listOf(-1, 443) || url.userInfo != null || url.ref != null ||
            url.path !in setOf("/v1/lyrics/search", "/v1/lyrics/get")
        ) throw IOException("Unsupported lyrics endpoint")

        suspendCancellableCoroutine { continuation ->
            val connection = url.openConnection() as HttpsURLConnection
            // Registered before blocking IO: cancellation requests connection closure.
            continuation.invokeOnCancellation { connection.disconnect() }
            try {
                if (!continuation.isActive) throw CancellationException("Lyrics request cancelled")
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Songlass/0.1.0 (https://github.com/cmdr-chara)")
                val status = connection.responseCode
                if (status in 300..399) throw IOException("Lyrics redirects are not allowed")
                val body = if (status == 200) {
                    if (connection.contentLengthLong > maxResponseBytes) throw IOException("Lyrics response is too large")
                    connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (!continuation.isActive) throw CancellationException("Lyrics request cancelled")
                            val count = input.read(buffer)
                            if (count == -1) break
                            if (count > maxResponseBytes - output.size()) throw IOException("Lyrics response is too large")
                            output.write(buffer, 0, count)
                        }
                        try {
                            Charsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(output.toByteArray())).toString()
                        } catch (error: CharacterCodingException) {
                            throw IOException("Lyrics response is not valid UTF-8", error)
                        }
                    }
                } else ""
                if (continuation.isActive) {
                    continuation.resume(LyricsHttpResponse(status, body, connection.getHeaderField("Retry-After")))
                }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                connection.disconnect()
            }
        }
    }
}
