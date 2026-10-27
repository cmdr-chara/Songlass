package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.net.HttpFailureKind
import dev.cmdrchara.songlass.core.net.HttpResult
import dev.cmdrchara.songlass.core.net.HttpTransport
import dev.cmdrchara.songlass.core.net.asJsonObjectOrNull
import dev.cmdrchara.songlass.core.net.intOrNull
import dev.cmdrchara.songlass.core.net.objectOrNull
import dev.cmdrchara.songlass.core.net.parseJsonOrNull
import dev.cmdrchara.songlass.core.net.stringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * The three Last.fm Web Services methods this app uses, plus their error handling.
 *
 * The client is stateless apart from the credentials: the session key is passed in by the caller, so
 * a failed authorization never rewrites stored state from here. Every failure is mapped to a
 * [LastFmFailure]; nothing is thrown for network problems, HTTP statuses, API error codes or
 * malformed payloads. Last.fm answers most errors with HTTP 200 and an `error` JSON object, so both
 * channels are checked.
 */
internal class LastFmClient(
    private val transport: HttpTransport,
    private val credentials: LastFmCredentials,
) {
    /** `auth.getToken`: a fresh token the user authorizes on the Last.fm website. */
    suspend fun requestToken(): LastFmResult<String> {
        val body = when (val response = call("auth.getToken", emptyMap())) {
            is LastFmResult.Success -> response.value
            is LastFmResult.Failure -> return response
        }
        val token = body.stringOrNull("token")?.takeIf(String::isNotBlank)
            ?: return failure(LastFmFailure.MalformedResponse)
        return LastFmResult.Success(token)
    }

    /** `auth.getSession`: trades an authorized token for a session key and the account name. */
    suspend fun createSession(token: String): LastFmResult<LastFmSession> {
        val body = when (val response = call("auth.getSession", mapOf("token" to token))) {
            is LastFmResult.Success -> response.value
            is LastFmResult.Failure -> return response
        }
        val session = body.objectOrNull("session") ?: return failure(LastFmFailure.MalformedResponse)
        val username = session.stringOrNull("name")?.takeIf(String::isNotBlank)
            ?: return failure(LastFmFailure.MalformedResponse)
        val key = session.stringOrNull("key")?.takeIf(String::isNotBlank)
            ?: return failure(LastFmFailure.MalformedResponse)
        return LastFmResult.Success(LastFmSession(username, key))
    }

    /** `track.updateNowPlaying`: what is playing now. Never part of the persistent queue. */
    suspend fun updateNowPlaying(sessionKey: String, track: ScrobbleTrack): LastFmResult<Unit> {
        val params = buildMap {
            put("artist", track.artist)
            put("track", track.title)
            track.album?.let { put("album", it) }
            if (track.durationSeconds > 0) put("duration", track.durationSeconds.toString())
            track.trackNumber?.takeIf { it > 0 }?.let { put("trackNumber", it.toString()) }
        }
        val response = call("track.updateNowPlaying", params, sessionKey)
        return when (response) {
            is LastFmResult.Success -> LastFmResult.Success(Unit)
            is LastFmResult.Failure -> response
        }
    }

    /**
     * `track.scrobble` for one batch of at most [LASTFM_MAX_BATCH] listens.
     *
     * A larger batch is a caller bug: the queue chunks before calling, and Last.fm rejects batches
     * above 50 with an error that cannot be distinguished from a bad listen.
     */
    suspend fun scrobble(sessionKey: String, batch: List<ScrobbleRecord>): LastFmResult<ScrobbleBatchReport> {
        require(batch.size in 1..LASTFM_MAX_BATCH) { "batch size must be 1..$LASTFM_MAX_BATCH" }
        val params = buildMap {
            batch.forEachIndexed { index, record ->
                put("artist[$index]", record.track.artist)
                put("track[$index]", record.track.title)
                record.track.album?.let { put("album[$index]", it) }
                put("timestamp[$index]", record.timestampSeconds.toString())
                if (record.durationSeconds > 0) put("duration[$index]", record.durationSeconds.toString())
                record.track.trackNumber?.takeIf { it > 0 }?.let { put("trackNumber[$index]", it.toString()) }
            }
        }
        val body = when (val response = call("track.scrobble", params, sessionKey)) {
            is LastFmResult.Success -> response.value
            is LastFmResult.Failure -> return response
        }
        return LastFmResult.Success(parseBatchReport(body, batch.size))
    }

    /** Signs, sends and validates one call. [method] is a Last.fm method name, never user input. */
    private suspend fun call(
        method: String,
        params: Map<String, String>,
        sessionKey: String? = null,
    ): LastFmResult<JsonObject> {
        val form = signedLastFmForm(credentials, method, params, sessionKey)
        val result = transport.post(
            url = LASTFM_API_URL,
            form = form,
            headers = mapOf("Accept" to "application/json"),
        )
        return when (result) {
            is HttpResult.Success -> {
                val element = parseJsonOrNull(result.body) ?: return failure(LastFmFailure.MalformedResponse)
                val body = element.asJsonObjectOrNull() ?: return failure(LastFmFailure.MalformedResponse)
                val errorCode = body.intOrNull("error")
                if (errorCode != null) {
                    // The API's own message is not surfaced: a fixed explanation is shown instead.
                    failure(failureForApiCode(errorCode))
                } else {
                    LastFmResult.Success(body)
                }
            }
            is HttpResult.Failure -> failure(failureForHttp(result))
        }
    }

    private fun parseBatchReport(body: JsonObject, sent: Int): ScrobbleBatchReport {
        val attributes = body.objectOrNull("scrobbles")?.objectOrNull("@attr")
        val accepted = attributes?.intOrNull("accepted")
        if (accepted == null) {
            // A 200 without an error object means the call was processed; the count is only a report.
            return ScrobbleBatchReport(accepted = sent, ignored = 0)
        }
        val ignored = attributes.intOrNull("ignored") ?: (sent - accepted).coerceAtLeast(0)
        return ScrobbleBatchReport(accepted = accepted.coerceIn(0, sent), ignored = ignored.coerceAtLeast(0))
    }
}

