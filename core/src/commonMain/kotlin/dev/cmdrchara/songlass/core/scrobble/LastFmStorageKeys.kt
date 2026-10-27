package dev.cmdrchara.songlass.core.scrobble

/**
 * Every key-value entry the Last.fm feature owns.
 *
 * The `lastfm.` prefix is deliberate: the `.songlass` v1 backup exports only `settings.*`,
 * `library.*` and `home.*`, so API credentials, the session key and the pending queue never travel
 * in a shared backup file or to another device. A consequence is that a restore leaves scrobbling
 * unconfigured, which the settings page explains.
 *
 * These are plain-text entries protected only by the operating system's app data sandbox; see
 * `docs/design/lastfm-scrobbling-2026-10-27.md`.
 */
object LastFmStorageKeys {
    /** The user's opt-in switch. */
    const val ENABLED: String = "lastfm.enabled"

    /** Last.fm API key; never committed to the repository. */
    const val API_KEY: String = "lastfm.api_key"

    /** Last.fm shared secret used to sign requests; never committed to the repository. */
    const val API_SECRET: String = "lastfm.api_secret"

    /** Secret `sk` of the granted session. */
    const val SESSION_KEY: String = "lastfm.session.key"

    /** Last.fm account name shown in the settings page. */
    const val SESSION_USERNAME: String = "lastfm.session.username"

    /** Persistent queue of listens that still have to be sent. */
    const val QUEUE: String = "lastfm.queue.v1"

    /** All of them, removed together by "disconnect and forget". */
    val ALL: Set<String> = setOf(ENABLED, API_KEY, API_SECRET, SESSION_KEY, SESSION_USERNAME, QUEUE)
}
