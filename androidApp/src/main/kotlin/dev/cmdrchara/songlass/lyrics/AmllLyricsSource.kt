package dev.cmdrchara.songlass.lyrics

import android.content.Context
import android.os.SystemClock
import dev.cmdrchara.songlass.core.lyrics.Lyrics
import dev.cmdrchara.songlass.core.lyrics.LyricsOrigin
import dev.cmdrchara.songlass.core.lyrics.LyricsParser
import dev.cmdrchara.songlass.core.lyrics.LyricsSource
import dev.cmdrchara.songlass.core.lyrics.WordLyricsCandidate
import dev.cmdrchara.songlass.core.lyrics.WordLyricsMatch
import dev.cmdrchara.songlass.core.model.Track
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Anonymous, on-demand TTML lookup. Fuzzy server results require strict local matching. */
class AmllLyricsSource internal constructor(
    cacheDirectory: File,
    private val request: LyricsHttpRequest = NativeLyricsHttpRequest(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val requestSpacingMs: Long = 350,
    private val requestTimeoutMs: Long = 5_000,
) : LyricsSource {
    constructor(context: Context) : this(context.cacheDir.resolve("lyrics-amll-v1"))

    private val cache = LyricsDiskCache(cacheDirectory)
    private val requests = Mutex()
    private var nextRequestAtMs = 0L
    private var cooldownUntilMs = 0L

    override suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val title = track.title.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH } ?: return@withContext null
        val artists = track.artistNames
        val artist = artists.firstOrNull()?.takeIf { it.isNotBlank() && it.length <= MAX_TAG_LENGTH } ?: return@withContext null
        val album = track.album?.trim().orEmpty()
        if (album.length > MAX_TAG_LENGTH) return@withContext null
        val parameters = buildList {
            add("musicName" to title)
            add("artistName" to artist)
            if (album.isNotEmpty()) add("albumName" to album)
            add("pageSize" to MAX_CANDIDATES.toString())
        }
        val query = parameters.joinToString("&") { (name, value) -> "$name=${encode(value)}" }
        // Full credits and duration partition the local cache, but are not extra API parameters.
        val signature = "v1:$query:${artists.joinToString("|") { encode(it) }}:${track.durationMs}"
        requests.withLock {
            currentCoroutineContext().ensureActive()
            cache.read(signature)?.let { body ->
                try { decodedLyrics(track, envelope(body))?.let { return@withLock it } } catch (_: IOException) {
                    // A disposable corrupt cache entry must not suppress a fresh lookup.
                }
            }
            val search = fetch(URL("$BASE/search?$query")) ?: return@withLock null
            val items = search.optJSONArray("items") ?: invalid("missing search items")
            if (items.length() > MAX_CANDIDATES) invalid("too many search items")
            val pagination = search.optJSONObject("pagination") ?: invalid("missing pagination")
            if (pagination.integer("page") != 1L) invalid("unexpected page")
            val total = pagination.integer("total")
            if (total < items.length()) invalid("invalid result count")
            val hasMore = pagination.opt("hasMore") as? Boolean ?: invalid("missing pagination boundary")
            val records = (0 until items.length()).map { record(items.optJSONObject(it) ?: invalid("invalid search item")) }
            if (records.map { it.candidate.id }.distinct().size != records.size) invalid("duplicate result IDs")
            val chosen = WordLyricsMatch.select(track, records.map { it.candidate }, truncated = hasMore || total > items.length())
                ?: return@withLock null
            val selected = records.single { it.candidate.id == chosen.id }
            val fetched = fetch(URL("$BASE/get?id=${encode(chosen.id)}")) ?: return@withLock null
            val actual = record(fetched)
            if (actual.candidate.id != chosen.id || actual.filename != selected.filename) return@withLock null
            val lyrics = decodedLyrics(track, fetched) ?: return@withLock null
            currentCoroutineContext().ensureActive()
            // Preserve the original contributor/recording metadata in the private offline cache.
            cache.write(signature, JSONObject().put("status", 200).put("data", fetched).toString())
            lyrics
        }
    }

    private suspend fun fetch(url: URL): JSONObject? {
        if (clock() < cooldownUntilMs) throw IOException("AMLL requests are temporarily paused")
        delay((nextRequestAtMs - clock()).coerceAtLeast(0))
        currentCoroutineContext().ensureActive()
        nextRequestAtMs = clock() + requestSpacingMs
        val response = try {
            withTimeout(requestTimeoutMs) { request.get(url) }
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw IOException("AMLL request timed out", timeout)
        }
        currentCoroutineContext().ensureActive()
        return when (response.status) {
            404 -> null
            429, 503 -> {
                val wait = LrcLibLyricsSource.retryAfterMillis(response.retryAfter)?.coerceIn(0, MAX_BACKOFF_MS) ?: 30_000
                val now = clock()
                cooldownUntilMs = now + wait.coerceAtMost(Long.MAX_VALUE - now)
                throw IOException("AMLL is temporarily unavailable (${response.status})")
            }
            200 -> envelope(response.body)
            else -> throw IOException("AMLL returned HTTP ${response.status}")
        }
    }

    private fun decodedLyrics(track: Track, value: JSONObject): Lyrics? {
        val candidate = record(value).candidate
        if (WordLyricsMatch.select(track, listOf(candidate)) == null) return null
        if (value.opt("format") != "ttml") return null
        val text = value.opt("lyrics") as? String ?: invalid("missing TTML")
        if (text.length > MAX_TTML_CHARS) invalid("TTML is too large")
        val lyrics = LyricsParser.parse(text, LyricsOrigin.Amll) ?: invalid("unsupported TTML")
        if (!lyrics.isSynced || !lyrics.hasWordTiming || lyrics.instrumental) return null
        if (track.durationMs in 1_000L..3_600_000L) {
            val latest = lyrics.lines.maxOfOrNull { line ->
                maxOf(line.timeMs ?: 0, line.words.maxOfOrNull { it.endMs ?: it.startMs } ?: 0)
            } ?: 0
            // Reject a clearly longer recording; never stretch timing to a local edit.
            if (latest > track.durationMs + 5_000) return null
        }
        return lyrics
    }

    private data class Record(val candidate: WordLyricsCandidate, val filename: String)

    private fun record(value: JSONObject): Record {
        val id = value.integer("id")
        if (id !in 1..MAX_SAFE_ID) invalid("invalid record ID")
        val filename = value.opt("filename") as? String ?: invalid("missing filename")
        if (filename.length !in 1..256 || !filename.endsWith(".ttml")) invalid("invalid filename")
        val recordingKeys = buildSet {
            for (field in listOf("ncmMusicIds", "qqMusicIds", "appleMusicIds", "spotifyIds", "isrcs")) {
                strings(value, field, required = false).forEach { raw ->
                    val key = raw.trim()
                    if (key.isNotEmpty()) add("$field:$key")
                }
            }
        }
        return Record(WordLyricsCandidate(
            id = id.toString(),
            titles = strings(value, "musicNames"),
            artists = strings(value, "artistNames"),
            albums = strings(value, "albumNames"),
            recordingKeys = recordingKeys,
            createdAtMs = if (value.has("createdAt")) value.integer("createdAt") else 0,
        ), filename)
    }

    private fun strings(value: JSONObject, name: String, required: Boolean = true): List<String> {
        val array = value.optJSONArray(name) ?: if (required || value.has(name)) invalid("invalid $name") else JSONArray()
        if (array.length() > 64) invalid("too many $name")
        return (0 until array.length()).map { index ->
            val text = array.opt(index) as? String ?: invalid("invalid $name item")
            if (text.length > MAX_TAG_LENGTH) invalid("oversized $name item")
            text
        }
    }

    private fun JSONObject.integer(name: String): Long {
        val number = opt(name) as? Number ?: invalid("invalid $name")
        val value = number.toLong()
        if (!number.toDouble().isFinite() || number.toDouble() != value.toDouble() || value < 0) invalid("invalid $name")
        return value
    }

    private fun envelope(body: String): JSONObject {
        if (body.length > MAX_RESPONSE_BYTES || body.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) invalid("response too large")
        val value = try { JSONObject(body) } catch (error: JSONException) { throw IOException("AMLL returned invalid JSON", error) }
        if (value.integer("status") != 200L) invalid("unsuccessful response envelope")
        return value.optJSONObject("data") ?: invalid("missing response data")
    }

    private fun invalid(reason: String): Nothing = throw IOException("AMLL: $reason")

    private companion object {
        const val BASE = "https://api.amll.dev/v1/lyrics"
        const val MAX_CANDIDATES = 20
        const val MAX_TAG_LENGTH = 1_024
        const val MAX_TTML_CHARS = 128 * 1_024
        const val MAX_RESPONSE_BYTES = 512 * 1_024
        const val MAX_SAFE_ID = 9_007_199_254_740_991L
        const val MAX_BACKOFF_MS = 60L * 60 * 1_000
        fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
