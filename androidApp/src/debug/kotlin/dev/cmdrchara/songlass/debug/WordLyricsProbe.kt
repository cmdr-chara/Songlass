package dev.cmdrchara.songlass.debug

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.cmdrchara.songlass.core.lyrics.Lyrics
import dev.cmdrchara.songlass.core.lyrics.LyricsLine
import dev.cmdrchara.songlass.core.lyrics.LyricsOrigin
import dev.cmdrchara.songlass.core.lyrics.LyricsRepository
import dev.cmdrchara.songlass.core.lyrics.LyricsResult
import dev.cmdrchara.songlass.core.lyrics.LyricsSource
import dev.cmdrchara.songlass.core.lyrics.PreferWordTimedLyricsSource
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.lyrics.AmllLyricsSource
import dev.cmdrchara.songlass.lyrics.EmbeddedLyricsSource
import dev.cmdrchara.songlass.lyrics.LrcLibLyricsSource
import dev.cmdrchara.songlass.lyrics.LyricsDiskCache
import dev.cmdrchara.songlass.lyrics.LyricsHttpRequest
import dev.cmdrchara.songlass.lyrics.LyricsHttpResponse
import dev.cmdrchara.songlass.lyrics.NativeLyricsHttpRequest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** Explicit debug broadcasts only; logs counts/assertions and never logs lyric bodies. */
internal object WordLyricsProbe {
    private const val TAG = "WordLyrics"
    private val song = Track("word-probe", "Probe verse", "probe://word-lyrics", artist = "Probe artist", album = "Probe album", durationMs = 10_000)
    // Self-authored fixture with original relative TTML timestamps.
    private const val TTML = """<tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="1s" end="3s"><span begin="0s" end="0.5s">Soft</span> <span begin="0.5s" end="1.5s">light</span></p></body></tt>"""

    suspend fun run(context: Context, mode: String) = withTimeout(25_000) {
        val root = File(context.cacheDir, "word-lyrics-probe-${System.nanoTime()}")
        when (mode) {
            "checks" -> checks(context, root)
            "live" -> live(root)
            else -> error("Unknown word lyrics probe mode")
        }
    }

    private fun record(id: Int = 1, title: String = song.title, text: String = TTML): JSONObject = JSONObject()
        .put("id", id).put("filename", "probe.ttml").put("musicNames", JSONArray().put(title))
        .put("artistNames", JSONArray().put("Probe artist")).put("albumNames", JSONArray().put("Probe album"))
        .put("format", "ttml").put("lyrics", text)

    private fun envelope(data: JSONObject) = LyricsHttpResponse(200, JSONObject().put("status", 200).put("data", data).toString())
    private fun search(item: JSONObject = record(), truncated: Boolean = false) = envelope(JSONObject()
        .put("items", JSONArray().put(item)).put("pagination", JSONObject().put("page", 1).put("total", if (truncated) 2 else 1).put("hasMore", truncated)))

