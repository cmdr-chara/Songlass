package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.net.HttpTransport
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Everything the Last.fm settings page needs to describe the current state. */
data class LastFmState(
    /** False on platforms without a tested HTTP engine (iOS): the whole feature is hidden. */
    val supported: Boolean = false,
    /** An API key and secret are present. Without them scrobbling cannot run. */
    val configured: Boolean = false,
    /** The opt-in switch the user controls. */
    val enabled: Boolean = false,
    /** Last.fm account name after a successful authorization. */
    val username: String? = null,
    /** Listens waiting for a connection, a retry or a new session. */
    val pendingScrobbles: Int = 0,
    /** Why the last call failed, until a successful call clears it. */
    val lastFailure: LastFmFailure? = null,
    /** Epoch milliseconds of the last accepted scrobble batch, if any. */
    val lastScrobbleAtMs: Long? = null,
) {
    val connected: Boolean get() = username != null
}

/** Result of one flush attempt, for the settings page and for tests. */
data class LastFmFlushOutcome(
    val status: LastFmFlushStatus,
    val accepted: Int = 0,
    val ignored: Int = 0,
    val failure: LastFmFailure? = null,
)

enum class LastFmFlushStatus {
    /** Nothing was due: no queued listen has reached its retry time. */
    Idle,

    /** At least one listen was accepted by Last.fm. */
    Sent,

    /** The batch is queued again with a later retry time. */
    RetryScheduled,

    /** The batch was discarded: too many retryable failures or an unusable request. */
    Dropped,

    /** The user has not enabled scrobbling, or no complete API key pair is configured. */
    Disabled,

    /** The platform has no tested HTTP engine: iOS. */
    Unsupported,

    /** Scrobbling is enabled but no Last.fm account is connected yet. */
    NotConnected,

    /** A previous failure needs the user to reconnect or fix the credentials: no request was made. */
    Blocked,
}

/** First step of the authorization flow: a token and the page to authorize it on. */
sealed interface LastFmAuthorizationStart {
    data class Ready(val token: String, val url: String) : LastFmAuthorizationStart
    data object Unsupported : LastFmAuthorizationStart
    data object NotConfigured : LastFmAuthorizationStart
    data class Failed(val failure: LastFmFailure) : LastFmAuthorizationStart
}

/** Second step: the token has been authorized on the website and is traded for a session. */
sealed interface LastFmAuthorizationResult {
    data class Connected(val session: LastFmSession) : LastFmAuthorizationResult
    data object Unsupported : LastFmAuthorizationResult
    data object NotConfigured : LastFmAuthorizationResult
    data class Failed(val failure: LastFmFailure) : LastFmAuthorizationResult
}

/**
 * Native Last.fm scrobbler: opt-in, metadata only, never on the playback thread.
 *
 * Responsibilities:
 * - observe playback samples and turn real listening into queued listens (Last.fm's own rule);
 * - send "now playing" for the current occurrence, throttled and never persisted;
 * - flush the durable queue in batches of at most 50 with exponential backoff;
 * - own the authorization flow and the stored session.
 *
 * It never writes preferences: [config] reads the opt-in switch and the API credentials, and the
 * settings page owns those writes. That keeps a disabled or unconfigured feature free of side
 * effects and makes the whole class testable with a fake transport.
 *
 * [scope] must outlive the UI (the process scope) and be confined to a single thread, like the
 * other persistent stores here. All network work happens on [Dispatchers.Default].
 */
