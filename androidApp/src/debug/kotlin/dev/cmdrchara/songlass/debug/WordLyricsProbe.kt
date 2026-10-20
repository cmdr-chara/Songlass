package dev.cmdrchara.songlass.debug

import android.content.Context
import android.util.Log
import dev.cmdrchara.songlass.core.lyrics.Lyrics
import dev.cmdrchara.songlass.core.lyrics.LyricsLine
import dev.cmdrchara.songlass.core.lyrics.LyricsOrigin
import dev.cmdrchara.songlass.core.lyrics.LyricsSource
import dev.cmdrchara.songlass.core.lyrics.PreferWordTimedLyricsSource
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.lyrics.AmllLyricsSource
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
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
            "checks" -> checks(root)
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

    private suspend fun checks(root: File) {
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

    private fun fakeUrl(host: String, open: (URL) -> URLConnection): URL =
        URL(null, "https://$host/v1/lyrics/search", object : URLStreamHandler() {
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
