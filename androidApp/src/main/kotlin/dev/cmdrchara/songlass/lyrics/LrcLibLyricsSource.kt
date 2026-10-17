package dev.cmdrchara.songlass.lyrics

import android.content.Context
import android.os.SystemClock
import dev.cmdrchara.songlass.core.lyrics.Lyrics
import dev.cmdrchara.songlass.core.lyrics.LyricsOrigin
import dev.cmdrchara.songlass.core.lyrics.LyricsParser
import dev.cmdrchara.songlass.core.lyrics.LyricsSource
import dev.cmdrchara.songlass.core.model.Track
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Exact metadata lookup; an untagged artist never produces a guessed search result. */
class LrcLibLyricsSource(context: Context) : LyricsSource {
    private val cache = LyricsDiskCache(context.cacheDir.resolve("lyrics"))
    private val requests = Mutex()
    private var nextRequestAtMs = 0L
    private var cooldownUntilMs = 0L

    override suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val artist = track.artist?.trim()?.takeIf(String::isNotEmpty) ?: return@withContext null
        val title = track.title.trim().takeIf(String::isNotEmpty) ?: return@withContext null
        val parameters = buildList {
            add("track_name" to title)
            add("artist_name" to artist)
            track.album?.trim()?.takeIf(String::isNotEmpty)?.let { add("album_name" to it) }
            if (track.durationMs in 1_000L..3_600_000L) {
                add("duration" to (track.durationMs / 1000.0).toString())
            }
        }
        val query = parameters.joinToString("&") { (name, value) ->
            "$name=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        requests.withLock {
            cache.read(query)?.let { body ->
                try {
                    decode(body)?.let { return@withLock it }
                } catch (_: IOException) {
                    // A corrupt disposable cache entry should fall through to a fresh lookup.
                }
            }
            if (SystemClock.elapsedRealtime() < cooldownUntilMs) {
                throw IOException("LRCLIB requests are temporarily paused")
            }
            delay((nextRequestAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            nextRequestAtMs = SystemClock.elapsedRealtime() + REQUEST_SPACING_MS
            val response = get(URL("https://lrclib.net/api/get?$query"))
            when (response.status) {
                404 -> null
                429, 503 -> {
                    val wait = retryAfterMillis(response.retryAfter) ?: DEFAULT_RETRY_DELAY_MS
                    val now = SystemClock.elapsedRealtime()
                    cooldownUntilMs = now + wait.coerceAtMost(Long.MAX_VALUE - now)
                    throw IOException("LRCLIB is temporarily unavailable (${response.status})")
                }
                200 -> decode(response.body)?.also { cache.write(query, response.body) }
                else -> throw IOException("LRCLIB returned HTTP ${response.status}")
            }
        }
    }

    private suspend fun get(url: URL): Response = suspendCancellableCoroutine { continuation ->
        val connection = url.openConnection() as HttpsURLConnection
        continuation.invokeOnCancellation { connection.disconnect() }
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Songlass/0.1.0 (https://github.com/cmdr-chara)")
            val status = connection.responseCode
            val body = if (status == 200) {
                if (connection.contentLengthLong > MAX_RESPONSE_BYTES) throw IOException("Lyrics response is too large")
                connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (!continuation.isActive) throw CancellationException("Lyrics request cancelled")
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("Lyrics response is too large")
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            } else ""
            if (continuation.isActive) continuation.resume(Response(status, body, connection.getHeaderField("Retry-After")))
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(val status: Int, val body: String, val retryAfter: String?)

    internal companion object {
        const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val REQUEST_SPACING_MS = 350L
        private const val DEFAULT_RETRY_DELAY_MS = 30_000L

        fun decode(body: String): Lyrics? {
            val value = try {
                JSONObject(body)
            } catch (error: JSONException) {
                throw IOException("LRCLIB returned invalid lyrics data", error)
            }
            (value.opt("syncedLyrics") as? String)?.let {
                LyricsParser.parse(it, LyricsOrigin.LrcLib)?.takeIf(Lyrics::isSynced)?.let { lyrics -> return lyrics }
            }
            (value.opt("plainLyrics") as? String)?.let {
                LyricsParser.parse(it, LyricsOrigin.LrcLib)?.let { lyrics -> return lyrics }
            }
            return if (value.optBoolean("instrumental", false)) {
                Lyrics(emptyList(), LyricsOrigin.LrcLib, instrumental = true)
            } else null
        }

        fun retryAfterMillis(value: String?, nowMs: Long = System.currentTimeMillis()): Long? {
            val header = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            header.toLongOrNull()?.let { seconds ->
                if (seconds < 0) return null
                return seconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
            }
            return try {
                (ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMs)
                    .coerceAtLeast(0L)
            } catch (_: java.time.DateTimeException) {
                null
            }
        }
    }
}
