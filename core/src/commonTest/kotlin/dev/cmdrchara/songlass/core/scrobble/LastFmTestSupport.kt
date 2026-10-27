package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.net.HttpFailureKind
import dev.cmdrchara.songlass.core.net.HttpResult
import dev.cmdrchara.songlass.core.net.HttpTransport

/**
 * Scripted Last.fm transport for tests: records the exact forms this feature would send and never
 * opens a socket. Every call goes to the same URL, so responses are chosen by [respond].
 */
internal class LastFmTestTransport(
    var respond: (Map<String, String>) -> HttpResult = { jsonBody("{}") },
) : HttpTransport {
    data class Request(val url: String, val form: Map<String, String>, val headers: Map<String, String>)

    val requests = mutableListOf<Request>()

    val lastForm: Map<String, String> get() = requests.last().form

    /** The most recent form with [method]; independent of interleaved "now playing" calls. */
    fun formFor(method: String): Map<String, String> = requests.last { it.form["method"] == method }.form

    fun countFor(method: String): Int = requests.count { it.form["method"] == method }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        error("Last.fm only uses form POST")

    override suspend fun post(url: String, form: Map<String, String>, headers: Map<String, String>): HttpResult {
        requests += Request(url, form, headers)
        return respond(form)
    }
}

internal fun jsonBody(body: String): HttpResult = HttpResult.Success(200, body)

internal fun transportFailure(kind: HttpFailureKind, detail: String): HttpResult = HttpResult.Failure(kind, detail)

internal fun apiError(code: Int): HttpResult = jsonBody("""{"error":$code,"message":"synthetic error"}""")

/** Accepted/unaccepted counts, exactly as `track.scrobble` reports them. */
internal fun scrobbleResponse(accepted: Int, ignored: Int = 0): HttpResult =
    jsonBody("""{"scrobbles":{"@attr":{"accepted":$accepted,"ignored":$ignored}}}""")

internal val SESSION_FIXTURE = """{"session":{"name":"synthetic_listener","key":"synthetic-session-key","subscriber":0}}"""

internal val NOW_PLAYING_FIXTURE = """{"nowplaying":{"artist":{"#text":"Synthetic Artist"},"track":{"#text":"Synthetic Song"}}}"""

internal fun testCredentials() = LastFmCredentials(
    apiKey = "0123456789abcdef0123456789abcdef",
    apiSecret = "secret",
)

internal fun sessionStore() = dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore().apply {
    putString(LastFmScrobbler.SESSION_KEY, "synthetic-session-key")
    putString(LastFmScrobbler.SESSION_USER, "synthetic_listener")
}