class LastFmScrobbler(
    private val store: KeyValueStore,
    private val scope: CoroutineScope,
    private val transport: HttpTransport?,
    private val clock: () -> Long,
    private val config: () -> LastFmConfig,
    /** Tests disable the background loop and call [flush] explicitly. */
    private val backgroundFlush: Boolean = true,
    private val flushIntervalMs: Long = 60_000L,
    private val nowPlayingThrottleMs: Long = 15_000L,
    /** JSON parsing and response handling stay off the playback thread; tests pin it. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val queue = ScrobbleQueue(store)
    private val tracker = LastFmListenTracker()
    private val flushMutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private var session: LastFmSession? = readSession()
    private var lastFailure: LastFmFailure? = null
    private var failedSessionKey: String? = null
    private var failedCredentials: LastFmCredentials? = null
    private var lastScrobbleAtMs: Long? = null
    private var occurrenceStartEpochMs = 0L
    private var lastNowPlayingKey: String? = null
    private var lastNowPlayingAtMs = 0L
    private var nowPlayingInFlight = false

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<LastFmState> = _state.asStateFlow()

    init {
        if (backgroundFlush) {
            scope.launch {
                while (isActive) {
                    // A queued listen, a finished authorization or the periodic tick all flush.
                    withTimeoutOrNull(flushIntervalMs) { wake.receive() }
                    flush()
                }
            }
        }
    }

    /**
     * One playback sample, from the same combined stream that drives the play counter.
     *
     * Returns immediately: only the tracker and the local queue run on the calling thread. Network
     * calls are launched in [scope] so a slow or missing connection can never stall the player.
     */
    fun observePlayback(
        track: Track?,
        occurrenceId: Long,
        discontinuityId: Long,
        positionMs: Long,
        durationMs: Long,
        playing: Boolean,
        elapsedRealtimeMs: Long,
    ) {
        val metadata = track?.let(ScrobbleTrack::from)
        val observation = tracker.observe(
            trackKey = track?.id,
            occurrenceId = occurrenceId,
            discontinuityId = discontinuityId,
            positionMs = positionMs,
            durationMs = if (metadata != null) durationMs else 0,
            playing = playing,
            elapsedRealtimeMs = elapsedRealtimeMs,
        )
        if (observation.occurrenceStarted) {
            val position = positionMs.coerceAtLeast(0)
            occurrenceStartEpochMs = (clock() - position).coerceAtLeast(0)
            lastNowPlayingKey = null
        }
        val credentials = config().takeIf { it.isActive }?.credentials ?: return
        val transport = transport ?: return
        if (metadata == null) return
        if (observation.occurrenceStarted && playing) {
            requestNowPlaying(transport, credentials, metadata)
        }
        if (observation.scrobbleDue) {
            val timestamp = if (occurrenceStartEpochMs > 0) occurrenceStartEpochMs / 1000 else clock() / 1000
            val record = ScrobbleRecord(metadata, timestampSeconds = timestamp, durationSeconds = metadata.durationSeconds)
            if (queue.enqueue(record)) {
                publish()
                wake.trySend(Unit)
            }
        }
    }

    /** `auth.getToken`: starts an authorization. Needs a complete key pair, not the opt-in switch. */
    suspend fun beginAuthorization(): LastFmAuthorizationStart {
        val transport = transport ?: return LastFmAuthorizationStart.Unsupported
        val credentials = config().credentials?.takeIf { it.isComplete }
            ?: return LastFmAuthorizationStart.NotConfigured
        val result = withContext(ioDispatcher) { LastFmClient(transport, credentials).requestToken() }
        return when (result) {
            is LastFmResult.Success -> LastFmAuthorizationStart.Ready(
                token = result.value,
                url = lastFmAuthorizationUrl(credentials.apiKey, result.value),
            )
            is LastFmResult.Failure -> LastFmAuthorizationStart.Failed(result.reason)
        }
    }

    /** `auth.getSession`: stores the session key and the account name after the user authorized. */
    suspend fun completeAuthorization(token: String): LastFmAuthorizationResult {
        val transport = transport ?: return LastFmAuthorizationResult.Unsupported
        val credentials = config().credentials?.takeIf { it.isComplete }
            ?: return LastFmAuthorizationResult.NotConfigured
        val result = withContext(ioDispatcher) { LastFmClient(transport, credentials).createSession(token) }
        return when (result) {
            is LastFmResult.Success -> {
                session = result.value
                store.putString(SESSION_KEY, result.value.key)
                store.putString(SESSION_USER, result.value.username)
                clearFailure()
                publish()
                wake.trySend(Unit)
                LastFmAuthorizationResult.Connected(result.value)
            }
            is LastFmResult.Failure -> {
                recordFailure(result.reason)
                publish()
                LastFmAuthorizationResult.Failed(result.reason)
            }
        }
    }

    /**
     * Logout: removes the stored session key and every queued listen. The opt-in switch and the API
     * credentials are preferences and stay until the user changes them.
     */
    fun disconnect() {
        session = null
        store.updateStrings(removing = setOf(SESSION_KEY, SESSION_USER), writing = emptyMap())
        queue.clear()
        tracker.reset()
        clearFailure()
        occurrenceStartEpochMs = 0L
        lastNowPlayingKey = null
        lastNowPlayingAtMs = 0L
        publish()
    }

    /** Called after the user changes the switch, the key pair or a session; retries immediately. */
    fun refresh() {
        clearFailure()
        queue.clearBackoff()
        publish()
        wake.trySend(Unit)
    }

    /**
     * Re-reads the stored session and queue without touching the network, for example after another
     * component removed their keys. Preferences are still read through [config] on demand.
     */
    fun reloadFromStorage() {
        session = readSession()
        queue.reload()
        publish()
    }

    /** Asks the background loop to flush now. Safe to call from the main thread. */
    fun requestFlush() {
        wake.trySend(Unit)
    }

    /** Sends at most one batch of due listens, honouring the stored backoff. */
    suspend fun flush(): LastFmFlushOutcome = flushMutex.withLock {
        val transport = transport ?: return@withLock LastFmFlushOutcome(LastFmFlushStatus.Unsupported)
        val current = config()
        val credentials = current.credentials?.takeIf { it.isComplete }
        if (!current.enabled || credentials == null) {
            publish()
            return@withLock LastFmFlushOutcome(LastFmFlushStatus.Disabled)
        }
        val session = session ?: run {
            publish()
            return@withLock LastFmFlushOutcome(LastFmFlushStatus.NotConnected)
        }
        val blocked = (lastFailure?.requiresReauthentication == true && failedSessionKey == session.key) ||
            (lastFailure?.requiresConfiguration == true && failedCredentials == credentials)
        if (blocked) {
            return@withLock LastFmFlushOutcome(LastFmFlushStatus.Blocked, failure = lastFailure)
        }
        val now = clock()
        val due = queue.due(now).take(LASTFM_MAX_BATCH)
        if (due.isEmpty()) return@withLock LastFmFlushOutcome(LastFmFlushStatus.Idle)
        val keys = due.mapTo(mutableSetOf()) { it.key }
        // Removed before the request: a crash loses these listens instead of scrobbling twice.
        queue.remove(keys)
        publish()
        val result = withContext(ioDispatcher) { LastFmClient(transport, credentials).scrobble(session.key, due.map { it.record }) }
        when (result) {
            is LastFmResult.Success -> {
                clearFailure()
                lastScrobbleAtMs = clock()
                publish()
                LastFmFlushOutcome(LastFmFlushStatus.Sent, accepted = result.value.accepted, ignored = result.value.ignored)
            }
            is LastFmResult.Failure -> {
                lastFailure = result.reason
                val outcome = when {
                    // The session is gone: keep every listen for after the reconnection.
                    result.reason.requiresReauthentication -> {
                        failedSessionKey = session.key
                        queue.requeue(due)
                        LastFmFlushOutcome(LastFmFlushStatus.Blocked, failure = result.reason)
                    }
                    // A rejected key or a suspension needs the user; queued listens are kept.
                    result.reason.requiresConfiguration -> {
                        failedCredentials = credentials
                        queue.requeue(due)
                        LastFmFlushOutcome(LastFmFlushStatus.Blocked, failure = result.reason)
                    }
                    // Retryable and unusable responses share the backoff, which caps the attempts.
                    else -> {
                        val retried = due.map {
                            it.copy(
                                attempts = it.attempts + 1,
                                nextAttemptMs = clock() + ScrobbleBackoff.delayMs(it.attempts + 1),
                            )
                        }
                        val keep = retried.filter { it.attempts < ScrobbleBackoff.MAX_ATTEMPTS }
                        queue.requeue(keep)
                        if (keep.isEmpty()) {
                            LastFmFlushOutcome(LastFmFlushStatus.Dropped, failure = result.reason)
                        } else {
                            LastFmFlushOutcome(LastFmFlushStatus.RetryScheduled, failure = result.reason)
                        }
                    }
                }
                publish()
                outcome
            }
        }
    }

    private fun requestNowPlaying(transport: HttpTransport, credentials: LastFmCredentials, track: ScrobbleTrack) {
        val session = session ?: return
        if (lastFailure?.requiresReauthentication == true && failedSessionKey == session.key) return
        if (nowPlayingInFlight) return
        val now = clock()
        val key = track.artist + '\u0000' + track.title
        if (key == lastNowPlayingKey && now - lastNowPlayingAtMs < nowPlayingThrottleMs) return
        nowPlayingInFlight = true
        scope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    LastFmClient(transport, credentials).updateNowPlaying(session.key, track)
                }
                when (result) {
                    is LastFmResult.Success -> {
                        lastNowPlayingKey = key
                        lastNowPlayingAtMs = clock()
                    }
                    // Now playing is best effort and never queued; only the status hint changes.
                    is LastFmResult.Failure -> {
                        lastFailure = result.reason
                        publish()
                    }
                }
            } finally {
                nowPlayingInFlight = false
            }
        }
    }

    private fun recordFailure(reason: LastFmFailure) {
        lastFailure = reason
        when {
            reason.requiresReauthentication -> failedSessionKey = session?.key
            reason.requiresConfiguration -> failedCredentials = config().credentials
        }
    }

    private fun clearFailure() {
        lastFailure = null
        failedSessionKey = null
        failedCredentials = null
    }

    private fun readSession(): LastFmSession? {
        val key = store.getString(SESSION_KEY)?.takeIf(String::isNotBlank) ?: return null
        val username = store.getString(SESSION_USER)?.takeIf(String::isNotBlank) ?: return null
        return LastFmSession(username, key)
    }

    private fun readState(): LastFmState {
        val current = config()
        return LastFmState(
            supported = transport != null,
            configured = current.credentials?.isComplete == true,
            enabled = current.enabled,
            username = session?.username,
            pendingScrobbles = queue.size,
            lastFailure = lastFailure,
            lastScrobbleAtMs = lastScrobbleAtMs,
        )
    }

    private fun publish() {
        _state.value = readState()
    }

    internal companion object {
        const val SESSION_KEY: String = LastFmStorageKeys.SESSION_KEY
        const val SESSION_USER: String = LastFmStorageKeys.SESSION_USERNAME
    }
}
