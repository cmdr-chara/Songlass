package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.net.HttpFailureKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fixed responses stand in for Last.fm; no request leaves the process. */
class LastFmClientTest {
    private val transport = LastFmTestTransport()
    private val client = LastFmClient(transport, testCredentials())

    @Test
    fun requestTokenReturnsTheTokenAndUsesAuthGetToken() = runTest {
        transport.respond = { jsonBody("""{"token":"request-token"}""") }
        val result = client.requestToken()
        assertEquals("request-token", assertIs<LastFmResult.Success<String>>(result).value)
        assertEquals("auth.getToken", transport.lastForm["method"])
        assertEquals("json", transport.lastForm["format"])
        assertTrue("api_sig" in transport.lastForm)
        assertTrue("sk" !in transport.lastForm)
    }

    @Test
    fun createSessionParsesNameAndKey() = runTest {
        transport.respond = { jsonBody(SESSION_FIXTURE) }
        val result = client.createSession("request-token")
        val session = assertIs<LastFmResult.Success<LastFmSession>>(result).value
        assertEquals("synthetic_listener", session.username)
        assertEquals("synthetic-session-key", session.key)
        assertEquals("request-token", transport.lastForm["token"])
        assertEquals("auth.getSession", transport.lastForm["method"])
    }

    @Test
    fun createSessionWithoutSessionObjectIsMalformed() = runTest {
        transport.respond = { jsonBody("""{"ok":true}""") }
        assertEquals(LastFmFailure.MalformedResponse, failureOf(client.createSession("t")))
    }

    @Test
    fun updateNowPlayingSendsMetadataOnlyAndSignsTheSession() = runTest {
        transport.respond = { jsonBody(NOW_PLAYING_FIXTURE) }
        val track = ScrobbleTrack(
            artist = "Synthetic Artist", title = "Synthetic Song",
            album = "Synthetic Album", durationMs = 245_400, trackNumber = 3,
        )
        assertIs<LastFmResult.Success<Unit>>(client.updateNowPlaying("session-key", track))
        val form = transport.lastForm
        assertEquals("track.updateNowPlaying", form["method"])
        assertEquals("session-key", form["sk"])
        assertEquals("Synthetic Artist", form["artist"])
        assertEquals("Synthetic Song", form["track"])
        assertEquals("Synthetic Album", form["album"])
        assertEquals("245", form["duration"])
        assertEquals("3", form["trackNumber"])
        assertEquals(form["api_sig"], signedLastFmForm(testCredentials(), "track.updateNowPlaying",
            buildMap {
                put("artist", "Synthetic Artist"); put("track", "Synthetic Song"); put("album", "Synthetic Album")
                put("duration", "245"); put("trackNumber", "3")
            }, "session-key")["api_sig"])
    }

    @Test
    fun scrobbleBatchUsesIndexedParameters() = runTest {
        transport.respond = { scrobbleResponse(accepted = 2) }
        val records = listOf(
            ScrobbleRecord(ScrobbleTrack("Artist One", "Title One", "Album One", 180_000), 1_700_000_000, 180),
            ScrobbleRecord(ScrobbleTrack("Artist Two", "Title Two", durationMs = 0), 1_700_000_100, 0),
        )
        val report = assertIs<LastFmResult.Success<ScrobbleBatchReport>>(client.scrobble("session-key", records)).value
        assertEquals(2, report.accepted)
        assertEquals(0, report.ignored)
        val form = transport.formFor("track.scrobble")
        assertEquals("track.scrobble", form["method"])
        assertEquals("Artist One", form["artist[0]"])
        assertEquals("Title One", form["track[0]"])
        assertEquals("Album One", form["album[0]"])
        assertEquals("1700000000", form["timestamp[0]"])
        assertEquals("180", form["duration[0]"])
        assertEquals("Artist Two", form["artist[1]"])
        // An unknown duration is omitted rather than sent as zero.
        assertTrue("duration[1]" !in form)
        assertEquals(form["api_sig"], signedLastFmForm(testCredentials(), "track.scrobble",
            buildMap {
                records.forEachIndexed { index, record ->
                    put("artist[$index]", record.track.artist)
                    put("track[$index]", record.track.title)
                    record.track.album?.let { put("album[$index]", it) }
                    put("timestamp[$index]", record.timestampSeconds.toString())
                    if (record.durationSeconds > 0) put("duration[$index]", record.durationSeconds.toString())
                }
            }, "session-key")["api_sig"])
    }

