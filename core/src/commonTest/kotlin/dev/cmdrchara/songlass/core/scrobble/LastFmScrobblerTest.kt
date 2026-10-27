package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.net.HttpFailureKind
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end behaviour of the scrobbler with a scripted transport: what is queued, what is sent,
 * when it is retried, and what the settings page can report. No socket is ever opened.
 */
class LastFmScrobblerTest {
    private val track = Track(
        id = "track-1",
        title = "Synthetic Song",
        uri = "content://private/library/song.mp3",
        artist = "Synthetic Artist",
        album = "Synthetic Album",
        durationMs = 300_000,
    )

    @Test
    fun disabledFeatureQueuesAndSendsNothing() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.config = LastFmConfig(enabled = false, credentials = testCredentials())
        harness.playback.start()
        harness.playback.play(300)
        advanceUntilIdle()
        assertTrue(harness.fake.requests.isEmpty())
        assertEquals(0, harness.scrobbler.state.value.pendingScrobbles)
        assertEquals(LastFmFlushStatus.Disabled, harness.scrobbler.flush().status)
        assertTrue(harness.fake.requests.isEmpty())
    }

    @Test
    fun platformWithoutATransportHidesTheFeature() = runTest {
        val harness = Harness(this, transport = null)
        harness.playback.start()
        harness.playback.play(300)
        assertEquals(LastFmFlushStatus.Unsupported, harness.scrobbler.flush().status)
        assertEquals(false, harness.scrobbler.state.value.supported)
        assertEquals(0, harness.scrobbler.state.value.pendingScrobbles)
    }

    @Test
    fun missingApiCredentialsKeepTheFeatureOff() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.config = LastFmConfig(enabled = true, credentials = null)
        harness.playback.start()
        harness.playback.play(300)
        advanceUntilIdle()
        assertEquals(LastFmFlushStatus.Disabled, harness.scrobbler.flush().status)
        assertTrue(harness.fake.requests.isEmpty())
        assertEquals(false, harness.scrobbler.state.value.configured)
    }

    @Test
    fun listenSurvivesAnOfflineSendAndIsRetriedWithBackoff() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.fake.respond = { transportFailure(HttpFailureKind.Network, "network request failed") }
        harness.playback.start()
        harness.playback.play(150)
        assertEquals(1, harness.scrobbler.state.value.pendingScrobbles)

        val first = harness.scrobbler.flush()
        assertEquals(LastFmFlushStatus.RetryScheduled, first.status)
        assertEquals(LastFmFailure.Offline, first.failure)
        assertEquals(1, harness.scrobbler.state.value.pendingScrobbles)

        // Still inside the first backoff window: nothing is sent and the queue is untouched.
        val early = harness.scrobbler.flush()
        assertEquals(LastFmFlushStatus.Idle, early.status)
        assertEquals(1, harness.scrobbler.state.value.pendingScrobbles)

        harness.clock.nowMs += ScrobbleBackoff.delayMs(1)
        harness.fake.respond = { scrobbleResponse(accepted = 1) }
        val retried = harness.scrobbler.flush()
        assertEquals(LastFmFlushStatus.Sent, retried.status)
        assertEquals(1, retried.accepted)
        assertEquals(0, harness.scrobbler.state.value.pendingScrobbles)
        assertNull(harness.scrobbler.state.value.lastFailure)
        assertEquals(harness.clock.nowMs, harness.scrobbler.state.value.lastScrobbleAtMs)
    }

    @Test
    fun queueSurvivesANewScrobblerInstance() = runTest {
        val store = sessionStore()
        val first = Harness(this, LastFmTestTransport(), store)
        first.fake.respond = { transportFailure(HttpFailureKind.Timeout, "request timed out") }
        first.playback.start()
        first.playback.play(150)
        assertEquals(LastFmFlushStatus.RetryScheduled, first.scrobbler.flush().status)

        val second = Harness(this, LastFmTestTransport(), store, clock = first.clock)
        assertEquals(1, second.scrobbler.state.value.pendingScrobbles)
        first.clock.nowMs += ScrobbleBackoff.delayMs(1)
        second.fake.respond = { scrobbleResponse(accepted = 1) }
        assertEquals(LastFmFlushStatus.Sent, second.scrobbler.flush().status)
    }

    @Test
    fun batchUsesIndexedParametersAndSendsMetadataOnly() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.fake.respond = { scrobbleResponse(accepted = 1) }
        harness.playback.start()
        harness.playback.play(150)
        assertEquals(LastFmFlushStatus.Sent, harness.scrobbler.flush().status)

        val form = harness.fake.formFor("track.scrobble")
        assertEquals("track.scrobble", form["method"])
        assertEquals("synthetic-session-key", form["sk"])
        assertEquals("Synthetic Artist", form["artist[0]"])
        assertEquals("Synthetic Song", form["track[0]"])
        assertEquals("Synthetic Album", form["album[0]"])
        // The listen started when this playback occurrence started, not when it became scrobble-able.
        assertEquals("1700000000", form["timestamp[0]"])
        assertEquals("300", form["duration[0]"])
        // No file path, URI or file name may ever reach the request body.
        assertTrue(form.values.none { it.contains("content://") || it.contains(".mp3") || it.contains("/song") })
        assertEquals(
            signedLastFmForm(
                testCredentials(), "track.scrobble",
                mapOf(
                    "artist[0]" to "Synthetic Artist", "track[0]" to "Synthetic Song", "album[0]" to "Synthetic Album",
                    "timestamp[0]" to "1700000000", "duration[0]" to "300",
                ),
                "synthetic-session-key",
            )["api_sig"],
            form["api_sig"],
        )
    }

    @Test
    fun flushSendsAtMostFiftyListensPerRequest() = runTest {
        val store = sessionStore()
        // A pre-existing backlog from an earlier session.
        val backlog = ScrobbleQueue(store)
        repeat(LASTFM_MAX_BATCH + 1) { index ->
            backlog.enqueue(
                ScrobbleRecord(
                    track = ScrobbleTrack("Synthetic Artist", "Title $index", durationMs = 200_000),
                    timestampSeconds = 1_700_000_000L + index,
                    durationSeconds = 200,
                ),
            )
        }
        val harness = Harness(this, LastFmTestTransport(), store)
        harness.fake.respond = { scrobbleResponse(accepted = LASTFM_MAX_BATCH) }

        val first = harness.scrobbler.flush()
        assertEquals(LastFmFlushStatus.Sent, first.status)
        assertEquals(LASTFM_MAX_BATCH, first.accepted)
        assertEquals(1, harness.scrobbler.state.value.pendingScrobbles)
        assertTrue("track[49]" in harness.fake.formFor("track.scrobble"))
        assertTrue("track[50]" !in harness.fake.formFor("track.scrobble"))

        harness.fake.respond = { scrobbleResponse(accepted = 1) }
        assertEquals(1, harness.scrobbler.flush().accepted)
        assertEquals(0, harness.scrobbler.state.value.pendingScrobbles)
    }

    @Test
    fun expiredSessionBlocksRequestsAndKeepsTheQueue() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.fake.respond = { apiError(9) }
        harness.playback.start()
        harness.playback.play(150)
        val blocked = harness.scrobbler.flush()
        assertEquals(LastFmFlushStatus.Blocked, blocked.status)
        assertEquals(LastFmFailure.SessionExpired, blocked.failure)
        assertEquals(LastFmFailure.SessionExpired, harness.scrobbler.state.value.lastFailure)
        assertEquals(1, harness.scrobbler.state.value.pendingScrobbles)

        val requestsSoFar = harness.fake.requests.size
        assertEquals(LastFmFlushStatus.Blocked, harness.scrobbler.flush().status)
        assertEquals(requestsSoFar, harness.fake.requests.size)
    }

    @Test
    fun rejectedCredentialsStopFurtherRequests() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.fake.respond = { apiError(10) }
        harness.playback.start()
        harness.playback.play(150)
        assertEquals(LastFmFlushStatus.Blocked, harness.scrobbler.flush().status)
        assertEquals(LastFmFailure.RejectedCredentials, harness.scrobbler.state.value.lastFailure)
        val requestsSoFar = harness.fake.requests.size
        assertEquals(LastFmFlushStatus.Blocked, harness.scrobbler.flush().status)
        assertEquals(requestsSoFar, harness.fake.requests.size)
    }

    @Test
    fun authorizationStoresTheSessionAndUsesTheBrowserUrl() = runTest {
        val harness = Harness(this, LastFmTestTransport(), InMemoryKeyValueStore())
        harness.fake.respond = { jsonBody("""{"token":"request-token"}""") }
        val start = assertIs<LastFmAuthorizationStart.Ready>(harness.scrobbler.beginAuthorization())
        assertEquals("request-token", start.token)
        assertEquals(lastFmAuthorizationUrl(testCredentials().apiKey, "request-token"), start.url)
        assertTrue(start.url.startsWith("https://www.last.fm/api/auth/?api_key="))

        harness.fake.respond = { jsonBody(SESSION_FIXTURE) }
        val connected = assertIs<LastFmAuthorizationResult.Connected>(harness.scrobbler.completeAuthorization(start.token))
        assertEquals("synthetic_listener", connected.session.username)
        assertEquals("synthetic-session-key", harness.store.getString(LastFmScrobbler.SESSION_KEY))
        assertEquals("synthetic_listener", harness.store.getString(LastFmScrobbler.SESSION_USER))
        assertEquals("synthetic_listener", harness.scrobbler.state.value.username)
        assertTrue(harness.scrobbler.state.value.connected)
    }

    @Test
    fun authorizationWithoutCredentialsDoesNothing() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.config = LastFmConfig(enabled = true, credentials = null)
        assertEquals(LastFmAuthorizationStart.NotConfigured, harness.scrobbler.beginAuthorization())
        assertTrue(harness.fake.requests.isEmpty())
    }

    @Test
    fun logoutRemovesTheSessionAndEveryQueuedListen() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.fake.respond = { transportFailure(HttpFailureKind.Network, "network request failed") }
        harness.playback.start()
        harness.playback.play(150)
        harness.scrobbler.flush()
        assertEquals(1, harness.scrobbler.state.value.pendingScrobbles)

        harness.scrobbler.disconnect()
        assertNull(harness.store.getString(LastFmScrobbler.SESSION_KEY))
        assertNull(harness.store.getString(LastFmScrobbler.SESSION_USER))
        assertEquals(0, harness.scrobbler.state.value.pendingScrobbles)
        assertNull(harness.scrobbler.state.value.username)
        assertNull(harness.scrobbler.state.value.lastFailure)
        assertEquals(0, ScrobbleQueue(harness.store).size)
    }

    @Test
    fun shortTracksAreNeverQueued() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        val short = track.copy(id = "short", durationMs = 20_000)
        harness.playback.start()
        harness.playback.play(60, track = short)
        advanceUntilIdle()
        assertEquals(0, harness.scrobbler.state.value.pendingScrobbles)
        assertTrue(harness.fake.requests.none { it.form["method"] == "track.scrobble" })
    }

    @Test
    fun nowPlayingIsSentOncePerOccurrence() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.fake.respond = { jsonBody(NOW_PLAYING_FIXTURE) }
        harness.playback.start()
        advanceUntilIdle()
        assertEquals(1, harness.fake.countFor("track.updateNowPlaying"))
        val form = harness.fake.formFor("track.updateNowPlaying")
        assertEquals("Synthetic Artist", form["artist"])
        assertEquals("synthetic-session-key", form["sk"])

        // More samples of the same occurrence do not repeat "now playing".
        harness.playback.play(30)
        advanceUntilIdle()
        assertEquals(1, harness.fake.countFor("track.updateNowPlaying"))

        // A new occurrence of the same track is announced again.
        harness.playback.start(occurrenceId = 2, discontinuityId = 2)
        advanceUntilIdle()
        assertEquals(2, harness.fake.countFor("track.updateNowPlaying"))
    }

    @Test
    fun seekDoesNotScrobbleAndResumedListeningDoes() = runTest {
        val harness = Harness(this, LastFmTestTransport())
        harness.playback.start()
        harness.playback.play(20)
        // Skip two minutes ahead with only one second of wall time.
        harness.playback.seekTo(140_000)
        harness.playback.play(129)
        assertTrue(harness.fake.requests.none { it.form["method"] == "track.scrobble" })
        harness.playback.play(40)
        assertEquals(1, harness.scrobbler.state.value.pendingScrobbles)
    }

    private inner class Harness(
        scope: TestScope,
        transport: LastFmTestTransport?,
        val store: InMemoryKeyValueStore = sessionStore(),
        clock: TestClock = TestClock(),
    ) {
        private val testTransport: LastFmTestTransport? = transport

        /** Non-null view for tests that script responses; the null-transport test uses [scrobbler] only. */
        val fake: LastFmTestTransport get() = requireNotNull(testTransport) { "this harness has no transport" }
        var config = LastFmConfig(enabled = true, credentials = testCredentials())
        val clock = clock
        val scrobbler = LastFmScrobbler(
            store = store,
            scope = scope,
            transport = testTransport,
            clock = clock::read,
            config = { config },
            backgroundFlush = false,
            ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        )
        val playback = Playback(scrobbler, track)
    }

    private class TestClock(var nowMs: Long = 1_700_000_000_000L) {
        fun read(): Long = nowMs
    }

    /** Feeds one monotonic second at a time; the first sample is the baseline the tracker needs. */
    private class Playback(private val scrobbler: LastFmScrobbler, private val track: Track?) {
        private var monotonicMs = 0L
        private var positionMs = 0L
        private var occurrenceId = 1L
        private var discontinuityId = 1L

        fun start(occurrenceId: Long = 1, discontinuityId: Long = 1) {
            this.occurrenceId = occurrenceId
            this.discontinuityId = discontinuityId
            sample()
        }

        fun play(seconds: Int, track: Track? = this.track) {
            repeat(seconds) {
                monotonicMs += 1_000
                positionMs += 1_000
                scrobbler.observePlayback(
                    track = track,
                    occurrenceId = occurrenceId,
                    discontinuityId = discontinuityId,
                    positionMs = positionMs,
                    durationMs = track?.durationMs ?: 0,
                    playing = true,
                    elapsedRealtimeMs = monotonicMs,
                )
            }
        }

        fun seekTo(targetMs: Long) {
            monotonicMs += 1_000
            positionMs = targetMs
            // The platform reports a new discontinuity for an explicit seek.
            discontinuityId += 1
            scrobbler.observePlayback(
                track = track,
                occurrenceId = occurrenceId,
                discontinuityId = discontinuityId,
                positionMs = positionMs,
                durationMs = track?.durationMs ?: 0,
                playing = true,
                elapsedRealtimeMs = monotonicMs,
            )
        }

        private fun sample() {
            scrobbler.observePlayback(
                track = track,
                occurrenceId = occurrenceId,
                discontinuityId = discontinuityId,
                positionMs = positionMs,
                durationMs = track?.durationMs ?: 0,
                playing = true,
                elapsedRealtimeMs = monotonicMs,
            )
        }
    }
}