/** Largest batch Last.fm documents for a single `track.scrobble` call. */
internal const val LASTFM_MAX_BATCH = 50

/** Last.fm API error code to user-facing failure. Unknown codes are treated as unusable responses. */
internal fun failureForApiCode(code: Int): LastFmFailure = when (code) {
    // Invalid service / method / format: the application is built against a changed protocol.
    2, 3, 5 -> LastFmFailure.RejectedCredentials
    // Rejected permissions, invalid API key, invalid signature: the configured key pair is wrong.
    4, 10, 13 -> LastFmFailure.RejectedCredentials
    // Invalid/unauthorized/expired session, or a method that requires a signed-in user.
    9, 14, 15, 17 -> LastFmFailure.SessionExpired
    // Suspended API key: only Last.fm can lift this.
    26 -> LastFmFailure.Suspended
    // Temporary backend or service problems.
    8, 11, 16 -> LastFmFailure.ServerError
    // Too many requests in a short period.
    29 -> LastFmFailure.RateLimited
    // Invalid parameters/resource: this exact request will keep failing.
    else -> LastFmFailure.MalformedResponse
}

/** Transport failure to [LastFmFailure], keeping only the classified kind. */
internal fun failureForHttp(result: HttpResult.Failure): LastFmFailure = when (result.kind) {
    HttpFailureKind.Network, HttpFailureKind.Timeout -> LastFmFailure.Offline
    HttpFailureKind.Malformed -> LastFmFailure.MalformedResponse
    HttpFailureKind.HttpError -> when {
        result.detail == "HTTP 429" -> LastFmFailure.RateLimited
        result.detail == "HTTP 401" || result.detail == "HTTP 403" -> LastFmFailure.RejectedCredentials
        result.detail.startsWith("HTTP 5") -> LastFmFailure.ServerError
        else -> LastFmFailure.MalformedResponse
    }
}

private fun failure(reason: LastFmFailure): LastFmResult<Nothing> = LastFmResult.Failure(reason)