    @Test
    fun scrobbleRejectsAnOversizedBatchInsteadOfTruncating() = runTest {
        val tooMany = List(LASTFM_MAX_BATCH + 1) {
            ScrobbleRecord(ScrobbleTrack("Artist", "Title", durationMs = 200_000), 1_700_000_000L + it, 200)
        }
        var rejected = false
        try {
            client.scrobble("session-key", tooMany)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun apiErrorCodesAreClassified() = runTest {
        val cases = mapOf(
            9 to LastFmFailure.SessionExpired,
            14 to LastFmFailure.SessionExpired,
            15 to LastFmFailure.SessionExpired,
            17 to LastFmFailure.SessionExpired,
            10 to LastFmFailure.RejectedCredentials,
            13 to LastFmFailure.RejectedCredentials,
            26 to LastFmFailure.Suspended,
            11 to LastFmFailure.ServerError,
            16 to LastFmFailure.ServerError,
            29 to LastFmFailure.RateLimited,
            6 to LastFmFailure.MalformedResponse,
        )
        for ((code, expected) in cases) {
            transport.respond = { apiError(code) }
            assertEquals(expected, failureOf(client.requestToken()), "API code $code")
        }
    }

    @Test
    fun transportAndHttpFailuresAreClassified() = runTest {
        val cases = listOf(
            transportFailure(HttpFailureKind.Network, "network request failed") to LastFmFailure.Offline,
            transportFailure(HttpFailureKind.Timeout, "request timed out") to LastFmFailure.Offline,
            transportFailure(HttpFailureKind.HttpError, "HTTP 500") to LastFmFailure.ServerError,
            transportFailure(HttpFailureKind.HttpError, "HTTP 429") to LastFmFailure.RateLimited,
            transportFailure(HttpFailureKind.HttpError, "HTTP 403") to LastFmFailure.RejectedCredentials,
            transportFailure(HttpFailureKind.HttpError, "HTTP 400") to LastFmFailure.MalformedResponse,
            transportFailure(HttpFailureKind.Malformed, "unexpected response") to LastFmFailure.MalformedResponse,
        )
        for ((response, expected) in cases) {
            transport.respond = { response }
            assertEquals(expected, failureOf(client.requestToken()), response.toString())
        }
    }

    @Test
    fun nonJsonAndNonObjectBodiesAreMalformed() = runTest {
        for (body in listOf("<html>not json</html>", "[]", "\"text\"")) {
            transport.respond = { jsonBody(body) }
            assertEquals(LastFmFailure.MalformedResponse, failureOf(client.requestToken()), body)
        }
    }

    @Test
    fun missingAcceptedCountIsTreatedAsAccepted() = runTest {
        // A 200 without an error object means the batch was processed even if Last.fm omits @attr.
        transport.respond = { jsonBody("""{"scrobbles":{"scrobble":{"track":{"#text":"x"}}}}""") }
        val record = ScrobbleRecord(ScrobbleTrack("Artist", "Title", durationMs = 200_000), 1_700_000_000, 200)
        val report = assertIs<LastFmResult.Success<ScrobbleBatchReport>>(client.scrobble("sk", listOf(record))).value
        assertEquals(1, report.accepted)
        assertEquals(0, report.ignored)
    }

    private fun failureOf(result: LastFmResult<*>): LastFmFailure =
        assertIs<LastFmResult.Failure>(result).reason
}