    private suspend fun checks(context: Context, root: File) {
        var assertions = 0
        fun verify(value: Boolean) { check(value); assertions++ }
        var calls = 0
        val request = LyricsHttpRequest { url -> calls++; if (url.path.endsWith("search")) search() else envelope(record()) }
        val source = AmllLyricsSource(File(root, "exact"), request, requestSpacingMs = 0)
        val result = checkNotNull(source.load(song))
        val verse = result.lines.first { it.words.isNotEmpty() }
        verify(result.source == LyricsOrigin.Amll && result.isSynced && result.hasWordTiming)
        verify(verse.timeMs == 1_000L && verse.words.map { it.startMs } == listOf(1_000L, 1_500L))
        verify(verse.words.map { it.endMs } == listOf(1_500L, 2_500L))
        verify(source.load(song) == result && calls == 2)
        // A new source must also recover the persisted entry without a transport call.
        verify(AmllLyricsSource(File(root, "exact"), LyricsHttpRequest { error("Cache miss") }, requestSpacingMs = 0).load(song) == result)

        for ((name, response) in listOf("mismatch" to search(record(title = "Other")), "truncated" to search(truncated = true))) {
            var requests = 0
            val invalid = AmllLyricsSource(File(root, name), LyricsHttpRequest { requests++; response }, requestSpacingMs = 0)
            verify(invalid.load(song) == null && requests == 1)
        }
        val wrongId = AmllLyricsSource(File(root, "id"), LyricsHttpRequest { url ->
            if (url.path.endsWith("search")) search() else envelope(record(id = 2))
        }, requestSpacingMs = 0)
        verify(wrongId.load(song) == null)
        val fallback = Lyrics(listOf(LyricsLine(0, "Fallback")), LyricsOrigin.LrcLib)
        val lineOnly = AmllLyricsSource(File(root, "line"), LyricsHttpRequest { url ->
            if (url.path.endsWith("search")) search() else envelope(record(text = """<tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="1s">Line only</p></body></tt>"""))
        }, requestSpacingMs = 0)
        verify(PreferWordTimedLyricsSource(lineOnly, LyricsSource { fallback }).load(song) === fallback)
        val failed = AmllLyricsSource(File(root, "failure"), LyricsHttpRequest { throw IOException("Fixture failure") }, requestSpacingMs = 0)
        verify(PreferWordTimedLyricsSource(failed, LyricsSource { fallback }).load(song) === fallback)
        var clock = 1_000L
        var rateCalls = 0
        val limited = AmllLyricsSource(File(root, "rate"), LyricsHttpRequest { rateCalls++; LyricsHttpResponse(429, "", "2") }, clock = { clock }, requestSpacingMs = 0)
        expectIo { limited.load(song) }; assertions++
        expectIo { limited.load(song) }; assertions++
        verify(rateCalls == 1)
        clock += 2_001
        expectIo { limited.load(song) }; assertions++
        verify(rateCalls == 2)
        coroutineScope {
            val entered = CompletableDeferred<Unit>()
            var cancelledCalls = 0
            var fallbackCalls = 0
            val cancelled = AmllLyricsSource(File(root, "cancel"), LyricsHttpRequest {
                cancelledCalls++; entered.complete(Unit); CompletableDeferred<LyricsHttpResponse>().await()
            }, requestSpacingMs = 0)
            val job = async { PreferWordTimedLyricsSource(cancelled, LyricsSource { fallbackCalls++; fallback }).load(song) }
            entered.await()
            job.cancelAndJoin()
            verify(job.isCancelled && cancelledCalls == 1 && fallbackCalls == 0)
            val retry = AmllLyricsSource(File(root, "cancel"), request, requestSpacingMs = 0)
            val before = calls
            verify(retry.load(song) != null && calls == before + 2)
        }
        assertions += transportChecks()
        assertions += lrcLibTransportChecks()
        assertions += cacheEncodingChecks(root)
        assertions += embeddedSelectionChecks(context)
        Log.i(TAG, "checks passed assertions=$assertions exactRequests=2 liveRequests=0")
    }

