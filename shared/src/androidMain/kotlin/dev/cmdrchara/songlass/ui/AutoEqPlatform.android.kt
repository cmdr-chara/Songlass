package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.equalizer.AutoEqRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal actual suspend fun downloadAutoEqProfile(url: String): String = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        val connection = URL(url).openConnection() as HttpsURLConnection
        continuation.invokeOnCancellation { connection.disconnect() }
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty("User-Agent", "Songlass/0.1.0 (https://github.com/cmdr-chara)")
            if (connection.responseCode != 200) throw IOException("AutoEQ HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > AutoEqRepository.MAX_PROFILE_BYTES) throw IOException("AutoEQ profile too large")
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    if (!continuation.isActive) throw CancellationException("AutoEQ request cancelled")
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (output.size() + n > AutoEqRepository.MAX_PROFILE_BYTES) throw IOException("AutoEQ profile too large")
                    output.write(buffer, 0, n)
                }
                output.toString(Charsets.UTF_8.name())
            }
            if (continuation.isActive) continuation.resume(body)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        } finally {
            connection.disconnect()
        }
    }
}
