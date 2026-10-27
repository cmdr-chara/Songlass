package dev.cmdrchara.songlass.core.scrobble

import dev.cmdrchara.songlass.core.model.Track

/** The application credentials Last.fm issues for one registered API account. */
data class LastFmCredentials(val apiKey: String, val apiSecret: String) {
    /** Both halves are needed: a key alone cannot sign a request. */
    val isComplete: Boolean get() = apiKey.isNotBlank() && apiSecret.isNotBlank()
}

/** A granted Last.fm session: the display name and the secret `sk` used to sign authenticated calls. */
data class LastFmSession(val username: String, val key: String)

/**
 * The metadata this feature is allowed to send.
 *
 * Built from tags only: never a file path, file name, audio, lyrics or anything else from the
 * device. The artist and title are required by Last.fm; album, duration and track number are
 * optional hints.
 */
data class ScrobbleTrack(
    val artist: String,
    val title: String,
    val album: String? = null,
    val durationMs: Long = 0,
    val trackNumber: Int? = null,
) {
    /** Last.fm accepts whole seconds; unknown durations stay 0 and are omitted from requests. */
    val durationSeconds: Long get() = if (durationMs <= 0) 0 else ((durationMs + 500) / 1000).coerceAtLeast(1)

    companion object {
        /**
         * Maps a library [Track] to the scrobble payload, or null when there is no artist or title
         * to send. The player's URI, path and file name are deliberately dropped here: they never
         * enter this type, so no later code can leak them.
         */
        fun from(track: Track): ScrobbleTrack? {
            val artist = track.artist?.trim()?.takeIf(String::isNotEmpty)
                ?: track.artistNames.joinToString(", ").takeIf(String::isNotEmpty)
                ?: return null
            val title = track.title.trim().takeIf(String::isNotEmpty) ?: return null
            return ScrobbleTrack(
                artist = artist,
                title = title,
                album = track.album?.trim()?.takeIf(String::isNotEmpty),
                durationMs = track.durationMs.coerceAtLeast(0),
                trackNumber = track.trackNumber,
            )
        }
    }
}

/**
 * One listen that satisfies Last.fm's rule, ready for `track.scrobble`.
 *
 * [timestampSeconds] is when the listen *started* (Last.fm's documented meaning) in Unix seconds.
 */
data class ScrobbleRecord(
    val track: ScrobbleTrack,
    val timestampSeconds: Long,
    val durationSeconds: Long,
) {
    /** Stable identity of the occurrence, used to drop exact duplicates. */
    val dedupeKey: String get() = "$timestampSeconds\u0000${track.artist}\u0000${track.title}"
}

/** The opt-in switch and the credentials, as read from preferences when a request is prepared. */
data class LastFmConfig(
    val enabled: Boolean = false,
    val credentials: LastFmCredentials? = null,
) {
    /** Scrobbling can only run when the user opted in and provided a complete key pair. */
    val isActive: Boolean get() = enabled && credentials?.isComplete == true
}

/** Why a call failed, in terms the settings page can explain. */
enum class LastFmFailure {
    /** The request never reached Last.fm: no connectivity, DNS failure or timeout. */
    Offline,

    /** HTTP 429 or API code 29: too many requests in a short period. */
    RateLimited,

    /** Temporary server or service problem (API codes 8, 11, 16; HTTP 5xx). */
    ServerError,

    /** The session key is no longer accepted (9, 14, 15, 17): the user must reconnect. */
    SessionExpired,

    /** The credentials were rejected (4, 10, 13): the API key/secret pair is wrong. */
    RejectedCredentials,

    /** The API key was suspended by Last.fm (26). */
    Suspended,

    /** The response could not be used: not JSON, missing fields or an unknown error code. */
    MalformedResponse,
    ;

    /** Whether repeating the same call later can succeed. */
    val retryable: Boolean
        get() = this == Offline || this == RateLimited || this == ServerError

    /** Whether the user has to reconnect before anything else can be sent. */
    val requiresReauthentication: Boolean
        get() = this == SessionExpired

    /** Whether the credentials or the configuration have to be corrected first. */
    val requiresConfiguration: Boolean
        get() = this == RejectedCredentials || this == Suspended
}

/** Result of one Last.fm call: a value, or a classified failure. Nothing else is thrown. */
sealed interface LastFmResult<out T> {
    data class Success<T>(val value: T) : LastFmResult<T>
    data class Failure(val reason: LastFmFailure) : LastFmResult<Nothing>

    val isSuccess: Boolean get() = this is Success<*>
}

/** What `track.scrobble` accepted and ignored for one batch. */
data class ScrobbleBatchReport(val accepted: Int, val ignored: Int)

/** Token plus browser URL for the first step of Last.fm's desktop/mobile authorization flow. */
data class LastFmAuthorization(val token: String, val url: String)