    private suspend fun expectIo(block: suspend () -> Any?) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: IOException) { return }
        error("Expected IOException")
    }

    private suspend fun transportChecks(): Int {
        var assertions = 0
        for ((bytes, declared, status) in listOf(
            Triple(ByteArray(9), -1L, 200), Triple(ByteArray(0), 9L, 200),
            Triple(byteArrayOf(0xc3.toByte(), 0x28), -1L, 200), Triple(ByteArray(0), 0L, 302),
        )) {
            var closed = false
            var disconnected = false
            var opened = false
            val input = object : ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
            val url = fakeUrl("api.amll.dev") { target ->
                opened = true
                object : HttpsURLConnection(target) {
                    override fun connect() = Unit
                    override fun disconnect() { disconnected = true }
                    override fun usingProxy() = false
                    override fun getCipherSuite() = "fixture"
                    override fun getLocalCertificates(): Array<Certificate>? = null
                    override fun getServerCertificates(): Array<Certificate> = emptyArray()
                    override fun getResponseCode() = status
                    override fun getContentLengthLong() = declared
                    override fun getInputStream(): InputStream = input
                }
            }
            expectIo { NativeLyricsHttpRequest(maxResponseBytes = 8).get(url) }
            check(opened && disconnected)
            if (status == 200 && declared <= 8) check(closed)
            assertions++
        }
        var foreignOpened = false
        val foreign = fakeUrl("foreign.invalid") { foreignOpened = true; error("Foreign endpoint opened") }
        expectIo { NativeLyricsHttpRequest().get(foreign) }
        check(!foreignOpened)
        return assertions + 1
    }

    private suspend fun lrcLibTransportChecks(): Int {
        var assertions = 0
        for ((bytes, declared, status) in listOf(
            Triple(ByteArray(9), -1L, 200), Triple(ByteArray(0), 9L, 200),
            Triple(byteArrayOf(0xc3.toByte(), 0x28), -1L, 200), Triple(ByteArray(0), 0L, 302),
        )) {
            var closed = false
            var disconnected = false
            var opened = false
            val input = object : ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
            val url = fakeUrl("lrclib.net", "/api/get") { target ->
                opened = true
                object : HttpsURLConnection(target) {
                    override fun connect() = Unit
                    override fun disconnect() { disconnected = true }
                    override fun usingProxy() = false
                    override fun getCipherSuite() = "fixture"
                    override fun getLocalCertificates(): Array<Certificate>? = null
                    override fun getServerCertificates(): Array<Certificate> = emptyArray()
                    override fun getResponseCode() = status
                    override fun getContentLengthLong() = declared
                    override fun getInputStream(): InputStream = input
                }
            }
            expectIo { LrcLibLyricsSource.get(url, maxResponseBytes = 8) }
            check(opened && disconnected)
            if (status == 200 && declared <= 8) check(closed)
            assertions++
        }
        var foreignOpened = false
        expectIo { LrcLibLyricsSource.get(fakeUrl("foreign.invalid", "/api/get") {
            foreignOpened = true; error("Foreign endpoint opened")
        }) }
        check(!foreignOpened)
        assertions++
        check(LrcLibLyricsSource.retryAfterMillis("2", nowMs = 0) == 2_000L); assertions++
        check(LrcLibLyricsSource.retryAfterMillis(Long.MAX_VALUE.toString(), nowMs = 0) == 3_600_000L); assertions++
        check(LrcLibLyricsSource.retryAfterMillis("Thu, 01 Jan 1970 00:00:02 GMT", nowMs = 1_000) == 1_000L); assertions++
        check(LrcLibLyricsSource.retryAfterMillis("Thu, 01 Jan 1970 00:00:02 GMT", nowMs = Long.MIN_VALUE) == 3_600_000L); assertions++
        check(LrcLibLyricsSource.retryAfterMillis("broken", nowMs = 0) == null); assertions++
        return assertions
    }

    private fun cacheEncodingChecks(root: File): Int {
        val directory = File(root, "utf8-cache")
        val cache = LyricsDiskCache(directory)
        val text = "Città 東京 😀"
        cache.write("fixture", text)
        check(cache.read("fixture") == text)
        val entry = checkNotNull(directory.listFiles()).single { it.extension == "json" }
        entry.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        check(cache.read("fixture") == null && entry.isFile)
        cache.write("fixture", text)
        check(cache.read("fixture") == text)
        return 3
    }

    /** Native metadata selection only: the independent ExoPlayer never prepares/opens audio. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun embeddedSelectionChecks(context: Context): Int = withContext(Dispatchers.Main.immediate) {
        val player = ExoPlayer.Builder(context).build()
        try {
            player.setMediaItem(MediaItem.Builder().setMediaId(song.id).setUri(song.uri).build())
            val source = EmbeddedLyricsSource(player)
            val fallback = Lyrics(listOf(LyricsLine(null, "Fallback")), LyricsOrigin.LrcLib)
            var assertions = 0
            for (requested in listOf(song.copy(id = "other-canonical-id"), song.copy(uri = "probe://reindexed"))) {
                var fallbackCalls = 0
                val repository = LyricsRepository(source, LyricsSource { fallbackCalls++; fallback })
                check(repository.load(requested) == LyricsResult.Found(fallback))
                check(fallbackCalls == 1 && currentCoroutineContext().isActive)
                assertions++
            }
            coroutineScope {
                var armed = false
                var fallbackCalls = 0
                lateinit var job: Deferred<LyricsResult>
                val cancelled = EmbeddedLyricsSource {
                    if (armed) job.cancel()
                    player
                }
                val repository = LyricsRepository(cancelled, LyricsSource { fallbackCalls++; fallback })
                job = async(start = CoroutineStart.LAZY) { repository.load(song) }
                armed = true
                job.start()
                try { job.await(); error("Expected real cancellation") }
                catch (_: CancellationException) { check(job.isCancelled && fallbackCalls == 0) }
                assertions++
            }
            assertions
        } finally { player.release() }
    }

    private fun fakeUrl(host: String, path: String = "/v1/lyrics/search", open: (URL) -> URLConnection): URL =
        URL(null, "https://$host$path", object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection = open(url)
        })

    private suspend fun live(root: File) {
        val tracks = listOf(
            Track("live-1", "Blinding Lights", "probe://live-1", artist = "The Weeknd", album = "Blinding Lights", durationMs = 240_000),
            Track("live-2", "Cruel Summer", "probe://live-2", artist = "Taylor Swift", album = "Lover", durationMs = 240_000),
        )
        val source = AmllLyricsSource(File(root, "live"), NativeLyricsHttpRequest())
        for ((index, track) in tracks.withIndex()) {
            val lyrics = checkNotNull(source.load(track)) { "Live fixture ${index + 1} has no exact word lyrics" }
            check(lyrics.source == LyricsOrigin.Amll && lyrics.isSynced && lyrics.hasWordTiming && !lyrics.instrumental)
            Log.i(TAG, "live fixture=${index + 1} lines=${lyrics.lines.size} words=${lyrics.lines.sumOf { it.words.size }} verified=true")
        }
        Log.i(TAG, "live passed tracks=2")
    }
}
